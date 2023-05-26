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
 * @author Andrea Aime - GeoSolutions Copyright 2012
 */
package org.geowebcache.diskquota.jdbc;

import com.thoughtworks.xstream.XStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import org.apache.commons.lang3.SerializationUtils;
import org.geowebcache.GeoWebCacheEnvironment;
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.io.GeoWebCacheXStream;

/**
 * A JDBC configuration for the GeoWebCache disk quota subsystem
 *
 * @author Andrea Aime - GeoSolutions
 */
public class JDBCConfiguration implements Serializable {

    String dialect;

    String JNDISource;

    /** To be able configure schema in geowebcache-diskquota-jdbc.xml */
    String schema;

    ConnectionPoolConfiguration connectionPool;

    /**
     * Loads a XML configuration from the specified file. The file must adhere to the {@code
     * geowebcache-diskquota-jdbc.xsd} schema.
     */
    public static JDBCConfiguration load(File sourceFile) throws ConfigurationException {

        try (FileInputStream fis = new FileInputStream(sourceFile)) {
            return load(fis);
        } catch (IOException e) {
            throw new ConfigurationException(
                    "Failed to load the configuration from " + sourceFile.getAbsolutePath(), e);
        }
    }

    /**
     * Loads a XML configuration from the specified file. The file must adhere to the {@code
     * geowebcache-diskquota-jdbc.xsd} schema.
     *
     * @param is InputStream to load the configuration from
     */
    public static JDBCConfiguration load(InputStream is) throws ConfigurationException {
        XStream xs = getXStream();

        JDBCConfiguration conf = (JDBCConfiguration) xs.fromXML(is);

        validateConfiguration(conf.clone(true));
        return conf;
    }

    private static void validateConfiguration(JDBCConfiguration conf)
            throws ConfigurationException {
        if (conf.getDialect() == null) {
            throw new ConfigurationException(
                    "A dialect must be provided, possible values are H2, HSQL, Oracle, PostgresSQL");
        }

        ConnectionPoolConfiguration cp = conf.getConnectionPool();
        if (conf.getJNDISource() == null
                && cp == null
                && !"H2".equals(conf.getDialect())
                && !"HSQL".equals(conf.getDialect())) {
            throw new ConfigurationException(
                    "No data source provided, either configure JNDISource or connectionPool");
        }

        if (cp != null) {
            if (cp.getDriver() == null) {
                throw new ConfigurationException("No JDBC driver provided");
            }
            if (cp.getUrl() == null) {
                throw new ConfigurationException("No JDBC URL provided");
            }
        }
    }

    public static void store(JDBCConfiguration config, OutputStream os) {
        XStream xs = getXStream();
        xs.toXML(config, os);
    }

