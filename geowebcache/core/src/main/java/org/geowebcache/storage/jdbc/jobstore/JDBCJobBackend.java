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
package org.geowebcache.storage.jdbc.jobstore;

import java.sql.SQLException;
import java.sql.Timestamp;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.storage.JobLogObject;
import org.geowebcache.storage.JobObject;
import org.geowebcache.storage.JobStore;
import org.geowebcache.storage.MetaStore;
import org.geowebcache.storage.SettingsObject;
import org.geowebcache.storage.StorageException;

/**
 * JDBC implementation of a {@link MetaStore}
 */
public class JDBCJobBackend implements JobStore {
    private static Log log = LogFactory
            .getLog(org.geowebcache.storage.jdbc.jobstore.JDBCJobBackend.class);

    /** Wrapper that sets everything up */
    private JDBCJobWrapper wrpr;

    private SettingsObject settings;
    
    private boolean enabled = true;

    public JDBCJobBackend(String driverClass, String jdbcString, String username, String password)
            throws ConfigurationException, StorageException {
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
    public JDBCJobBackend(String driverClass, String jdbcString, String username, String password,
            boolean useConnectionPooling, int maxConnections) throws ConfigurationException, StorageException {
        if (useConnectionPooling && maxConnections <= 0) {
            throw new IllegalArgumentException(
                    "If connection pooling is enabled maxConnections shall be a positive integer: "
                            + maxConnections);
        }
        try {
            wrpr = new JDBCJobWrapper(driverClass, jdbcString, username, password,
                    useConnectionPooling, maxConnections);
            initSettings();
        } catch (SQLException se) {
            enabled = false;
            throw new ConfigurationException(se.getMessage());
        }
    }

    private void initSettings() throws StorageException {
        // init the settings, they are cached at this level
        settings = new SettingsObject();
        settings.setClearOldJobs(getClearOldJobsSetting());
    }

    public JDBCJobBackend(DefaultStorageFinder defStoreFind) throws ConfigurationException, StorageException {
        this(defStoreFind, false, -1);
    }

    public JDBCJobBackend(DefaultStorageFinder defStoreFind, boolean useConnectionPooling,
            int maxConnections) throws ConfigurationException, StorageException {
        if (useConnectionPooling && maxConnections <= 0) {
            throw new IllegalArgumentException(
                    "If connection pooling is enabled maxConnections shall be a positive integer: "
                            + maxConnections);
        }

        try {
            wrpr = new JDBCJobWrapper(defStoreFind, useConnectionPooling, maxConnections);
            initSettings();
        } catch (SQLException se) {
            log.error("Failed to start JDBC jobstore: " + se.getMessage());
            log.warn("Disabling JDBC jobstore, not all functionality will be available!");
            enabled = false;
            wrpr = null;
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean delete(long jobId) throws StorageException {
        try {
            wrpr.deleteJob(jobId);
            return true;
        } catch (SQLException se) {
            String logMsg = "Failed to delete job '" + jobId + "'";
            log.error(logMsg, se);
            throw new StorageException(logMsg + ", " + se.getMessage());
        }
    }

    public boolean get(JobObject stObj) throws StorageException {
        try {
            boolean response = wrpr.getJob(stObj);
            return response;
        } catch (SQLException se) {
            String logMsg = "Failed to get job '" + stObj.getJobId() + "'";
            log.error(logMsg, se);
            throw new StorageException(logMsg + ", " + se.getMessage());
        }
    }

    public void put(JobObject stObj) throws StorageException {
        try {
            wrpr.putJob(stObj);
        } catch (SQLException se) {
            String logMsg = "Failed to put job '" + stObj.getJobId() + "'";
            log.error(logMsg, se);
            throw new StorageException(logMsg + ", " + se.getMessage());
        }
    }

    public long getCount() throws StorageException {
        try {
            long response = wrpr.getJobCount();
            return response;
        } catch (SQLException se) {
            String logMsg = "Failed to get job count";
            log.error(logMsg, se);
            throw new StorageException(logMsg + ", " + se.getMessage());
        }
    }

    public Iterable<JobObject> getJobs() throws StorageException {
        try {
            Iterable<JobObject> response = wrpr.getJobs();
            return response;
        } catch (SQLException se) {
            String logMsg = "Failed to get jobs";
            log.error(logMsg, se);
            throw new StorageException(logMsg + ", " + se.getMessage());
        }
    }

    public void clear() throws StorageException {
        if (wrpr.driverClass.equals("org.h2.Driver")) {
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

    public Iterable<JobObject> getPendingScheduledJobs() throws StorageException {
        try {
            Iterable<JobObject> response = wrpr.getPendingScheduledJobs();
            return response;
        } catch (SQLException se) {
            String logMsg = "Failed to get pending scheduled jobs";
            log.error(logMsg, se);
            throw new StorageException(logMsg + ", " + se.getMessage());
        }
    }

    public Iterable<JobObject> getInterruptedJobs() throws StorageException {
        try {
            wrpr.setRunningJobsToInterrupted();
            Iterable<JobObject> response = wrpr.getInterruptedJobs();
            return response;
        } catch (SQLException se) {
            String logMsg = "Failed to get interrupted jobs";
            log.error(logMsg, se);
            throw new StorageException(logMsg + ", " + se.getMessage());
        }
    }

    public Iterable<JobLogObject> getLogs(long jobId) throws StorageException {
        try {
            Iterable<JobLogObject> response = wrpr.getLogs(jobId);
            return response;
        } catch (Exception e) {
            String logMsg = "Failed to get logs for job '" + jobId + "'";
            log.error(logMsg, e);
            throw new StorageException(logMsg + ", " + e.getMessage());
        }
    }

    public Iterable<JobLogObject> getAllLogs() throws StorageException {
        try {
            Iterable<JobLogObject> response = wrpr.getAllLogs();
            return response;
        } catch (Exception e) {
            String logMsg = "Failed to get all logs.";
            log.error(logMsg, e);
            throw new StorageException(logMsg + ", " + e.getMessage());
        }
    }

    public long getClearOldJobsSetting() throws StorageException {
        if(settings.getClearOldJobs() == -1) {
            try {
                long response = wrpr.getClearOldJobs();
                settings.setClearOldJobs(response);
                return response;
            } catch (Exception e) {
                String logMsg = "Failed to get 'clear old jobs' variable.";
                log.error(logMsg, e);
                throw new StorageException(logMsg + ", " + e.getMessage());
            }
        } else {
            return(settings.getClearOldJobs());
        }
    }

    public void setClearOldJobsSetting(long clearOldJobsVal) throws StorageException {
        try {
            wrpr.setClearOldJobs(clearOldJobsVal);
            settings.setClearOldJobs(clearOldJobsVal);
        } catch (Exception e) {
            String logMsg = "Failed to set 'clear old jobs' variable to " + clearOldJobsVal;
            log.error(logMsg, e);
            throw new StorageException(logMsg + ", " + e.getMessage());
        }
    }

    public long purgeOldJobs(Timestamp ts) throws StorageException {
        try {
            long result = wrpr.purgeOldJobs(ts);
            return result;
        } catch (Exception e) {
            String logMsg = "Failed to purge old jobs.";
            log.error(logMsg, e);
            throw new StorageException(logMsg + ", " + e.getMessage());
        }
    }
}
