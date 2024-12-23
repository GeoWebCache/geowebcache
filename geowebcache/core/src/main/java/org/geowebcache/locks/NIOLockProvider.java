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

    public static Logger LOGGER = Logging.getLogger(NIOLockProvider.class.getName());

    private final String root;

    /** The wait to occur in case the lock cannot be acquired */
    private final int waitBeforeRetry;

    /** max lock attempts */
    private final int maxLockAttempts;

    MemoryLockProvider memoryProvider = new MemoryLockProvider();

    public NIOLockProvider(DefaultStorageFinder storageFinder) throws ConfigurationException {
        this(storageFinder.getDefaultPath());
    }

    public NIOLockProvider(DefaultStorageFinder storageFinder, int waitBeforeRetry, int maxLockAttempts)
            throws ConfigurationException {
        this.root = storageFinder.getDefaultPath();
        this.waitBeforeRetry = waitBeforeRetry;
        this.maxLockAttempts = maxLockAttempts;
    }

    public NIOLockProvider(String root) throws ConfigurationException {
        this.root = root;
        this.waitBeforeRetry = 20;
        this.maxLockAttempts = 120 * 1000 / waitBeforeRetry;
    }

    @Override
    @SuppressWarnings({"PMD.CloseResource", "PMD.UseTryWithResources"})
    // complex but seemingly correct resource handling
    public LockProvider.Lock getLock(final String lockKey) throws GeoWebCacheException {
        File file = null;
        // first off, synchronize among threads in the same jvm (the nio locks won't lock
        // threads in the same JVM)
        final LockProvider.Lock memoryLock = memoryProvider.getLock(lockKey);
        // then synch up between different processes
        try {
            file = getFile(lockKey);
            FileOutputStream currFos = null;
            FileLock currLock = null;
            try {
                // try to lock
                int count = 0;
                while (currLock == null && count < maxLockAttempts) {
                    // the file output stream can also fail to be acquired due to the
                    // other nodes deleting the file
                    try {
                        currFos = new FileOutputStream(file);

                        currLock = currFos.getChannel().lock();
                    } catch (OverlappingFileLockException | IOException e) {
                        IOUtils.closeQuietly(currFos);
                        try {
                            Thread.sleep(waitBeforeRetry);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            // ok, moving on
                        }
                    }
                    count++;
                }

                // verify we managed to get the FS lock
                if (count >= maxLockAttempts) {
                    throw new GeoWebCacheException(
                            "Failed to get a lock on key " + lockKey + " after " + count + " attempts");
                }

                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Lock "
                            + lockKey
                            + " acquired by thread "
                            + Thread.currentThread().getName()
                            + " on file "
                            + file);
                }

                // store the results in a final variable for the inner class to use
                final FileOutputStream fos = currFos;
                final FileLock lock = currLock;

                // nullify so that we don't close them, the locking occurred as expected
                currFos = null;
                currLock = null;

                final File lockFile = file;
                return new LockProvider.Lock() {

                    boolean released;

                    @Override
                    public void release() throws GeoWebCacheException {
                        if (released) {
                            return;
                        }

                        try {
                            released = true;
                            if (!lock.isValid()) {
                                // do not crap out, locks usage in GWC is only there to prevent
                                // duplication of work
                                if (LOGGER.isLoggable(Level.FINE)) {
                                    LOGGER.fine(
                                            "Lock key "
                                                    + lockKey
                                                    + " for releasing lock is unkonwn, it means "
                                                    + "this lock was never acquired, or was released twice. "
                                                    + "Current thread is: "
                                                    + Thread.currentThread().getName()
                                                    + ". "
                                                    + "Are you running two GWC instances in the same JVM using NIO locks? "
                                                    + "This case is not supported and will generate exactly this error message");
                                    return;
                                }
                            }
                            try {
                                lock.release();
                                IOUtils.closeQuietly(fos);
                                lockFile.delete();

                                if (LOGGER.isLoggable(Level.FINE)) {
                                    LOGGER.fine("Lock "
                                            + lockKey
                                            + " on file "
                                            + lockFile
                                            + " released by thread "
                                            + Thread.currentThread().getName());
                                }
                            } catch (IOException e) {
                                throw new GeoWebCacheException(
                                        "Failure while trying to release lock for key " + lockKey, e);
                            }
                        } finally {
                            memoryLock.release();
                        }
                    }
                };
            } finally {
                try {
                    if (currLock != null) {
                        currLock.release();
                    }
                    IOUtils.closeQuietly(currFos);
                    file.delete();
                } finally {
                    memoryLock.release();
                }
            }
        } catch (IOException e) {
            throw new GeoWebCacheException("Failure while trying to get lock for key " + lockKey, e);
        }
    }

    private File getFile(String lockKey) {
        File locks = new File(root, "lockfiles");
        locks.mkdirs();
        String sha1 = DigestUtils.sha1Hex(lockKey);
        return new File(locks, sha1 + ".lck");
    }
}
