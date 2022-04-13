/*
   Java Util Logging RELP Handler
   Copyright (C) 2021  Suomen Kanuuna Oy

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package com.teragrep.jla_04;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Formatter;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

public class RelpConfig {
    private int port; // Relp server port
    private String address; // Relp server address
    private String appname; // appname for syslog message
    private String hostname; // hostname for syslog message
    private String realHostName; // hostname for syslog origin header
    private String name; // logger name
    private Boolean useSD; // if structured data should be used
    private int connectionTimeout; // Relp connection timeout
    private int reconnectInterval; // sleep between relp connection reconnects
    private int readTimeout; // relp connection reading timeout
    private int writeTimeout; // relp connection writing timeout
    private LogManager manager;
    private Formatter formatter;

    public RelpConfig(String name) throws NumberFormatException, IllegalArgumentException, NoSuchFieldException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        initLogger();
        // Name must be set first, others do not depend on the ordering
        initName(name);
        // The rest
        initFormatter();
        initPort();
        initAddress();
        initAppname();
        initRealHostName();
        initHostName();
        initReconnectInterval();
        initUseSD();
        initConnectionTimeout();
        initReadTimeout();
        initWriteTimeout();
    }

    private void initLogger() {
        this.manager = LogManager.getLogManager();
        String configpath = System.getProperty("java.util.logging.config.file");
        if(configpath != null) {
            File configfile = new File(configpath);
            if(!configfile.exists() || !configfile.isFile()) {
                System.out.println("Can't find properties file at " + configpath);
                return;
            }
            FileInputStream inputStream = null;
            try {
                inputStream = new FileInputStream(configpath);
                LogManager.getLogManager().readConfiguration(inputStream);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void initFormatter() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        // Get normal prop and fallback to manager prop; from that fallback to simple formatter
        String formatter_name = System.getProperty("java.util.logging.RelpHandler." + this.getName() + ".formatter", this.manager.getProperty("java.util.logging.RelpHandler." + this.getName() + ".formatter"));
        if(formatter_name != null) {
            ClassLoader classloader = ClassLoader.getSystemClassLoader();
            if (classloader == null) {
                System.out.println("Unable to initialize ClassLoader.getSystemClassLoader(), defaulting to SimpleFormatter");
            }
            if (classloader != null) {
                Object formatter_object = classloader.loadClass(formatter_name).newInstance();
                this.formatter = (Formatter) formatter_object;
            }
        }
        else {
            this.formatter = new SimpleFormatter() {
                @Override
                public synchronized String format(LogRecord logrecord) {
                    return String.format("%1$s", logrecord.getMessage());
                }
            };
        }
    }

    public Formatter getFormatter() {
        return this.formatter;
    }

    private String getProperty(String name, String fallback) {
        // Overrides from props
        String prop = System.getProperty("java.util.logging.RelpHandler." + this.getName() + "." + name);
        if (prop != null) {
            if(prop.equals("")) {
                throw new IllegalArgumentException("Field is set but has no value: java.util.logging.RelpHandler." + this.getName() + "." + name);
            }
            return prop;
        }
        // Check if values are set in prop file
        String from_prop = this.manager.getProperty("java.util.logging.RelpHandler." + this.getName() + "." + name);
        if (from_prop != null) {
            if(from_prop.equals("")) {
                throw new IllegalArgumentException("Field is set but has no value: java.util.logging.RelpHandler." + this.getName() + "." + name);
            }
            return from_prop;
        }
        // Default
        return fallback;
    }

    private void initName(String name) throws NoSuchFieldException, IllegalArgumentException {
        if (name == null) {
            throw new NoSuchFieldException("Logger name is null");
        }
        if (name.equals("")) {
            throw new IllegalArgumentException("Logger name is empty");
        }
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    private void initPort() throws NumberFormatException {
        String prop = getProperty("server.port", "601");
        try {
            this.port = Integer.parseInt(prop);
            if (this.port <= 0 || this.port > 65535) {
                throw new IllegalArgumentException("RELP server port is invalid: " + this.port + ", expected to be in range from 1 to 65535");
            }
        }
        catch (NumberFormatException e) {
            throw new NumberFormatException("RELP server port is not a number: " + e);
        }
    }

    public int getPort() {
        return this.port;
    }

    private void initAddress() {
        this.address = getProperty("server.address", "127.0.0.1");
    }

    public String getAddress() {
        return this.address;
    }

    private void initAppname() {
        this.appname = getProperty("appname", "RelpHandler");
    }

    public String getAppname() {
        return this.appname;
    }



    private void initRealHostName() {
        try {
            this.realHostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            this.realHostName = "localhost";
        }
    }

    public String getRealHostName() {
        return this.realHostName;
    }

    private void initHostName() {
        this.hostname = getProperty("hostname", "localhost");
    }

    public String getHostname() {
        return this.hostname;
    }

    private void initReconnectInterval() throws NumberFormatException {
        String prop = getProperty("server.reconnectInterval", "1000");
        try {
            this.reconnectInterval = Integer.parseInt(prop);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("RELP server reconnect interval is not a number: " + e);
        }
        if (this.reconnectInterval < 0) {
            throw new IllegalArgumentException("RELP server reconnect interval is invalid: " + this.reconnectInterval + ", expected to be >= 0");
        }
    }

    public int getReconnectInterval() {
        return this.reconnectInterval;
    }

    private void initUseSD() {
        // Any truthy value is yes.
        this.useSD = Boolean.valueOf(getProperty("useStructuredData", "true"));
    }

    public boolean getUseSD() {
        return this.useSD;
    }

    private void initConnectionTimeout() throws IllegalArgumentException, NumberFormatException {
        String prop = getProperty("server.connectionTimeout", "0");
        try {
            this.connectionTimeout = Integer.parseInt(prop);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("RELP server connection timeout is not a number: " + e);
        }
        if (this.connectionTimeout < 0) {
            throw new IllegalArgumentException("RELP server connection timeout is invalid: " + this.connectionTimeout + ", expected to be >= 0");
        }
    }

    public int getConnectionTimeout() {
        return this.connectionTimeout;
    }

    private void initWriteTimeout() throws NumberFormatException, IllegalArgumentException {
        String prop = getProperty("server.writeTimeout", "0");
        try {
            this.writeTimeout = Integer.parseInt(prop);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("RELP server write timeout is not a number: " + e);
        }
        if (this.writeTimeout < 0) {
            throw new IllegalArgumentException("RELP server write timeout is invalid: " + this.writeTimeout + ", expected to be >= 0");
        }
    }

    public int getWriteTimeout() {
        return this.writeTimeout;
    }

    private void initReadTimeout() throws NumberFormatException, IllegalArgumentException {
        String prop = getProperty("server.readTimeout", "0");
        try {
            this.readTimeout = Integer.parseInt(prop);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("RELP server read timeout is not a number: " + e);
        }
        if (this.readTimeout < 0) {
            throw new IllegalArgumentException("RELP server read timeout is invalid: " + this.readTimeout + ", expected to be >= 0");
        }
    }

    public int getReadTimeout() {
        return this.readTimeout;
    }
}
