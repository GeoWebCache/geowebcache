/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Alessio Fabiani, GeoSolutions S.A.S., Copyright 2017
 */
package org.geowebcache;

import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.PlaceholderConfigurerSupport;
import org.springframework.core.Constants;
import org.springframework.util.PropertyPlaceholderHelper;
import org.springframework.util.PropertyPlaceholderHelper.PlaceholderResolver;

/**
 * Utility class uses to process GeoWebCache configuration workflow through external environment variables.
 * 
 * This class must be used everytime we need to resolve a configuration placeholder at runtime.
 * <p>
 * An instance of this class needs to be registered in spring context as follows.
 * 
 * <pre>
 * <code>
 *         &lt;bean id="geoWebCacheEnvironment" class="org.geowebcache.GeoWebCacheEnvironment" depends-on="geoWebCacheExtensions"/&gt;
 * </code>
 * </pre>
 * 
 * It must be a singleton, and must not be loaded lazily. Furthermore, this bean must be loaded before any beans that use it.
 * 
 * @author Alessio Fabiani, GeoSolutions
 *
 */
public class GeoWebCacheEnvironment {

    /**
     * logger
     */
    public static Log LOGGER = LogFactory.getLog(GeoWebCacheEnvironment.class);

    private static final Constants constants = new Constants(PlaceholderConfigurerSupport.class);

    /**
     * Constant set via System Environment in order to instruct GeoWebCache to make use or not of the config placeholders translation.
     * 
     * Default to FALSE
     */
    public final static boolean ALLOW_ENV_PARAMETRIZATION = Boolean
            .valueOf(GeoWebCacheExtensions.getProperty("ALLOW_ENV_PARAMETRIZATION"));

    private static final String nullValue = "null";

    private final PropertyPlaceholderHelper helper = new PropertyPlaceholderHelper(
            constants.asString("DEFAULT_PLACEHOLDER_PREFIX"),
            constants.asString("DEFAULT_PLACEHOLDER_SUFFIX"),
            constants.asString("DEFAULT_VALUE_SEPARATOR"), true);

    private final PlaceholderResolver resolver = new PlaceholderResolver() {

        @Override
        public String resolvePlaceholder(String placeholderName) {
            return GeoWebCacheEnvironment.this.resolvePlaceholder(placeholderName);
        }
    };

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
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Could not access system property '" + key + "': " + ex);
            }
            return null;
        }
    }

    protected String resolveStringValue(String strVal) throws BeansException {
        String resolved = this.helper.replacePlaceholders(strVal, this.resolver);

        return (resolved.equals(nullValue) ? null : resolved);
    }

    /**
     * Translates placeholders in the form of Spring Property placemark ${...}
     * into their real values.
     * 
     * The method first looks for System variables which take precedence on
     * local ones, then into internal props injected through the applicationContext. 
     * 
     * @param value
     * @return
     */
    public Object resolveValue(Object value) {
        if (value != null) {
            if (value instanceof String) {
                return resolveStringValue((String) value);
            }
        }

        return value;
    }
}