    public static void store(JDBCConfiguration config, File file) throws ConfigurationException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            store(config, fos);
        } catch (IOException e) {
            throw new ConfigurationException(
                    "Failed to store the configuration into " + file.getAbsolutePath(), e);
        }
    }

    private static XStream getXStream() {
        XStream xs = new GeoWebCacheXStream();
        // Allow anything that's part of GWC
        // TODO: replace this with a more narrow whitelist
        xs.allowTypesByWildcard(new String[] {"org.geowebcache.**"});

        xs.setMode(XStream.NO_REFERENCES);

        xs.alias("gwcJdbcConfiguration", JDBCConfiguration.class);
        xs.alias("connectionPool", ConnectionPoolConfiguration.class);
        return xs;
    }

    public String getJNDISource() {
        return JNDISource;
    }

    public void setJNDISource(String jndiSource) {
        this.JNDISource = jndiSource;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public ConnectionPoolConfiguration getConnectionPool() {
        return connectionPool;
    }

    public void setConnectionPool(ConnectionPoolConfiguration connectionPool) {
        this.connectionPool = connectionPool;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((JNDISource == null) ? 0 : JNDISource.hashCode());
        result = prime * result + ((connectionPool == null) ? 0 : connectionPool.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        JDBCConfiguration other = (JDBCConfiguration) obj;
        if (JNDISource == null) {
            if (other.JNDISource != null) return false;
        } else if (!JNDISource.equals(other.JNDISource)) return false;
        if (connectionPool == null) {
            if (other.connectionPool != null) return false;
        } else if (!connectionPool.equals(other.connectionPool)) return false;
        return true;
    }

    public String getDialect() {
        return dialect;
    }

    public void setDialect(String dialect) {
        this.dialect = dialect;
    }

    @Override
    public String toString() {
        return "JDBCConfiguration [dialect="
                + dialect
                + ", JNDISource="
                + JNDISource
                + ", connectionPool="
                + connectionPool
                + "]";
    }

    /**
     * The connection pool configuration, used to build a local connection pool (with DBCP or other
     * connection pool library)
     *
     * @author Andrea Aime - GeoSolutions
     */
    public static class ConnectionPoolConfiguration implements Serializable {
        private static final long serialVersionUID = 6677252877141737936L;

        String driver;

        String url;

        String username;

        String password;

        int minConnections;

        int maxConnections;

        int connectionTimeout;

        String validationQuery;

        int maxOpenPreparedStatements;

        public String getDriver() {
            return driver;
        }

        public void setDriver(String driver) {
            this.driver = driver;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public int getMinConnections() {
            return minConnections;
        }

        public void setMinConnections(int minConnections) {
            this.minConnections = minConnections;
        }

        public int getMaxConnections() {
            return maxConnections;
        }

        public void setMaxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
        }

        public int getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        public String getValidationQuery() {
            return validationQuery;
        }

        public void setValidationQuery(String validationQuery) {
            this.validationQuery = validationQuery;
        }

        public int getMaxOpenPreparedStatements() {
            return maxOpenPreparedStatements;
        }

        public void setMaxOpenPreparedStatements(int maxOpenPreparedStatements) {
            this.maxOpenPreparedStatements = maxOpenPreparedStatements;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + connectionTimeout;
            result = prime * result + ((driver == null) ? 0 : driver.hashCode());
            result = prime * result + maxConnections;
            result = prime * result + maxOpenPreparedStatements;
            result = prime * result + minConnections;
            result = prime * result + ((password == null) ? 0 : password.hashCode());
            result = prime * result + ((url == null) ? 0 : url.hashCode());
            result = prime * result + ((username == null) ? 0 : username.hashCode());
            result = prime * result + ((validationQuery == null) ? 0 : validationQuery.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            ConnectionPoolConfiguration other = (ConnectionPoolConfiguration) obj;
            if (connectionTimeout != other.connectionTimeout) return false;
            if (driver == null) {
                if (other.driver != null) return false;
            } else if (!driver.equals(other.driver)) return false;
            if (maxConnections != other.maxConnections) return false;
            if (maxOpenPreparedStatements != other.maxOpenPreparedStatements) return false;
            if (minConnections != other.minConnections) return false;
            if (password == null) {
                if (other.password != null) return false;
            } else if (!password.equals(other.password)) return false;
            if (url == null) {
                if (other.url != null) return false;
            } else if (!url.equals(other.url)) return false;
            if (username == null) {
                if (other.username != null) return false;
            } else if (!username.equals(other.username)) return false;
            if (validationQuery == null) {
                if (other.validationQuery != null) return false;
            } else if (!validationQuery.equals(other.validationQuery)) return false;
            return true;
        }

        @Override
        public String toString() {
            return "ConnectionPoolConfiguration [driver="
                    + driver
                    + ", url="
                    + url
                    + ", username="
                    + username
                    + ", password="
                    + password
                    + ", minConnections="
                    + minConnections
                    + ", maxConnections="
                    + maxConnections
                    + ", connectionTimeout="
                    + connectionTimeout
                    + ", validationQuery="
                    + validationQuery
                    + ", maxOpenPreparedStatements="
                    + maxOpenPreparedStatements
                    + "]";
        }
    }

    /** */
    public JDBCConfiguration clone(boolean allowEnvParametrization) {

        JDBCConfiguration conf = SerializationUtils.clone(this);

        final GeoWebCacheEnvironment gwcEnvironment =
                GeoWebCacheExtensions.bean(GeoWebCacheEnvironment.class);

        if (allowEnvParametrization
                && gwcEnvironment != null
                && GeoWebCacheEnvironment.ALLOW_ENV_PARAMETRIZATION) {
            conf.setDialect((String) gwcEnvironment.resolveValue(getDialect()));
            conf.setJNDISource((String) gwcEnvironment.resolveValue(getJNDISource()));
            ConnectionPoolConfiguration connectionPoolConfig = getConnectionPool();
            if (connectionPoolConfig != null) {
                ConnectionPoolConfiguration expConnectionPoolConfig =
                        SerializationUtils.clone(connectionPoolConfig);
                expConnectionPoolConfig.setDriver(
                        (String) gwcEnvironment.resolveValue(connectionPoolConfig.getDriver()));
                expConnectionPoolConfig.setUrl(
                        (String) gwcEnvironment.resolveValue(connectionPoolConfig.getUrl()));
                expConnectionPoolConfig.setUsername(
                        (String) gwcEnvironment.resolveValue(connectionPoolConfig.getUsername()));
                expConnectionPoolConfig.setPassword(
                        (String) gwcEnvironment.resolveValue(connectionPoolConfig.getPassword()));
                expConnectionPoolConfig.setValidationQuery(
                        (String)
                                gwcEnvironment.resolveValue(
                                        connectionPoolConfig.getValidationQuery()));

                conf.setConnectionPool(expConnectionPoolConfig);
            }
        }

        return conf;
    }
}
