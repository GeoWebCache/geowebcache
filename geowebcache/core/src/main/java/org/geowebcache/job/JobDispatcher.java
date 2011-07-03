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
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 */
package org.geowebcache.job;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.storage.JobObject;
import org.geowebcache.storage.JobStore;
import org.geowebcache.storage.StorageException;
import org.springframework.beans.factory.DisposableBean;

/**
 * Serves jobs from the jobstore.
 */
public class JobDispatcher implements DisposableBean {

    private static Log log = LogFactory.getLog(org.geowebcache.job.JobDispatcher.class);

    private JobStore jobStore;
    
    public JobDispatcher() {
        reInit();
    }

    /**
     * Returns the job based on the {@code jobId} parameter.
     * 
     * @throws GeoWebCacheException
     *             if no such job exists
     */
    public JobObject getJob(final long jobId) throws GeoWebCacheException {

        JobObject obj = new JobObject();
        obj.setJobId(jobId);
        
        try {
            jobStore.get(obj);
        } catch(StorageException se) {
            throw new GeoWebCacheException("Thread " + Thread.currentThread().getId()
                    + " Unable to get job " + jobId + ". It this job should exist, check the log files.", se);
        }
        
        return obj;
    }

    /***
     * 
     * 
     */
    public void reInit() {
        // TODO JIMG This is called after something would change the list of tasks so they get reinitialised
        // may not affect jobs like layers because jobs are handled by the persistence system
        initialize();
    }

    public long getJobCount() throws GeoWebCacheException {
        try {
            return jobStore.getCount();
        } catch(StorageException se) {
            throw new GeoWebCacheException("Thread " + Thread.currentThread().getId()
                    + " Unable to get job count.", se);
        }
    }

    /**
     * Returns a list of all the jobs.
     * 
     * @return a list view of all jobs in the system
     * @throws GeoWebCacheException 
     */
    public Iterable<JobObject> getJobList() throws GeoWebCacheException {
        try {
            return jobStore.getJobs();
        } catch(StorageException se) {
            throw new GeoWebCacheException("Thread " + Thread.currentThread().getId()
                    + " Unable to get jobs.", se);
        }
    }

    private void initialize() {
        log.debug("initializing job dispatcher...");
        // nothing to do yet
    }

    /**
     * @see org.springframework.beans.factory.DisposableBean#destroy()
     */
    public void destroy() throws Exception {
        jobStore.destroy();
        jobStore = null;
    }

    public boolean remove(final long jobId) throws GeoWebCacheException {
        try {
            return jobStore.delete(jobId);
        } catch(StorageException se) {
            throw new GeoWebCacheException("Thread " + Thread.currentThread().getId()
                    + " Unable to delete job " + jobId + ".", se);
        }
    }

    public void setJobStore(JobStore jobStore) {
        this.jobStore = jobStore;
    }
}
