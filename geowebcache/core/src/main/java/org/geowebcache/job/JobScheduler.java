package org.geowebcache.job;

import it.sauronsoftware.cron4j.InvalidPatternException;
import it.sauronsoftware.cron4j.Scheduler;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

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
    
    /**
     * The job scheduler keeps track of the cron4j schedule ID's and maps them to jobs.
     */
    private static Map<Long, String> scheduleIds;
    
    static {
        instance = new Scheduler();
        instance.start();
        scheduleIds = new HashMap<Long, String>();
    }
    
    public static void scheduleJob(JobObject job, TileBreeder seeder, JobStore jobStore) {
        // scheduled jobs don't run immediately, but the job monitor needs to be aware of them.
        ScheduledJobInitiator sji = new ScheduledJobInitiator(job, seeder, jobStore);
        synchronized(instance) {
            try {
                scheduleIds.put(job.getJobId(), instance.schedule(job.getSchedule(), sji));
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

    public static void deschedule(long jobId) {
        String scheduleId = null;
        synchronized(instance) {
            if(scheduleIds.containsKey(jobId)) {
                scheduleId = scheduleIds.get(jobId);
            }
        }
        
        if(scheduleId != null) {
            synchronized(instance) {
                instance.deschedule(scheduleId);
                if(scheduleIds.containsValue(scheduleId)) {
                    scheduleIds.remove(jobId);
                    log.info("Job " + jobId + " has been de-scheduled.");
                }
            }
        }
    }

    private JobScheduler() {
        ;
    }
}
