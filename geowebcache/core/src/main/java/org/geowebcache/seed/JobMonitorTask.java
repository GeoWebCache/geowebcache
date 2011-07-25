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
 * @author Arne Kepp / The Open Planning Project 2008 
 */
package org.geowebcache.seed;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.job.JobScheduler;
import org.geowebcache.storage.JobLogObject;
import org.geowebcache.storage.JobObject;
import org.geowebcache.storage.JobStore;
import org.geowebcache.storage.StorageException;

public class JobMonitorTask extends GWCTask {
    private static Log log = LogFactory.getLog(JobMonitorTask.class);

    private final JobStore jobStore;
    private final TileBreeder seeder;
    private final long updateFrequency;
    private final String purgeJobTaskSchedule;
    private final ConcurrentLinkedQueue<GWCTask> finishedTasks;

    public JobMonitorTask(JobStore js, TileBreeder seeder, long updateFrequency, String purgeJobTaskSchedule) {
        this.jobStore = js;
        this.seeder = seeder;
        this.updateFrequency = updateFrequency;
        this.purgeJobTaskSchedule = purgeJobTaskSchedule;
        this.finishedTasks = new ConcurrentLinkedQueue<GWCTask>();

        super.taskType = GWCTask.TYPE.JOB_MONITOR;
    }

    /**
     * Infrequently wake up and go through the list of running tasks to update the jobs (task groups).
     * @see org.geowebcache.seed.GWCTask#doActionInternal()
     */
    @Override
    protected void doActionInternal() throws GeoWebCacheException, InterruptedException {
        super.state = GWCTask.STATE.RUNNING;

        Thread.currentThread().setPriority(PRIORITY.LOW.getThreadPriority());
        
        int consecutiveFailures = 0;
        
        initCron4J();

        scheduleOldJobPurgeTask();
        
        restartInterruptedTasks();

        while((!Thread.interrupted()) && super.state != GWCTask.STATE.INTERRUPTED) {
            try {
                Iterator<Entry<Long, GWCTask>> tasks = seeder.getRunningTasksIterator();
                
                List<Long> jobIds = new ArrayList<Long>();
                while(tasks.hasNext()) {
                    GWCTask task = tasks.next().getValue();
                    
                    if(!(task instanceof JobMonitorTask)) {
                        if(!jobIds.contains(task.getJobId())) {
                            jobIds.add(task.getJobId());
                            
                            JobObject job = new JobObject();
                            job.setJobId(task.getJobId());
                            if(jobStore.get(job)) {
                                job.update(seeder);
                                jobStore.put(job);
                            }                        
                        }
                    }
                }
                
                // clear out completed tasks
                jobIds = new ArrayList<Long>();
                while(!finishedTasks.isEmpty()) {
                    GWCTask task;
                    synchronized(finishedTasks) {
                        task = finishedTasks.poll();
                    }
                    if(!(task instanceof JobMonitorTask)) {
                        if(!jobIds.contains(task.getJobId())) {
                            jobIds.add(task.getJobId());
                            
                            JobObject job = new JobObject();
                            job.setJobId(task.getJobId());
                            if(jobStore.get(job)) {
                                job.update(task);
                                jobStore.put(job);
                            }                        
                        }
                    }
                }

                consecutiveFailures = 0;
            } catch (Exception e) {
                log.error("During job monitor task: " + e.getClass().getName() + ": " + e.getMessage());
                
                consecutiveFailures++;
                
                if(consecutiveFailures > 5) {
                    log.error("5 consecutive failures in a row, job monitoring will be shut down.");
                    super.state = GWCTask.STATE.DEAD;
                }
            }
            
            Thread.sleep(updateFrequency);
        }

        if (super.state != GWCTask.STATE.DEAD) {
            super.state = GWCTask.STATE.DONE;
            log.debug("Completed job monitoring.");
        }
    }

    /**
     * The job monitor doesn't do anything on abnormal exit - assumes that things are so unstable at
     * this point that trying to save off the latest state of the tasks isn't worth attempting.
     */
    protected void doAbnormalExit(Throwable t) {
        ; 
    }

    /**
     * Ensures all ready jobs in the system are scheduled using Cron4J
     * Called when this task begins.  
     */
    private void initCron4J() {
        Iterator<JobObject> iter;
        try {
            iter = jobStore.getPendingScheduledJobs().iterator();
            while(iter.hasNext()) {
                JobObject job = iter.next();
                JobScheduler.scheduleJob(job, seeder, jobStore);
            }
        } catch (StorageException e) {
            log.error("Job Monitor couldn't initialise Cron4J due to a storage exception.", e);
        }
    }

    /**
     * Schedules a task to daily check for old jobs and delete them.
     */
    private void scheduleOldJobPurgeTask() {
        OldJobPurgeTask ojct = new OldJobPurgeTask(jobStore);
        JobScheduler.getSchedulerInstance().schedule(purgeJobTaskSchedule, ojct);
    }

    /**
     * Checks for interrupted tasks or tasks that the store thought was running 
     * and starts them.
     * Called when this task begins.  
     */
    private void restartInterruptedTasks() {
        try {
            Iterator<JobObject> iter = jobStore.getInterruptedJobs().iterator();
            
            while(iter.hasNext()) {
                JobObject job = iter.next();
                try {
                    job.setState(STATE.INTERRUPTED);
                    job.addLog(JobLogObject.createWarnLog(job.getJobId(), "Job Interruption Detected", "This job was running last time GeoWebCache was running. Changing state to interrupted."));
                    seeder.executeJob(job);
                } catch (GeoWebCacheException e) {
                    log.error("Couldn't restart interrupted job: " + e.getMessage(), e);
                }
            }
        } catch (StorageException e) {
            log.error("Job Monitor couldn't restart interrupted jobs due to a storage exception.", e);
        }
    }

    @Override
    protected void dispose() {
        // do nothing
    }

    public void addFinishedTask(GWCTask task) {
        synchronized(finishedTasks) {
            finishedTasks.add(task);
        }
    }
}
