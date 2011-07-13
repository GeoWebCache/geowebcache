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

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

public class SeederThreadPoolExecutor extends ThreadPoolExecutor implements DisposableBean {

    private static final Log log = LogFactory.getLog(SeederThreadPoolExecutor.class);

    private static final ThreadFactory tf = new CustomizableThreadFactory("GWC Seeder Thread-");

    long currentId = 0;

    TreeMap<Long, GWCTask> currentPool = new TreeMap<Long, GWCTask>();

    public SeederThreadPoolExecutor(int corePoolSize, int maxPoolSize) {
        super(corePoolSize, maxPoolSize, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(),
                tf);
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);

        synchronized (this) {
            GWCTask task = extractGWCTask(r);

            if (task != null) {
                Long taskId = this.getNextId();
                task.setTaskId(taskId);
                this.currentPool.put(taskId, task);
            }
        }
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        try {
            synchronized (this) {
                GWCTask task = extractGWCTask(r);

                if (task != null) {
                    this.currentPool.remove(task.getTaskId());
                }
                
                checkJobStatus(task);
            }
        } finally {
            super.afterExecute(r, t);
        }
    }

    /** 
     * Called as a task finished execution.
     * Add job to the queue of completed tasks for the monitor to take a look at.
     * @param task
     */
    private void checkJobStatus(GWCTask task) {
        
        JobMonitorTask jmt = getJobMonitorTask();
        
        if(jmt != null) {
            jmt.addFinishedTask(task);
        }
    }

    /**
     * FutureTask does not provide access to the actual task, so we used reflection to get at it, at
     * least for the time being.
     * 
     * TODO There's hopefully a pretty way to do this ?
     * 
     * @param fT
     * @return
     */
    private GWCTask extractGWCTask(Runnable r) {
        FutureTask fT = null;
        if (r instanceof FutureTask) {
            fT = (FutureTask) r;
        } else {
            return null;
        }

        GWCTask task = null;

        Class<?> c = fT.getClass();
        try {
            Field sync = c.getDeclaredField("sync");
            sync.setAccessible(true);
            Object obj = sync.get(fT);
            c = obj.getClass();
            Field callable = c.getDeclaredField("callable");
            callable.setAccessible(true);
            MTSeeder mts = (MTSeeder) callable.get(obj);
            task = mts.task;
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        return task;
    }

    public boolean terminateGWCTask(long id) {
        GWCTask task = this.currentPool.get(id);

        if (task != null && GWCTask.TYPE.TRUNCATE != task.getType()) {
            task.terminateNicely();
            return true;
        } else {
            return false;
        }
    }

    public Iterator<Entry<Long, GWCTask>> getRunningTasksIterator() {
        return this.currentPool.entrySet().iterator();
    }

    private JobMonitorTask getJobMonitorTask() {
        Iterator<Entry<Long, GWCTask>> iter = this.currentPool.entrySet().iterator();
        
        while(iter.hasNext()) {
            GWCTask task = iter.next().getValue();
            if(task instanceof JobMonitorTask) {
                return (JobMonitorTask)task;
            }
        }
        
        return null;
    }

    /**
     * Generates (increments) a unique id to assign to tasks, it's assumed the calling function is
     * synchronized!
     * 
     * @return a unique id for the task
     */
    private long getNextId() {
        long ret = this.currentId;
        this.currentId++;
        return ret;
    }

    /**
     * Destroy method called by the application context at shutdown, needed to gracefully shutdown
     * this thread pool executor and any running thread
     * 
     * @see org.springframework.beans.factory.DisposableBean#destroy()
     */
    public void destroy() throws Exception {
        log.info("Initiating shut down for running and pending seed tasks...");
        this.shutdownNow();
        while (!this.isTerminated()) {
            log.debug("Waiting for pending tasks to terminate....");
            Thread.sleep(500);
        }
        log.info("Seeder thread pool executor shut down complete.");
    }
}
