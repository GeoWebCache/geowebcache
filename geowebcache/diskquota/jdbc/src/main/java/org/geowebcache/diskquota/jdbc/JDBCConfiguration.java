package org.geowebcache.diskquota.jdbc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.io.IOUtils;

import com.thoughtworks.xstream.XStream;

/**
 * A JDBC configuration for GeoWebCache
 * 
 * @author Andrea Aime - GeoSolutions
 */
public class JDBCConfiguration {

    String JNDISource;

    ConnectionPoolConfiguration connectionPool;

    /**
     * Loads a XML configuration from the specified file
     * 
     * @param sourceFile
     * @return
     * @throws IOException
     */
    public static JDBCConfiguration load(File sourceFile) throws IOException {
        XStream xs = getXStream();

        FileInputStream fis = null;
        try {
            fis = new FileInputStream(sourceFile);
            return (JDBCConfiguration) xs.fromXML(fis);
        } finally {
            IOUtils.closeQuietly(fis);
        }
    }

    public static void store(JDBCConfiguration config, File file) throws IOException {
        XStream xs = getXStream();

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            xs.toXML(config, fos);
        } finally {
            IOUtils.closeQuietly(fos);
        }

    }

    private static XStream getXStream() {
        XStream xs = new XStream();
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
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        JDBCConfiguration other = (JDBCConfiguration) obj;
        if (JNDISource == null) {
            if (other.JNDISource != null)
                return false;
        } else if (!JNDISource.equals(other.JNDISource))
            return false;
        if (connectionPool == null) {
            if (other.connectionPool != null)
                return false;
        } else if (!connectionPool.equals(other.connectionPool))
            return false;
        return true;
    }


    public static class ConnectionPoolConfiguration {
        String driver;

        String url;
        
        String username;
        
        String password;

        int minConnections;

        int maxConnections;

        int fetchSize;

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

        public int getFetchSize() {
            return fetchSize;
        }

        public void setFetchSize(int fetchSize) {
            this.fetchSize = fetchSize;
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
            result = prime * result + fetchSize;
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
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ConnectionPoolConfiguration other = (ConnectionPoolConfiguration) obj;
            if (connectionTimeout != other.connectionTimeout)
                return false;
            if (driver == null) {
                if (other.driver != null)
                    return false;
            } else if (!driver.equals(other.driver))
                return false;
            if (fetchSize != other.fetchSize)
                return false;
            if (maxConnections != other.maxConnections)
                return false;
            if (maxOpenPreparedStatements != other.maxOpenPreparedStatements)
                return false;
            if (minConnections != other.minConnections)
                return false;
            if (password == null) {
                if (other.password != null)
                    return false;
            } else if (!password.equals(other.password))
                return false;
            if (url == null) {
                if (other.url != null)
                    return false;
            } else if (!url.equals(other.url))
                return false;
            if (username == null) {
                if (other.username != null)
                    return false;
            } else if (!username.equals(other.username))
                return false;
            if (validationQuery == null) {
                if (other.validationQuery != null)
                    return false;
            } else if (!validationQuery.equals(other.validationQuery))
                return false;
            return true;
        }

        @Override
        public String toString() {
            return "ConnectionPoolConfiguration [driver=" + driver + ", url=" + url + ", username="
                    + username + ", password=" + password + ", minConnections=" + minConnections
                    + ", maxConnections=" + maxConnections + ", fetchSize=" + fetchSize
                    + ", connectionTimeout=" + connectionTimeout + ", validationQuery="
                    + validationQuery + ", maxOpenPreparedStatements=" + maxOpenPreparedStatements
                    + "]";
        }

        
        
        

    }

    
}
