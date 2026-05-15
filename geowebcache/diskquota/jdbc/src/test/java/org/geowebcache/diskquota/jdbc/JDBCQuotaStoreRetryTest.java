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

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.Collections;
import org.geowebcache.diskquota.storage.Quota;
import org.geowebcache.diskquota.storage.TilePageCalculator;
import org.geowebcache.diskquota.storage.TileSet;
import org.geowebcache.storage.DefaultStorageFinder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** Offline tests for the retry helper in {@link JDBCQuotaStore}: cap exhaustion, interrupt handling, and skip-paths. */
@SuppressWarnings("unchecked") // raw TransactionCallback in Mockito matchers
public class JDBCQuotaStoreRetryTest {

    private JDBCQuotaStore store;
    private TransactionTemplate tt;
    private TileSet tileSet;

    @Before
    public void setUp() {
        store = new JDBCQuotaStore(mock(DefaultStorageFinder.class), mock(TilePageCalculator.class));
        tt = mock(TransactionTemplate.class);
        store.tt = tt;
        // Keep test runtime small: 3 attempts, 1ms initial backoff.
        store.maxTransactionAttempts = 3;
        store.initialTransactionBackoffMs = 1L;
        tileSet = new TileSet("layer", "EPSG:4326", "image/png", null);
    }

    @After
    public void tearDown() {
        // Clear any interrupt left behind by the interrupt test so it doesn't leak to other tests.
        Thread.interrupted();
    }

    @Test
    public void retryExhaustionPropagatesOriginalException() throws Exception {
        CannotAcquireLockException abort = new CannotAcquireLockException("simulated SSI abort");
        when(tt.execute(any(TransactionCallback.class))).thenThrow(abort);

        try {
            store.addToQuotaAndTileCounts(tileSet, new Quota(BigInteger.ZERO), Collections.emptyList());
            fail("expected CannotAcquireLockException after retry exhaustion");
        } catch (CannotAcquireLockException actual) {
            assertSame(abort, actual);
        }
        verify(tt, times(store.maxTransactionAttempts)).execute(any(TransactionCallback.class));
    }

    @Test
    public void interruptDuringRetryBackoffPropagatesImmediately() throws Exception {
        CannotAcquireLockException abort = new CannotAcquireLockException("simulated SSI abort");
        when(tt.execute(any(TransactionCallback.class))).thenThrow(abort);

        Thread.currentThread().interrupt();
        try {
            store.addToQuotaAndTileCounts(tileSet, new Quota(BigInteger.ZERO), Collections.emptyList());
            fail("expected CannotAcquireLockException to propagate when interrupted mid-retry");
        } catch (CannotAcquireLockException actual) {
            assertSame(abort, actual);
            assertTrue(
                    "interrupt flag must be preserved", Thread.currentThread().isInterrupted());
        }
        // Only one tt.execute call: first attempt fails, backoff sleep sees the interrupt and bails out.
        verify(tt, times(1)).execute(any(TransactionCallback.class));
    }

    @Test
    public void nonConcurrencyExceptionIsNotRetried() throws Exception {
        IllegalStateException nonRetryable = new IllegalStateException("not a retryable abort");
        when(tt.execute(any(TransactionCallback.class))).thenThrow(nonRetryable);

        try {
            store.addToQuotaAndTileCounts(tileSet, new Quota(BigInteger.ZERO), Collections.emptyList());
            fail("expected IllegalStateException to propagate without retry");
        } catch (IllegalStateException actual) {
            assertSame(nonRetryable, actual);
        }
        verify(tt, times(1)).execute(any(TransactionCallback.class));
    }
}
