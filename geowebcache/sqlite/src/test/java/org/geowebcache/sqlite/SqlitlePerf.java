/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Nuno Oliveira, GeoSolutions S.A.S., Copyright 2016
 */
package org.geowebcache.sqlite;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.blobstore.file.FileBlobStore;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Measures the performance of the {@link SqliteConnectionManager}
 * and the {@link MbtilesBlobStore} against using a raw query.
 */
final class SqlitlePerf {

    private static Log LOGGER = LogFactory.getLog(SqlitlePerf.class);

    // number of workers that will be used to perform the selects
    final static int WORKERS = 10;
    // number of tiles to store and retrieve
    final static int TILES = 1000000;

    public static void main(String[] args) throws Exception {
        // initiate sqlite drive
        Class.forName("org.sqlite.JDBC");
        // create the directory that will contain all created files
        File rootDirectory = Files.createTempDirectory("gwc-").toFile();
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Root directory '%s'.", rootDirectory));
        }
        // seeding file system
        long[][] tiles = new long[TILES][3];
        File seedDirectory = seedFileSystem(rootDirectory, tiles);
        fileStore(seedDirectory, tiles);
        FileUtils.deleteDirectory(seedDirectory);
        // seeding a database with some random tiles
        File seedFile = createSeedFile(rootDirectory, tiles);
        // select tiles using a raw connection
        rawSqlitle(rootDirectory, seedFile, tiles);
        // select tiles using the connection manager
        pooledSqlitle(rootDirectory, seedFile, tiles);
        // select tiles using the mbtiles blobstore
        mbtilesStore(rootDirectory, seedFile, tiles);
        // cleaning everything
        FileUtils.deleteDirectory(rootDirectory);
    }

    /**
     * Select the created tiles using a raw connection.
     */
    private static void rawSqlitle(File rootDirectory, File seedFile, long[][] tiles) throws Exception {
        // creating a new database by copying the seeded one
        File databaseFile = new File(rootDirectory, "raw_perf_test.sqlite");
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Start raw select from file '%s'.", databaseFile));
        }
        FileUtils.copyFile(seedFile, databaseFile);
        // submitting the select tasks
        ExecutorService executor = Executors.newFixedThreadPool(WORKERS);
        long startTime = System.currentTimeMillis();
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + seedFile.getPath());
        for (int i = 0; i < tiles.length; i++) {
            long[] tile = tiles[i];
            executor.submit((Runnable) () -> getTile(connection, tile));
            if (i != 0 && i % 10000 == 0) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(String.format("Submitted %d select tasks.", i));
                }
            }
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(String.format("Submitted %d select tasks.", TILES));
        }
        // lets wait for the workers to finish
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.MINUTES);
        // computing some stats
        long endTime = System.currentTimeMillis();
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Tiles raw select time '%d'.", endTime - startTime));
        }
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Tiles raw selected per second '%f'.", TILES / (float) (endTime - startTime) * 1000));
        }
        // clean everything
        connection.close();
        FileUtils.deleteQuietly(databaseFile);
    }

    /**
     * Select the created tiles using a connection provide by the connection manager.
     */
    private static void pooledSqlitle(File rootDirectory, File seedFile, long[][] tiles) throws Exception {
        // creating a new database by copying the seeded one
        File databaseFile = new File(rootDirectory, "pooled_perf_test.sqlite");
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Start pooled select from file '%s'.", databaseFile));
        }
        FileUtils.copyFile(seedFile, databaseFile);
        // submitting the select tasks
        ExecutorService executor = Executors.newFixedThreadPool(WORKERS);
        SqliteConnectionManager connectionManager = new SqliteConnectionManager(10, 2000);
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < tiles.length; i++) {
            long[] tile = tiles[i];
            executor.submit((Runnable) () -> connectionManager.doWork(databaseFile, true, connection -> {
                getTile(connection, tile);
            }));
            if (i != 0 && i % 10000 == 0) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(String.format("Submitted %d select tasks.", i));
                }
            }
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(String.format("Submitted %d select tasks.", TILES));
        }
        // lets wait for the workers to finish
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.MINUTES);
        // computing some stats
        long endTime = System.currentTimeMillis();
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Tiles pooled select time '%d'.", endTime - startTime));
        }
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Tiles pooled selected per second '%f'.", TILES / (float) (endTime - startTime) * 1000));
        }
        // clean everything
        connectionManager.reapAllConnections();
        connectionManager.stopPoolReaper();
        FileUtils.deleteQuietly(databaseFile);
    }

    /**
     * Retrieve the created tiles using the mbtiles blobstore.
     */
    private static void mbtilesStore(File rootDirectory, File seedFile, long[][] tiles) throws Exception {
        // creating a new database by copying the seeded one
        File databaseFile = new File(rootDirectory, Utils.buildPath("grid", "layer", "image_png", "mbtiles_perf_test.sqlite"));
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Start mbtiles select from file '%s'.", databaseFile));
        }
        FileUtils.copyFile(seedFile, databaseFile);
        // submitting the select tasks
        ExecutorService executor = Executors.newFixedThreadPool(WORKERS);
        long startTime = System.currentTimeMillis();
        // mbtiles store configuration
        MbtilesInfo configuration = new MbtilesInfo();
        configuration.setRootDirectory(rootDirectory.getPath());
        configuration.setTemplatePath(Utils.buildPath("{grid}", "{layer}", "{format}", "mbtiles_perf_test.sqlite"));
        configuration.setUseCreateTime(false);
        // instantiate the mbtiles blobstore
        SqliteConnectionManager connectionManager = new SqliteConnectionManager(10, 2000);
        MbtilesBlobStore mbtilesBlobStore = new MbtilesBlobStore(configuration, connectionManager);
        for (int i = 0; i < tiles.length; i++) {
            long[] tile = tiles[i];
            executor.submit((Runnable) () -> {
                TileObject mbtile = TileObject.createQueryTileObject("layer", tile, "grid", "image/png", null);
                try {
                    mbtilesBlobStore.get(mbtile);
                } catch (Exception exception) {
                    throw Utils.exception(exception, "Error retrieving tile '%s'.", mbtile);
                }
            });
            if (i != 0 && i % 10000 == 0) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(String.format("Submitted %d select tasks.", i));
                }
            }
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(String.format("Submitted %d select tasks.", TILES));
        }
        // lets wait for the workers to finish
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.MINUTES);
        // computing some stats
        long endTime = System.currentTimeMillis();
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Tiles mbtiles blobstore select time '%d'.", endTime - startTime));
        }
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Tiles mbtiles blobstore selected per second '%f'.", TILES / (float) (endTime - startTime) * 1000));
        }
        // clean everything
        connectionManager.reapAllConnections();
        connectionManager.stopPoolReaper();
        FileUtils.deleteQuietly(databaseFile);
    }

    /**
     * Retrieve the created tiles using the file blobstore.
     */
    private static void fileStore(File seedDirectory, long[][] tiles) throws Exception {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Start reading from directory'%s'.", seedDirectory));
        }
        // submitting the read tasks
        ExecutorService executor = Executors.newFixedThreadPool(WORKERS);
        long startTime = System.currentTimeMillis();
        // instantiate the file blobstore
        BlobStore fileBlobStore = new FileBlobStore(seedDirectory.getPath());
        for (int i = 0; i < tiles.length; i++) {
            long[] tile = tiles[i];
            executor.submit((Runnable) () -> {
                TileObject mbtile = TileObject.createQueryTileObject("layer", tile, "grid", "image/png", null);
                try {
                    fileBlobStore.get(mbtile);
                } catch (Exception exception) {
                    throw Utils.exception(exception, "Error retrieving tile '%s'.", mbtile);
                }
            });
            if (i != 0 && i % 10000 == 0) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(String.format("Submitted %d read tasks.", i));
                }
            }
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(String.format("Submitted %d read tasks.", TILES));
        }
        // lets wait for the workers to finish
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.MINUTES);
        // computing some stats
        long endTime = System.currentTimeMillis();
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Tiles file blobstore read time '%d'.", endTime - startTime));
        }
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Tiles file blobstore reads per second '%f'.", TILES / (float) (endTime - startTime) * 1000));
        }
    }

    /**
     * Store random tiles in the filesystem.
     */
    private static File seedFileSystem(File rootDirectory, long[][] tiles) throws Exception {
        // creating the root directory where tiles will be saved
        File seedDirectory = new File(rootDirectory, "tiles");
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Start seeding file system '%s'.", seedDirectory));
        }
        BlobStore fileBlobStore = new FileBlobStore(seedDirectory.getPath());
        // start seeding the tiles
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < TILES; i++) {
            Tile tile = Tile.random();
            tiles[i][0] = tile.x;
            tiles[i][1] = tile.y;
            tiles[i][2] = tile.z;
            fileBlobStore.put(TileObject.createCompleteTileObject("layer",
                    new long[]{tile.x, tile.y, tile.z},
                    "epsg:4326",
                    "image/png",
                    null,
                    Utils.byteArrayToResource(tile.data)));
            if (i != 0 && i % 10000 == 0) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(String.format("Stored %d tiles.", i));
                }
            }
        }
        long endTime = System.currentTimeMillis();
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Insert time '%d' (batch mode).", endTime - startTime));
        }
        return seedDirectory;
    }

    /**
     * Seeding a file with random tiles.
     */
    private static File createSeedFile(File rootDirectory, long[][] tiles) throws Exception {
        // creating the database that will be seeded
        File seedFile = new File(rootDirectory, "seed_perf_test.sqlite");
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Start seeding file '%s'.", seedFile));
        }
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + seedFile.getPath());
        String createTableSql = "CREATE TABLE IF NOT EXISTS tiles (zoom_level integer, tile_column integer, " +
                "tile_row integer, tile_data blob, CONSTRAINT pk_tiles PRIMARY KEY(zoom_level, tile_column,tile_row));";
        executeSql(connection, createTableSql);
        // start seeding wrapped in a transaction (improves performance)
        long startTime = System.currentTimeMillis();
        executeSql(connection, "BEGIN TRANSACTION;");
        String sql = "INSERT OR REPLACE INTO tiles VALUES(?, ?, ?, ?);";
        // insert the tiles in batches
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < TILES; i++) {
                Tile tile = Tile.random();
                tiles[i][0] = tile.x;
                tiles[i][1] = tile.y;
                tiles[i][2] = tile.z;
                statement.setLong(1, tile.z);
                statement.setLong(2, tile.x);
                statement.setLong(3, tile.y);
                statement.setBytes(4, tile.data);
                statement.addBatch();
                if (i != 0 && i % 10000 == 0) {
                    statement.executeBatch();
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(String.format("Inserted batch %d.", i));
                    }
                }
            }
            statement.executeBatch();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(String.format("Inserted batch %d.", TILES));
            }
        } catch (Exception exception) {
            throw Utils.exception(exception, "Error executing SQL '%s'.", sql);
        }
        // clean everything
        executeSql(connection, "END TRANSACTION;");
        long endTime = System.currentTimeMillis();
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Insert time '%d' (batch mode).", endTime - startTime));
        }
        connection.close();
        return seedFile;
    }

    /**
     * Helper method that fetches a tile form the database using the provided connection.
     */
    private static byte[] getTile(Connection connection, long[] xyz) {
        String sql = "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?;";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, xyz[2]);
            statement.setLong(2, xyz[0]);
            statement.setLong(3, xyz[1]);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                // the tile exists
                byte[] data = resultSet.getBytes(1);
                if (data.length != 2024) {
                    // the data doesn't have the expected size
                    if (LOGGER.isErrorEnabled()) {
                        LOGGER.error(String.format("Tile %d-%d-%d data is not valid.", xyz[2], xyz[0], xyz[1]));
                    }
                }
                // the tile data looks good
                return data;
            } else {
                // the tile was not found
                if (LOGGER.isErrorEnabled()) {
                    LOGGER.error(String.format("Failed to load tile %d-%d-%d.", xyz[2], xyz[0], xyz[1]));
                }
                return null;
            }
        } catch (Exception exception) {
            throw Utils.exception(exception, "Error executing SQL '%s'.", sql);
        }
    }

    /**
     * Helper method that executes an SQL statement using the provided connection.
     */
    private static void executeSql(Connection connection, String sql) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        } catch (Exception exception) {
            throw Utils.exception(exception, "Error executing SQL '%s'.", sql);
        }
    }

    /**
     * Helper class that stores a tile information.
     */
    private final static class Tile {

        private final static Random random = new Random();

        final long x;
        final long y;
        final long z;
        final byte[] data;

        private Tile(long x, long y, long z, byte[] data) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.data = data;
        }

        /**
         * Creates a random tile.
         */
        static Tile random() {
            byte[] data = new byte[2024];
            random.nextBytes(data);
            return new Tile(random.nextInt(1000000),
                    random.nextInt(1000000),
                    random.nextInt(10),
                    data);
        }
    }
}
