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
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.storage.DiscontinuousTileRange;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.StorageObject;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;
import org.geowebcache.storage.WFSObject;
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

    private JdbcConnectionPool connPool;

    protected JDBCMBWrapper(String driverClass, String jdbcString, String username,
            String password, boolean useConnectionPooling, int maxConnections)
            throws StorageException, SQLException {
        this.jdbcString = jdbcString;
        this.username = username;
        this.password = password;
        this.driverClass = driverClass;
        this.useConnectionPooling = useConnectionPooling;
        this.maxConnections = maxConnections;
        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException cnfe) {
            throw new StorageException("Class not found: " + cnfe.getMessage());
        }

        if (!useConnectionPooling) {
            persistentConnection = getConnection();
        }

        checkTables();
    }

    public JDBCMBWrapper(DefaultStorageFinder defStoreFind, boolean useConnectionPooling,
            int maxConnections) throws StorageException, SQLException {
        String envStrUsername = defStoreFind
                .findEnvVar(DefaultStorageFinder.GWC_METASTORE_USERNAME);
        String envStrPassword = defStoreFind
                .findEnvVar(DefaultStorageFinder.GWC_METASTORE_PASSWORD);
        String envStrJdbcUrl = defStoreFind.findEnvVar(DefaultStorageFinder.GWC_METASTORE_JDBC_URL);
        String envStrDriver = defStoreFind
                .findEnvVar(DefaultStorageFinder.GWC_METASTORE_DRIVER_CLASS);
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
                throw new StorageException("Unable to create " + dir.getAbsolutePath()
                        + " for H2 database.");
            }
            this.jdbcString = "jdbc:h2:file:" + path + File.separator + "gwc_metastore"
                    + ";TRACE_LEVEL_FILE=0;AUTO_SERVER=TRUE";
        }

        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException cnfe) {
            throw new StorageException("Class not found: " + cnfe.getMessage());
        }

        if (!useConnectionPooling) {
            persistentConnection = getConnection();
        }

        checkTables();
    }

    protected Connection getConnection() throws SQLException {
        if (!closing) {
            Connection conn;
            if (useConnectionPooling) {
                if (connPool == null) {
                    connPool = JdbcConnectionPool.create(jdbcString, username,
                            password == null ? "" : password);
                    connPool.setMaxConnections(maxConnections);
                }
                conn = connPool.getConnection();
            } else {
                conn = DriverManager.getConnection(jdbcString, username, password);
            }
            conn.setAutoCommit(true);
            return conn;
        } else {
            return null;
        }
    }

    private void checkTables() throws StorageException, SQLException {
        Connection conn = null;
        try {
            conn = getConnection();

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
            if (conn != null)
                conn.close();
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
    protected int getDbVersion(Connection conn) throws SQLException, StorageException {

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
            if (rs != null)
                rs.close();
            if (st != null)
                st.close();
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
            if (st != null)
                st.close();
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
                prep.setString(1, "111");
                prep.setString(2, "db_version");
                prep.execute();
                prep.close();

                query = "ALTER TABLE TILES ADD LOCK TIMESTAMP";
                Statement st = conn.createStatement();
                st.execute(query);
                st.close();

                query = "ALTER TABLE WFS ADD LOCK TIMESTAMP";
                st = conn.createStatement();
                st.execute(query);
                st.close();
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
                prep.setString(1, "120");
                prep.setString(2, "db_version");
                prep.execute();
                prep.close();

                query = "ALTER TABLE TILES ALTER COLUMN SRS_ID RENAME TO GRIDSET_ID";
                Statement st = conn.createStatement();
                st.execute(query);
                st.close();

                // Now add the existing grids to the IdCache, so that the old stuff
                // continues working: EPSG:4326 -> 4326 , which is what the SRS_ID used to be
                query = "SELECT GRIDSET_ID FROM TILES GROUP BY GRIDSET_ID";
                st = conn.createStatement();
                ResultSet rs = st.executeQuery(query);
                while (rs.next()) {
                    int val = rs.getInt(1);
                    query = "INSERT INTO GRIDSETS (ID, VALUE) VALUES (?,?)";
                    prep = getConnection().prepareStatement(query);

                    prep.setLong(1, val);
                    prep.setString(2, "EPSG:" + val);

                    prep.executeUpdate();
                    prep.close();
                }
                rs.close();
                st.close();

                log.info("Database upgrade from 111 to 120 completed");
            } catch (SQLException se) {
                log.error("111 to 120 upgrade failed: " + se.getMessage());
            }
        }

    }

    protected void deleteTile(TileObject stObj) throws SQLException {

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

        Connection conn = getConnection();

        PreparedStatement prep = conn.prepareStatement(query);
        prep.setLong(1, stObj.getLayerId());
        prep.setLong(2, xyz[0]);
        prep.setLong(3, xyz[1]);
        prep.setLong(4, xyz[2]);
        prep.setLong(5, stObj.getGridSetIdId());
        prep.setLong(6, stObj.getFormatId());

        if (stObj.getParametersId() != -1L) {
            prep.setLong(7, stObj.getParametersId());
        }

        try {
            prep.execute();

        } finally {
            prep.close();
            conn.close();
        }
    }

    protected void deleteWFS(Long parameters, WFSObject wfsObj) throws SQLException {
        String query = null;
        PreparedStatement prep = null;
        Connection conn = getConnection();

        if (parameters != null) {
            query = "DELETE FROM WFS WHERE " + " PARAMETERS_ID = ?";

            prep = conn.prepareStatement(query);
            prep.setLong(1, parameters);
        } else {
            query = "DELETE FROM WFS WHERE " + " QUERY_BLOB_MD5 LIKE ? AND QUERY_BLOB_SIZE = ?";

            prep = conn.prepareStatement(query);
            prep.setString(1, wfsObj.getQueryBlobMd5());
            prep.setInt(2, wfsObj.getQueryBlobSize());
        }

        try {
            prep.execute();

        } finally {
            if (prep != null)
                prep.close();

            conn.close();
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

        Connection conn = getConnection();

        PreparedStatement prep = conn.prepareStatement(query);
        prep.setLong(1, stObj.getLayerId());
        prep.setLong(2, xyz[0]);
        prep.setLong(3, xyz[1]);
        prep.setLong(4, xyz[2]);
        prep.setLong(5, stObj.getGridSetIdId());
        prep.setLong(6, stObj.getFormatId());

        if (stObj.getParametersId() != -1L) {
            prep.setLong(7, stObj.getParametersId());
        }

        ResultSet rs = null;

        try {
            rs = prep.executeQuery();

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
                        deleteTile(stObj);
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
            if (rs != null)
                rs.close();

            if (prep != null)
                prep.close();

            conn.close();
        }
    }

    protected boolean getWFS(Long parameters, WFSObject wfsObj) throws SQLException {
        String query = null;
        PreparedStatement prep = null;
        Connection conn = getConnection();

        if (parameters != null) {
            query = "SELECT WFS_ID,BLOB_SIZE,CREATED,LOCK,NOW() FROM WFS WHERE "
                    + " PARAMETERS_ID = ? LIMIT 1 ";

            prep = conn.prepareStatement(query);
            prep.setLong(1, parameters);
        } else {
            query = "SELECT WFS_ID,BLOB_SIZE,CREATED,LOCK,NOW() FROM WFS WHERE "
                    + " QUERY_BLOB_MD5 LIKE ? AND QUERY_BLOB_SIZE = ? LIMIT 1";

            prep = conn.prepareStatement(query);
            prep.setString(1, wfsObj.getQueryBlobMd5());
            prep.setInt(2, wfsObj.getQueryBlobSize());
        }

        ResultSet rs = null;

        try {
            rs = prep.executeQuery();

            if (rs.next()) {
                Timestamp lock = rs.getTimestamp(4);

                // This tile is locked
                if (lock != null) {
                    Timestamp now = rs.getTimestamp(5);
                    if (now.getTime() - lock.getTime() > lockTimeout) {
                        log.warn("Database lock exceeded for " + wfsObj.toString()
                                + ", clearing WFS object.");
                        deleteWFS(parameters, wfsObj);
                        wfsObj.setStatus(StorageObject.Status.EXPIRED_LOCK);
                    } else {
                        wfsObj.setStatus(StorageObject.Status.LOCK);
                    }

                    // This puts the request back in the queue
                    return false;
                }

                wfsObj.setId(rs.getLong(1));
                wfsObj.setBlobSize(rs.getInt(2));
                wfsObj.setCreated(rs.getLong(3));

                wfsObj.setStatus(StorageObject.Status.HIT);
                return true;
            } else {
                wfsObj.setStatus(StorageObject.Status.MISS);
                return false;
            }
        } finally {
            if (rs != null)
                rs.close();

            if (prep != null)
                prep.close();

            conn.close();
        }
    }

    public void putTile(TileObject stObj) throws SQLException, StorageException {

        String query = "MERGE INTO "
                + "TILES(LAYER_ID,X,Y,Z,GRIDSET_ID,FORMAT_ID,PARAMETERS_ID,BLOB_SIZE,LOCK,CREATED) "
                + "KEY(LAYER_ID,X,Y,Z,GRIDSET_ID,FORMAT_ID,PARAMETERS_ID) "
                + "VALUES(?,?,?,?,?,?,?,?,NOW(),?)";

        long[] xyz = stObj.getXYZ();

        Connection conn = getConnection();

        try {
            PreparedStatement prep = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
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
            Long insertId = wrappedInsert(prep);

            if (insertId == null) {
                log.error("Did not receive a id for " + query);
            } else {
                stObj.setId(insertId.longValue());
            }
        } finally {
            conn.close();
        }

    }

    public void putWFS(Long parameters, WFSObject stObj) throws SQLException, StorageException {

        PreparedStatement prep = null;
        String query = null;
        Connection conn = getConnection();

        try {
            if (parameters != null) {
                query = "MERGE INTO " + "WFS(PARAMETERS_ID,BLOB_SIZE,CREATED,LOCK) "
                        + "KEY(PARAMETERS_ID) " + "VALUES(?,?,?,NOW())";

                prep = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
                prep.setLong(1, parameters);
                prep.setInt(2, stObj.getBlobSize());
                prep.setLong(3, stObj.getCreated());

            } else {
                query = "MERGE INTO "
                        + "WFS(QUERY_BLOB_MD5, QUERY_BLOB_SIZE,BLOB_SIZE,CREATED,LOCK) "
                        + "KEY(QUERY_BLOB_MD5, QUERY_BLOB_SIZE) " + "VALUES(?,?,?,?,NOW())";

                prep = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);

                prep.setString(1, stObj.getQueryBlobMd5());
                prep.setInt(2, stObj.getQueryBlobSize());
                prep.setInt(3, stObj.getBlobSize());
                prep.setLong(4, stObj.getCreated());
            }

            Long insertId = wrappedInsert(prep);

            if (insertId == null) {
                log.error("Did not receive a id for " + query);
            } else {
                if (insertId.longValue() == 0) {
                    // This was an update due to merge, so we do a get to pick up the id
                    this.getWFS(parameters, stObj);
                } else {
                    stObj.setId(insertId.longValue());
                }
            }
        } finally {
            conn.close();
        }
    }

    public boolean unlockTile(TileObject stObj) throws SQLException, StorageException {

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

        Connection conn = getConnection();

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
            if (prep != null)
                prep.close();

            conn.close();
        }

    }

    public boolean unlockWFS(Long parameters, WFSObject stObj) throws SQLException,
            StorageException {

        PreparedStatement prep = null;
        String query = null;
        Connection conn = getConnection();

        try {
            if (parameters != null) {
                query = "UPDATE WFS SET LOCK = NULL WHERE PARAMETERS_ID = ? ";

                prep = conn.prepareStatement(query);
                prep.setLong(1, parameters);
            } else {
                query = "UPDATE WFS SET LOCK = NULL WHERE QUERY_BLOB_MD5 = ? AND QUERY_BLOB_SIZE = ?";

                prep = conn.prepareStatement(query);

                prep.setString(1, stObj.getQueryBlobMd5());
                prep.setInt(2, stObj.getQueryBlobSize());
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
            if (prep != null)
                prep.close();

            conn.close();
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
            if (rs != null)
                rs.close();

            if (st != null)
                st.close();
        }
    }

    protected void destroy() {
        this.closing = true;
        if (persistentConnection != null) {
            try {
                persistentConnection.createStatement().execute("SHUTDOWN");
            } catch (SQLException se) {
                log.warn("SHUTDOWN call to JDBC resulted in: " + se.getMessage());
            } finally {
                try {
                    persistentConnection.close();
                } catch (SQLException se) {
                    log.warn(se.getMessage());
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

    private ResultSet getTileSet(long layerId, long formatId, long parametersId, long zoomLevel,
            long[] bounds, long srsNumber) throws SQLException {
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

        Connection conn = getConnection();

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

        return prep.executeQuery();
    }

    private void deleteRange(long layerId, long formatId, long parametersId, int zoomLevel,
            long[] bounds, long gridSetIdId) throws SQLException {
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

        Connection conn = getConnection();

        PreparedStatement prep = conn.prepareStatement(query);
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
    }

    public void deleteLayer(long layerId) throws SQLException {

        String query = "DELETE FROM TILES WHERE LAYER_ID = ?";

        Connection conn = getConnection();

        PreparedStatement prep = conn.prepareStatement(query);
        prep.setLong(1, layerId);

        prep.execute();

    }

    public boolean deleteRange(BlobStore blobStore, TileRange trObj, int zoomLevel, long layerId,
            long formatId, long parametersId, long gridSetIdId) {

        DiscontinuousTileRange dtrObj = null;
        long[] deletedTiles = null;

        if (trObj instanceof DiscontinuousTileRange) {
            dtrObj = (DiscontinuousTileRange) trObj;
            deletedTiles = new long[100];
        }

        long[] bounds = trObj.rangeBounds[zoomLevel];

        ResultSet rs = null;
        try {
            rs = getTileSet(layerId, formatId, parametersId, zoomLevel, bounds, gridSetIdId);
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

                TileObject to = TileObject.createQueryTileObject(trObj.layerName, xyz,
                        trObj.gridSetId, trObj.mimeType.getFormat(), trObj.parameters);

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
                        deleteTileSet(deletedTiles, deletedIdx);
                        deletedIdx = 0;
                    }
                }
            }

            // Now remove the tiles from the database
            if (deletedTiles != null) {
                deleteTileSet(deletedTiles, deletedIdx);
            } else {
                deleteRange(layerId, formatId, parametersId, zoomLevel, bounds, gridSetIdId);
            }
        } catch (SQLException e) {
            log.error("deleteRange failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            try {
                rs.close();
            } catch (SQLException e) {
                log.debug(e.getMessage());
            }
        }

        return true;
    }

    /**
     * Deletes a tile
     * 
     * @param tileIds
     * @param stopIdx
     */
    private void deleteTileSet(long[] tileIds, int stopIdx) {
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

        Connection conn = null;
        try {
            conn = getConnection();
            PreparedStatement prepDel = conn.prepareStatement(sb.toString());
            prepDel.execute();

        } catch (SQLException se) {
            log.error("Error deleting tile set: " + se.getMessage());
        } finally {
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException se) {
                log.error("Error closing connection: " + se.getMessage());
            }
        }

        log.debug("Deleted " + Arrays.toString(tileIds));
    }

    public void expireRange(TileRange trObj, int zoomLevel, long layerId, long formatId,
            long parametersId, long gridSetIdId) throws SQLException {

        long[] bounds = trObj.rangeBounds[zoomLevel];

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

        Connection conn = getConnection();

        PreparedStatement prep = conn.prepareStatement(query);
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
    }

}
