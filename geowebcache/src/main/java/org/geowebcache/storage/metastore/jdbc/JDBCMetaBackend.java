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

import java.sql.SQLException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.storage.MetaStore;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.WFSObject;

public class JDBCMetaBackend implements MetaStore {
    private static Log log = LogFactory.getLog(org.geowebcache.storage.metastore.jdbc.JDBCMetaBackend.class);
    
    /** Wrapper that sets everything up */
    private final JDBCMBWrapper wrpr;
    
    /** Cache for translating layers and parameter strings to ids */
    private final JDBCMBIdCache idCache;
    
    public JDBCMetaBackend(String driverClass, String jdbcString, 
            String username, String password) throws StorageException {
        try {
            wrpr = new JDBCMBWrapper(driverClass, jdbcString, username, password);
        } catch(SQLException se) {
            throw new StorageException(se.getMessage());
        }
        
        idCache = new JDBCMBIdCache(wrpr);
    }
    
    public JDBCMetaBackend(DefaultStorageFinder defStoreFind) throws StorageException {
        try {
            wrpr = new JDBCMBWrapper(defStoreFind);
        } catch(SQLException se) {
            throw new StorageException(se.getMessage());
        }
        
        idCache = new JDBCMBIdCache(wrpr);
    }

    public boolean get(TileObject stObj) throws StorageException {
        stObj.setLayerId(idCache.getLayerId(stObj.getLayerName()));
        stObj.setFormatId(idCache.getFormatId(stObj.getBlobFormat()));
        if(stObj.getParameters() != null && stObj.getParameters().length() != 0) {
            stObj.setParamtersId(idCache.getParametersId(stObj.getParameters()));
        }
        
        try {
            return wrpr.getTile(stObj);
        } catch (SQLException se) {
            log.error("Failed to get tile: " + se.getMessage());
        }
        
        return false;
    }
    
    public boolean get(WFSObject stObj) throws StorageException {
        Long parameters_id = null;
        
        if(stObj.getParameters() != null && stObj.getParameters().length() != 0) {
            parameters_id = idCache.getParametersId(stObj.getParameters());
        }
        
        try {
            return wrpr.getWFS(parameters_id, stObj);
        } catch (SQLException se) {
            log.error("Failed to get WFS object: " + se.getMessage());
        }
        
        return false;
    }


    public void put(TileObject stObj) throws StorageException {
        stObj.setLayerId(idCache.getLayerId(stObj.getLayerName()));
        stObj.setFormatId(idCache.getFormatId(stObj.getBlobFormat()));
        if(stObj.getParameters() != null && stObj.getParameters().length() != 0) {
            stObj.setParamtersId(idCache.getParametersId(stObj.getParameters()));
        }
        
        try {
            wrpr.putTile(stObj);
        } catch (SQLException se) {
            log.error("Failed to put tile: " + se.getMessage());
        }
    }
    

    public void put(WFSObject stObj) throws StorageException {
        Long parameters_id = null;

        if (stObj.getParameters() != null && stObj.getParameters().length() != 0) {
            parameters_id = idCache.getParametersId(stObj.getParameters());
        }

        try {
            wrpr.putWFS(parameters_id, stObj);
        } catch (SQLException se) {
            log.error("Failed to put WFS object: " + se.getMessage());
        }

    }
    
    public void delete(WFSObject stObj) {
        // TODO Auto-generated method stub
        
    }

    public void delete(TileObject stObj) {
        // TODO Auto-generated method stub
        
    }

    public void clear() throws StorageException {
       if(wrpr.driverClass.equals("org.h2.Driver")) {
           //TODO 
       //    wrpr.getConnection().getMetaData().get
       //    DeleteDbFiles.execute(findTempDir(), TESTDB_NAME, true);
       //} else {
           throw new StorageException(
                   "clear() has not been implemented for "+wrpr.driverClass);
       }
        
    }

    public void destroy() {
        if(this.wrpr != null) {
            wrpr.destroy();
        }
        
    }

}
