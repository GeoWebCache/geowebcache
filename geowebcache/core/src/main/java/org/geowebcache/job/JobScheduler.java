package org.geowebcache.job;

import it.sauronsoftware.cron4j.InvalidPatternException;
import it.sauronsoftware.cron4j.Scheduler;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.seed.ScheduledJobInitiator;
import org.geowebcache.seed.TileBreeder;
import org.geowebcache.storage.JobLogObject;
import org.geowebcache.storage.JobObject;
import org.geowebcache.storage.JobStore;
import org.geowebcache.storage.StorageException;

/**
 * Synchronises all access to a singleton cron4j Scheduler.
 */
public class JobScheduler {

    private static Log log = LogFactory.getLog(JobScheduler.class);
    
    private static Scheduler instance;
    
    static {
        instance = new Scheduler();
        instance.start();
    }
    
    public static void scheduleJob(JobObject job, TileBreeder seeder, JobStore jobStore) {
        // scheduled jobs don't run immediately, but the job monitor needs to be aware of them.
        ScheduledJobInitiator sji = new ScheduledJobInitiator(job, seeder, jobStore);
        synchronized(instance) {
            try {
                sji.setScheduleId(instance.schedule(job.getSchedule(), sji));
                log.info("Job " + job.getJobId() + " has been scheduled.");
            } catch (InvalidPatternException e) {
                log.error("Couldn't schedule job " + job.getJobId() + " - invalid schedule pattern: '" + job.getSchedule() + "'.");
                job.addLog(JobLogObject.createErrorLog(job.getJobId(), "Couldn't schedule job", "Job has an invalid schedule pattern: '" + job.getSchedule() + "'."));
                try {
                    jobStore.put(job);
                } catch (StorageException se) {
                    log.error("Couldn't save job log.", se);
                }
            }
        }
    }

    public static void deschedule(String scheduleId) {
        synchronized(instance) {
            instance.deschedule(scheduleId);
        }
    }

    private JobScheduler() {
        ;
    }
}
