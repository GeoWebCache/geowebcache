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
 * 
 * @author Marius Suta / The Open Planning Project 2008 
 */
package org.geowebcache.seed;

import java.sql.Timestamp;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.storage.JobStore;
import org.geowebcache.storage.StorageException;

public class OldJobPurgeTask implements Runnable {
    private static Log log = LogFactory.getLog(org.geowebcache.seed.OldJobPurgeTask.class);

    protected JobStore jobStore = null;

    public OldJobPurgeTask(JobStore jobStore) {
        this.jobStore = jobStore;
    }
    
    public void run() {
        try {
            long seconds = jobStore.getClearOldJobsSetting();
            if(seconds > 0) {
                long millis = System.currentTimeMillis() - (seconds * 1000);
                Timestamp ts = new Timestamp(millis);
                
                log.info("Purging finished jobs older than " + ts + "...");
                long count = jobStore.purgeOldJobs(ts);
                log.info("Old jobs purged: " + count);
            }
        } catch (StorageException e) {
            log.error("Storage exception while purging old jobs: " + e.getMessage(), e);
        }
    }
}