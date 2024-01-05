/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Nuno Oliveira, GeoSolutions S.A.S., Copyright 2016
 */
package org.geowebcache.sqlite;

import static org.geowebcache.sqlite.Utils.Tuple;
import static org.geowebcache.sqlite.Utils.Tuple.tuple;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.storage.StorageException;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public final class SqliteConnectionManagerTest extends TestSupport {

    private static Logger LOGGER = Logging.getLogger(SqliteConnectionManagerTest.class.getName());

    private List<SqliteConnectionManager> connectionManagersToClean;

    @Override
    @Before
    public void beforeTest() throws Exception {
        super.beforeTest();
        connectionManagersToClean = new ArrayList<>();
    }

    @Override
    @After
    public void afterTest() throws Exception {
        for (SqliteConnectionManager connectionManager : connectionManagersToClean) {
            try {
                connectionManager.reapAllConnections();
                connectionManager.stopPoolReaper();
            } catch (Exception exception) {
                // nothing that we can do, so just ignoring this exception
            }
        }
        super.afterTest();
    }

    @Test
    public void testGetConnection() throws StorageException {
        SqliteConnectionManager connectionManager =
                new SqliteConnectionManager(Integer.MAX_VALUE, 1000);
        connectionManagersToClean.add(connectionManager);
        connectionManager.doWork(
                buildRootFile("tiles", "data_base.sqlite"),
                true,
                connection -> {
                    insertInTestTable(connection, "name", "europe");
                });
        connectionManager.reapAllConnections();
        assertThat(connectionManager.getPool().size(), is(0));
        connectionManager.doWork(
                buildRootFile("tiles", "data_base.sqlite"),
                true,
                connection -> {
                    String value = getFromTestTable(connection, "name");
                    assertThat(value, notNullValue());
                    assertThat(value, is("europe"));
                    closeConnectionQuietly(connection);
                });
    }

    @Test
    @Ignore
    public void testMultiThreadsWithSingleFile() throws Exception {
        genericMultiThreadsTest(10, 500, Integer.MAX_VALUE, buildRootFile("data_base_a.sqlite"));
    }

    @Test
    @Ignore
    public void testMultiThreadsWithMultipleFiles() throws Exception {
        genericMultiThreadsTest(
                10,
                500,
                10,
                buildRootFile("data_base_a.sqlite"),
                buildRootFile("data_base_b.sqlite"),
                buildRootFile("data_base_c.sqlite"),
                buildRootFile("data_base_d.sqlite"),
                buildRootFile("data_base_e.sqlite"));
    }

    @Test
    @Ignore
    public void testMultiThreadsWithMultipleFilesWithCacheLimit() throws Exception {
        genericMultiThreadsTest(
                10,
                500,
                1,
                buildRootFile("data_base_a.sqlite"),
                buildRootFile("data_base_b.sqlite"),
                buildRootFile("data_base_c.sqlite"),
                buildRootFile("data_base_d.sqlite"),
                buildRootFile("data_base_e.sqlite"));
    }

    @Test
    @Ignore
    public void testReplaceOperation() throws Exception {
        SqliteConnectionManager connectionManager =
                new SqliteConnectionManager(Integer.MAX_VALUE, 1000);
        connectionManagersToClean.add(connectionManager);
        File file1 = buildRootFile("tiles", "data_base_1.sqlite");
        Utils.createFileParents(file1);
        File file2 = buildRootFile("tiles", "data_base_2.sqlite");
        Utils.createFileParents(file2);
        connectionManager.doWork(
                file1,
                false,
                connection -> {
                    insertInTestTable(connection, "name", "europe");
                    closeConnectionQuietly(connection);
                });
        file2.createNewFile();
        connectionManager.replace(file1, file2);
        connectionManager.doWork(
                file1,
                false,
                connection -> {
                    createTestTable(connection);
                    String value = getFromTestTable(connection, "name");
                    assertThat(value, nullValue());
                    closeConnectionQuietly(connection);
                });
    }

    private void genericMultiThreadsTest(
            int threadsNumber, int workersNumber, long poolSize, File... files) throws Exception {
        SqliteConnectionManager connectionManager = new SqliteConnectionManager(poolSize, 10);
        connectionManagersToClean.add(connectionManager);
        @SuppressWarnings("PMD.CloseResource") // implements AutoCloseable in Java 21
        ExecutorService executor = Executors.newFixedThreadPool(threadsNumber);
        Random random = new Random();
        List<Future<Tuple<File, String>>> results = new ArrayList<>();
        for (int i = 0; i < workersNumber; i++) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(String.format("Submitted worker '%d'\\'%d'.", i, workersNumber));
            }
            executor.submit(
                    () -> {
                        File file = files[random.nextInt(files.length)];
                        String key = UUID.randomUUID().toString();
                        return connectionManager.doWork(
                                file,
                                false,
                                connection -> {
                                    insertInTestTable(connection, key, "value-" + key);
                                    closeConnectionQuietly(connection);
                                    return tuple(file, key);
                                });
                    });
        }
        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.SECONDS);
        connectionManager.reapAllConnections();
        assertThat(connectionManager.getPool().size(), is(0));
        for (Future<Tuple<File, String>> result : results) {
            File file = result.get().first;
            String key = result.get().second;
            connectionManager.doWork(
                    file,
                    true,
                    connection -> {
                        String value = getFromTestTable(connection, key);
                        assertThat(value, notNullValue());
                        assertThat(value, is("value-" + key));
                        closeConnectionQuietly(connection);
                    });
        }
    }

    private static void closeConnectionQuietly(Connection connection) {
        try {
            connection.close();
        } catch (Exception exception) {
            throw Utils.exception(exception, "Error closing connection.");
        }
    }

    private static void createTestTable(Connection connection) {
        execute(
                connection,
                "CREATE TABLE IF NOT EXISTS test "
                        + "(key text, value text, CONSTRAINT pk_metadata PRIMARY KEY(key));");
    }

    private static void insertInTestTable(Connection connection, String key, String value) {
        createTestTable(connection);
        execute(connection, "INSERT INTO test VALUES ('%s', '%s');", key, value);
    }

    private static String getFromTestTable(Connection connection, String key) {
        return new ExecuteQuery(
                connection, "SELECT value FROM test WHERE key = '%s' ORDER BY key;", key) {

            String result;

            @Override
            public void extract(ResultSet resultSet) throws Exception {
                if (resultSet.next()) {
                    result = resultSet.getString(1);
                }
            }
        }.result;
    }

    private static void execute(Connection connection, String sql, Object... arguments) {
        String finalSql = String.format(sql, arguments);
        try (PreparedStatement statement = connection.prepareStatement(finalSql)) {
            statement.execute();
        } catch (Exception exception) {
            throw Utils.exception(exception, "Error executing SQL '%s'.", finalSql);
        }
    }

    private abstract static class ExecuteQuery {

        public abstract void extract(ResultSet resultSet) throws Exception;

        public ExecuteQuery(Connection connection, String query, Object... arguments) {
            String finalQuery = String.format(query, arguments);
            try (PreparedStatement statement = connection.prepareStatement(finalQuery)) {
                extract(statement.executeQuery());
            } catch (Exception exception) {
                throw Utils.exception(exception, "Error executing query '%s'.", finalQuery);
            }
        }
    }
}
