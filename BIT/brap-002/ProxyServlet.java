package no.tornado.brap.servlet;

import no.tornado.brap.auth.*;
import no.tornado.brap.common.InputStreamArgumentPlaceholder;
import no.tornado.brap.common.InvocationRequest;
import no.tornado.brap.common.InvocationResponse;
import no.tornado.brap.exception.RemotingException;
import no.tornado.brap.modification.ChangesIgnoredModificationManager;
import no.tornado.brap.modification.ModificationManager;

import javax.servlet.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * This ProxyServlet is configured from web.xml for each service you wish
 * to expose as a remoting service.
 * <p/>
 * This class provides the basic capabilities needed to instantiate and expose
 * a remoting service. Only <code>service</code> is required. The rest of
 * the parameters have default values.
 * <p/>
 * Concider subclassing to provide custom creation by overriding
 * <code>getServiceWrapper</code> or just one of
 * <code>getAuthenticationProvider</code> or <code>getAuthorizationProvider</code>.
 * <p/>
 * <p>Example of exposing a remoting service without requiring authentication:</p>
 * <pre>
 *  &lt;servlet&gt;
 *      &lt;servlet-name&gt;hello&lt;/servlet-name&gt;
 *      &lt;servlet-class&gt;no.tornado.brap.servlet.ProxyServlet&lt;/servlet-class&gt;
 *      &lt;init-param&gt;
 *          &lt;param-name&gt;service&lt;/param-name&gt;
 *          &lt;param-value&gt;class.of.the.service.to.instantiate&lt;/param-value&gt;
 *      &lt;/init-param&gt;
 *      &lt;init-param&gt;
 *          &lt;param-name&gt;authorizationProvider&lt;/param-name&gt;
 *          &lt;param-value&gt;no.tornado.brap.auth.AuthenticationNotRequiredAuthorizer&lt;/param-value&gt;
 *      &lt;/init-param&gt;
 *      &lt;load-on-startup&gt;1&lt;/load-on-startup&gt;
 *  &lt;/servlet&gt;
 *  &lt;servlet-mapping&gt;
 *      &lt;servlet-name&gt;hello&lt;/servlet-name&gt;
 *      &lt;url-pattern&gt;/remoting/hello&lt;/url-pattern&gt;
 *  &lt;/servlet-mapping&gt;
 * </pre>
 */
public class ProxyServlet implements Servlet {
    private static final Integer DEFAULT_STREAM_BUFFER_SIZE = 16384;

    public final String INIT_PARAM_AUTHENTICATION_PROVIDER = "authenticationProvider";
    public final String INIT_PARAM_AUTHORIZATION_PROVIDER = "authorizationProvider";
    public final String INIT_PARAM_MODIFICATION_MANAGER = "modificationManager";

    public final String INIT_PARAM_SERVICE = "service";
    protected ServiceWrapper serviceWrapper;
    protected ServletConfig servletConfig;

    public void init(ServletConfig servletConfig) throws ServletException {
        this.servletConfig = servletConfig;
        try {
            createServiceWrapper();
        } catch (Exception e) {
            throw new ServletException("Failed to instantiate the serviceWrapper", e);
        }
    }

