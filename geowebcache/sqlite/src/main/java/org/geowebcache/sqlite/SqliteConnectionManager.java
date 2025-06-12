/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Nuno Oliveira, GeoSolutions S.A.S., Copyright 2016
 */
package org.geowebcache.sqlite;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
import org.geotools.util.logging.Logging;

/**
 * Manages the connections to sqlite databases files taking care of the concurrent access. The concurrent access are
 * managed by JVM if two JVMs access the same database file the result is unpredictable.
 */
public final class SqliteConnectionManager {

    private static Logger LOGGER = Logging.getLogger(SqliteConnectionManager.class.getName());

    private final ConcurrentHashMap<File, PooledConnection> pool = new ConcurrentHashMap<>();

    private volatile boolean stopPoolReaper = false;

    public SqliteConnectionManager(SqliteInfo configuration) {
        this(configuration.getPoolSize(), configuration.getPoolReaperIntervalMs());
    }

    SqliteConnectionManager(long poolSize, long poolReaperIntervalMs) {
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.info("Initiating connection poll: [poolSize='%d', poolReaperIntervalMs='%d']."
                    .formatted(poolSize, poolReaperIntervalMs));
        }
        // let's load the sqlite driver
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (Exception exception) {
            throw Utils.exception(exception, "Error initiating sqlite driver.");
        }
        // computing some values used by the pool reaper
        double poolSizeThreshold = poolSize * 0.9;
        double connectionsToRemove = poolSize * 0.1;
        // starting the connection pool reaper
        new Thread(() -> {
                    while (!stopPoolReaper) {
                        if (LOGGER.isLoggable(Level.FINE)) {
                            LOGGER.fine("Current pool size is '%d' and threshold is '%f'."
                                    .formatted(pool.size(), poolSizeThreshold));
                        }
                        if (pool.size() > poolSizeThreshold) {
                            // we exceed the pool size threshold, time to reap the less used
                            // connections
                            if (LOGGER.isLoggable(Level.INFO)) {
                                LOGGER.info("Reaping connections, current pool size %d.".formatted(pool.size()));
                            }
                            List<PooledConnection> pooledConnections = new ArrayList<>(pool.values());
                            Collections.sort(pooledConnections);
                            for (int i = 0; i < connectionsToRemove && i < pooledConnections.size(); i++) {
                                pooledConnections.get(i).reapConnection();
                            }
                        }
                        try {
                            Thread.sleep(poolReaperIntervalMs);
                        } catch (Exception exception) {
                            Thread.currentThread().interrupt();
                            if (LOGGER.isLoggable(Level.WARNING)) {
                                LOGGER.warning("Pool reaper interrupted.");
                            }
                        }
                    }
                    if (LOGGER.isLoggable(Level.WARNING)) {
                        LOGGER.warning("Pool reaper stop.");
                    }
                })
                .start();
    }

    /** Helper interface to submit work. */
    interface Work {
        void doWork(Connection connection);
    }

    /** Helper interface to submit work that needs to return something. */
    interface WorkWithResult<T> {
        T doWork(Connection connection);
    }

    /** Helper interface to submit work that needs to return something. */
    interface ResultExtractor<T> {
        T extract(ResultSet resultSet) throws SQLException;
    }

    /** Submit an SQL statement to be executed. */
    void executeSql(File file, String sql, Object... parameters) {
        doWork(file, false, connection -> {
            executeSql(connection, sql, parameters);
        });
    }

    /** Submit an SQL statement to be executed with the provided connection. */
    void executeSql(Connection connection, String sql, Object... parameters) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Executing SQL '%s'.".formatted(sql));
        }
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            statement.execute();
        } catch (Exception exception) {
            throw Utils.exception(exception, "Error executing SQL '%s'.", sql);
        }
    }

    /** Submit a query to be executed. */
    <T> T executeQuery(File file, ResultExtractor<T> extractor, String query, Object... parameters) {
        return doWork(file, true, connection -> {
            return executeQuery(connection, extractor, query, parameters);
        });
    }

    /** Submit a query to be executed with the provided connection. */
    <T> T executeQuery(Connection connection, ResultExtractor<T> extractor, String query, Object... parameters) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Executing query '%s'.".formatted(query));
        }
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            return extractor.extract(statement.executeQuery());
        } catch (Exception exception) {
            throw Utils.exception(exception, "Error executing query '%s'.", query);
        }
    }

    /** Submit some work to be executed. */
    void doWork(File file, boolean readOnly, Work work) {
        doWork(file, readOnly, (WorkWithResult<Void>) connection -> {
            work.doWork(connection);
            return null;
        });
    }

    /** Submit some work to be executed that need to return something. */
    <T> T doWork(File file, boolean readOnly, WorkWithResult<T> work) {
        if (readOnly) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Starting work on file '%s' in readonly mode.".formatted(file));
            }
        } else {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Starting work on file '%s' in write mode.".formatted(file));
            }
        }
        // let's find or instantiate on the fly a pool connection for the current file
        PooledConnection pooledConnection = getPooledConnection(file);
        // acquiring the proper lock on the pooled connection (read or write lock)
        pooledConnection = readOnly
                ? pooledConnection.getReadLockOnValidConnection()
                : pooledConnection.getWriteLockOnValidConnection();
        // the PooledConnection seems to  manage the actual connection
        @SuppressWarnings("PMD.CloseResource")
        ExtendedConnection connection = pooledConnection.getExtendedConnection();
        try {
            // do the work
            T result = work.doWork(connection);
            if (!connection.closeInvoked()) {
                // the work didn't close the connection, this is fine unless the connection was
                // retained for future usage
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Close was not invoked on extended connection.");
                }
            }
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Work on file '%s' is done.".formatted(file));
            }
            return result;
        } finally {
            // releasing the acquired lock
            if (readOnly) {
                pooledConnection.releaseReadLock();
            } else {
                pooledConnection.releaseWriteLock();
            }
        }
    }

    void replace(File currentFile, File newFile) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Replacing file '%s' with file '%s'.".formatted(currentFile, newFile));
        }
        PooledConnection currentPooledConnection =
                getPooledConnection(currentFile).getWriteLockOnValidConnection();
        try {
            currentPooledConnection.reapConnection();
            if (currentFile.exists()) {
                Files.delete(currentFile.toPath());
            }
            FileUtils.moveFile(newFile, currentFile);
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("File '%s' replaced with file '%s'.".formatted(currentFile, newFile));
            }
        } catch (Exception exception) {
            throw Utils.exception(exception, "Error replacing file '%s' with file '%s'.", currentFile, newFile);
        } finally {
            currentPooledConnection.releaseWriteLock();
        }
    }

    void delete(File file) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Deleting file '%s'.".formatted(file));
        }
        if (!file.exists()) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("File '%s' doesn't exists.".formatted(file));
            }
            return;
        }
        PooledConnection pooledConnection = getPooledConnection(file).getWriteLockOnValidConnection();
        try {
            pooledConnection.closeConnection();
            FileUtils.deleteQuietly(file);
            pool.remove(file);
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("File '%s' deleted.".formatted(file));
            }
        } catch (Exception exception) {
            throw Utils.exception(exception, "Error deleting file '%s'.", file);
        } finally {
            pooledConnection.releaseWriteLock();
        }
    }

    void rename(File currentFile, File newFile) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Renaming file '%s' to '%s'.".formatted(currentFile, newFile));
        }
        PooledConnection pooledConnection = getPooledConnection(currentFile).getWriteLockOnValidConnection();
        try {
            pooledConnection.closeConnection();
            pool.remove(currentFile);
            FileUtils.moveFile(currentFile, newFile);
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("File '%s' renamed to '%s'.".formatted(currentFile, newFile));
            }
        } catch (Exception exception) {
            throw Utils.exception(exception, "Renaming file '%s' to '%s'.", currentFile, newFile);
        } finally {
            pooledConnection.releaseWriteLock();
        }
    }

    public Map<File, PooledConnection> getPool() {
        return pool;
    }

    void reapAllConnections() {
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.info("Reaping all connections.");
        }
        pool.values().forEach(PooledConnection::reapConnection);
    }

    void stopPoolReaper() {
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.info("Stopping the pool reaper.");
        }
        stopPoolReaper = true;
    }

    private PooledConnection getPooledConnection(File file) {
        try {
            PooledConnection pooledConnection = pool.get(file);
            if (pooledConnection != null) {
                // a connection already exists
                return pooledConnection;
            }
            // creating a new pooled connection for the database file
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Creating pooled connection to file '%s'.".formatted(file));
            }
            pooledConnection = new PooledConnection(file);
            pooledConnection.getWriteLock();
            try {
                PooledConnection existing = pool.putIfAbsent(file, pooledConnection);
                if (existing != null) {
                    // someone create a pooled connection for this file in the meantime
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.fine("Connection to file '%s' already exists, closing this one.".formatted(file));
                    }
                    pooledConnection.closeConnection();
                    return existing;
                }
                // effectively open a connection to the database file
                pooledConnection.init();
                return pooledConnection;
            } finally {
                pooledConnection.releaseWriteLock();
            }
        } catch (Exception exception) {
            throw Utils.exception(exception, "Error opening connection to file '%s'.", file);
        }
    }

    /** Helper class that contains all the info associated to an open connection. */
    private final class PooledConnection implements Comparable<PooledConnection> {

        private final File file;
        private Connection connection;

        private final ReentrantReadWriteLock lock;

        private long lastAccess;
        private volatile boolean closed;

        PooledConnection(File file) {
            this.file = file;
            lock = new ReentrantReadWriteLock();
            closed = true;
        }

        void init() {
            connection = openConnection(file);
            lastAccess = System.currentTimeMillis();
            closed = false;
        }

        @Override
        public int compareTo(PooledConnection other) {
            if (this.lastAccess < other.lastAccess) {
                return -1;
            }
            return 1;
        }

        ExtendedConnection getExtendedConnection() {
            lastAccess = System.currentTimeMillis();
            return new ExtendedConnection(connection);
        }

        void reapConnection() {
            getWriteLock();
            closeConnection();
            pool.remove(file);
            releaseWriteLock();
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("Connection to file '%s' reaped.".formatted(file));
            }
        }

        void closeConnection() {
            if (!closed) {
                // this connection is open let's close it
                try {
                    connection.close();
                    closed = true;
                } catch (Exception exception) {
                    throw Utils.exception("Error closing connection to file '%s'.", file);
                }
                if (LOGGER.isLoggable(Level.INFO)) {
                    LOGGER.info("Connection to file '%s' closed.".formatted(file));
                }
            }
        }

        void getReadLock() {
            String logId = "";
            if (LOGGER.isLoggable(Level.FINE)) {
                logId = UUID.randomUUID().toString();
                LOGGER.fine("[%s] Waiting for read lock on file '%s'.".formatted(logId, file));
            }
            lock.readLock().lock();
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("[%s] Read lock on file '%s' obtained.".formatted(logId, file));
            }
        }

        PooledConnection getReadLockOnValidConnection() {
            getReadLock();
            if (!closed) {
                // this connection is ok
                return this;
            }
            releaseReadLock();
            // this connection was closed in the meantime we need to create a new one (trying 10
            // times)
            for (int i = 0; i < 10; i++) {
                PooledConnection pooledConnection = SqliteConnectionManager.this.getPooledConnection(file);
                // obtain the read lock
                pooledConnection.getReadLock();
                if (!pooledConnection.closed) {
                    return pooledConnection;
                }
            }
            throw Utils.exception("Could not obtain a valid connection to file '%s'.", file);
        }

        void releaseReadLock() {
            lock.readLock().unlock();
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Read lock on file '%s' released.".formatted(file));
            }
        }

        void getWriteLock() {
            String logId = "";
            if (LOGGER.isLoggable(Level.FINE)) {
                logId = UUID.randomUUID().toString();
                LOGGER.fine("[%s] Waiting for write lock on file '%s'.".formatted(logId, file));
            }
            lock.writeLock().lock();
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("[%s] Write lock on file '%s' obtained.".formatted(logId, file));
            }
        }

        PooledConnection getWriteLockOnValidConnection() {
            getWriteLock();
            if (!closed) {
                // this connection is ok
                return this;
            }
            releaseWriteLock();
            // this connection was closed in the meantime we need to create a new one (trying 10
            // times)
            for (int i = 0; i < 10; i++) {
                PooledConnection pooledConnection = SqliteConnectionManager.this.getPooledConnection(file);
                // obtain the write lock
                pooledConnection.getWriteLock();
                if (!pooledConnection.closed) {
                    return pooledConnection;
                }
            }
            throw Utils.exception("Could not obtain a valid connection to file '%s'.", file);
        }

        void releaseWriteLock() {
            lock.writeLock().unlock();
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Write lock on file '%s' released.".formatted(file));
            }
        }

        private Connection openConnection(File file) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("Opening connection to file '%s'.".formatted(file));
            }
            Utils.createFileParents(file);
            try {
                return DriverManager.getConnection("jdbc:sqlite:" + file.getPath());
            } catch (Exception exception) {
                throw Utils.exception(exception, "Error opening connection to file '%s'.", file);
            }
        }
    }
}
