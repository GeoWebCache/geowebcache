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
 * <p>Copyright 2026
 */
package org.geowebcache.diskquota.jdbc;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.apache.commons.dbcp.BasicDataSource;
import org.geowebcache.diskquota.storage.PageStatsPayload;
import org.geowebcache.diskquota.storage.Quota;
import org.geowebcache.diskquota.storage.TilePage;
import org.geowebcache.diskquota.storage.TilePageCalculator;
import org.geowebcache.diskquota.storage.TileSet;
import org.geowebcache.storage.DefaultStorageFinder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Concurrency suite for {@link JDBCQuotaStore}, run by each dialect's subclass.
 *
 * <p>Verifies the two invariants the retry layer must hold: no {@code DataAccessException} escapes, and the ledger
 * total matches the sum of deltas exactly (drift means an abort was dropped instead of replayed). On engines that
 * serialize on row locks (HSQL) the abort path isn't exercised, but the scenarios still pass.
 */
public abstract class AbstractJDBCQuotaStoreConcurrencyTest {

    protected static final int THREAD_COUNT = 4;
    protected static final int ITERATIONS_PER_THREAD = 200;
    protected static final long BYTES_PER_ITERATION = 1024L;

    protected DataSource dataSource;

    protected JDBCQuotaStore store;
    protected TileSet tileSet;
    protected TilePage tilePage;

    protected abstract DataSource newDataSource() throws Exception;

    protected abstract SQLDialect newDialect();

    /** Drops the schema tables for a clean state. Oracle overrides; it needs {@code CASCADE CONSTRAINTS}. */
    protected void cleanupDatabase(DataSource ds) throws SQLException {
        try (Connection cx = ds.getConnection();
                Statement st = cx.createStatement()) {
            try {
                st.execute("DROP TABLE TILEPAGE CASCADE");
            } catch (SQLException ignored) {
                // table may not exist on first run
            }
            try {
                st.execute("DROP TABLE TILESET CASCADE");
            } catch (SQLException ignored) {
                // table may not exist on first run
            }
        }
    }

    /** Pool sized for {@link #THREAD_COUNT} writers plus headroom; short max-wait so deadlocked tests fail fast. */
    protected static BasicDataSource newPooledDataSource(String driver, String url, String user, String password) {
        BasicDataSource ds = new BasicDataSource();
        ds.setDriverClassName(driver);
        ds.setUrl(url);
        ds.setUsername(user);
        ds.setPassword(password);
        ds.setPoolPreparedStatements(true);
        ds.setAccessToUnderlyingConnectionAllowed(true);
        ds.setMinIdle(1);
        ds.setMaxActive(THREAD_COUNT + 2);
        ds.setMaxWait(5000);
        return ds;
    }

    @Before
    public final void setUpStore() throws Exception {
        dataSource = newDataSource();
        cleanupDatabase(dataSource);

        DefaultStorageFinder finder = mock(DefaultStorageFinder.class);
        TilePageCalculator calculator = mock(TilePageCalculator.class);
        when(calculator.getLayerNames()).thenReturn(Collections.emptySet());
        when(calculator.getTilesPerPage(any(TileSet.class), anyInt())).thenReturn(BigInteger.valueOf(1_000_000));

        store = new JDBCQuotaStore(finder, calculator);
        store.setDataSource(dataSource);
        store.setDialect(newDialect());
        store.initialize();

        tileSet = new TileSet("layer", "EPSG:4326", "image/png", null);
        tilePage = new TilePage(tileSet.getId(), 0, 0, 0);

        // Pre-create both rows so the contention is row-update, not row-insert.
        store.addToQuotaAndTileCounts(tileSet, new Quota(BigInteger.ZERO), Collections.singletonList(payload(1)));
    }

    @After
    public final void tearDownStore() throws Exception {
        if (store != null) {
            store.close();
        }
    }

    /** Drift in the final total means an aborted transaction was dropped instead of replayed. */
    @Test(timeout = 120_000)
    public void concurrentAddToQuota_doesNotDriftUnderSerializable() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < THREAD_COUNT; t++) {
            futures.add(pool.submit(() -> {
                start.await();
                Quota delta = new Quota(BigInteger.valueOf(BYTES_PER_ITERATION));
                for (int i = 0; i < ITERATIONS_PER_THREAD; i++) {
                    store.addToQuotaAndTileCounts(tileSet, delta, Collections.singletonList(payload(1)));
                }
                return null;
            }));
        }
        start.countDown();
        try {
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }

        BigInteger expected = BigInteger.valueOf((long) THREAD_COUNT * ITERATIONS_PER_THREAD * BYTES_PER_ITERATION);
        assertEquals(
                "TILESET.BYTES drifted: aborted transactions were not replayed",
                expected,
                store.getUsedQuotaByTileSetId(tileSet.getId()).getBytes());
        assertEquals(
                "Global TILESET.BYTES drifted: aborted transactions were not replayed",
                expected,
                store.getGloballyUsedQuota().getBytes());
    }

    /** Truncation is idempotent, so the assertion is negative: no abort exception escapes. */
    @Test(timeout = 120_000)
    public void concurrentSetTruncated_doesNotThrowUnderSerializable() throws Exception {
        // Bring the page row into a state where setTruncated has work to do (fillFactor > 0).
        store.addToQuotaAndTileCounts(tileSet, new Quota(BigInteger.ZERO), Collections.singletonList(payload(100)));

        int truncators = 2;
        int iterations = 100;
        ExecutorService pool = Executors.newFixedThreadPool(truncators);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < truncators; t++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < iterations; i++) {
                    store.setTruncated(tilePage);
                }
                return null;
            }));
        }
        start.countDown();
        try {
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    protected PageStatsPayload payload(int numTiles) {
        PageStatsPayload p = new PageStatsPayload(tilePage);
        p.setNumTiles(numTiles);
        return p;
    }
}
