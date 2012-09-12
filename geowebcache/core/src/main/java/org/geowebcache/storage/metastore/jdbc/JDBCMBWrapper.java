/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Arne Kepp / The Open Planning Project 2009
 *  
 */
package org.geowebcache.storage.metastore.jdbc;

import static org.geowebcache.storage.metastore.jdbc.JDBCUtils.*;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import javax.sql.DataSource;

import org.apache.commons.dbcp.ConnectionFactory;
import org.apache.commons.dbcp.DriverManagerConnectionFactory;
import org.apache.commons.dbcp.PoolableConnectionFactory;
import org.apache.commons.dbcp.PoolingDataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.pool.ObjectPool;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.storage.StorageObject;
import org.geowebcache.storage.TileObject;

/**
 * Wrapper class for the JDBC object, used by JDBCMetaBackend
 * 
 * Performs mundane tasks such as
 * <ul>
 * <li>initialize the database</li>
 * <li>create tables</li>
 * <li>create iterators</li>
 * </ul>
 * 
 */
class JDBCMBWrapper {
    private static Log log = LogFactory
            .getLog(org.geowebcache.storage.metastore.jdbc.JDBCMBWrapper.class);

    /** Database version, for automatic updates */
    static int DB_VERSION = 120;

    /** Connection information */
    final String jdbcString;

    final String username;

    final String password;

    final String driverClass;

    /**
     * H2 would close all the file handles to the database once you closed the last connection.
     * Reopening from scratch can take almost a second, so keeping one connection around in the
     * background ensures that this doesn't happen.
     * <p>
     * It _really_ makes a difference if connection pooling is disabled!
     * </p>
     */
    private Connection persistentConnection;

    boolean closing = false;

    /** Timeout for locked objects, 60 seconds by default **/
    protected long lockTimeout = 60000;

    private boolean useConnectionPooling;

    private int maxConnections;

    private volatile DataSource connPool;

    protected JDBCMBWrapper(String driverClass, String jdbcString, String username,
            String password, boolean useConnectionPooling, int maxConnections)
            throws ConfigurationException, SQLException {
        this.jdbcString = jdbcString;
        this.username = username;
        this.password = password;
        this.driverClass = driverClass;
        this.useConnectionPooling = useConnectionPooling;
        this.maxConnections = maxConnections;
        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException cnfe) {
            throw new ConfigurationException("Class not found: " + cnfe.getMessage());
        }

