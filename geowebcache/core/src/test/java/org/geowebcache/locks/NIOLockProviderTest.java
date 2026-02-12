/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geowebcache.locks;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.geowebcache.GeoWebCacheException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * The file lock provider is actually not reentrant (not possible with Java) but the keys for meta-tile caching are
 * different, so we don't really need it to handle nested locks on the same key.
 */
public class NIOLockProviderTest {

    /** Small timeout for testing failure cases quickly */
    private static final int TEST_TIMEOUT_SECONDS = 1;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private NIOLockProvider provider;
    private String root;

    @Before
    public void setUp() throws Exception {
        root = tempFolder.newFolder("lockRoot").getCanonicalPath();
        provider = new NIOLockProvider(root, TEST_TIMEOUT_SECONDS);
    }

    @Test
    public void testAcquireAndReleaseDeletesFile() throws Exception {
        String key = "test-key";
        LockProvider.Lock lock = provider.getLock(key);

        File lockFile = getLockFile(key);
        assertTrue("Lock file should exist on disk", lockFile.exists());

        lock.release();

        // Use awaitility to handle potential OS delay in file deletion
        await().atMost(2, SECONDS).until(() -> !lockFile.exists());
    }

    @Test
    public void testLockTimeoutThrowsException() throws Exception {
        String key = "timeout-key";
        // Create a second provider instance pointing to the same root
        // This ensures they don't share the same MemoryLockProvider
        NIOLockProvider provider2 = new NIOLockProvider(root, TEST_TIMEOUT_SECONDS);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch threadAHold = new CountDownLatch(1);
            CountDownLatch releaseThreadA = new CountDownLatch(1);

            executor.submit(() -> {
                try {
                    LockProvider.Lock lock = provider.getLock(key);
                    threadAHold.countDown();
                    try {
                        releaseThreadA.await(5, SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        try {
                            lock.release();
                        } catch (GeoWebCacheException e) {
                            fail("Failed to release lock: " + e.getMessage());
                        }
                    }
                } catch (GeoWebCacheException e) {
                    throw new RuntimeException(e);
                }
            });

            assertTrue("Thread A failed to acquire lock", threadAHold.await(2, SECONDS));

            long start = System.currentTimeMillis();
            try {
                // Use the SECOND provider; it will be blocked by the file on disk
                provider2.getLock(key);
                fail("Should have thrown IllegalStateException due to timeout");
            } catch (IllegalStateException e) {
                long duration = System.currentTimeMillis() - start;
                assertTrue("Timeout was too fast: " + duration, duration >= 1000);
                assertTrue(e.getMessage().contains("ms"));
            } finally {
                releaseThreadA.countDown();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testInterruptionDuringAcquisition() throws Exception {
        String key = "interrupt-key";

        // Mock a situation where the lock is already held
        LockProvider.Lock firstLock = provider.getLock(key);

        Thread testThread = new Thread(() -> {
            try {
                provider.getLock(key);
                fail("Should have been interrupted");
            } catch (Exception e) {
                // Expected
            }
        });

        testThread.start();
        Thread.sleep(200); // Let it enter the loop
        testThread.interrupt();

        testThread.join(2000);
        assertFalse("Thread should have terminated after interruption", testThread.isAlive());

        firstLock.release();
    }

    private File getLockFile(String key) {
        // This mimics the internal logic of FileLockProvider to verify disk state
        String hash = org.apache.commons.codec.digest.DigestUtils.sha256Hex(key);
        return new File(new File(root, "lockfiles"), hash + ".lck");
    }
}
