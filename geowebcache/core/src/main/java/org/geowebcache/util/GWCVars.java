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
package org.geowebcache.util;

import jakarta.servlet.ServletContext;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.geotools.util.logging.Logging;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;

public class GWCVars {

    private static final Logger log = Logging.getLogger(GWCVars.class.toString());

    // everything here requires initialization

    public static final int CACHE_VALUE_UNSET = 0;

    public static final int CACHE_DISABLE_CACHE = -1;

    public static final int CACHE_NEVER_EXPIRE = -2;

    public static final int CACHE_USE_WMS_BACKEND_VALUE = -4;

    /**
     * enum containing and describing the possible sources of lookup for "environment variables", in order of
     * precedence.
     */
    public static enum VariableType {
        ENV("Java system property") {
            protected @Override String apply(ApplicationContext context, String varName) {
                return System.getProperty(varName);
            }
        },
        SERVLET("servlet context parameter") {
            protected @Override String apply(ApplicationContext context, String varName) {
                if (context instanceof WebApplicationContext applicationContext) {
                    ServletContext servletContext = applicationContext.getServletContext();
                    return servletContext == null ? null : servletContext.getInitParameter(varName);
                }
                return null;
            }
        },
        SYSTEM("system environment variable") {
            protected @Override String apply(ApplicationContext context, String varName) {
                return System.getenv(varName);
            }
        };

        private final String source;

        private VariableType(String source) {
            this.source = source;
        }

        public String getSource() {
            return source;
        }

        protected abstract String apply(ApplicationContext context, String varName);

        /**
         * @return a non-null {@literal Variable} with the evaluated value for the given variable name, possibly
         *     {@literal null}.
         */
        Variable get(ApplicationContext context, String varName) {
            return Variable.ofNullable(this, varName, this.apply(context, varName));
        }
    }

    /**
     * Type holder for the result of a variable lookup of a given
     * {@link VariableType}. The {@link #getValue() value) may be {@literal null}.
     */
    public static class Variable {
        private final VariableType type;
        private final String name;
        private final String value;

        private Variable(VariableType type, String name, String value) {
            this.type = type;
            this.name = name;
            this.value = value;
        }

        public VariableType getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        static Variable ofNullable(VariableType type, String name, String nullableValue) {
            Objects.requireNonNull(type);
            Objects.requireNonNull(name);
            return new Variable(type, name, nullableValue);
        }
    }

    /**
     * The first non-null value for the given variable name in the order of lookup precedence defined by
     * {@link VariableType} (that is, Java system property -> servlet context parameter -> system environment variable)
     */
    public static String findEnvVar(ApplicationContext context, String varStr) {
        return lookup(context, varStr)
                .map(GWCVars::log)
                .filter(GWCVars::nonNullValue)
                .findFirst()
                .map(Variable::getValue)
                .orElse(null);
    }

    /**
     * @return all the results of a given variable look up in order of precedence, without filtering for null (i.e. not
     *     set) values
     */
    public static List<Variable> findVariable(ApplicationContext context, String varName) {
        return lookup(context, varName).collect(Collectors.toList());
    }

    private static Stream<Variable> lookup(ApplicationContext context, String varName) {

        return Arrays.stream(VariableType.values()).map(t -> t.get(context, varName));
    }

    private static boolean nonNullValue(Variable v) {
        return v.getValue() == null ? false : true;
    }

    private static Variable log(Variable v) {
        String value = v.getValue();
        String source = v.getType().getSource();
        String name = v.getName();
        if (value == null && log.isLoggable(Level.FINER)) {
            log.config("Not found " + source + " for " + name);
        } else if (value != null && log.isLoggable(Level.INFO)) {
            log.config("Found " + source + " for " + name + " set to " + value);
        }
        return v;
    }
}
