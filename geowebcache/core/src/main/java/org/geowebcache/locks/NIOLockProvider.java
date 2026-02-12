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
 * <p>Copyright 2019
 */
package org.geowebcache.locks;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.codec.digest.DigestUtils;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.util.IOUtils;

/**
 * A lock provider based on file system locks
 *
 * @author Andrea Aime - GeoSolutions
 */
public class NIOLockProvider implements LockProvider {

    public static final int DEFAULT_WAIT_BEFORE_RETRY = 20;
    public static Logger LOGGER = Logging.getLogger(NIOLockProvider.class.getName());

    private final String root;

    /** The wait to occur in case the lock cannot be acquired */
    private final int waitBeforeRetry;

    private final int timeoutSeconds;

    MemoryLockProvider memoryProvider = new MemoryLockProvider();

    public NIOLockProvider(DefaultStorageFinder storageFinder) throws ConfigurationException {
        this(storageFinder.getDefaultPath());
    }

    public NIOLockProvider(DefaultStorageFinder storageFinder, int waitBeforeRetry, int timeoutSeconds)
            throws ConfigurationException {
        this.root = storageFinder.getDefaultPath();
        this.waitBeforeRetry = waitBeforeRetry;
        this.timeoutSeconds = timeoutSeconds;
    }

    public NIOLockProvider(String root) {
        this.root = root;
        this.waitBeforeRetry = DEFAULT_WAIT_BEFORE_RETRY;
        this.timeoutSeconds = GWC_LOCK_TIMEOUT;
    }

    public NIOLockProvider(String root, int timeoutSeconds) {
        this.root = root;
        this.waitBeforeRetry = DEFAULT_WAIT_BEFORE_RETRY;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    @SuppressWarnings({"PMD.CloseResource"})
    // complex but seemingly correct resource handling
    public LockProvider.Lock getLock(final String lockKey) throws GeoWebCacheException {
        // first off, synchronize among threads in the same jvm (the nio locks won't lock
        // threads in the same JVM)
        final LockProvider.Lock memoryLock = memoryProvider.getLock(lockKey);
        final File file = getFile(lockKey);

        // Track these to ensure cleanup on failure
        FileOutputStream currFos = null;
        FileLock currLock = null;

        if (LOGGER.isLoggable(Level.FINE))
            LOGGER.fine("Mapped lock key " + lockKey + " to lock file " + file + ". Attempting to lock on it.");
        try {
            long lockTimeoutMs = timeoutSeconds * 1000L;
            long startTime = System.currentTimeMillis();

            while (currLock == null && (System.currentTimeMillis() - startTime) < lockTimeoutMs) {
                try {
                    currFos = new FileOutputStream(file);
                    currLock = currFos.getChannel().tryLock();

                    if (currLock == null) {
                        IOUtils.closeQuietly(currFos);
                        Thread.sleep(waitBeforeRetry);
                    }
                } catch (OverlappingFileLockException | IOException | InterruptedException e) {
                    IOUtils.closeQuietly(currFos);
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    Thread.sleep(waitBeforeRetry);
                }
            }

            if (currLock == null) {
                throw new IllegalStateException("Failed to get lock on " + lockKey + " after " + lockTimeoutMs + "ms");
            }

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Lock "
                        + lockKey
                        + " acquired by thread "
                        + Thread.currentThread().getId()
                        + " on file "
                        + file);
            }

            final FileOutputStream finalFos = currFos;
            final FileLock finalLock = currLock;

            return new LockProvider.Lock() {
                boolean released;

                @Override
                public void release() throws GeoWebCacheException {
                    if (released) return;
                    try {
                        released = true;
                        if (finalLock.isValid()) {
                            finalLock.release();
                            IOUtils.closeQuietly(finalFos);
                            file.delete(); // Proper place for deletion

                            if (LOGGER.isLoggable(Level.FINE)) {
                                LOGGER.fine(String.format(
                                        "Lock %s mapped onto %s released by thread %d",
                                        lockKey, file, Thread.currentThread().getId()));
                            }
                        } else {
                            // do not crap out, locks usage is only there to prevent duplication
                            // of work
                            if (LOGGER.isLoggable(Level.FINE)) {
                                LOGGER.fine(String.format(
                                        "Lock key %s for releasing lock is unknown, it means this lock was never"
                                                + " acquired, or was released twice. Current thread is: %d. Are you"
                                                + " running two instances in the same JVM using NIO locks? This case is"
                                                + " not supported and will generate exactly this error message",
                                        lockKey, Thread.currentThread().getId()));
                            }
                        }

                    } catch (IOException e) {
                        throw new IllegalStateException("Failure releasing lock " + lockKey, e);
                    } finally {
                        memoryLock.release();
                    }
                }
            };
        } catch (Exception e) {
            // If we get here, acquisition failed or timed out
            if (currLock != null) {
                try {
                    currLock.release();
                } catch (IOException ignored) {
                }
            }
            IOUtils.closeQuietly(currFos);
            memoryLock.release(); // Must release memory lock on failure
            throw (e instanceof RuntimeException) ? (RuntimeException) e : new IllegalStateException(e);
        }
        // Note: No finally block deleting the file here, it's done in the returned lock
    }

    private File getFile(String lockKey) {
        File locks = new File(root, "lockfiles");
        locks.mkdirs();
        // cryptographically strong and has a chance of collision around 10^-59
        String sha1 = DigestUtils.sha256Hex(lockKey);
        return new File(locks, sha1 + ".lck");
    }
}
