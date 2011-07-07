package org.geowebcache.job;

import it.sauronsoftware.cron4j.InvalidPatternException;
import it.sauronsoftware.cron4j.Scheduler;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.seed.ScheduledJobInitiator;
import org.geowebcache.seed.TileBreeder;
import org.geowebcache.storage.JobObject;

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
    
    public static void scheduleJob(JobObject job, TileBreeder seeder) {
        // scheduled jobs don't run immediately, but the job monitor needs to be aware of them.
        ScheduledJobInitiator sji = new ScheduledJobInitiator(job, seeder);
        synchronized(instance) {
            try {
                sji.setScheduleId(instance.schedule(job.getSchedule(), sji));
                log.info("Job " + job.getJobId() + " has been scheduled.");
            } catch (InvalidPatternException e) {
                // TODO: job error can't start due to bad schedule
                log.error("Couldn't schedule job " + job.getJobId() + " - invalid schedule pattern: '" + job.getSchedule() + "'.");
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