    /**
     * Override this method to control every detail of the creation of the service wrapper.
     * <p/>
     * Normally you would just override one or more of the methods that provide the service wrapper details.
     *
     * @see ProxyServlet#getService()
     * @see ProxyServlet#getAuthenticationProvider()
     * @see ProxyServlet#getAuthorizationProvider()
     */
    public void createServiceWrapper() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        serviceWrapper = new ServiceWrapper();
        serviceWrapper.setService(getService());
        serviceWrapper.setAuthenticationProvider(getAuthenticationProvider());
        serviceWrapper.setAuthorizationProvider(getAuthorizationProvider());
        serviceWrapper.setModificationManager(getModificationManager());
    }

    /**
     * Override to configure a different Authorization Provider. The default provider
     * authorizes every authenticated invocation. In many cases, requiring Authentication and providing
     * an AuthenticationProvider is sufficient, but you can use the Authorization Provider
     * to allow/deny access to spesific method-calls based on the principal in
     * <code>AuthenticationContext#getPrincipal()</code>.
     * <p/>
     * You can either subclass or supply the "authorizationProvider" init-param to
     * change the AuthorizationProvider.
     *
     * @return the AuthorizationProvider
     */
    protected AuthorizationProvider getAuthorizationProvider() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        if (servletConfig.getInitParameter(INIT_PARAM_AUTHORIZATION_PROVIDER) != null)
            return (AuthorizationProvider) Class.forName(servletConfig.getInitParameter(INIT_PARAM_AUTHORIZATION_PROVIDER)).newInstance();

        return new AuthenticationRequiredAuthorizer();
    }

    /**
     * Override to configure a different Authentication Provider. The default provider
     * authenticates every invocation.
     * <p/>
     * You can either subclass or supply the "authenticationProvider" init-param to
     * change the AuthenticationProvider.
     *
     * @return the AuthenticationProvider
     */
    protected AuthenticationProvider getAuthenticationProvider() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        if (servletConfig.getInitParameter(INIT_PARAM_AUTHENTICATION_PROVIDER) != null)
            return (AuthenticationProvider) Class.forName(servletConfig.getInitParameter(INIT_PARAM_AUTHENTICATION_PROVIDER)).newInstance();

        return new AuthenticationNotRequiredAuthenticator();
    }

    /**
     * Supply the service to expose via this servlet.
     * <p/>
     * You can either subclass or supply the "service" init-param to
     * configure what service class to instantiate.
     *
     * @return
     */
    protected Object getService() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        return Class.forName(servletConfig.getInitParameter(INIT_PARAM_SERVICE)).newInstance();
    }

    /**
     * The service method performs the actual deserialization of the InvocationRequest and returns
     * an InvocationResponse in the body of the ServletResponse.
     * <p/>
     * Standard Java object serialization/deserialization is used to retrieve and set the invocation
     * request/response.
     * <p/>
     * The configured <code>AuthenticationProvider</code> and <code>AuthorizationProvider</code>
     * are consulted.
     * <p/>
     * A ThreadLocal in the <code>AuthenticationContext</code> holds on to any principal created during
     * authentication, so that it is available to both the AuthorizationProvider and any service
     * that whishes to get hold of the principal via <code>AuthenticationContext#getPrincipal()</code>.
     * <p/>
     * You are encouraged to use your existing domain object AllowAllAuthorizerfor authentication :)
     *
     * @param request  The ServletRequest
     * @param response the ServletResponse
     * @throws ServletException
     * @throws IOException
     * @see no.tornado.brap.common.InvocationRequest
     * @see no.tornado.brap.common.InvocationResponse
     */
    public void service(ServletRequest request, ServletResponse response) throws ServletException, IOException {
        AuthenticationContext.enter();
        InvocationResponse invocationResponse = null;
        Method method = null;
        Object result = null;
        try {
            invocationResponse = new InvocationResponse();

            InvocationRequest invocationRequest = (InvocationRequest) new ObjectInputStream(request.getInputStream()).readObject();

            serviceWrapper.getAuthenticationProvider().authenticate(invocationRequest);
            serviceWrapper.getAuthorizationProvider().authorize(invocationRequest);
            Object[] proxiedParameters = serviceWrapper.getModificationManager().applyModificationScheme(invocationRequest.getParameters());
            method = getMethod(invocationRequest.getMethodName(), invocationRequest.getParameterTypes());

            // If the first argument was an input-stream, reroute it from the request inputStream
            if (invocationRequest.getParameters().length > 0 && InputStreamArgumentPlaceholder.class.equals(invocationRequest.getParameters()[0].getClass()))
                proxiedParameters[0] = request.getInputStream();

            result = method.invoke(serviceWrapper.getService(), proxiedParameters);
            invocationResponse.setResult((Serializable) result);
            invocationResponse.setModifications(serviceWrapper.getModificationManager().getModifications());

        } catch (Exception e) {
            if (e instanceof InvocationTargetException) {
                InvocationTargetException ite = (InvocationTargetException) e;
                invocationResponse.setException(ite.getTargetException());
            } else {
                if (method != null && method.getExceptionTypes() != null) {
                    for (Class exType : method.getExceptionTypes()) {
                        if (exType.isAssignableFrom(e.getClass()))
                            invocationResponse.setException(e);
                    }
                }
                invocationResponse.setException(new RemotingException(e));
            }
        } finally {
            AuthenticationContext.exit();
            if (result != null && result instanceof InputStream) {
                streamResultToResponse(result, response);
            } else {
                new ObjectOutputStream(response.getOutputStream()).writeObject(invocationResponse);
            }
        }
    }

    private void streamResultToResponse(Object result, ServletResponse response) throws IOException {
        InputStream in = (InputStream) result;
        OutputStream out = response.getOutputStream();
        byte[] buf = new byte[getStreamBufferSize()];
        int len;
        while ((len = in.read(buf)) != -1) {
            out.write(buf, 0, len);
        }
    }

    public ModificationManager getModificationManager() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        if (servletConfig.getInitParameter(INIT_PARAM_MODIFICATION_MANAGER) != null)
            return (ModificationManager) Class.forName(servletConfig.getInitParameter(INIT_PARAM_MODIFICATION_MANAGER)).newInstance();

        return new ChangesIgnoredModificationManager();
    }

    /**
     * Retrieves the <code>java.lang.Reflect.Method</code> to invoke on the wrapped service class.
     * The <code>methodName</code> and <code>parameterTypes</code> arguments are retrieved from the
     * <code>InvocationRequest</code> that was encapsulated in the ServletRequest body.
     *
     * @param methodName
     * @param parameterTypes
     * @return
     * @throws NoSuchMethodException
     */
    private Method getMethod(String methodName, Class[] parameterTypes) throws NoSuchMethodException {
        Class serviceClass = serviceWrapper.getService().getClass();

        while (serviceClass != null) {
            try {
                return serviceClass.getMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException e) {
                serviceClass = serviceClass.getSuperclass();
            }
        }

        throw new NoSuchMethodException(methodName);
    }

    public String getServletInfo() {
        return getClass().getCanonicalName();
    }

    public void destroy() {

    }

    public ServletConfig getServletConfig() {
        return servletConfig;
    }

    public Integer getStreamBufferSize() {
        return DEFAULT_STREAM_BUFFER_SIZE;
    }
}
