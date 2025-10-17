/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Jody Garnett - GeoCat 2022
 */
package org.geowebcache.util;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.factory.GeoTools;
import org.geotools.util.logging.LoggerFactory;
import org.geotools.util.logging.Logging;
import org.springframework.context.ApplicationContext;

/**
 * Used to configure logging on application startup.
 *
 * <p>Uses {@link org.geotools.util.logging.Logging} to delegate to provided logging environment.
 */
public class LoggingContextListener implements ServletContextListener {

    /** Logger lazy-created for use by {@link #log()}. */
    private static Logger LOGGER;

    /**
     * Defines logging factory to configure org.geowebcache logging.
     *
     * <p>The following are provided by {@link Logging#setLoggerFactory(String)}:
     *
     * <ul>
     *   <li>Log4J2 - log4j2 API
     *   <li>Log4J1 - log4j1.2 API, requires reload4j or log4j-1.2-api
     *   <li>LOGBACK - slf4j api, requires logback
     *   <li>CommonsLogging - commons logging
     *   <li>JavaLogging - configure with default java util logging
     *   <li>Provided - logging configuration already provided
     *   <li>(empty string): defaults to java util logging, managed by logging.properties
     * </ul>
     *
     * Override with {@code -Dorg.geowebcache.util.logging.policy=SL4J}.
     */
    public static final String LOGGER_POLICY = "org.geowebcache.util.logging.policy";

    /** Configuration alternatives for {@link #LOGGER_POLICY}. */
    public enum Policy {
        LOG4J2("org.geotools.util.logging.Log4J2LoggerFactory"),
        LOG4J1("org.geotools.util.logging.Log4JLoggerFactory"),
        LOGBACK("org.geotools.util.logging.LogbackLoggerFactory"),
        COMMONSLOGGING("org.geotools.util.logging.CommonsLoggerFactory"),
        JAVALOGGING(null),
        PROVIDED("Provided");

        private final String factoryName;

        Policy(String factoryName) {
            this.factoryName = factoryName;
        }

        /**
         * Lookup geotools logging factory by policy name.
         *
         * @param policyName logging policy name
         * @return geotools logging factory
         */
        public static Policy find(String policyName) {
            if (policyName == null) {
                return null;
            }
            for (Policy policy : values()) {
                if (policy.name().equalsIgnoreCase(policyName)) {
                    return policy;
                }
            }
            return null; // java util logging
        }
        /**
         * Factory name, or {@code null} for JavaLogging.
         *
         * @return factory name
         */
        public String getFactoryName() {
            return factoryName;
        }
    }

    @Override
    public void contextInitialized(ServletContextEvent contextEvent) {
        final ServletContext context = contextEvent.getServletContext();

        String policyName = getProperty(context, LOGGER_POLICY, "Log4J2");
        Policy policy = Policy.find(policyName);

        if (policy != Policy.PROVIDED) {
            Throwable troubleSettingUpLogging = null;
            try {
                if (org.geotools.util.logging.Logging.ALL.getLoggerFactory() == null) {
                    org.geotools.util.logging.Logging.ALL.setLoggerFactory(policy.getFactoryName());
                }
            } catch (Throwable trouble) {
                // delay reporting problem until fallback logging setup
                troubleSettingUpLogging = trouble;
            }
            log().config("GeoWebCache Logger:" + log());
            if (troubleSettingUpLogging != null) {
                log().log(
                                Level.WARNING,
                                "Unable to use org.geowebcache.util.logging.policy property '"
                                        + policy
                                        + "' to configure logging:"
                                        + troubleSettingUpLogging,
                                troubleSettingUpLogging);
            }
        }
        LoggerFactory<?> loggerFactory =
                Logging.getLogging(LoggingContextListener.class.getName()).getLoggerFactory();
        if (log().isLoggable(Level.CONFIG)) {
            if (loggerFactory != null) {
                String config = loggerFactory.lookupConfiguration();
                log().config(loggerFactory.getClass().getSimpleName() + " config: " + config);
            } else {
                log().config("java.util.logging config: " + julLoggingConfiguration());
            }
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        GeoTools.setLoggerFactory(null);
        if (LOGGER != null) {
            LOGGER = null;
        }
    }

    /**
     * Looks up property from system property, context parameter, or value variable.
     *
     * <p>This is a light-weight search following the same precedence as {@link GWCVars#findEnvVar(ApplicationContext,
     * String)} variable lookup: system properties, servlet init parameters, environmental variables.
     *
     * @param context Web context
     * @param name Property name
     * @return property value, or defaultProperty if unavailable
     */
    public static String getProperty(ServletContext context, String name) {
        return getProperty(context, name, null);
    }

    /**
     * Looks up property from system property, context parameter, or value variable.
     *
     * <p>This is a light-weight search following the same precedence as {@link GWCVars#findEnvVar(ApplicationContext,
     * String)} variable lookup: system properties, servlet init parameters, environmental variables.
     *
     * @param context Web context
     * @param name Property name
     * @param defaultProperty default value if provided property is null, or otherwise empty
     * @return property value, or defaultProperty if unavailable
     */
    public static String getProperty(ServletContext context, String name, String defaultProperty) {
        if (name == null) {
            return null;
        }
        if (System.getProperties().containsKey(name)) {
            String property = System.getProperty(name);
            if (property != null && !property.isEmpty()) {
                return property;
            }
        }
        if (context != null) {
            String parameter = context.getInitParameter(name);
            if (parameter != null && !parameter.isEmpty()) {
                return parameter;
            }
        }
        if (System.getenv().containsKey(name)) {
            String variable = System.getenv(name);
            if (variable != null && !variable.isEmpty()) {
                return variable;
            }
        }
        return defaultProperty;
    }

    /**
     * Mirror workflow of {@code LoggingManager.readConfiguration()} to determine java util logging configuration.
     *
     * @return java util logging configuration
     */
    String julLoggingConfiguration() {
        String configClass = System.getProperty("java.util.logging.config.class");
        String configFile = System.getProperty("java.util.logging.config.file");
        String javaHome = System.getProperty("java.home");
        if (configClass != null) {
            return configClass;
        } else if (configFile != null) {
            return configFile;
        } else if (javaHome != null) {
            return javaHome + "/lib/logging.properties";
        } else {
            return "java.util.logging";
        }
    }

    /**
     * Lazy creation access to Logger, to allow initialization to occur first.
     *
     * @return Logger for LoggingContextListener.class
     */
    private Logger log() {
        if (LOGGER == null) {
            LOGGER = Logging.getLogger(LoggingContextListener.class.getName());
        }
        return LOGGER;
    }
}
