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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.storage.DefaultStorageFinder;

/**
 * A lock provider based on file system locks
 * 
 * @author Andrea Aime - GeoSolutions
 */
public class NIOLockProvider implements LockProvider {

    private String root;
    /**
     * The wait to occurr in case the lock cannot be acquired
     */
    int waitBeforeRetry = 20; 
    /**
     * max lock attempts
     */
    int maxLockAttempts = 120 * 1000 / waitBeforeRetry;

    private Map<String, Lock> locks = new ConcurrentHashMap<String, Lock>();

    public NIOLockProvider(DefaultStorageFinder storageFinder) throws ConfigurationException {
        this.root = storageFinder.getDefaultPath();
    }

    public NIOLockProvider(String root) throws ConfigurationException {
        this.root = root;
    }

    public void getLock(String lockKey) throws GeoWebCacheException {
        File file = getFile(lockKey);
        try {
            FileOutputStream fos = null;
            FileLock lock = null;
            try {
                // try to lock
                int count = 0;
                while(lock == null && count < maxLockAttempts) {
                    // the file output stream can also fail to be acquired due to the
                    // other nodes deleting the file
                    fos = new FileOutputStream(file);
                    try {
                        lock = fos.getChannel().lock();
                    } catch(OverlappingFileLockException e) {
                        IOUtils.closeQuietly(fos);
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException ie) {
                            // ok, moving on
                        }
                    } catch(IOException e) {
                        // this one is also thrown with a message "avoided fs deadlock"
                        IOUtils.closeQuietly(fos);
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

                // store the lock so that release can find it
                locks.put(lockKey, new Lock(file, fos, lock));

                // nullify so that we don't close them, the locking occurred as expected
                fos = null;
                lock = null;
            } finally {
                if (lock != null) {
                    lock.release();
                }
                IOUtils.closeQuietly(fos);
                file.delete();
            }
        } catch (IOException e) {
            throw new GeoWebCacheException("Failure while trying to get lock for key " + lockKey, e);
        }

    }

    public void releaseLock(String lockKey) throws GeoWebCacheException {
        Lock lock = locks.get(lockKey);
        if (lock == null) {
            throw new GeoWebCacheException(
                    "Lock key used for releasing lock is unkonwn, it means this lock was never acquired, or was released twice");
        }
        try {
            locks.remove(lockKey);
            lock.lock.release();
            IOUtils.closeQuietly(lock.fos);
            lock.file.delete();
        } catch (IOException e) {
            throw new GeoWebCacheException("Failure while trying to release lock for key "
                    + lockKey, e);
        }

    }

    private File getFile(String lockKey) {
        File locks = new File(root, "lockfiles");
        locks.mkdirs();
        String sha1 = DigestUtils.shaHex(lockKey);
        return new File(locks, sha1 + ".lck");
    }

    static class Lock {
        File file;

        FileOutputStream fos;

        FileLock lock;

        public Lock(File file, FileOutputStream fos, FileLock lock) {
            this.file = file;
            this.fos = fos;
            this.lock = lock;
        }

    }
}
