package org.geowebcache.storage.metastore.jdbc;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import junit.framework.TestCase;

import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.storage.TileObject;
import org.geowebcache.util.FileUtils;

public class JDBCMBWrapperPerfTest extends TestCase {

    private JDBCMBWrapper jdbcWrapper;

    private static final int NUM_TILES_PER_THREAD = 4000;

    private File directory;

    final Resource blob = new ByteArrayResource(new byte[4096]);

    final String parameters = null;

    final String format = "image/jpeg";

    final String gridSetId = "EPSG:4326";

    final String layerName = "fakeLayer";

    private boolean USE_DELETE_PUT_UNLOCK;

    private boolean USE_CONNECTION_POOLING;

    private int NUM_THREADS;

    @Override
    public void setUp() throws Exception {
        directory = new File("target/" + getClass().getSimpleName());
        if (directory.exists()) {
            FileUtils.rmFileCacheDir(directory, null);
        }
        directory.mkdir();
    }

    @Override
    public void tearDown() {
        if (jdbcWrapper != null) {
            jdbcWrapper.destroy();
        }
        FileUtils.rmFileCacheDir(directory, null);
    }

    private JDBCMBWrapper getJdbcWrapper() throws Exception {
        if (jdbcWrapper == null) {
            File db = new File(directory, "h2db");
            String dbFileLocStr = db.toURI().toURL().toExternalForm();
            String jdbcURL = "jdbc:h2:" + dbFileLocStr;

            jdbcWrapper = new JDBCMBWrapper("org.h2.Driver", jdbcURL, "sa", null,
                    USE_CONNECTION_POOLING, 15);
        }
        return jdbcWrapper;
    }

    public void testSingleThreadOldStyle() throws Exception {
        USE_CONNECTION_POOLING = false;
        USE_DELETE_PUT_UNLOCK = true;
        NUM_THREADS = 1;
        putTiles();
    }

    public void testSingleThreadOldStyleWithPooling() throws Exception {
        USE_CONNECTION_POOLING = true;
        USE_DELETE_PUT_UNLOCK = true;
        NUM_THREADS = 1;
        putTiles();
    }

    public void testSingleThreadNewStyle() throws Exception {
        USE_CONNECTION_POOLING = true;
        USE_DELETE_PUT_UNLOCK = false;
        NUM_THREADS = 1;
        putTiles();
    }

    public void testMultiThreadOldStyle() throws Exception {
        USE_CONNECTION_POOLING = false;
        USE_DELETE_PUT_UNLOCK = true;
        NUM_THREADS = 5;
        putTiles();
    }

    public void testMultiThreadOldStyleWithPooling() throws Exception {
        USE_CONNECTION_POOLING = true;
        USE_DELETE_PUT_UNLOCK = true;
        NUM_THREADS = 5;
        putTiles();
    }

    public void testMultiThreadNewStyle() throws Exception {
        USE_CONNECTION_POOLING = true;
        USE_DELETE_PUT_UNLOCK = false;
        NUM_THREADS = 5;
        putTiles();
    }

    private void putTiles() throws Exception {

        final int numThreads = NUM_THREADS;

        // warm up
        insertTiles(NUM_TILES_PER_THREAD, 0);

        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
        Collection<Callable<Long>> tasks = new ArrayList<Callable<Long>>();
        class InsertTask implements Callable<Long> {

            private long numtiles;

            private int offset;

            public InsertTask(long numtiles, int offset) {
                this.numtiles = numtiles;
                this.offset = offset;
            }

            public Long call() throws Exception {
                return insertTiles(numtiles, offset);
            }

        }
        for (int i = 0; i < numThreads; i++) {
            tasks.add(new InsertTask(NUM_TILES_PER_THREAD, i * NUM_TILES_PER_THREAD));
        }
        System.out.println("\nInserting " + NUM_TILES_PER_THREAD * numThreads
                + " tiles spread over " + numThreads + " threads.\nConnection pooling: "
                + USE_CONNECTION_POOLING + ". Old style put: " + USE_DELETE_PUT_UNLOCK);

        List<Future<Long>> results = executorService.invokeAll(tasks);

        double totalTime = 0;
        for (Future<Long> result : results) {
            totalTime += result.get().longValue();
        }
        System.out.println("****Inserted " + NUM_TILES_PER_THREAD * numThreads + " tiles in "
                + (totalTime / numThreads / 1000) + "s. ("
                + (NUM_TILES_PER_THREAD * numThreads / (totalTime / numThreads / 1000))
                + " tiles/s)");
    }

    private long insertTiles(final long numTiles, final int offset) throws Exception {
        long[] xyz;
        TileObject tileObject;
        long totalTime = 0;
        for (int i = 0; i < numTiles; i++) {
            xyz = new long[] { i + offset, i + offset, 0 };
            tileObject = TileObject.createCompleteTileObject(layerName, xyz, gridSetId, format,
                    parameters, blob);
            totalTime += insertTile(tileObject);
        }
        return totalTime;
    }

    private long insertTile(TileObject stObj) throws Exception {
        long t = System.currentTimeMillis();
        JDBCMBWrapper wrapper = getJdbcWrapper();
        if (USE_DELETE_PUT_UNLOCK) {
            wrapper.deleteTile(stObj);
        }

        wrapper.putTile(stObj);

        if (USE_DELETE_PUT_UNLOCK) {
            wrapper.unlockTile(stObj);
        }
        t = System.currentTimeMillis() - t;
        return t;
    }
}
