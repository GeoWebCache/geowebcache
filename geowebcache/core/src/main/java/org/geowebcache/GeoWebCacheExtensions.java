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
 * <p>Copyright 2019
 */
package org.geowebcache;

import jakarta.servlet.ServletContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.geotools.util.logging.Logging;
import org.geowebcache.config.BaseConfiguration;
import org.geowebcache.util.GWCVars;
import org.geowebcache.util.SuppressFBWarnings;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.web.context.WebApplicationContext;

/**
 * Utility class uses to process GeoWebCache extension points.
 *
 * <p>An instance of this class needs to be registered in spring context as follows. <code>
 *         <pre>
 *         &lt;bean id="gwowebCacheExtensions" class="org.geowebcache.GeoWebCacheExtensions"/&gt;
 *         </pre>
 * </code> It must be a singleton, and must not be loaded lazily. Furthermore, this bean must be loaded before any beans
 * that use it.
 *
 * <p>Priority will be respected for extensions that implement {@link GeoWebCacheExtensionPriority} interface.
 *
 * @author Gabriel Roldan based on GeoServer's {@code GeoServerExtensions}
 */
public class GeoWebCacheExtensions implements ApplicationContextAware, ApplicationListener {

    /** logger */
    private static Logger LOGGER = Logging.getLogger(GeoWebCacheExtensions.class.getName());

    /**
     * Caches the names of the beans for a particular type, so that the lookup (expensive) wont' be needed. We cache
     * names instead of beans because doing the latter we would break the "singleton=false" directive of some beans
     */
    static WeakHashMap<Class<?>, String[]> extensionsCache = new WeakHashMap<>(40);

    /** A static application context */
    static ApplicationContext context;

