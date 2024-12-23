/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Arne Kepp / The Open Planning Project 2008
 */
package org.geowebcache.seed;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;

/** */
public abstract class GWCTask {

    private static final Logger log = Logging.getLogger(GWCTask.class.getName());

    public static enum TYPE {
        UNSET,
        SEED,
        RESEED,
        TRUNCATE
    }

    public static enum STATE {
        UNSET,
        READY,
        RUNNING,
        DONE,
        DEAD
    }

    /**
     * Value shared between all the threads in the group, is incremented each time a task starts working and decremented
     * each time one task finishes (either normally or abnormally)
     */
    protected AtomicInteger sharedThreadCount = new AtomicInteger();

    protected int threadOffset = 0;

    long taskId = -1;

    protected TYPE parsedType = TYPE.UNSET;

    protected STATE state = STATE.UNSET;

    protected String layerName = null;

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
                log.info("Thread group finished " + parsedType + " task after " + groupTotalTimeSecs + " seconds");
            }
        }
    }

    protected abstract void dispose();

    /** Extension point for subclasses to do what they do */
    protected abstract void doActionInternal() throws GeoWebCacheException, InterruptedException;

    /**
     * @param sharedThreadCount a counter of number of active tasks in the task group, incremented when this task starts
     *     working and decremented when it stops
     * @param threadOffset REVISIT: may not be needed anymore. Just check if sharedThreadCount == 1?
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

    /** @return total number of tiles (in the whole task group), or < 0 if too many to count */
    public long getTilesTotal() {
        return tilesTotal;
    }

    public long getTilesDone() {
        return tilesDone;
    }

    /** @return estimated remaining time in seconds, or {@code -2} if unknown */
    public long getTimeRemaining() {
        if (tilesTotal > 0) {
            return timeRemaining;
        } else {
            return -2;
        }
    }

    /** @return task time spent in seconds */
    public long getTimeSpent() {
        return timeSpent;
    }

    public void terminateNicely() {
        this.terminate = true;
    }

    public TYPE getType() {
        return parsedType;
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

    @Override
    public String toString() {
        return new StringBuilder("[")
                .append(getTaskId())
                .append(": ")
                .append(getLayerName())
                .append(", ")
                .append(getType())
                .append(", ")
                .append(getState())
                .append("]")
                .toString();
    }
}
