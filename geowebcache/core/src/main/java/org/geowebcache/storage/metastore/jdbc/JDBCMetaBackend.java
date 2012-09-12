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
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.StorageObject.Status;
import org.geowebcache.storage.TileObject;

/**
 * JDBC implementation of a {@link MetaStore}
 */
public class JDBCMetaBackend  {
    private static Log log = LogFactory
            .getLog(org.geowebcache.storage.metastore.jdbc.JDBCMetaBackend.class);

    /** Wrapper that sets everything up */
    private JDBCMBWrapper wrpr;

    /** Cache for translating layers and parameter strings to ids */
    private final JDBCMBIdCache idCache;

    /** Delay before trying a lock again, in ms **/
    private long lockRetryDelay = 50;

    private boolean enabled = true;


    /**
     * Use the constructor with the DefaultStorageFinder instead.
     * Otherwise disabling the MetaStore will fail. 
     */
    @Deprecated
    public JDBCMetaBackend(String driverClass, String jdbcString, String username, String password)
    	throws ConfigurationException {
    		this(null, driverClass, jdbcString, username, password, false, -1);
    }
    
    /**
     * Use the constructor with the DefaultStorageFinder instead.
     * Otherwise disabling the MetaStore will fail. 
     */
    @Deprecated
    public JDBCMetaBackend(String driverClass, String jdbcString, String username, String password,
            boolean useConnectionPooling, int maxConnections) throws ConfigurationException {
        this(null, driverClass, jdbcString, username, password, useConnectionPooling, maxConnections);
    }

    public JDBCMetaBackend(DefaultStorageFinder defStoreFind, String driverClass, String jdbcString, String username, String password)
            throws ConfigurationException {
        this(defStoreFind, driverClass, jdbcString, username, password, false, -1);
    }
    
    /**
     * 
     * @param driverClass
     * @param jdbcString
     * @param username
     * @param password
     * @param useConnectionPooling
     *            Maximun number of connections in the pool iif useConnectionPooling
     * @param maxConnections
     *            Whether to use JDBC connection pooling
     * @throws StorageException
     */
    public JDBCMetaBackend(DefaultStorageFinder defStoreFind, String driverClass, String jdbcString, String username, String password,
            boolean useConnectionPooling, int maxConnections) throws ConfigurationException {
        if (useConnectionPooling && maxConnections <= 0) {
            throw new IllegalArgumentException(
                    "If connection pooling is enabled maxConnections shall be a positive integer: "
                            + maxConnections);
        }
       if(defStoreFind != null && isMetaStoreDisabled(defStoreFind)) {
    	    //No MetaStore:
            enabled = false;
            wrpr = null;
            idCache = null;
        } else {
    		try {
                wrpr = new JDBCMBWrapper(driverClass, jdbcString, username, password,
                        useConnectionPooling, maxConnections);
            } catch (SQLException se) {
                enabled = false;
                throw new ConfigurationException(se.getMessage());
            }

            if (enabled) {
                idCache = new JDBCMBIdCache(wrpr);
            } else {
                idCache = null;
            }
        }
    }
    
    private boolean isMetaStoreDisabled(DefaultStorageFinder defStoreFind) {
    	String metaStoreDisabled = defStoreFind.findEnvVar(DefaultStorageFinder.GWC_METASTORE_DISABLED);
        return (metaStoreDisabled != null && Boolean.parseBoolean(metaStoreDisabled));
    }

    public JDBCMetaBackend(DefaultStorageFinder defStoreFind) throws ConfigurationException {
        this(defStoreFind, false, -1);
    }

    public JDBCMetaBackend(DefaultStorageFinder defStoreFind, boolean useConnectionPooling,
            int maxConnections) throws ConfigurationException {
        if (useConnectionPooling && maxConnections <= 0) {
            throw new IllegalArgumentException(
                    "If connection pooling is enabled maxConnections shall be a positive integer: "
                            + maxConnections);
        }
        // Check whether we want a meta store at all, or whether GS just gave us a dummy
        String metaStoreDisabled = defStoreFind
                .findEnvVar(DefaultStorageFinder.GWC_METASTORE_DISABLED);
        if (metaStoreDisabled != null && Boolean.parseBoolean(metaStoreDisabled)) {
            enabled = false;
            wrpr = null;
            idCache = null;
        } else {
            try {
                wrpr = new JDBCMBWrapper(defStoreFind, useConnectionPooling, maxConnections);
            } catch (SQLException se) {
                log.error("Failed to start JDBC metastore: " + se.getMessage());
                log.warn("Disabling JDBC metastore, not all functionality will be available!");
                enabled = false;
                wrpr = null;
            }

            if (enabled) {
                idCache = new JDBCMBIdCache(wrpr);
            } else {
                idCache = null;
            }
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean get(TileObject stObj) throws StorageException {
        stObj.setLayerId(idCache.getLayerId(stObj.getLayerName()));
        stObj.setFormatId(idCache.getFormatId(stObj.getBlobFormat()));
        stObj.setGridSetIdId(idCache.getGridSetsId(stObj.getGridSetId()));
        stObj.setParametersId(String.valueOf(idCache.getParametersId(stObj.getParameters())));

        try {

            boolean response = wrpr.getTile(stObj);
            while (stObj.getStatus().equals(Status.LOCK)) {
                try {
                    Thread.sleep(lockRetryDelay);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                response = wrpr.getTile(stObj);
            }

            return response;

        } catch (SQLException se) {
            log.error("Failed to get tile: " + se.getMessage());
        }

        return false;
    }

    public void clear() throws StorageException {
        if (wrpr.driverClass.equals("org.h2.Driver")) {
            // TODO
            // wrpr.getConnection().getMetaData().get
            // DeleteDbFiles.execute(findTempDir(), TESTDB_NAME, true);
            // } else {
            throw new StorageException("clear() has not been implemented for " + wrpr.driverClass);
        }

    }

    public void destroy() {
        if (this.wrpr != null) {
            wrpr.destroy();
        }

    }

    public void setLockTimeout(long lockTimeout) {
        wrpr.lockTimeout = lockTimeout;
    }

    public void setLockRetryDelay(long lockRetryDelay) {
        this.lockRetryDelay = lockRetryDelay;
    }

}
