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

import static org.geowebcache.storage.jdbc.JDBCUtils.close;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.SRS;
import org.geowebcache.seed.GWCTask.PRIORITY;
import org.geowebcache.seed.GWCTask.STATE;
import org.geowebcache.seed.GWCTask.TYPE;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.storage.JobLogObject;
import org.geowebcache.storage.JobObject;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.JobLogObject.LOG_LEVEL;
import org.h2.jdbcx.JdbcConnectionPool;

/**
 * Wrapper class for the JDBC object, used by JDBCJobBackend
 * 
 * Performs mundane jobs such as
 * <ul>
 * <li>initialize the database</li>
 * <li>create tables</li>
 * <li>create iterators</li>
 * </ul>
 * 
 */
class JDBCJobWrapper {
    private static Log log = LogFactory
            .getLog(org.geowebcache.storage.jdbc.jobstore.JDBCJobWrapper.class);

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

    private boolean useConnectionPooling;

    private int maxConnections;

    private JdbcConnectionPool connPool;
    
    protected JDBCJobWrapper(String driverClass, String jdbcString, String username,
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

    public JDBCJobWrapper(DefaultStorageFinder defStoreFind, boolean useConnectionPooling,
            int maxConnections) throws StorageException, SQLException {
        String envStrUsername;
        String envStrPassword;
        String envStrJdbcUrl;
        String envStrDriver;
        envStrUsername = defStoreFind.findEnvVar(DefaultStorageFinder.GWC_JOBTORE_USERNAME);
        envStrPassword = defStoreFind.findEnvVar(DefaultStorageFinder.GWC_JOBTORE_PASSWORD);
        envStrJdbcUrl = defStoreFind.findEnvVar(DefaultStorageFinder.GWC_JOBSTORE_JDBC_URL);
        envStrDriver = defStoreFind.findEnvVar(DefaultStorageFinder.GWC_JOBSTORE_DRIVER_CLASS);
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
            String path = defStoreFind.getDefaultPath() + File.separator + "job_jdbc_h2";
            File dir = new File(path);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new StorageException("Unable to create " + dir.getAbsolutePath()
                        + " for H2 database.");
            }
            this.jdbcString = "jdbc:h2:file:" + path + File.separator + "gwc_jobstore"
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

    private void checkTables() throws StorageException, SQLException {
        final Connection conn = getConnection();
        try {

            checkJobsTable(conn);
            checkJobLogsTable(conn);
            
            int fromVersion = getDbVersion(conn);
            log.info("JobStore database is version " + fromVersion);

            if (fromVersion != DB_VERSION) {
                if (fromVersion < DB_VERSION) {
                    runDbUpgrade(conn, fromVersion);
                } else {
                    log.error("Jobstore database is newer than the running version of GWC. Proceeding with undefined results.");
                }
            }
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
                        + JDBCJobWrapper.DB_VERSION + ")");

                return JDBCJobWrapper.DB_VERSION;
            }
        } finally {
            close(rs);
            close(st);
        }
    }

    private void checkJobsTable(Connection conn) throws SQLException {
        
        condCreate(conn, "JOBS", "job_id BIGINT AUTO_INCREMENT PRIMARY KEY, " + 
                "layer_name VARCHAR(254), " + 
                "state VARCHAR(20), " + 
                "time_spent BIGINT, " + 
                "time_remaining BIGINT, " + 
                "tiles_done BIGINT, " + 
                "tiles_total BIGINT, " + 
                "failed_tile_count BIGINT, " + 
                "bounds VARCHAR(254), " + 
                "gridset_id VARCHAR(254), " + 
                "srs INT, " + 
                "thread_count INT, " + 
                "zoom_start INT, " + 
                "zoom_stop INT, " + 
                "format VARCHAR(32), " + 
                "job_type VARCHAR(10), " + 
                "throughput FLOAT, " + 
                "max_throughput INT, " + 
                "priority VARCHAR(10), " + 
                "schedule VARCHAR(254), " + 
                "run_once BOOL, " + 
                "filter_update BOOL, " + 
                "parameters VARCHAR(1024), " + 
                "time_first_start TIMESTAMP, " + 
                "time_latest_start TIMESTAMP ", 
                "layer_name", 
                null);
    }

    private void checkJobLogsTable(Connection conn) throws SQLException {
        
        condCreate(conn, "JOB_LOGS", "job_log_id BIGINT AUTO_INCREMENT PRIMARY KEY, " + 
                "job_id BIGINT, " + 
                "log_level VARCHAR(6), " + 
                "log_time TIMESTAMP, " +  
                "log_summary VARCHAR(254), " +  
                "log_text CLOB ", 
                "log_time", 
                null);
        
        Statement st = null;
        try {
            st = conn.createStatement();
            st.execute("ALTER TABLE JOB_LOGS ADD FOREIGN KEY(job_id) REFERENCES JOBS(job_id)");
        } finally {
            close(st);
        }
           
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
        log.info("Upgrading H2 database from " + fromVersion + " to " + JDBCJobWrapper.DB_VERSION);

        boolean earlier = false;

        if(earlier) {
            // no changes yet, so no upgrades
        }
    }

    /**
     * Delete the job including all logs related to the job
     * @param jobId
     * @throws SQLException
     */
    public void deleteJob(long jobId) throws SQLException {
        final Connection conn = getConnection();
        try {
            deleteJob(conn, jobId);
        } finally {
            close(conn);
        }
    }
    public void deleteJob(JobObject stObj) throws SQLException {
        final Connection conn = getConnection();
        try {
            deleteJob(conn, stObj.getJobId());
        } finally {
            close(conn);
        }
    }

    protected void deleteJob(Connection conn, long jobId) throws SQLException {

        String query = "DELETE FROM JOB_LOGS WHERE JOB_ID = ?";
        PreparedStatement prep = conn.prepareStatement(query);
        try {
            prep.setLong(1, jobId);

            prep.execute();
        } finally {
            close(prep);
        }

        query = "DELETE FROM JOBS WHERE JOB_ID = ?";
        prep = conn.prepareStatement(query);
        try {
            prep.setLong(1, jobId);

            prep.execute();
        } finally {
            close(prep);
        }
    }

    protected boolean getJob(JobObject stObj) throws SQLException {
        String query = "SELECT * FROM JOBS WHERE JOB_ID = ? LIMIT 1 ";

        final Connection conn = getConnection();

        PreparedStatement prep = null;
        
        try {
            prep = conn.prepareStatement(query);
            prep.setLong(1, stObj.getJobId());

            ResultSet rs = prep.executeQuery();
            try {
                if (rs.next()) {
                    readJob(stObj, rs);
                    stObj.setErrorCount(getJobLogCount(stObj, LOG_LEVEL.ERROR));
                    stObj.setWarnCount(getJobLogCount(stObj, LOG_LEVEL.WARN));
                    return true;
                } else {
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

    public void putJob(JobObject stObj) throws SQLException, StorageException {

        String query = "MERGE INTO " + 
                "JOBS(job_id, layer_name, state, time_spent, time_remaining, tiles_done, " + 
                "tiles_total, failed_tile_count, bounds, gridset_id, srs, thread_count, " + 
                "zoom_start, zoom_stop, format, job_type, throughput, max_throughput, " +  
                "priority, schedule, run_once, filter_update, parameters, " + 
                "time_first_start, time_latest_start) " + 
                "KEY(job_id) " + 
                "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        final Connection conn = getConnection();

        try {
            Long insertId;
            PreparedStatement prep = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
            try {
                if(stObj.getJobId() == -1) {
                    prep.setNull(1, java.sql.Types.BIGINT);
                } else {
                    prep.setLong(1, stObj.getJobId());
                }

                prep.setString(2, stObj.getLayerName());
                prep.setString(3, stObj.getState().name());
                prep.setLong(4, stObj.getTimeSpent());
                prep.setLong(5, stObj.getTimeRemaining());
                prep.setLong(6, stObj.getTilesDone());
                prep.setLong(7, stObj.getTilesTotal());
                prep.setLong(8, stObj.getFailedTileCount());
                
                prep.setString(9, stObj.getBounds().toString());
                prep.setString(10, stObj.getGridSetId());
                prep.setInt(11, stObj.getSrs().getNumber());

                prep.setInt(12, stObj.getThreadCount());
                prep.setInt(13, stObj.getZoomStart());
                prep.setInt(14, stObj.getZoomStop());
                prep.setString(15, stObj.getFormat());
                prep.setString(16, stObj.getJobType().name());
                prep.setFloat(17, stObj.getThroughput());
                prep.setInt(18, stObj.getMaxThroughput());
                prep.setString(19, stObj.getPriority().name());
                prep.setString(20, stObj.getSchedule());
                prep.setBoolean(21, stObj.isRunOnce());
                prep.setBoolean(22, stObj.isFilterUpdate());
                prep.setString(23, stObj.getEncodedParameters());
                
                prep.setTimestamp(24, stObj.getTimeFirstStart());
                prep.setTimestamp(25, stObj.getTimeLatestStart());
                
                insertId = wrappedInsert(prep);
            } finally {
                close(prep);
            }
            if (insertId == null) {
                log.error("Did not receive an id for " + query);
            } else {
                if(stObj.getJobId() == -1) {
                    // only use the inserted id if we were doing an insert.
                    // what insertid will be if we weren't doing an insert is not defined.
                    stObj.setJobId(insertId.longValue());
                }
            }
            
            putRecentJobLogs(stObj, conn);

        } finally {
            conn.close();
        }
    }

    /**
     * Goes through recently added logs for this job and persists them
     * Clears recent logs from the list of recent logs. 
     * Uses a ConcurrentLinkedQueue and is threadsafe. 
     * @param stObj
     * @param conn
     * @throws SQLException 
     * @throws StorageException 
     */
    private void putRecentJobLogs(JobObject stObj, Connection conn) throws StorageException, SQLException {
        ConcurrentLinkedQueue<JobLogObject> logs = stObj.getNewLogs();
        
        while(!logs.isEmpty()) {
            JobLogObject joblog;
            synchronized(logs) {
                joblog = logs.poll();
            }
            // Make sure the joblog points to this job. Sometimes a job might have logs before first 
            // being saved so the logs won't be pointing to the right ID yet.
            joblog.setJobId(stObj.getJobId());
            putJobLog(joblog);
        }
    }

    public void putJobLog(JobLogObject stObj) throws SQLException, StorageException {

        // Not really a fan of tacking log_ onto the front of every field, but it ensures
        // common keywords like log, level, time, summary, text won't clash with database 
        // keywords, causing unnecessary pain.
        String query = "MERGE INTO " + 
                "JOB_LOGS(job_log_id, job_id, log_level, log_time, log_summary, log_text) " + 
                "KEY(job_log_id) " + 
                "VALUES(?,?,?,?,?,?)";

        final Connection conn = getConnection();

        try {
            Long insertId;
            PreparedStatement prep = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
            try {
                if(stObj.getJobLogId() == -1) {
                    prep.setNull(1, java.sql.Types.BIGINT);
                } else {
                    prep.setLong(1, stObj.getJobLogId());
                }

                prep.setLong(2, stObj.getJobId());
                prep.setString(3, stObj.getLogLevel().name());
                prep.setTimestamp(4, stObj.getLogTime());
                prep.setString(5, stObj.getLogSummary());
                
                Reader reader = (Reader) new BufferedReader(new StringReader(stObj.getLogText()));
                prep.setCharacterStream(6, reader, stObj.getLogText().length());
                
                insertId = wrappedInsert(prep);
            } finally {
                close(prep);
            }
            if (insertId == null) {
                log.error("Did not receive an id for " + query);
            } else {
                if(stObj.getJobLogId() == -1) {
                    // only use the inserted id if we were doing an insert.
                    // what insertid will be if we weren't doing an insert is not defined.
                    stObj.setJobLogId(insertId.longValue());
                }
            }

        } finally {
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
            close(rs);
        }
    }

    public long getJobCount() throws SQLException {
        
        long result = 0;

        String query = "SELECT COUNT(*) FROM JOBS";

        final Connection conn = getConnection();
        PreparedStatement prep = null; 
        try {
            prep = conn.prepareStatement(query);
            ResultSet rs = prep.executeQuery();
            try {
                if (rs.first()) {
                    result = rs.getLong(1);
                }
            } finally {
                close(rs);
            }
        } finally {
            close(prep);
            close(conn);
        }

        return result;
    }

    public long getJobLogCount(JobObject stObj) throws SQLException {
        return getJobLogCount(stObj, null);
    }
    /**
     * Gets a count of logs for a job
     * @param stObj The job to get a count of logs for
     * @param level optionally only count logs of a certain level (ERROR, WARN or INFO)
     * @return count
     * @throws SQLException
     */
    public long getJobLogCount(JobObject stObj, LOG_LEVEL level) throws SQLException {
        
        long result = 0;

        String query = "SELECT COUNT(*) FROM JOB_LOGS WHERE job_id = ?";
        
        if(level != null) {
            query += " AND log_level = ?";
        }

        final Connection conn = getConnection();
        PreparedStatement prep = null; 
        try {
            prep = conn.prepareStatement(query);
            prep.setLong(1, stObj.getJobId());
            if(level != null) {
                prep.setString(2, level.name());
            }

            ResultSet rs = prep.executeQuery();
            try {
                if (rs.first()) {
                    result = rs.getLong(1);
                }
            } finally {
                close(rs);
            }
        } finally {
            close(prep);
            close(conn);
        }

        return result;
    }

    /**
     * Gets all jobs in the database
     * NOTE: This isn't very futureproof - needs to be able to paginate
     * @return an iterable list of JobObjects
     * @throws SQLException
     */
    public Iterable<JobObject> getJobs() throws SQLException {
        ArrayList<JobObject> result = new ArrayList<JobObject>();
        
        String query = "SELECT * FROM JOBS";

        final Connection conn = getConnection();

        PreparedStatement prep = null;
        
        try {
            prep = conn.prepareStatement(query);

            ResultSet rs = prep.executeQuery();
            try {
                while(rs.next()) {
                    JobObject job = new JobObject();
                    readJob(job, rs);
                    job.setErrorCount(getJobLogCount(job, LOG_LEVEL.ERROR));
                    job.setWarnCount(getJobLogCount(job, LOG_LEVEL.WARN));
                    result.add(job);
                }
            } finally {
                close(rs);
            }
        } finally {
            close(prep);
            close(conn);
        }
        return result;
    }

    /**
     * Gets all jobs logged in the system.
     * @return a potentially very long list of job logs.
     * @throws SQLException
     * @throws IOException
     */
    public Iterable<JobLogObject> getAllLogs() throws SQLException, IOException {
        return getLogsInternal(-1);
    }

    /**
     * Get logs for a job
     * If the job provided doesn't have an ID set (i.e. it's the default value of -1) then
     * it can't have any jobs in the datastore so an empty list is returned.
     * @param job
     * @return Iterable of JobLogObjects for this job
     * @throws SQLException
     * @throws IOException
     */
    public Iterable<JobLogObject> getLogs(JobObject job) throws SQLException, IOException {
        return getLogs(job.getJobId());
    }
    
    public Iterable<JobLogObject> getLogs(long jobId) throws SQLException, IOException {
        if(jobId == -1) {
            return new ArrayList<JobLogObject>();
        } else {
            return getLogsInternal(jobId);
        }
    }

    /**
     * Gets logs for a job or all logs in the system if jobId is -1
     * @param jobId
     * @return
     * @throws SQLException
     * @throws IOException
     */
    protected Iterable<JobLogObject> getLogsInternal(long jobId) throws SQLException, IOException {
        ArrayList<JobLogObject> result = new ArrayList<JobLogObject>();

        String query = "SELECT * FROM JOB_LOGS";
        
        if(jobId != -1) {
            query += " WHERE job_id = ?";
        }
        
        query += " ORDER BY log_time DESC";        
        
        final Connection conn = getConnection();

        PreparedStatement prep = null;
        
        try {
            prep = conn.prepareStatement(query);

            if(jobId != -1) {
                prep.setLong(1, jobId);
            }

            ResultSet rs = prep.executeQuery();
            try {
                while(rs.next()) {
                    JobLogObject joblog = new JobLogObject();
                    readJobLog(joblog, rs);
                    result.add(joblog);
                }
            } finally {
                close(rs);
            }
        } finally {
            close(prep);
            close(conn);
        }
        return result;
    }

    /**
     * Gets all jobs considered pending and scheduled.
     * Pending is any job that is ready to be run (state = READY). Shceduled is any job with a schedule set.
     * @return
     * @throws SQLException
     */
    public Iterable<JobObject> getPendingScheduledJobs() throws SQLException {
        String query = "SELECT * FROM JOBS " + 
                        "WHERE (state = '" + STATE.READY.name() + "') " + 
                          "AND schedule IS NOT NULL";
        return getFilteredJobs(query);
    }

    /**
     * Updates all jobs with a status of running in the store to instead be interrupted.
     * This method is only valid if called before jobs are actually started by GeoWebCache. 
     * This must be done to capture the use case where a job couldn't be marked as 
     * interrupted because the system went down without a chance to update the store.
     * @throws SQLException
     */
    public void setRunningJobsToInterrupted() throws SQLException {
        String query = "UPDATE JOBS " + 
                               "SET state = '" + STATE.INTERRUPTED.name() + "' " + 
                             "WHERE state = '" + STATE.RUNNING.name() + "'";

        final Connection conn = getConnection();
        PreparedStatement prep = conn.prepareStatement(query);
        try {
            prep.execute();
        } finally {
            close(prep);
        }
    }
    

    /**
     * Gets all jobs that are considered to have been interrupted during execution (state = INTERRUPTED).
     * @return
     * @throws SQLException
     */
    public Iterable<JobObject> getInterruptedJobs() throws SQLException {
        String query = "SELECT * FROM JOBS " + 
                        "WHERE state = '" + STATE.INTERRUPTED.name() + "'";
        return getFilteredJobs(query);
    }
    
    private Iterable<JobObject> getFilteredJobs(String query) throws SQLException {
        ArrayList<JobObject> result = new ArrayList<JobObject>();
        
        final Connection conn = getConnection();

        PreparedStatement prep = null;
        
        try {
            prep = conn.prepareStatement(query);

            ResultSet rs = prep.executeQuery();
            try {
                while(rs.next()) {
                    JobObject job = new JobObject();
                    readJob(job, rs);
                    result.add(job);
                }
            } finally {
                close(rs);
            }
        } finally {
            close(prep);
            close(conn);
        }
        return result;
    }
    
    private void readJob(JobObject job, ResultSet rs) throws SQLException {
        job.setJobId(rs.getLong("job_id"));
        job.setLayerName(rs.getString("layer_name"));
        job.setState(STATE.valueOf(rs.getString("state")));
        job.setTimeSpent(rs.getLong("time_spent"));
        job.setTimeRemaining(rs.getLong("time_remaining"));
        job.setTilesDone(rs.getLong("tiles_done"));
        job.setTilesTotal(rs.getLong("tiles_total"));
        job.setFailedTileCount(rs.getLong("failed_tile_count"));
        
        job.setBounds(new BoundingBox(rs.getString("bounds")));
        job.setGridSetId(rs.getString("gridset_id"));
        job.setSrs(SRS.getSRS(rs.getInt("srs")));
   
        job.setThreadCount(rs.getInt("thread_count"));
        job.setZoomStart(rs.getInt("zoom_start"));
        job.setZoomStop(rs.getInt("zoom_stop"));
        job.setFormat(rs.getString("format"));
        job.setJobType(TYPE.valueOf(rs.getString("job_type")));
        job.setThroughput(rs.getFloat("throughput"));
        job.setMaxThroughput(rs.getInt("max_throughput"));
        job.setPriority(PRIORITY.valueOf(rs.getString("priority")));
        job.setSchedule(rs.getString("schedule"));
        job.setRunOnce(rs.getBoolean("run_once"));
        job.setFilterUpdate(rs.getBoolean("filter_update"));
        job.setEncodedParameters(rs.getString("parameters"));
        
        job.setTimeFirstStart(rs.getTimestamp("time_first_start"));
        job.setTimeLatestStart(rs.getTimestamp("time_latest_start"));
    }

    private void readJobLog(JobLogObject joblog, ResultSet rs) throws SQLException, IOException {
        joblog.setJobLogId(rs.getLong("job_log_id"));
        joblog.setJobId(rs.getLong("job_id"));
        joblog.setLogLevel(JobLogObject.LOG_LEVEL.valueOf(rs.getString("log_level")));
        joblog.setLogTime(rs.getTimestamp("log_time"));
        joblog.setLogSummary(rs.getString("log_summary"));
        joblog.setLogText(readClob(rs, "log_text"));
    }

    private String readClob(ResultSet rs, String field) throws SQLException, IOException {
        StringBuilder sb = new StringBuilder();
        Reader reader = rs.getCharacterStream(field);
        
        while (true) {
            char[] buff = new char[1024];
            int len = reader.read(buff);
            if (len < 0) {
                break;
            } else {
                sb.append(buff, 0, len);
            }
        }
        return sb.toString();
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
}
