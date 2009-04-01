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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.WFSObject;

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
    private static Log log = LogFactory.getLog(org.geowebcache.storage.metastore.jdbc.JDBCMBWrapper.class);
    
    /** Database version, for automatic updates */
    static int DB_VERSION = 110;
    
    /** Connection information */
    final String jdbcString;
    
    final String username;
    
    final String password;
        
    final String driverClass;
    
    final Connection persistentConnection;
    
    boolean closing = false;
    
    protected JDBCMBWrapper(String driverClass, String jdbcString,String username, String password)
    throws StorageException,SQLException {
        this.jdbcString = jdbcString;
        this.username = username;
        this.password = password;
        this.driverClass = driverClass;
        
        try {
            Class.forName(driverClass);
        } catch(ClassNotFoundException cnfe) {
            throw new StorageException("Class not found: " + cnfe.getMessage());
        }
        
        persistentConnection = getConnection();
        
        checkTables();
    }
    
    public JDBCMBWrapper(DefaultStorageFinder defStoreFind) throws StorageException,SQLException {
        this.username = "sa";
        this.password = "";
        this.driverClass = "org.h2.Driver";
        String path = defStoreFind.getDefaultPath() + File.separator + "meta_jdbc_h2";
        File dir = new File(path);
        dir.mkdirs();
        this.jdbcString = "jdbc:h2:file:"+path+File.separator+"gwc_metastore";
        
        try {
            Class.forName(driverClass);
        } catch(ClassNotFoundException cnfe) {
            throw new StorageException("Class not found: " + cnfe.getMessage());
        }
        
        persistentConnection = getConnection();
        
        checkTables();
    }

    protected Connection getConnection() throws SQLException {
        if(! closing) {
            return DriverManager.getConnection(jdbcString,username,password);
        } else {
            return null;
        }
    }
    
    private void checkTables() throws StorageException,SQLException {
        Connection conn = null;
        try {
            conn = getConnection();
            
            checkVariableTable(conn);
            
            /** Easy ones */
            condCreate(conn,
                    "LAYERS", 
                    "ID BIGINT AUTO_INCREMENT PRIMARY KEY, VALUE VARCHAR(254) UNIQUE", 
                    "VALUE",
                    null);
            condCreate(conn,
                    "PARAMETERS", 
                    "ID BIGINT AUTO_INCREMENT PRIMARY KEY, VALUE VARCHAR(254) UNIQUE", 
                    "VALUE",
                    null);
            condCreate(conn,
                    "FORMATS", 
                    "ID BIGINT AUTO_INCREMENT PRIMARY KEY, VALUE VARCHAR(126) UNIQUE", 
                    "VALUE",
                    null);
            
            /** Slightly more complex */
            checkWFSTable(conn);
            checkTilesTable(conn);
        } finally {
            if(conn != null)
                conn.close();
        }
    }
    
    /**
     * Checks / creates the "variables" table and verifies that the db_version
     * variable is.
     * 
     * @param conn
     * @throws SQLException
     * @throws StorageException if the database is newer than the software
     */
    private void checkVariableTable(Connection conn) 
    throws SQLException, StorageException {
        
        condCreate(conn,
                "VARIABLES", 
                "KEY VARCHAR(32), VALUE VARCHAR(128)", 
                "KEY",
                null);
        
        Statement st = null;
        ResultSet rs = null;
        
        try {
            st = conn.createStatement();            
            rs = st.executeQuery("SELECT VALUE FROM VARIABLES WHERE KEY LIKE 'db_version'");

            if (rs.first()) {
                // Check what version of the database this is
                String db_versionStr = rs.getString("value");
                int cur_db_version = Integer.parseInt(db_versionStr);
                if (cur_db_version > JDBCMBWrapper.DB_VERSION) {
                    throw new StorageException(
                            "The H2 database was created by a newer version of the software.");
                } else if (cur_db_version < JDBCMBWrapper.DB_VERSION) {
                    runDbUpgrade(conn, cur_db_version);
                }
            } else {
                // This is a new database, insert current value
                st.execute("INSERT INTO VARIABLES (KEY,VALUE) "
                        +" VALUES ('db_version',"+JDBCMBWrapper.DB_VERSION+")");
            }
        } finally {
            if(rs != null)
                rs.close();
            if(st != null)
                st.close();
        }
    }
    
    private void checkWFSTable(Connection conn) throws SQLException {
        condCreate(conn,
                "WFS", 
                "WFS_ID BIGINT AUTO_INCREMENT PRIMARY KEY, PARAMETERS_ID BIGINT, "
                +"QUERY_BLOB_MD5 VARCHAR(32), QUERY_BLOB_SIZE INT, "
                +"BLOB_SIZE INT, "
                +"CREATED BIGINT, ACCESS_LAST BIGINT, ACCESS_COUNT BIGINT",
                "PARAMETERS_ID",
                "QUERY_BLOB_MD5, QUERY_BLOB_SIZE");
    }
    
    private void checkTilesTable(Connection conn) throws SQLException {
        condCreate(conn,
                "TILES", 
                "TILE_ID BIGINT AUTO_INCREMENT PRIMARY KEY, LAYER_ID BIGINT, "
                + "X BIGINT, Y BIGINT, Z BIGINT, SRS_ID INT, FORMAT_ID BIGINT, "
                + "PARAMETERS_ID BIGINT, BLOB_SIZE INT, "
                + "CREATED BIGINT, ACCESS_LAST BIGINT, ACCESS_COUNT BIGINT",
                "LAYER_ID, X, Y, Z, SRS_ID, FORMAT_ID, PARAMETERS_ID",
                null);
    }
    
    private void condCreate(Connection conn, String tableName, 
            String columns, String indexColumns, String index2Columns) 
    throws SQLException {
        Statement st = null;
        
        try {
            st = conn.createStatement();
            
            st.execute("CREATE TABLE IF NOT EXISTS "
                    +tableName+" ("+columns+")");

            st.execute("CREATE INDEX IF NOT EXISTS "+
                    "IDX_"+tableName +" ON "+tableName+" ("+indexColumns+")");
            
            if(index2Columns != null) {
                st.execute("CREATE INDEX IF NOT EXISTS "+
                        "IDX2_"+tableName +" ON "+tableName+" ("+index2Columns+")");
            }
        } finally {
            if(st != null)
                st.close();
        }
        
    }
    
    private void runDbUpgrade(Connection conn, int fromVersion) {
        log.info("Upgrading  H2 database from " + fromVersion 
                + " to " + JDBCMBWrapper.DB_VERSION);
    }
    
    protected boolean getTile(TileObject stObj) throws SQLException {

        String query;
        if(stObj.getParametersId() == -1L) {
            query = "SELECT TILE_ID,BLOB_SIZE,CREATED FROM TILES WHERE " 
                + " LAYER_ID = ? AND X = ? AND Y = ? AND Z = ? AND SRS_ID = ? " 
                + " AND FORMAT_ID = ? AND PARAMETERS_ID IS NULL LIMIT 1 ";
        } else {
            query = "SELECT TILE_ID,BLOB_SIZE,CREATED FROM TILES WHERE " 
                + " LAYER_ID = ? AND X = ? AND Y = ? AND Z = ? AND SRS_ID = ? "
                + " AND FORMAT_ID = ? AND PARAMETERS_ID = ? LIMIT 1 ";
        }
        long[] xyz = stObj.getXYZ();
        
        Connection conn = getConnection();
        
        PreparedStatement prep = conn.prepareStatement(query);
        prep.setLong(1, stObj.getLayerId());
        prep.setLong(2, xyz[0]);
        prep.setLong(3, xyz[1]);
        prep.setLong(4, xyz[2]);
        prep.setLong(5, stObj.getSrs());
        prep.setLong(6, stObj.getFormatId());
        
        if(stObj.getParametersId() != -1L) {
            prep.setLong(7, stObj.getParametersId());
        }
        
        ResultSet rs = null;
        
        try {
            rs = prep.executeQuery();

            if (rs.first()) {
                stObj.setId(rs.getLong(1));
                stObj.setBlobSize(rs.getInt(2));
                stObj.setCreated(rs.getLong(3));
                return true;
            } else {
                return false;
            }
        } finally {
            if(rs != null)
                rs.close();
            
            if(prep != null)
                prep.close();
            
            conn.close();
        }
    }
    
    protected boolean getWFS(Long parameters, WFSObject wfsObj) 
    throws SQLException {
        String query = null;
        PreparedStatement prep = null;
        Connection conn = getConnection();
        
        if(parameters != null) {
            query = "SELECT WFS_ID,BLOB_SIZE,CREATED FROM WFS WHERE " 
                + " PARAMETERS_ID = ? LIMIT 1 ";
             
            prep = conn.prepareStatement(query);
            prep.setLong(1, parameters);
        } else {
            query = "SELECT WFS_ID,BLOB_SIZE,CREATED FROM WFS WHERE " 
                + " QUERY_BLOB_MD5 LIKE ? AND QUERY_BLOB_SIZE = ? LIMIT 1";
            
            prep = conn.prepareStatement(query);
            prep.setString(1,wfsObj.getQueryBlobMd5());
            prep.setInt(2, wfsObj.getQueryBlobSize());
        }
        
        ResultSet rs = null;
        
        try {
            rs = prep.executeQuery();

            if (rs.next()) {
                wfsObj.setId(rs.getLong(1));
                wfsObj.setBlobSize(rs.getInt(2));
                wfsObj.setCreated(rs.getLong(3));
                return true;
            } else {
                return false;
            }
        } finally {
            if(rs != null)
                rs.close();
            
            if(prep != null)
                prep.close();
            
            conn.close();
        }
    }
    
    public void putTile(TileObject stObj) 
    throws SQLException, StorageException {

        String query = "INSERT INTO TILES (" 
                + "  LAYER_ID,X,Y,Z,SRS_ID,FORMAT_ID,PARAMETERS_ID,BLOB_SIZE" 
                + ") VALUES(?,?,?,?,?,?,?,?)";
        
        long[] xyz = stObj.getXYZ();
        
        Connection conn = getConnection();
        
        PreparedStatement prep = conn.prepareStatement(
                query, Statement.RETURN_GENERATED_KEYS);
        prep.setLong(1, stObj.getLayerId());
        prep.setLong(2, xyz[0]);
        prep.setLong(3, xyz[1]);
        prep.setLong(4, xyz[2]);
        prep.setLong(5, stObj.getSrs());
        prep.setLong(6, stObj.getFormatId());
        if(stObj.getParametersId() == -1L) {
            prep.setNull(7, java.sql.Types.BIGINT);
        } else {
            prep.setLong(7, stObj.getParametersId());
        }
        prep.setInt(8, stObj.getBlobSize());
        
        Long insertId = wrappedInsert(prep);
        
        if(insertId == null) {
            log.error("Did not receive a id for " + query);
        } else {
            stObj.setId(insertId.longValue());
        }
        
        conn.close();
    }
    
    public void putWFS(Long parameters, WFSObject stObj)
            throws SQLException, StorageException {

        PreparedStatement prep = null;
        String query = null;
        Connection conn = getConnection();
        
        if (parameters != null) {
            query = "INSERT INTO WFS (" 
                    + "  PARAMETERS_ID,BLOB_SIZE,CREATED" 
                    + ") VALUES(?,?,?)";

            prep = conn.prepareStatement(query,
                    Statement.RETURN_GENERATED_KEYS);
            prep.setLong(1, parameters);
            prep.setInt(2, stObj.getBlobSize());
            prep.setLong(3, stObj.getCreated());
            
        } else {
            query = "INSERT INTO WFS (" 
                    + " QUERY_BLOB_MD5, QUERY_BLOB_SIZE,BLOB_SIZE,CREATED"
                    + ") VALUES(?,?,?,?)";

            prep = conn.prepareStatement(query,
                    Statement.RETURN_GENERATED_KEYS);

            prep.setString(1, stObj.getQueryBlobMd5());
            prep.setInt(2, stObj.getQueryBlobSize());
            prep.setInt(3, stObj.getBlobSize());
            prep.setLong(4, stObj.getCreated());
        }

        Long insertId = wrappedInsert(prep);

        if (insertId == null) {
            log.error("Did not receive a id for " + query);
        } else {
            stObj.setId(insertId.longValue());
        }
        
        conn.close();
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
        try {
            persistentConnection.createStatement().execute("SHUTDOWN");
        } catch (SQLException se) {
            log.warn("SHUTDOWN call to JDBC resulted in: " + se.getMessage());
        }
        try {
            persistentConnection.close();
        } catch (SQLException se) {
            log.warn(se.getMessage());
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
}
