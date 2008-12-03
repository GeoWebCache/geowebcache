package org.geowebcache.rest;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


public class SeederThreadPoolExecutor extends ThreadPoolExecutor {
    long currentId = 0;
    
    TreeMap<Long,GWCTask> currentPool = new TreeMap<Long,GWCTask>();
    
    public SeederThreadPoolExecutor(int threadNumber, int threadMaxNumber,
            long maxValue, TimeUnit seconds,
            LinkedBlockingQueue<Runnable> workQueue) {
        super(threadNumber, threadMaxNumber, maxValue, seconds, workQueue);
    }

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

    protected void afterExecute(Runnable r, Throwable t) {
        try {
            synchronized (this) {
                GWCTask task = extractGWCTask(r);

                if (task != null) {
                    this.currentPool.remove(task.taskId);
                }
            }
        } finally {
            super.afterExecute(r, t);
        }
    }
    
    /**
     * FutureTask does not provide access to the actual task,
     * so we used reflection to get at it, at least for the time being.
     * 
     *  TODO There's hopefully a pretty way to do this ?
     * 
     * @param fT
     * @return
     */
    private GWCTask extractGWCTask(Runnable r) {
        FutureTask fT = null;
        if(r instanceof FutureTask) {
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
    
    protected boolean terminateGWCTask(long id) {
        GWCTask task = this.currentPool.get(id);
        
        if(task != null && task.type != task.TYPE_TRUNCATE) {
            task.terminateNicely();
            return true;
        } else {
            return false;
        }
    }
    
    protected Iterator<Entry<Long,GWCTask>> getRunningTasksIterator() {
        return this.currentPool.entrySet().iterator();
    }
    
    /**
     * Generates (increments) a unique id to assign to tasks,
     * it's assumed the calling function is synchronized!
     * 
     * @return a unique id for the task
     */
    private long getNextId() {
        long ret = this.currentId;
        this.currentId++;
        return ret;
    }
}
