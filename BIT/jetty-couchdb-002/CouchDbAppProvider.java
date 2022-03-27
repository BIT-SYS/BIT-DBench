/*
Copyright (c) 2010, Jesper André Lyngesen Pedersen
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

 - Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.

 - Redistributions in binary form must reproduce the above copyright
   notice, this list of conditions and the following disclaimer in the
   documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package org.signaut.jetty.deploy.providers.couchdb;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.concurrent.atomic.AtomicLong;

import org.codehaus.jackson.map.ObjectMapper;
import org.eclipse.jetty.deploy.App;
import org.eclipse.jetty.deploy.AppProvider;
import org.eclipse.jetty.deploy.DeploymentManager;
import org.eclipse.jetty.security.Authenticator;
import org.eclipse.jetty.security.Authenticator.Factory;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.SessionManager;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.webapp.WebAppContext;
import org.signaut.common.http.SimpleHttpClient.HttpResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CouchDbAppProvider extends AbstractLifeCycle implements AppProvider {

    public interface SessionManagerProvider {
        /**
         * Create a new instance of a SessionManager
         * 
         * @return
         */
        SessionManager get();
    }

    private DeploymentManager deploymentManager;
    private final CouchDbDeployerProperties couchDeployerProperties;
    private final Authenticator.Factory authenticatorFactory;
    private final SessionManagerProvider sessionManagerProvider;
    private final CouchDbClient couchDbClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Logger log = LoggerFactory.getLogger(getClass());
    /*
     * Latest couchdb sequence. Used in the event the connection between
     * this and couchdb is broken. If we did not have the latest sequence, all
     * apps would be redeployed, and we don't want that.
     */
    private final AtomicLong sequence = new AtomicLong();
    
    private Thread changeListenerThread;
    private String serverClasses[] = { "com.google.inject.", "org.slf4j.", "org.apache.log4j." };
    private String systemClasses[] = null;

    public CouchDbAppProvider(CouchDbDeployerProperties couchDeployerProperties, Factory authenticatorFactory,
                              SessionManagerProvider sessionManagerProvider) {
        this.couchDeployerProperties = couchDeployerProperties;
        this.authenticatorFactory = authenticatorFactory;
        this.sessionManagerProvider = sessionManagerProvider;
        couchDbClient = new CouchDbClientImpl(couchDeployerProperties.getDatabaseUrl(), couchDeployerProperties.getUsername(),
                couchDeployerProperties.getPassword());
        
    }

    @Override
    protected void doStart() {
        if (changeListenerThread != null) {
            throw new IllegalArgumentException("Already running");
        }
        changeListenerThread = new ChangeListener();
        changeListenerThread.start();
    }
    
    private final class ChangeListener extends Thread {
        final HttpResponseHandler<Void> changeSetHandler = new HttpResponseHandler<Void>(){

            @Override
            public Void handleInput(int responseCode, HttpURLConnection connection) {
                try {
                    final BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String change;
                    while ((change = reader.readLine())!=null) {
                        final ChangeSet changeSet = decode(change, ChangeSet.class);
                        if (changeSet == null || changeSet.getSequence() == null) {
                            if (changeSet != null && changeSet.getLastSequence() == null) {
                                throw new IllegalStateException(String.format("bad change: %s", change));
                            }
                            continue;
                        }
                        if ( ! changeSet.isDeleted()) {
                            final WebAppDocument webapp = couchDbClient.getDocument(changeSet.getId(), WebAppDocument.class);
                            if (webapp != null) {
                                //undeploy existing app at this app's context path
                                final App oldApp = deploymentManager.getAppByOriginId(changeSet.getId());
                                if (oldApp != null) {
                                    log.debug("Undeploying {} at {}", oldApp.getOriginId(), oldApp.getContextPath());
                                    deploymentManager.removeApp(oldApp);
                                }
                            }
                            deploymentManager.addApp(new App(deploymentManager, CouchDbAppProvider.this, changeSet.getId()));
                            ContextHandlerCollection chc = deploymentManager.getContexts();
                            for (Handler c: chc.getHandlers()) {
                                log.debug(String.format("Context: %s (isRunning: %s)",c,c.isRunning() ));
                            }
                            //log.debug(deploymentManager.getServer().dump());
                        }
                        sequence.set(changeSet.getSequence());
                    }
                } catch (IOException e) {
                    //Ignore
                }
                return null;
            }};
        
        @Override
        public void run() {
            while (isRunning()) {
                try {
                    log.info("CouchDB sequence: " + sequence.get());
                    couchDbClient.get("/_changes?feed=continuous" +
                                      "&heartbeat="+couchDeployerProperties.getHeartbeat()+
                                      "&filter="+couchDeployerProperties.getFilter()+
                                      "&since="+sequence.get(), 
                                      changeSetHandler);
                } catch (Throwable t) {
                    log.error("While listening for changes", t);
                }
            }
        }
    }
    
    @Override
    public void setDeploymentManager(DeploymentManager deploymentManager) {
        if (isRunning()) {
            throw new IllegalStateException("running");
        }
        this.deploymentManager = deploymentManager;
    }

    @Override
    public ContextHandler createContextHandler(App app) throws Exception {
        final WebAppDocument webapp = couchDbClient.getDocument(app.getOriginId(), WebAppDocument.class);
        if (webapp.getWar() == null) {
            deploymentManager.removeApp(app);
            throw new IllegalArgumentException("No war file for " + webapp); 
        }

        final File directory = new File(couchDeployerProperties.getTemporaryDirectory()+"/"+app.getOriginId());
        //Point war to full path of downloaded file
        final String path = couchDbClient.downloadAttachment(app.getOriginId(), webapp.getWar(), directory);
        if (path == null) {
            deploymentManager.removeApp(app);
            throw new IllegalArgumentException("War file not found: " + webapp); 
        }
        webapp.setWar(path);
        return createContext(webapp);
    }

    private ContextHandler createContext(WebAppDocument desc) {
        log.info("Creating new context for " + desc);
        final WebAppContext context = new WebAppContext(desc.getName(), desc.getContextPath());
        context.setServerClasses(concat(context.getServerClasses(), serverClasses));
        context.setSystemClasses(concat(context.getSystemClasses(), systemClasses));

        context.setWar(desc.getWar());
        final ErrorHandler errorHandler = new JsonErrorHandler();
        errorHandler.setShowStacks(desc.isShowingFullStacktrace());
        context.setErrorHandler(errorHandler);
        context.getSecurityHandler().setAuthenticatorFactory(authenticatorFactory);
        context.setSessionHandler(new SessionHandler(sessionManagerProvider.get()));
        context.setParentLoaderPriority(false);
        return context;
    }

    private <T> T decode(String str, Class<T> type) {
        try {
            return objectMapper.readValue(str, type);
        } catch (EOFException e) {
            return null;
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format("While parsing %s as %s", str, type), e);
        }
    }
    
    
    public void setServerClasses(String[] serverClasses) {
        this.serverClasses = serverClasses;
    }

    public void setSystemClasses(String[] systemClasses) {
        this.systemClasses = systemClasses;
    }

    private final String[] concat(String[] l, String[] r) {
        if (l == null || l.length == 0) {
            return r;
        }
        if (r == null || r.length == 0) {
            return l;
        }
        final String combined[] = new String[l.length + r.length];
        System.arraycopy(l, 0, combined, 0, l.length);
        System.arraycopy(r, 0, combined, l.length, r.length);
        return combined;
    }

}
