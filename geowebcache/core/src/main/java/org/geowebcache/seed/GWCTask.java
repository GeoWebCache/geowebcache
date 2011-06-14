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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;

/**
 * 
 */
public abstract class GWCTask {

    private static final Log log = LogFactory.getLog(GWCTask.class);

    public static enum TYPE {
        UNSET, SEED, RESEED, TRUNCATE
    };

    public static enum STATE {
        UNSET, READY, RUNNING, DONE, DEAD
    };

    private static final int PRIORITY_STEP = (java.lang.Thread.NORM_PRIORITY + java.lang.Thread.MIN_PRIORITY) / 2;
    
    public static enum PRIORITY {
        MIN("Lowest", java.lang.Thread.MIN_PRIORITY), 
        LOW("Low", PRIORITY_STEP), 
        NORMAL("Normal", java.lang.Thread.NORM_PRIORITY), 
        HIGH("High", java.lang.Thread.MAX_PRIORITY - PRIORITY_STEP), 
        MAX("Highest", java.lang.Thread.MAX_PRIORITY);
        
        private String readableName;
        private int threadPriority;
        
        private PRIORITY(String readableName, int threadPriority) {
            this.readableName = readableName;
            this.threadPriority = threadPriority;
        }
        
        public String getReadableName() { return readableName; }
        public int getThreadPriority() { return threadPriority; }
        
        public String toString() { return String.format("%02d - %s", threadPriority, readableName); }
    };
    
    public static String NO_SCHEDULE = null;
    public static int NO_THROUGHOUT_RESTRICTIONS = -1;

    /**
     * Value shared between all the threads in the group, is incremented each time a task starts
     * working and decremented each time one task finishes (either normally or abnormally)
     */
    protected AtomicInteger sharedThreadCount = new AtomicInteger();

    protected int threadOffset = 0;

    long taskId = -1;

    protected TYPE parsedType = TYPE.UNSET;

    protected STATE state = STATE.UNSET;

    protected String layerName = null;
    
    protected PRIORITY priority = PRIORITY.LOW;
    
    protected long timeSpent = -1;

    protected long timeRemaining = -1;

    protected long tilesDone = -1;

    protected long tilesTotal = -1;

    protected boolean terminate = false;

    private long groupStartTime;

    /**
     * Marks this task as active in the group by incrementing the shared counter, delegates to
     * {@link #doActionInternal()}, and makes sure to remove this task from the group count.
     */
    public final void doAction() throws GeoWebCacheException, InterruptedException {
        this.sharedThreadCount.incrementAndGet();
        this.groupStartTime = System.currentTimeMillis();
        try {
            doActionInternal();
        } finally {
            dispose();
            int membersRemaining = this.sharedThreadCount.decrementAndGet();
            if (0 == membersRemaining) {
                double groupTotalTimeSecs = (System.currentTimeMillis() - (double) groupStartTime) / 1000;
                log.info("Thread group finished " + parsedType + " task after "
                        + groupTotalTimeSecs + " seconds");
            }
        }
    }

    protected abstract void dispose();

    /**
     * Extension point for subclasses to do what they do
     */
    protected abstract void doActionInternal() throws GeoWebCacheException, InterruptedException;

    /**
     * @param sharedThreadCount
     *            a counter of number of active tasks in the task group, incremented when this task
     *            starts working and decremented when it stops
     * @param threadOffset
     *            REVISIT: may not be needed anymore. Just check if sharedThreadCount == 1?
     */
    public void setThreadInfo(AtomicInteger sharedThreadCount, int threadOffset) {
        this.sharedThreadCount = sharedThreadCount;
        this.threadOffset = threadOffset;
    }

    public void setTaskId(long taskId) {
        this.taskId = taskId;
    }

    public long getTaskId() {
        return taskId;
    }

    public int getThreadCount() {
        return sharedThreadCount.get();
    }

    public int getThreadOffset() {
        return threadOffset;
    }

    public String getLayerName() {
        return layerName;
    }

    /**
     * @return total number of tiles (in the whole task group), or < 0 if too many to count
     */
    public long getTilesTotal() {
        return tilesTotal;
    }

    public long getTilesDone() {
        return tilesDone;
    }

    /**
     * @return estimated remaining time in seconds, or {@code -2} if unknown
     */
    public long getTimeRemaining() {
        if (tilesTotal > 0) {
            return timeRemaining;
        } else {
            return -2;
        }
    }

    /**
     * @return task time spent in seconds
     */
    public long getTimeSpent() {
        return timeSpent;
    }

    public void terminateNicely() {
        this.terminate = true;
    }

    public TYPE getType() {
        return parsedType;
    }

    /**
     * Controls the priority of this GWCTask. Default is GWCTask.PRIORITY.LOW
     * 
     * @return Priority of this task as a PRIORITY enum value.
     */
    public GWCTask.PRIORITY getPriority() {
        return priority;
    }

    public STATE getState() {
        return state;
    }
    protected void checkInterrupted() throws InterruptedException {
        if (Thread.interrupted()) {
            this.state = STATE.DEAD;
            throw new InterruptedException();
        }
    }
}
