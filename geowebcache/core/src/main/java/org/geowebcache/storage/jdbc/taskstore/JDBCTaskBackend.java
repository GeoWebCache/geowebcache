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
package org.geowebcache.storage.jdbc.taskstore;

import java.sql.SQLException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.storage.MetaStore;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TaskObject;
import org.geowebcache.storage.TaskStore;

/**
 * JDBC implementation of a {@link MetaStore}
 */
public class JDBCTaskBackend implements TaskStore {
    private static Log log = LogFactory
            .getLog(org.geowebcache.storage.jdbc.taskstore.JDBCTaskBackend.class);

    /** Wrapper that sets everything up */
    private JDBCTaskWrapper wrpr;

    private boolean enabled = true;

    public JDBCTaskBackend(String driverClass, String jdbcString, String username, String password)
            throws StorageException {
        this(driverClass, jdbcString, username, password, false, -1);
    }

    /**
     * 
     * @param driverClass
     * @param jdbcString
     * @param username
     * @param password
     * @param useConnectionPooling
     *            Maximum number of connections in the pool iif useConnectionPooling
     * @param maxConnections
     *            Whether to use JDBC connection pooling
     * @throws StorageException
     */
    public JDBCTaskBackend(String driverClass, String jdbcString, String username, String password,
            boolean useConnectionPooling, int maxConnections) throws StorageException {
        if (useConnectionPooling && maxConnections <= 0) {
            throw new IllegalArgumentException(
                    "If connection pooling is enabled maxConnections shall be a positive integer: "
                            + maxConnections);
        }
        try {
            wrpr = new JDBCTaskWrapper(driverClass, jdbcString, username, password,
                    useConnectionPooling, maxConnections);
        } catch (SQLException se) {
            enabled = false;
            throw new StorageException(se.getMessage());
        }
    }

    public JDBCTaskBackend(DefaultStorageFinder defStoreFind) throws StorageException {
        this(defStoreFind, false, -1);
    }

    public JDBCTaskBackend(DefaultStorageFinder defStoreFind, boolean useConnectionPooling,
            int maxConnections) throws StorageException {
        if (useConnectionPooling && maxConnections <= 0) {
            throw new IllegalArgumentException(
                    "If connection pooling is enabled maxConnections shall be a positive integer: "
                            + maxConnections);
        }

        try {
            wrpr = new JDBCTaskWrapper(defStoreFind, useConnectionPooling, maxConnections);
        } catch (SQLException se) {
            log.error("Failed to start JDBC taskstore: " + se.getMessage());
            log.warn("Disabling JDBC taskstore, not all functionality will be available!");
            enabled = false;
            wrpr = null;
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean delete(long taskId) throws StorageException {
        try {
            wrpr.deleteTask(taskId);
            return true;
        } catch (SQLException se) {
            log.error("Failed to delete task '" + taskId + "'", se);
        }

        return false;
    }

    public boolean get(TaskObject stObj) throws StorageException {
        try {
            boolean response = wrpr.getTask(stObj);
            return response;
        } catch (SQLException se) {
            log.error("Failed to get task: " + se.getMessage());
        }

        return false;
    }

    public void put(TaskObject stObj) throws StorageException {
        try {
            wrpr.putTask(stObj);
        } catch (SQLException se) {
            log.error("Failed to put tile: " + se.getMessage());
        }
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
}