        if (!useConnectionPooling) {
            persistentConnection = getConnection();
        }
    }

    public JDBCMBWrapper(DefaultStorageFinder defStoreFind, boolean useConnectionPooling,
            int maxConnections) throws ConfigurationException, SQLException {
        String envStrUsername;
        String envStrPassword;
        String envStrJdbcUrl;
        String envStrDriver;
        envStrUsername = defStoreFind.findEnvVar(DefaultStorageFinder.GWC_METASTORE_USERNAME);
        envStrPassword = defStoreFind.findEnvVar(DefaultStorageFinder.GWC_METASTORE_PASSWORD);
        envStrJdbcUrl = defStoreFind.findEnvVar(DefaultStorageFinder.GWC_METASTORE_JDBC_URL);
        envStrDriver = defStoreFind.findEnvVar(DefaultStorageFinder.GWC_METASTORE_DRIVER_CLASS);
        this.useConnectionPooling = useConnectionPooling;
        this.maxConnections = maxConnections;
        if (envStrUsername != null) {
            username = envStrUsername;
        } else {
            this.username = "sa";
        }

        if (envStrPassword != null) {
            this.password = envStrPassword;
        } else {
            this.password = "";
        }

        if (envStrDriver != null) {
            this.driverClass = envStrDriver;
        } else {
            this.driverClass = "org.h2.Driver";
        }

        if (envStrJdbcUrl != null) {
            this.jdbcString = envStrJdbcUrl;
        } else {
            String path = defStoreFind.getDefaultPath() + File.separator + "meta_jdbc_h2";
            File dir = new File(path);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new ConfigurationException("Unable to create " + dir.getAbsolutePath()
                        + " for H2 database.");
            }
            this.jdbcString = "jdbc:h2:file:" + path + File.separator + "gwc_metastore"
                    + ";TRACE_LEVEL_FILE=0;AUTO_SERVER=TRUE";
        }

        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException cnfe) {
            throw new ConfigurationException("Class not found: " + cnfe.getMessage());
        }

        if (!useConnectionPooling) {
            persistentConnection = getConnection();
        }
    }

    protected Connection getConnection() throws SQLException {
        if (closing) {
            throw new IllegalStateException(getClass().getSimpleName() + " is being shut down");
        }
        Connection conn;
        if (useConnectionPooling) {
            if (connPool == null) {
                synchronized (this) {
                    if (connPool == null) {
                        connPool = createDataSource();
                    }
                }
            }
            conn = connPool.getConnection();
        } else {
            conn = DriverManager.getConnection(jdbcString, username, password);
        }
        conn.setAutoCommit(true);
        return conn;
    }

    private DataSource createDataSource() {
        // connPool = JdbcConnectionPool.create(jdbcString, username, password == null ? ""
        // : password);
        // connPool.setMaxConnections(maxConnections);

        // ObjectPool that serves as the actual pool of connections.
        GenericObjectPool.Config config = new GenericObjectPool.Config();
        config.maxActive = maxConnections;
        config.maxIdle = (int) Math.ceil(maxConnections * 0.5);
        config.minIdle = (int) Math.min(10, Math.ceil(maxConnections * 0.1));
        config.maxWait = 1000;

        ObjectPool connectionPool = new GenericObjectPool(null, config);

        // ConnectionFactory that the pool will use to create Connections.

        ConnectionFactory connectionFactory = new DriverManagerConnectionFactory(jdbcString,
                username, password == null ? "" : password);

        // PoolableConnectionFactory, wraps the "real" Connections
        PoolableConnectionFactory poolableConnectionFactory = new PoolableConnectionFactory(
                connectionFactory, connectionPool, null, null, false, true);
        connectionPool.setFactory(poolableConnectionFactory);

        //
        // Finally, we create the PoolingDriver itself,
        // passing in the object pool we created.
        //
        PoolingDataSource poolingDataSource = new PoolingDataSource(connectionPool);

        return poolingDataSource;
    }

    protected boolean getTile(TileObject stObj) throws SQLException {
        String query;
        if (stObj.getParametersId() == null) {
            query = "SELECT TILE_ID,BLOB_SIZE,CREATED,LOCK,NOW() FROM TILES WHERE "
                    + " LAYER_ID = ? AND X = ? AND Y = ? AND Z = ? AND GRIDSET_ID = ? "
                    + " AND FORMAT_ID = ? AND PARAMETERS_ID IS NULL LIMIT 1 ";
        } else {
            query = "SELECT TILE_ID,BLOB_SIZE,CREATED,LOCK,NOW() FROM TILES WHERE "
                    + " LAYER_ID = ? AND X = ? AND Y = ? AND Z = ? AND GRIDSET_ID = ? "
                    + " AND FORMAT_ID = ? AND PARAMETERS_ID = ? LIMIT 1 ";
        }
        long[] xyz = stObj.getXYZ();

        final Connection conn = getConnection();
        PreparedStatement prep = null;
        try {
            prep = conn.prepareStatement(query);
            prep.setLong(1, stObj.getLayerId());
            prep.setLong(2, xyz[0]);
            prep.setLong(3, xyz[1]);
            prep.setLong(4, xyz[2]);
            prep.setLong(5, stObj.getGridSetIdId());
            prep.setLong(6, stObj.getFormatId());

            if (stObj.getParametersId() != null) {
                prep.setLong(7, Long.parseLong(stObj.getParametersId()));
            }

            ResultSet rs = prep.executeQuery();
            try {
                if (rs.first()) {
                    Timestamp lock = rs.getTimestamp(4);

                    // This tile is locked
                    if (lock != null) {
                        Timestamp now = rs.getTimestamp(5);
                        long diff = now.getTime() - lock.getTime();
                        // System.out.println(now.getTime() + " " + System.currentTimeMillis());
                        if (diff > lockTimeout) {
                            log.warn("Database lock exceeded (" + diff + "ms , " + lock.toString()
                                    + ") for " + stObj.toString() + ", clearing tile.");
                            stObj.setStatus(StorageObject.Status.EXPIRED_LOCK);
                        } else {
                            stObj.setStatus(StorageObject.Status.LOCK);
                        }

                        // This puts the request back in the queue
                        return false;
                    }

                    stObj.setId(rs.getLong(1));
                    stObj.setBlobSize(rs.getInt(2));
                    stObj.setCreated(rs.getLong(3));
                    stObj.setStatus(StorageObject.Status.HIT);
                    return true;
                } else {
                    stObj.setStatus(StorageObject.Status.MISS);
                    return false;
                }
            } finally {
                close(rs);
            }
        } finally {
            close(prep);
            close(conn);
        }
    }


    public void destroy() {
        Connection conn = null;
        try {
            conn = getConnection();
            this.closing = true;
            try {
                conn.createStatement().execute("SHUTDOWN");
            } catch (SQLException se) {
                log.warn("SHUTDOWN call to JDBC resulted in: " + se.getMessage());
            }
        } catch (SQLException e) {
            log.error("Couldn't obtain JDBC Connection to perform database shut down", e);
        } finally {
            if (conn != null) {
                // should be already closed after SHUTDOWN
                boolean closed = false;
                try {
                    closed = conn.isClosed();
                } catch (SQLException e) {
                    log.debug("It's OK, db called for shutdown previously", e);
                }
                if (!closed) {
                    log.error("Connection used for metastore db shutdown should be closed at this point");
                }
            }
        }

        try {
            Thread.sleep(250);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.gc();
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.gc();
    }

    private PreparedStatement getTileSet(final Connection conn, long layerId, long formatId,
            long parametersId, long zoomLevel, long[] bounds, long srsNumber) throws SQLException {
        String query;

        if (parametersId == -1L) {
            query = "SELECT TILE_ID, X, Y, Z FROM TILES WHERE "
                    + " LAYER_ID = ? AND X >= ? AND X <= ? AND Y >= ? AND Y <= ? AND Z = ? AND GRIDSET_ID = ? "
                    + " AND FORMAT_ID = ? AND PARAMETERS_ID IS NULL";
        } else {
            query = "SELECT TILE_ID, X, Y, Z FROM TILES WHERE "
                    + " LAYER_ID = ? AND X >= ? AND X <= ? AND Y >= ? AND Y <= ? AND Z = ? AND GRIDSET_ID = ? "
                    + " AND FORMAT_ID = ? AND PARAMETERS_ID = ?";
        }

        PreparedStatement prep = conn.prepareStatement(query);
        prep.setLong(1, layerId);
        prep.setLong(2, bounds[0]);
        prep.setLong(3, bounds[2]);
        prep.setLong(4, bounds[1]);
        prep.setLong(5, bounds[3]);
        prep.setLong(6, zoomLevel);
        prep.setLong(7, srsNumber);
        prep.setLong(8, formatId);

        if (parametersId != -1L) {
            prep.setLong(9, parametersId);
        }
        return prep;
    }

}
