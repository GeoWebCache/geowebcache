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
 */
package org.geowebcache.locks;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.storage.DefaultStorageFinder;

/**
 * A lock provider based on file system locks
 * 
 * @author Andrea Aime - GeoSolutions
 */
public class NIOLockProvider implements LockProvider {
    
    public static Log LOGGER = LogFactory.getLog(NIOLockProvider.class);

    private String root;
    /**
     * The wait to occur in case the lock cannot be acquired
     */
    int waitBeforeRetry = 20; 
    /**
     * max lock attempts
     */
    int maxLockAttempts = 120 * 1000 / waitBeforeRetry;

    MemoryLockProvider memoryProvider = new MemoryLockProvider();

    public NIOLockProvider(DefaultStorageFinder storageFinder) throws ConfigurationException {
        this.root = storageFinder.getDefaultPath();
    }

    public NIOLockProvider(String root) throws ConfigurationException {
        this.root = root;
    }

    public LockProvider.Lock getLock(final String lockKey) throws GeoWebCacheException {
        // first off, synchronize among threads in the same jvm (the nio locks won't lock 
        // threads in the same JVM)
        final LockProvider.Lock memoryLock = memoryProvider.getLock(lockKey);
        // then synch up between different processes
        final File file = getFile(lockKey);
        try {
            FileOutputStream currFos = null;
            FileLock currLock = null;
            try {
                // try to lock
                int count = 0;
                while(currLock == null && count < maxLockAttempts) {
                    // the file output stream can also fail to be acquired due to the
                    // other nodes deleting the file
                    currFos = new FileOutputStream(file);
                    try {
                        currLock = currFos.getChannel().lock();
                    } catch(OverlappingFileLockException e) {
                        IOUtils.closeQuietly(currFos);
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException ie) {
                            // ok, moving on
                        }
                    } catch(IOException e) {
                        // this one is also thrown with a message "avoided fs deadlock"
                        IOUtils.closeQuietly(currFos);
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException ie) {
                            // ok, moving on
                        }
                    }
                    count++;
                }
                
                // verify we managed to get the FS lock
                if(count >= maxLockAttempts) {
                    throw new GeoWebCacheException("Failed to get a lock on key " + lockKey + " after " + count + " attempts");
                }

                if(LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Lock " + lockKey + " acquired by thread " + Thread.currentThread().getId() + " on file " + file);
                }
                
                // store the results in a final variable for the inner class to use
                final FileOutputStream fos = currFos;
                final FileLock lock = currLock;

                // nullify so that we don't close them, the locking occurred as expected
                currFos = null;
                currLock = null;
                
                return new LockProvider.Lock() {
                    
                    boolean released;

                    public void release() throws GeoWebCacheException {
                        if(released) {
                            return;
                        }
                        
                        try {
                            released = true;
                            if (!lock.isValid()) {
                                // do not crap out, locks usage in GWC is only there to prevent duplication of work
                                if(LOGGER.isDebugEnabled()) {
                                    LOGGER.debug("Lock key " + lockKey + " for releasing lock is unkonwn, it means " +
                                            "this lock was never acquired, or was released twice. " +
                                            "Current thread is: " + Thread.currentThread().getId() + ". " +
                                             "Are you running two GWC instances in the same JVM using NIO locks? " +
                                             "This case is not supported and will generate exactly this error message");
                                    return;
                                }
                            }
                            try {
                                lock.release();
                                IOUtils.closeQuietly(fos);
                                file.delete();
                                
                                if(LOGGER.isDebugEnabled()) {
                                    LOGGER.debug("Lock " + lockKey + " released by thread " + Thread.currentThread().getId());
                                }
                            } catch (IOException e) {
                                throw new GeoWebCacheException("Failure while trying to release lock for key "
                                        + lockKey, e);
                            }
                        } finally {
                            memoryLock.release();
                        }
                        
                    }
                };
            } finally {
                if (currLock != null) {
                    currLock.release();
                    memoryLock.release();
                }
                IOUtils.closeQuietly(currFos);
                file.delete();
            }
        } catch (IOException e) {
            throw new GeoWebCacheException("Failure while trying to get lock for key " + lockKey, e);
        }

    }

    private File getFile(String lockKey) {
        File locks = new File(root, "lockfiles");
        locks.mkdirs();
        String sha1 = DigestUtils.shaHex(lockKey);
        return new File(locks, sha1 + ".lck");
    }
    
}
