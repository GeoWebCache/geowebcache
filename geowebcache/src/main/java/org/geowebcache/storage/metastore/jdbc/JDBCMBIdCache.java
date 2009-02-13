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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.storage.StorageException;

class JDBCMBIdCache {
    private static Log log = LogFactory.getLog(org.geowebcache.storage.metastore.jdbc.JDBCMBIdCache.class);
    
    public static int MAX_FORMATS = 50;
    public static int MAX_LAYERS = 100;
    public static int MAX_PARAMETERS = 100;
    
    private final Map<String,Integer> formatsCache;
    private final Map<String,Integer> layersCache;
    private final Map<String,Integer> parametersCache;
    
    private final JDBCMBWrapper wrpr;
    
    protected JDBCMBIdCache(JDBCMBWrapper wrpr) {
        formatsCache = new HashMap<String,Integer>();
        layersCache = new HashMap<String,Integer>();
        parametersCache = new HashMap<String,Integer>();
        
        this.wrpr = wrpr;
    }
    
    private Integer getOrInsert(String key, Map<String, Integer> map, 
            int maxSize, String table) 
    throws StorageException {
        if(key.length() > 254) {
            throw new StorageException(
                    "Value is too big for table " + table + ":" + key );
        }
        
        Integer res = null;
        try {
            res = doSelect(table, key);
        
            if(res == null)
                res = doInsert(table,key);
        } catch(SQLException se) {
            log.error("Error on Select or Insert: " + se.getMessage());
        }
        
        /** Keep the result for later */
        if(res != null) {
            if(map.size() > maxSize) 
                map.clear();
        
            map.put(key, res);
        }
        
        return res;
    }
    
    /** Ask the database for next auto increment */
    private Integer doInsert(String table, String key) {
        Integer res = null;
        
        try {
            String query = "INSERT INTO " + table + " (value) VALUES (?)";
            
            PreparedStatement prep = wrpr.getConnection().prepareStatement(
                    query, 
                    Statement.RETURN_GENERATED_KEYS );
            
            prep.setString(1, key);
            
            prep.executeUpdate();
            ResultSet rs = null;
            
            rs = prep.getGeneratedKeys();
            rs.first();
            res = Integer.valueOf(rs.getInt(1));
            rs.close();
            prep.close();
        } catch (SQLException se) {
            log.error(se.getMessage());
        }
        
        return res;
    }
    
    /** See whether the database knows anything */
    private Integer doSelect(String table, String key) 
    throws SQLException {
        PreparedStatement prep = null;
        ResultSet rs = null;
        
        try {
            String query = "SELECT ID FROM " + table + " WHERE VALUE LIKE ? LIMIT 1";
            
            prep = wrpr.getConnection().prepareStatement(query);
            prep.setString(1, key);
            
            rs = prep.executeQuery();
            
            if(rs.first()) {
                return Integer.valueOf(Integer.valueOf(rs.getInt(1)));
            }
        } catch (SQLException se) {
            log.error(se.getMessage());
        } finally {
            if(prep != null)
                prep.close();
            if(rs != null)
                rs.close();
        }
        return null;
    }

    
    protected Integer getFormatId(String format) throws StorageException {
        synchronized (this.formatsCache) {
            Integer ret = formatsCache.get(format);
            if (ret == null)
                ret = getOrInsert(format, formatsCache, MAX_FORMATS, "FORMATS");

            return ret;
        }
    }
    
    protected Integer getLayerId(String layer) throws StorageException {
        synchronized (this.layersCache) {
            Integer ret = layersCache.get(layer);
            if (ret == null)
                ret = getOrInsert(layer, layersCache, MAX_LAYERS, "LAYERS");

            return ret;
        }
    }
    
    protected Integer getParametersId(String parameters) throws StorageException {
        synchronized (this.parametersCache) {
            Integer ret = parametersCache.get(parameters);
            if (ret == null)
                ret = getOrInsert(parameters, parametersCache, MAX_PARAMETERS,"PARAMETERS");

            return ret;
        }
    }
}