    /**
     * Sets the web application context to be used for looking up extensions.
     *
     * <p>This method is called by the spring container, and should never be called by client code. If client needs to
     * supply a particular context, methods which take a context are available.
     *
     * <p>This is the context that is used for methods which dont supply their own context.
     */
    @Override
    @SuppressFBWarnings("ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD")
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        GeoWebCacheExtensions.context = context;
        extensionsCache.clear();
    }

    /**
     * Loads all extensions implementing or extending <code>extensionPoint</code>.
     *
     * @param extensionPoint The class or interface of the extensions.
     * @param context The context in which to perform the lookup.
     * @return A collection of the extensions, or an empty collection.
     */
    @SuppressWarnings("unchecked")
    public static final <T> List<T> extensions(Class<T> extensionPoint, ApplicationContext context) {
        String[] names;
        if (GeoWebCacheExtensions.context == context) {
            names = extensionsCache.get(extensionPoint);
        } else {
            names = null;
        }
        if (names == null) {
            checkContext(context);
            if (context != null) {
                try {
                    names = getBeansNamesOrderedByPriority(extensionPoint, context);
                    // update cache only if dealing with the same context
                    if (GeoWebCacheExtensions.context == context) {
                        extensionsCache.put(extensionPoint, names);
                    }
                } catch (Exception e) {
                    // JD: this can happen during testing... if the application
                    // context has been closed and a non-one time setup test is
                    // run that triggers an extension lookup
                    LOGGER.log(Level.SEVERE, "bean lookup error", e);
                    return Collections.emptyList();
                }
            } else {
                return Collections.emptyList();
            }
        }

        // look up all the beans
        List<T> result = new ArrayList<>(names.length);
        for (String name : names) {
            T bean = (T) context.getBean(name);
            result.add(bean);
        }

        return result;
    }

    /**
     * Return all bean names that correspond to the provided extension type. If the provided extensions type implements
     * the {@link GeoWebCacheExtensionPriority} interface, they are returned sorted by priority
     *
     * <p>We return the bean names and not the beans themselves because we cache the beans by name rather than the bean
     * itself to avoid breaking the singleton directive.
     *
     * @param extensionType type of beans to return
     * @return Array of sorted bean names
     */
    public static <T> String[] getBeansNamesOrderedByPriority(Class<T> extensionType) {
        return getBeansNamesOrderedByPriority(extensionType, context);
    }

    /**
     * We lookup all the beans names that correspond to the provided extension type. If the provided extensions type
     * implements the {@link GeoWebCacheExtensionPriority} interface we sort the beans names by their priority.
     *
     * <p>We return the bean names and not the beans themselves because we cache the beans by name rather than the bean
     * itself to avoid breaking the singleton directive.
     */
    @SuppressWarnings("unchecked")
    private static <T> String[] getBeansNamesOrderedByPriority(Class<T> extensionType, ApplicationContext context) {
        // asking spring for a map that will contains all beans that match the extensions type
        // indexed by their name
        Map<String, T> beans = context.getBeansOfType(extensionType);
        if (!GeoWebCacheExtensionPriority.class.isAssignableFrom(extensionType)) {
            // no priority so nothing ot do
            return beans.keySet().toArray(new String[beans.size()]);
        }
        // this extension type is priority aware
        List<Map.Entry<String, T>> beansEntries = new ArrayList<>(beans.entrySet());
        // sorting beans by their priority
        Collections.sort(beansEntries, (extensionA, extensionB) -> {
            GeoWebCacheExtensionPriority extensionPriorityA =
                    ((Map.Entry<String, GeoWebCacheExtensionPriority>) extensionA).getValue();
            GeoWebCacheExtensionPriority extensionPriorityB =
                    ((Map.Entry<String, GeoWebCacheExtensionPriority>) extensionB).getValue();
            if (extensionPriorityA.getPriority() < extensionPriorityB.getPriority()) {
                return -1;
            }
            return extensionPriorityA.getPriority() == extensionPriorityB.getPriority() ? 0 : 1;
        });
        // returning only the beans names
        return beansEntries.stream().map(Map.Entry::getKey).toArray(String[]::new);
    }

    /**
     * Loads all extensions implementing or extending <code>extensionPoint</code>.
     *
     * <p>This method uses the "default" application context to perform the lookup. See
     * {@link #setApplicationContext(ApplicationContext)}.
     *
     * @param extensionPoint The class or interface of the extensions.
     * @return A collection of the extensions, or an empty collection.
     */
    public static final <T> List<T> extensions(Class<T> extensionPoint) {
        return extensions(extensionPoint, context);
    }

    /**
     * Return a list of configurations in priority order.
     *
     * @param extensionPoint The extension point of the configuration, may affect priority.
     */
    public static <T extends BaseConfiguration> List<T> configurations(
            Class<T> extensionPoint, ApplicationContext context) {
        return extensions(extensionPoint, context).stream()
                .sorted((x, y) -> Integer.signum(x.getPriority(extensionPoint) - y.getPriority(extensionPoint)))
                .collect(Collectors.toList());
    }

    /**
     * Return a list of configurations in priority order.
     *
     * @param extensionPoint The extension point of the configuration, may affect priority.
     */
    public static <T extends BaseConfiguration> List<T> configurations(Class<T> extensionPoint) {
        return configurations(extensionPoint, context);
    }

    /** Reinitialize all reinitializable beans in the context. */
    public static void reinitialize(ApplicationContext context) {
        List<ReinitializingBean> extensions = extensions(ReinitializingBean.class, context);
        for (ReinitializingBean bean : extensions) {
            try {
                bean.deinitialize();
            } catch (Exception e) {
                if (bean instanceof BaseConfiguration configuration) {
                    LOGGER.log(
                            Level.SEVERE,
                            "Error while preparing configuration to reinitialize "
                                    + configuration.getIdentifier()
                                    + " from "
                                    + configuration.getLocation(),
                            e);
                } else {
                    LOGGER.log(Level.SEVERE, "Error while preparing bean to reinitialize " + bean.toString(), e);
                }
            }
        }
        for (ReinitializingBean bean : extensions) {
            try {
                bean.reinitialize();
            } catch (Exception e) {
                if (bean instanceof BaseConfiguration configuration) {
                    LOGGER.log(
                            Level.SEVERE,
                            "Error while reinitializing configuration "
                                    + configuration.getIdentifier()
                                    + " from "
                                    + configuration.getLocation(),
                            e);
                } else {
                    LOGGER.log(Level.SEVERE, "Error while reinitializing bean " + bean.toString(), e);
                }
            }
        }
    }

    /** Returns a specific bean given its name */
    public static final Object bean(String name) {
        return bean(name, context);
    }

    /** Returns a specific bean given its name with a specified application context. */
    public static final Object bean(String name, ApplicationContext context) {
        checkContext(context);
        return context != null ? context.getBean(name) : null;
    }

    /**
     * Loads a single bean by its type.
     *
     * <p>This method returns null if there is no such bean. An exception is thrown if multiple beans of the specified
     * type exist.
     *
     * @param type THe type of the bean to lookup.
     * @throws IllegalArgumentException If there are multiple beans of the specified type in the context.
     */
    public static final <T> T bean(Class<T> type) throws IllegalArgumentException {
        checkContext(context);
        return context != null ? bean(type, context) : null;
    }

    /**
     * Loads a single bean by its type from the specified application context.
     *
     * <p>This method returns null if there is no such bean. An exception is thrown if multiple beans of the specified
     * type exist.
     *
     * @param type THe type of the bean to lookup.
     * @param context The application context
     * @throws IllegalArgumentException If there are multiple beans of the specified type in the context.
     */
    public static final <T> T bean(Class<T> type, ApplicationContext context) throws IllegalArgumentException {
        List<T> beans = extensions(type, context);
        if (beans.isEmpty()) {
            return null;
        }

        if (beans.size() > 1) {
            throw new IllegalArgumentException("Multiple beans of type " + type.getName());
        }

        return beans.get(0);
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ContextRefreshedEvent) extensionsCache.clear();
    }

    /** Checks the context, if null will issue a warning. */
    static void checkContext(ApplicationContext context) {
        if (context == null) {
            LOGGER.fine("Extension lookup occured, but ApplicationContext is unset.");
        }
    }

    /**
     * Looks up for a named string property in the order defined by {@link #getProperty(String, ApplicationContext)}
     * using the internally cached spring application context.
     *
     * <p>Care should be taken when using this method. It should not be called during startup or from tests cases as the
     * internal context will not have been set.
     *
     * @param propertyName The property name to lookup.
     * @return The property value, or null if not found
     */
    public static String getProperty(String propertyName) {
        return getProperty(propertyName, context);
    }

    /**
     * Looks up for a named string property into the following contexts (in order):
     *
     * <ul>
     *   <li>System Property
     *   <li>web.xml init parameters (only works if the context is a {@link WebApplicationContext}
     *   <li>Environment variable
     * </ul>
     *
     * and returns the first non null, non empty value found.
     *
     * @param propertyName The property name to be searched
     * @param context The Spring context (may be null)
     * @return The property value, or null if not found
     * @see GWCVars#findEnvVar(ApplicationContext, String)
     */
    public static String getProperty(String propertyName, ApplicationContext context) {
        return GWCVars.findEnvVar(context, propertyName);
    }

    /**
     * Looks up for a named string property into the following contexts (in order):
     *
     * <ul>
     *   <li>System Property
     *   <li>web.xml init parameters
     *   <li>Environment variable
     * </ul>
     *
     * and returns the first non null, non empty value found.
     *
     * @param propertyName The property name to be searched
     * @param context The servlet context used to look into web.xml (may be null)
     * @return The property value, or null if not found
     * @deprecated since 1.21, use {@link GWCVars#findEnvVar(ApplicationContext, String)} instead
     */
    @Deprecated
    public static String getProperty(String propertyName, ServletContext context) {
        // TODO: this code comes from the data directory lookup and it's useful as
        // long as we don't provide a way for the user to manually inspect the three contexts
        // (when trying to debug why the variable they thing they've set, and so on, see also
        // http://jira.codehaus.org/browse/GEOS-2343
        // Once that is fixed, we can remove the logging code that makes this method more complex
        // than strictly necessary

        final String[] typeStrs = {
            "Java environment variable ", "Servlet context parameter ", "System environment variable "
        };

        String result = null;
        for (int j = 0; j < typeStrs.length; j++) {
            // Lookup section
            switch (j) {
                case 0:
                    result = System.getProperty(propertyName);
                    break;
                case 1:
                    if (context != null) {
                        result = context.getInitParameter(propertyName);
                    }
                    break;
                case 2:
                    result = System.getenv(propertyName);
                    break;
            }

            if (result == null || result.equalsIgnoreCase("")) {
                LOGGER.finer("Found " + typeStrs[j] + ": '" + propertyName + "' to be unset");
            } else {
                break;
            }
        }

        return result;
    }
}
