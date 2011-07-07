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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.job.JobScheduler;
import org.geowebcache.storage.JobObject;

public class ScheduledJobInitiator implements Runnable {
    private static Log log = LogFactory.getLog(org.geowebcache.seed.ScheduledJobInitiator.class);

    protected JobObject job = null;
    protected TileBreeder seeder = null;
    protected String scheduleId = null;

    public ScheduledJobInitiator(JobObject job, TileBreeder seeder) {
        this.job = job;
        this.seeder = seeder;
    }
    
    public String getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(String scheduleId) {
        this.scheduleId = scheduleId;
    }

    public void run() {
        if(job.isRunOnce()) {
            log.info("Starting scheduled run-once job: " + job.getJobId());
            // remove run-once job from schedule.
            JobScheduler.deschedule(scheduleId);
        } else {
            log.info("Starting scheduled repeating job. New job is: " + job.getJobId());
            // Clone the existing job and run it. The original job already scheduled stays in the system.
            // To do this we just need to clear the ID. Jobs are persisted on execution and a job with 
            // no ID will be treated as a new job according to the store.
            job.setJobId(-1);
        }
        
        try {
            job.setSchedule(null);
            seeder.executeJob(job);
        } catch(GeoWebCacheException gwce) {
            log.error("Couldn't start scheduled job: " + gwce.getMessage(), gwce);
        }
    }
}
