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
package org.geowebcache.storage.jdbc.metastore;

import static org.geowebcache.storage.jdbc.JDBCUtils.close;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.storage.DiscontinuousTileRange;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.StorageObject;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;
import org.h2.jdbcx.JdbcConnectionPool;

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
            .getLog(org.geowebcache.storage.jdbc.metastore.JDBCMBWrapper.class);

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

    private JdbcConnectionPool connPool;

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

        checkTables();
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

        checkTables();
    }

    protected Connection getConnection() throws SQLException {
        if (closing) {
            throw new IllegalStateException(getClass().getSimpleName() + " is being shut down");
        }
        Connection conn;
        if (useConnectionPooling) {
            if (connPool == null) {
                connPool = JdbcConnectionPool.create(jdbcString, username, password == null ? ""
                        : password);
                connPool.setMaxConnections(maxConnections);
            }
            conn = connPool.getConnection();
        } else {
            conn = DriverManager.getConnection(jdbcString, username, password);
        }
        conn.setAutoCommit(true);
        return conn;
    }

    private void checkTables() throws ConfigurationException, SQLException {
        final Connection conn = getConnection();
        try {

            /** Easy ones */
            condCreate(conn, "LAYERS",
                    "ID BIGINT AUTO_INCREMENT PRIMARY KEY, VALUE VARCHAR(254) UNIQUE", "VALUE",
                    null);
            condCreate(conn, "PARAMETERS",
                    "ID BIGINT AUTO_INCREMENT PRIMARY KEY, VALUE VARCHAR(254) UNIQUE", "VALUE",
                    null);
            condCreate(conn, "FORMATS",
                    "ID BIGINT AUTO_INCREMENT PRIMARY KEY, VALUE VARCHAR(126) UNIQUE", "VALUE",
                    null);

            condCreate(conn, "GRIDSETS",
                    "ID BIGINT AUTO_INCREMENT PRIMARY KEY, VALUE VARCHAR(126) UNIQUE", "VALUE",
                    null);

            int fromVersion = getDbVersion(conn);
            log.info("MetaStore database is version " + fromVersion);

            if (fromVersion != DB_VERSION) {
                if (fromVersion < DB_VERSION) {
                    runDbUpgrade(conn, fromVersion);
                } else {
                    log.error("Metastore database is newer than the runnin version of GWC. Proceeding with undefined results.");
                }
            }

            /** Slightly more complex */
            checkWFSTable(conn);
            checkTilesTable(conn);
        } finally {
            close(conn);
        }
    }

    /**
     * Checks / creates the "variables" table and verifies that the db_version variable is.
     * 
     * @param conn
     * @throws SQLException
     * @throws StorageException
     *             if the database is newer than the software
     */
    protected int getDbVersion(Connection conn) throws SQLException, ConfigurationException {

        condCreate(conn, "VARIABLES", "KEY VARCHAR(32), VALUE VARCHAR(128)", "KEY", null);

        Statement st = null;
        ResultSet rs = null;

        try {
            st = conn.createStatement();
            rs = st.executeQuery("SELECT VALUE FROM VARIABLES WHERE KEY LIKE 'db_version'");

            if (rs.first()) {
                // Check what version of the database this is
                String db_versionStr = rs.getString("value");
                int cur_db_version = Integer.parseInt(db_versionStr);
                return cur_db_version;
            } else {
                // This is a new database, insert current value
                st.execute("INSERT INTO VARIABLES (KEY,VALUE) " + " VALUES ('db_version',"
                        + JDBCMBWrapper.DB_VERSION + ")");

                return JDBCMBWrapper.DB_VERSION;
            }
        } finally {
            close(rs);
            close(st);
        }
    }

    private void checkWFSTable(Connection conn) throws SQLException {
        condCreate(conn, "WFS", "WFS_ID BIGINT AUTO_INCREMENT PRIMARY KEY, PARAMETERS_ID BIGINT, "
                + "QUERY_BLOB_MD5 VARCHAR(32), QUERY_BLOB_SIZE INT, " + "BLOB_SIZE INT, "
                + "CREATED BIGINT, ACCESS_LAST BIGINT, ACCESS_COUNT BIGINT, " + "LOCK TIMESTAMP",
                "PARAMETERS_ID", "QUERY_BLOB_MD5, QUERY_BLOB_SIZE");
    }

    private void checkTilesTable(Connection conn) throws SQLException {
        condCreate(conn, "TILES", "TILE_ID BIGINT AUTO_INCREMENT PRIMARY KEY, LAYER_ID BIGINT, "
                + "X BIGINT, Y BIGINT, Z BIGINT, GRIDSET_ID INT, FORMAT_ID BIGINT, "
                + "PARAMETERS_ID BIGINT, BLOB_SIZE INT, "
                + "CREATED BIGINT, ACCESS_LAST BIGINT, ACCESS_COUNT BIGINT, " + "LOCK TIMESTAMP",
                "LAYER_ID, X, Y, Z, GRIDSET_ID, FORMAT_ID, PARAMETERS_ID", null);
    }

    private void condCreate(Connection conn, String tableName, String columns, String indexColumns,
            String index2Columns) throws SQLException {
        Statement st = null;

        try {
            st = conn.createStatement();

            st.execute("CREATE TABLE IF NOT EXISTS " + tableName + " (" + columns + ")");

            st.execute("CREATE INDEX IF NOT EXISTS " + "IDX_" + tableName + " ON " + tableName
                    + " (" + indexColumns + ")");

            if (index2Columns != null) {
                st.execute("CREATE INDEX IF NOT EXISTS " + "IDX2_" + tableName + " ON " + tableName
                        + " (" + index2Columns + ")");
            }
        } finally {
            close(st);
        }

    }

    private void runDbUpgrade(Connection conn, int fromVersion) {
        log.info("Upgrading  H2 database from " + fromVersion + " to " + JDBCMBWrapper.DB_VERSION);

        boolean earlier = false;

        if (fromVersion == 110) {
            log.info("Running database upgrade from 110 to 111");

            earlier = true;
            try {
                // We start with this one to block other instances
                String query = "UPDATE VARIABLES SET VALUE = ? WHERE KEY = ?";
                PreparedStatement prep = conn.prepareStatement(query);
                try {
                    prep.setString(1, "111");
                    prep.setString(2, "db_version");
                    prep.execute();
                } finally {
                    close(prep);
                }

                query = "ALTER TABLE TILES ADD LOCK TIMESTAMP";
                Statement st = conn.createStatement();
                try {
                    st.execute(query);
                } finally {
                    close(st);
                }

                query = "ALTER TABLE WFS ADD LOCK TIMESTAMP";
                st = conn.createStatement();
                try {
                    st.execute(query);
                } finally {
                    close(st);
                }
                log.info("Database upgrade from 110 to 111 completed");
            } catch (SQLException se) {
                log.error("110 to 111 upgrade failed: " + se.getMessage());
            }
        }

        if (fromVersion == 111 || earlier) {
            log.info("Running database upgrade from 111 to 120");
            try {
                // We start with this one to block other instances
                String query = "UPDATE VARIABLES SET VALUE = ? WHERE KEY = ?";
                PreparedStatement prep = conn.prepareStatement(query);
                try {
                    prep.setString(1, "120");
                    prep.setString(2, "db_version");
                    prep.execute();
                } finally {
                    close(prep);
                }
                query = "ALTER TABLE TILES ALTER COLUMN SRS_ID RENAME TO GRIDSET_ID";
                Statement st = conn.createStatement();
                try {
                    st.execute(query);
                } finally {
                    close(st);
                }
                // Now add the existing grids to the IdCache, so that the old stuff
                // continues working: EPSG:4326 -> 4326 , which is what the SRS_ID used to be
                query = "SELECT GRIDSET_ID FROM TILES GROUP BY GRIDSET_ID";
                st = conn.createStatement();
                try {
                    ResultSet rs = st.executeQuery(query);
                    try {
                        while (rs.next()) {
                            int val = rs.getInt(1);
                            query = "INSERT INTO GRIDSETS (ID, VALUE) VALUES (?,?)";
                            prep = conn.prepareStatement(query);
                            try {
                                prep.setLong(1, val);
                                prep.setString(2, "EPSG:" + val);
                                prep.executeUpdate();
                            } finally {
                                close(prep);
                            }
                        }
                    } finally {
                        close(rs);
                    }
                } finally {
                    close(st);
                }

                log.info("Database upgrade from 111 to 120 completed");
            } catch (SQLException se) {
                log.error("111 to 120 upgrade failed: " + se.getMessage());
            }
        }

    }

    protected void deleteTile(TileObject stObj) throws SQLException {
        final Connection conn = getConnection();
        try {
            deleteTile(conn, stObj);
        } finally {
            close(conn);
        }
    }

    protected void deleteTile(Connection conn, TileObject stObj) throws SQLException {

        String query;
        if (stObj.getParametersId() == -1L) {
            query = "DELETE FROM TILES WHERE "
                    + " LAYER_ID = ? AND X = ? AND Y = ? AND Z = ? AND GRIDSET_ID = ? "
                    + " AND FORMAT_ID = ? AND PARAMETERS_ID IS NULL";
        } else {
            query = "DELETE FROM TILES WHERE "
                    + " LAYER_ID = ? AND X = ? AND Y = ? AND Z = ? AND GRIDSET_ID = ? "
                    + " AND FORMAT_ID = ? AND PARAMETERS_ID = ?";
        }
        long[] xyz = stObj.getXYZ();

        PreparedStatement prep = conn.prepareStatement(query);
        try {
            prep.setLong(1, stObj.getLayerId());
            prep.setLong(2, xyz[0]);
            prep.setLong(3, xyz[1]);
            prep.setLong(4, xyz[2]);
            prep.setLong(5, stObj.getGridSetIdId());
            prep.setLong(6, stObj.getFormatId());

            if (stObj.getParametersId() != -1L) {
                prep.setLong(7, stObj.getParametersId());
            }

            prep.execute();
        } finally {
            close(prep);
        }
    }

    protected boolean getTile(TileObject stObj) throws SQLException {
        String query;
        if (stObj.getParametersId() == -1L) {
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

            if (stObj.getParametersId() != -1L) {
                prep.setLong(7, stObj.getParametersId());
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
                            deleteTile(conn, stObj);
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

    public void putTile(TileObject stObj) throws SQLException {

        String query = "MERGE INTO "
                + "TILES(LAYER_ID,X,Y,Z,GRIDSET_ID,FORMAT_ID,PARAMETERS_ID,BLOB_SIZE,LOCK,CREATED) "
                + "KEY(LAYER_ID,X,Y,Z,GRIDSET_ID,FORMAT_ID,PARAMETERS_ID) "
                + "VALUES(?,?,?,?,?,?,?,?,NOW(),?)";

        long[] xyz = stObj.getXYZ();

        final Connection conn = getConnection();

        try {
            Long insertId;
            PreparedStatement prep = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
            try {
                prep.setLong(1, stObj.getLayerId());
                prep.setLong(2, xyz[0]);
                prep.setLong(3, xyz[1]);
                prep.setLong(4, xyz[2]);
                prep.setLong(5, stObj.getGridSetIdId());
                prep.setLong(6, stObj.getFormatId());
                if (stObj.getParametersId() == -1L) {
                    prep.setNull(7, java.sql.Types.BIGINT);
                } else {
                    prep.setLong(7, stObj.getParametersId());
                }
                prep.setInt(8, stObj.getBlobSize());
                prep.setLong(9, System.currentTimeMillis());
                insertId = wrappedInsert(prep);
            } finally {
                close(prep);
            }
            if (insertId == null) {
                log.error("Did not receive a id for " + query);
            } else {
                stObj.setId(insertId.longValue());
            }

        } finally {
            conn.close();
        }

    }

    public boolean unlockTile(TileObject stObj) throws SQLException {

        String query = null;

        if (stObj.getParametersId() == -1L) {
            query = "UPDATE TILES SET LOCK = NULL WHERE "
                    + "  LAYER_ID = ? AND X = ? AND Y = ? AND Z = ? "
                    + " AND GRIDSET_ID = ? AND FORMAT_ID = ? AND " + " PARAMETERS_ID IS NULL";
        } else {
            query = "UPDATE TILES SET LOCK = NULL WHERE "
                    + "  LAYER_ID = ? AND X = ? AND Y = ? AND Z = ? "
                    + " AND GRIDSET_ID = ? AND FORMAT_ID = ? AND " + " PARAMETERS_ID = ?";
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
            if (stObj.getParametersId() != -1L) {
                prep.setLong(7, stObj.getParametersId());
            }

            int affected = prep.executeUpdate();
            // System.out.println("Affected: " + affected);
            if (affected == 1) {
                return true;
            } else {
                log.error("Expected to clear lock on one row, but got " + affected);
                return false;
            }
        } finally {
            close(prep);
            close(conn);
        }

    }

    protected Long wrappedInsert(PreparedStatement st) throws SQLException {
        ResultSet rs = null;

        try {
            st.executeUpdate();
            rs = st.getGeneratedKeys();

            if (rs.next()) {
                return Long.valueOf(rs.getLong(1));
            }

            return null;

        } finally {
            close(rs);
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
                    log.error(e);
                }
                if (!closed) {
                    close(conn);
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

    private void deleteRange(final Connection conn, long layerId, long formatId, long parametersId,
            int zoomLevel, long[] bounds, long gridSetIdId) throws SQLException {
        String query;

        if (parametersId == -1L) {
            query = "DELETE FROM TILES WHERE "
                    + " LAYER_ID = ? AND X >= ? AND X <= ? AND Y >= ? AND Y <= ? AND Z = ? AND GRIDSET_ID = ? "
                    + " AND FORMAT_ID = ? AND PARAMETERS_ID IS NULL";
        } else {
            query = "DELETE FROM TILES WHERE "
                    + " LAYER_ID = ? AND X >= ? AND X <= ? AND Y >= ? AND Y <= ? AND Z = ? AND GRIDSET_ID = ? "
                    + " AND FORMAT_ID = ? AND PARAMETERS_ID = ?";
        }

        PreparedStatement prep = conn.prepareStatement(query);
        try {
            prep.setLong(1, layerId);
            prep.setLong(2, bounds[0]);
            prep.setLong(3, bounds[2]);
            prep.setLong(4, bounds[1]);
            prep.setLong(5, bounds[3]);
            prep.setLong(6, zoomLevel);
            prep.setLong(7, gridSetIdId);
            prep.setLong(8, formatId);

            if (parametersId != -1L) {
                prep.setLong(9, parametersId);
            }

            prep.execute();
        } finally {
            close(prep);
        }

    }

    public void deleteLayer(long layerId) throws SQLException {

        String query = "DELETE FROM TILES WHERE LAYER_ID = ?";

        final Connection conn = getConnection();
        try {
            PreparedStatement prep = conn.prepareStatement(query);
            try {
                prep.setLong(1, layerId);
                prep.execute();
            } finally {
                close(prep);
            }
        } finally {
            close(conn);
        }
    }

    public void deleteLayerGridSubset(final long layerId, final long gridsetId) throws SQLException {
        String query;
        query = "DELETE FROM TILES WHERE LAYER_ID = ? AND GRIDSET_ID = ? ";

        final Connection conn = getConnection();
        try {
            PreparedStatement prep = conn.prepareStatement(query);
            try {
                prep.setLong(1, layerId);
                prep.setLong(2, gridsetId);
                prep.execute();
            } finally {
                close(prep);
            }
        } finally {
            close(conn);
        }
    }

    public void renameLayer(final long layerId, final String newLayerName) throws SQLException {
        String statement = "UPDATE LAYERS SET VALUE = ? WHERE ID = ?";
        final Connection conn = getConnection();
        try {
            PreparedStatement prep = conn.prepareStatement(statement);
            try {
                prep.setString(1, newLayerName);
                prep.setLong(2, layerId);
                prep.execute();
                int updateCount = prep.getUpdateCount();
                if (1 == updateCount) {
                    log.info("Layer " + layerId + " successfully renamed to '" + newLayerName + "'");
                } else {
                    log.warn("Attempt to rename layer with id " + layerId + " as '" + newLayerName
                            + "' did not produce any update on the database!");
                }
            } finally {
                close(prep);
            }
        } finally {
            close(conn);
        }
    }

    public boolean deleteRange(BlobStore blobStore, TileRange trObj, int zoomLevel, long layerId,
            long formatId, long parametersId, long gridSetIdId) {

        DiscontinuousTileRange dtrObj = null;
        long[] deletedTiles = null;

        if (trObj instanceof DiscontinuousTileRange) {
            dtrObj = (DiscontinuousTileRange) trObj;
            deletedTiles = new long[100];
        }

        long[] bounds = trObj.rangeBounds(zoomLevel);

        final Connection conn;
        try {
            conn = getConnection();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        PreparedStatement tileSetQuery = null;
        ResultSet rs = null;
        try {
            tileSetQuery = getTileSet(conn, layerId, formatId, parametersId, zoomLevel, bounds,
                    gridSetIdId);

            rs = tileSetQuery.executeQuery();

            int deletedIdx = 0;

            while (rs.next()) {
                // TILE_ID, X, Y, Z
                long[] xyz = new long[3];
                xyz[0] = rs.getLong(2);
                xyz[1] = rs.getLong(3);
                xyz[2] = rs.getLong(4);

                if (dtrObj != null && !dtrObj.contains(xyz)) {
                    continue;
                }

                // System.out.println("x: " + xyz[0] + " y: " + xyz[1] + " z: " + xyz[2]);

                TileObject to = TileObject.createQueryTileObject(trObj.getLayerName(), xyz,
                        trObj.getGridSetId(), trObj.getMimeType().getFormat(),
                        trObj.getParameters());
                to.setParamtersId(parametersId);

                try {
                    blobStore.delete(to);
                } catch (StorageException e) {
                    log.debug("Error while deleting range: " + e.getMessage());
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (deletedTiles != null) {
                    deletedTiles[deletedIdx] = rs.getLong(1);
                    deletedIdx++;

                    if (deletedIdx == deletedTiles.length) {
                        deleteTileSet(conn, deletedTiles, deletedIdx);
                        deletedIdx = 0;
                    }
                }
            }

            // Now remove the tiles from the database
            if (deletedTiles != null) {
                deleteTileSet(conn, deletedTiles, deletedIdx);
            } else {
                deleteRange(conn, layerId, formatId, parametersId, zoomLevel, bounds, gridSetIdId);
            }
        } catch (SQLException e) {
            log.error("deleteRange failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            close(rs);
            close(tileSetQuery);
            close(conn);
        }

        return true;
    }

    /**
     * Deletes a tile
     * 
     * @param tileIds
     * @param stopIdx
     * @throws SQLException
     */
    private void deleteTileSet(final Connection conn, long[] tileIds, int stopIdx)
            throws SQLException {
        if (stopIdx == 0) {
            return;
        }

        StringBuffer sb = new StringBuffer();
        sb.append("DELETE FROM TILES WHERE TILE_ID IN (");
        sb.append(tileIds[0]);
        for (int i = 1; i < stopIdx; i++) {
            sb.append(",");
            sb.append(tileIds[i]);
        }
        sb.append(")");

        PreparedStatement prepDel = conn.prepareStatement(sb.toString());
        try {
            prepDel.execute();
        } finally {
            close(prepDel);
        }

        log.debug("Deleted " + Arrays.toString(tileIds));
    }

    public void expireRange(TileRange trObj, int zoomLevel, long layerId, long formatId,
            long parametersId, long gridSetIdId) throws SQLException {

        long[] bounds = trObj.rangeBounds(zoomLevel);

        String query;

        if (parametersId == -1L) {
            query = "UPDATE TILES SET CREATED = -1 WHERE "
                    + " LAYER_ID = ? AND X >= ? AND X <= ? AND Y >= ? AND Y <= ? AND Z = ? AND GRIDSET_ID = ? "
                    + " AND FORMAT_ID = ? AND PARAMETERS_ID IS NULL";
        } else {
            query = "UPDATE TILES SET CREATED = -1 WHERE "
                    + " LAYER_ID = ? AND X >= ? AND X <= ? AND Y >= ? AND Y <= ? AND Z = ? AND GRIDSET_ID = ? "
                    + " AND FORMAT_ID = ? AND PARAMETERS_ID = ?";
        }

        final Connection conn = getConnection();
        try {
            PreparedStatement prep = conn.prepareStatement(query);
            try {
                prep.setLong(1, layerId);
                prep.setLong(2, bounds[0]);
                prep.setLong(3, bounds[2]);
                prep.setLong(4, bounds[1]);
                prep.setLong(5, bounds[3]);
                prep.setLong(6, zoomLevel);
                prep.setLong(7, gridSetIdId);
                prep.setLong(8, formatId);

                if (parametersId != -1L) {
                    prep.setLong(9, parametersId);
                }

                prep.execute();
            } finally {
                close(prep);
            }
        } finally {
            close(conn);
        }
    }

}
