/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Alessio Fabiani, GeoSolutions S.A.S., Copyright 2017
 */
package org.geowebcache;

import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang3.StringUtils;
import org.geotools.util.logging.Logging;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.PlaceholderConfigurerSupport;
import org.springframework.core.Constants;
import org.springframework.util.PropertyPlaceholderHelper;
import org.springframework.util.PropertyPlaceholderHelper.PlaceholderResolver;

/**
 * Utility class uses to process GeoWebCache configuration workflow through external environment
 * variables.
 *
 * <p>This class must be used everytime we need to resolve a configuration placeholder at runtime.
 *
 * <p>An instance of this class needs to be registered in spring context as follows.
 *
 * <pre>
 * <code>
 *         &lt;bean id="geoWebCacheEnvironment" class="org.geowebcache.GeoWebCacheEnvironment" depends-on="geoWebCacheExtensions"/&gt;
 * </code>
 * </pre>
 *
 * It must be a singleton, and must not be loaded lazily. Furthermore, this bean must be loaded
 * before any beans that use it.
 *
 * @author Alessio Fabiani, GeoSolutions
 */
public class GeoWebCacheEnvironment {

    /** logger */
    public static Logger LOGGER = Logging.getLogger(GeoWebCacheEnvironment.class.getName());

    private static final Constants constants = new Constants(PlaceholderConfigurerSupport.class);

    /**
     * Constant set via System Environment in order to instruct GeoWebCache to make use or not of
     * the config placeholders translation.
     *
     * <p>Default to FALSE
     */
    public static final boolean ALLOW_ENV_PARAMETRIZATION =
            Boolean.valueOf(GeoWebCacheExtensions.getProperty("ALLOW_ENV_PARAMETRIZATION"));

    private static final String nullValue = "null";

    private final PropertyPlaceholderHelper helper =
            new PropertyPlaceholderHelper(
                    constants.asString("DEFAULT_PLACEHOLDER_PREFIX"),
                    constants.asString("DEFAULT_PLACEHOLDER_SUFFIX"),
                    constants.asString("DEFAULT_VALUE_SEPARATOR"),
                    true);

    private final PlaceholderResolver resolver =
            placeholderName -> resolvePlaceholder(placeholderName);

    private Properties props;

    /**
     * Internal "props" getter method.
     *
     * @return the props
     */
    public Properties getProps() {
        return props;
    }

    /**
     * Internal "props" setter method.
     *
     * @param props the props to set
     */
    public void setProps(Properties props) {
        this.props = props;
    }

    protected String resolvePlaceholder(String placeholder) {
        String propVal = null;
        propVal = resolveSystemProperty(placeholder);

        if (props != null && propVal == null) {
            propVal = props.getProperty(placeholder);
        }

        return propVal;
    }

    protected String resolveSystemProperty(String key) {
        try {
            String value = GeoWebCacheExtensions.getProperty(key);
            if (value == null) {
                value = System.getenv(key);
            }
            return value;
        } catch (Throwable ex) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Could not access system property '" + key + "': " + ex);
            }
            return null;
        }
    }

    protected String resolveStringValue(String strVal) throws BeansException {
        String resolved = this.helper.replacePlaceholders(strVal, this.resolver);

        return (resolved.equals(nullValue) ? null : resolved);
    }

    /**
     * Translates placeholders in the form of Spring Property placemark ${...} into their real
     * values.
     *
     * <p>The method first looks for System variables which take precedence on local ones, then into
     * internal props injected through the applicationContext.
     */
    public Object resolveValue(Object value) {
        if (value != null) {
            if (value instanceof String) {
                return resolveStringValue((String) value);
            }
        }

        return value;
    }

    private String resolveValueIfEnabled(String value) {
        if (ALLOW_ENV_PARAMETRIZATION) return (String) resolveValue(value);
        else return value;
    }

    private boolean validateBoolean(String value) {
        value = value.trim();
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
    }

    /**
     * Resolve placeholders for a String parameter if enabled on configurations.
     *
     * @param value String value with optional placeholders.
     * @param type Data Type to return.
     * @return Optional resolved value.
     */
    @SuppressWarnings("unchecked")
    public <T extends Object> Optional<T> resolveValueIfEnabled(final String value, Class<T> type) {
        if (StringUtils.isBlank(value)) return Optional.empty();
        final String resultValue = resolveValueIfEnabled(value);
        if (type.isAssignableFrom(String.class)) {
            return (Optional<T>) Optional.of(resultValue);
        } else if (type.isAssignableFrom(Integer.class)) {
            try {
                Integer intValue = Integer.valueOf(resultValue);
                return (Optional<T>) Optional.of(intValue);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Illegal String parameter: Resolved value is not an integer.", ex);
            }
        } else if (type.isAssignableFrom(Boolean.class)) {
            if (!validateBoolean(resultValue))
                throw new IllegalArgumentException(
                        "Illegal String parameter: Resolved value is not a boolean.");
            Boolean boolValue = Boolean.valueOf(value);
            return (Optional<T>) Optional.of(boolValue);
        }
        throw new IllegalArgumentException("No type convertion available for " + type);
    }
}
