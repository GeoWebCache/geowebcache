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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
        
        checkTables();
    }
    
    protected Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcString,username,password);
    }
    
    private void checkTables() throws StorageException,SQLException {
        Connection conn = null;
        try {
            conn = getConnection();
            
            checkVariableTable(conn);
            
            /** Easy ones */
            condCreate(conn,
                    "LAYERS", 
                    "ID INT AUTO_INCREMENT PRIMARY KEY, VALUE VARCHAR(254) UNIQUE", 
                    "VALUE",
                    null);
            condCreate(conn,
                    "PARAMETERS", 
                    "ID INT AUTO_INCREMENT PRIMARY KEY, VALUE VARCHAR(254) UNIQUE", 
                    "VALUE",
                    null);
            condCreate(conn,
                    "FORMATS", 
                    "ID INT AUTO_INCREMENT PRIMARY KEY, VALUE VARCHAR(126) UNIQUE", 
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
                "WFS_ID BIGINT AUTO_INCREMENT PRIMARY KEY, PARAMETERS_ID INT, "
                +"QUERY_BLOB_MD5 VARCHAR(32), QUERY_BLOB_SIZE INT, "
                +"BLOB_SIZE INT, "
                +"CREATED BIGINT, ACCESS_LAST BIGINT, ACCESS_COUNT BIGINT",
                "PARAMETERS_ID",
                "QUERY_BLOB_MD5, QUERY_BLOB_SIZE");
    }
    
    private void checkTilesTable(Connection conn) throws SQLException {
        condCreate(conn,
                "TILES", 
                "TILE_ID BIGINT AUTO_INCREMENT PRIMARY KEY, LAYER_ID INT, "
                + "X BIGINT, Y BIGINT, Z BIGINT, SRS_ID INT, FORMAT_ID INT, "
                + "PARAMETERS_ID INT, BLOB_SIZE INT, "
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
    
    protected boolean getTile(Integer layer_id, long[] xyz, Integer format_id,
            Integer parameters, TileObject tileObj) throws SQLException {

        String query;
        if(parameters == null) {
            query = "SELECT TILE_ID,BLOB_SIZE,CREATED FROM TILES WHERE " 
                + " LAYER_ID = ? AND X = ? AND Y = ? AND Z = ? AND FORMAT_ID = ? "
                + " AND PARAMETERS_ID IS NULL LIMIT 1 ";
        } else {
            query = "SELECT TILE_ID,BLOB_SIZE,CREATED FROM TILES WHERE " 
                + " LAYER_ID = ? AND X = ? AND Y = ? AND Z = ? AND FORMAT_ID = ? "
                + " AND PARAMETERS_ID = ? LIMIT 1 ";
        }
        PreparedStatement prep = getConnection().prepareStatement(query);
        prep.setInt(1, layer_id);
        prep.setLong(2, xyz[0]);
        prep.setLong(3, xyz[1]);
        prep.setLong(4, xyz[2]);
        prep.setInt(5, format_id);
        if(parameters != null) {
            prep.setInt(6, parameters);
        }

        ResultSet rs = null;
        
        try {
            rs = prep.executeQuery();

            if (rs.first()) {
                tileObj.setId(rs.getLong(1));
                tileObj.setBlobSize(rs.getInt(2));
                tileObj.setCreated(rs.getLong(3));
                return true;
            } else {
                return false;
            }
        } finally {
            if(rs != null)
                rs.close();
            
            if(prep != null)
                prep.close();
        }
    }
    
    protected boolean getWFS(Integer parameters, WFSObject wfsObj) 
    throws SQLException {
        String query = null;
        PreparedStatement prep = null;
        
        if(parameters != null) {
            query = "SELECT WFS_ID,BLOB_SIZE,CREATED FROM WFS WHERE " 
                + " PARAMETERS_ID = ? LIMIT 1 ";
             
            prep = getConnection().prepareStatement(query);
            prep.setInt(1, parameters);
        } else {
            query = "SELECT WFS_ID,BLOB_SIZE,CREATED FROM WFS WHERE " 
                + " QUERY_BLOB_MD5 LIKE ? AND QUERY_BLOB_SIZE = ? LIMIT 1";
            
            prep = getConnection().prepareStatement(query);
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
        }
    }
    
    public void putTile(Integer layer_id, long[] xyz, Integer format_id,
            Integer parameters, TileObject stObj) 
    throws SQLException, StorageException {

        String query = "INSERT INTO TILES (" 
                + "  LAYER_ID,X,Y,Z,SRS_ID,FORMAT_ID,PARAMETERS_ID,BLOB_SIZE" 
                + ") VALUES(?,?,?,?,?,?,?,?)";
        
        PreparedStatement prep = getConnection().prepareStatement(
                query, Statement.RETURN_GENERATED_KEYS);
        prep.setInt(1, layer_id);
        prep.setLong(2, xyz[0]);
        prep.setLong(3, xyz[1]);
        prep.setLong(4, xyz[2]);
        prep.setInt(5, stObj.getSrs());
        prep.setInt(6, format_id);
        if(parameters == null) {
            prep.setNull(7, java.sql.Types.INTEGER);
        } else {
            prep.setInt(7, parameters);
        }
        prep.setInt(8, stObj.getBlobSize());
        
        Long insertId = wrappedInsert(prep);
        
        if(insertId == null) {
            log.error("Did not receive a id for " + query);
        } else {
            stObj.setId(insertId.longValue());
        }
    }
    
    public void putWFS(Integer parameters, WFSObject stObj)
            throws SQLException, StorageException {

        PreparedStatement prep = null;
        String query = null;

        if (parameters != null) {
            query = "INSERT INTO WFS (" 
                    + "  PARAMETERS_ID,BLOB_SIZE,CREATED" 
                    + ") VALUES(?,?,?)";

            prep = getConnection().prepareStatement(query,
                    Statement.RETURN_GENERATED_KEYS);
            prep.setInt(1, parameters);
            prep.setInt(2, stObj.getBlobSize());
            prep.setLong(3, stObj.getCreated());
            
        } else {
            query = "INSERT INTO WFS (" 
                    + " QUERY_BLOB_MD5, QUERY_BLOB_SIZE,BLOB_SIZE,CREATED"
                    + ") VALUES(?,?,?,?)";

            prep = getConnection().prepareStatement(query,
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
}
