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
 * @author Arne Kepp / The Open Planning Project 2009
 *  
 */
package org.geowebcache.storage;

import java.sql.Timestamp;
import java.util.Iterator;
import java.util.Map.Entry;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.seed.GWCTask;
import org.geowebcache.seed.SeedRequest;
import org.geowebcache.seed.SeedTask;
import org.geowebcache.seed.TileBreeder;
import org.geowebcache.seed.TruncateTask;
import org.geowebcache.seed.GWCTask.PRIORITY;
import org.geowebcache.seed.GWCTask.STATE;
import org.geowebcache.seed.GWCTask.TYPE;

/**
 * Represents a geowebcache job - which is a collection of tasks (threads). Jobs may be complete, running, or not started yet.
 */
public class JobObject extends StorageObject {

    public static final String OBJECT_TYPE = "job";

    private long jobId = -1l;
    private String layerName = null;
    private STATE state = STATE.UNSET;
    private long timeSpent = -1;
    private long timeRemaining = -1;
    private long tilesDone = -1;
    private long tilesTotal = -1;
    private long failedTileCount = 0;

    private BoundingBox bounds = null;
    private String gridSetId = null;
    private SRS srs = null;
    private int threadCount = -1;
    private int zoomStart = -1;
    private int zoomStop = -1;
    private String format = null;
    private TYPE jobType = TYPE.UNSET;
    private int maxThroughput = -1;
    private PRIORITY priority = PRIORITY.LOW;
    private String schedule = null;
    
    private Timestamp timeFirstStart = null;
    private Timestamp timeLatestStart = null;
    

    public static JobObject createJobObject(TileLayer tl, SeedRequest sr) throws GeoWebCacheException {
        JobObject obj = new JobObject();

        obj.layerName = sr.getLayerName();

        obj.gridSetId = sr.getGridSetId();

        // TODO: tilebreeder and here should both be determining default gridset the same way - by asking the TileLayer what it should be.
        // There is an implementation there... check it's OK to use...
        if (obj.gridSetId == null) {
            obj.gridSetId = tl.getGridSubsetForSRS(sr.getSRS()).getName();
        }
        if (obj.gridSetId == null) {
            obj.gridSetId = tl.getGridSubsets().entrySet().iterator().next().getKey();
        }

        GridSubset gridSubset = tl.getGridSubset(obj.gridSetId);

        if (gridSubset == null) {
            throw new GeoWebCacheException("Unknown grid set " + obj.gridSetId);
        }

        if(sr.getBounds() == null) {
            obj.bounds = new BoundingBox(gridSubset.getGridSetBounds());
        } else {
            obj.bounds = new BoundingBox(sr.getBounds());
        }
        
        if(sr.getSRS() == null) {
            obj.srs = gridSubset.getSRS();
        } else {
            obj.srs = sr.getSRS();
        }

        obj.threadCount = sr.getThreadCount();

        obj.zoomStart = sr.getZoomStart();
        obj.zoomStop = sr.getZoomStop();
        obj.format = sr.getMimeFormat();
        
        obj.jobType = sr.getType();

        obj.maxThroughput = sr.getMaxThroughput();
        obj.priority = sr.getPriority();
        obj.schedule = sr.getSchedule();
        
        return obj;
    }
    
    /**
     * Update a job based on all running tasks currently active in the system.
     * @param tb
     */
    public void update(TileBreeder tb) {
        // rationalise state from the running tasks
        Iterator<Entry<Long, GWCTask>> iter = tb.getRunningTasksIterator();
        
        boolean foundTask = false;
        boolean isRunning = false;

        /*
         * A potential concern: if we are locked on the sharedfailurecounter and the 
         * running tasks list needs to change, there will be an exception because this 
         * is iterating a list that another wants to change.
         * 
         * This method needs to only be called by the thread that has the power to 
         * change the running tasks list, or we'll need to gate access to the list as 
         * a whole.
         */
        while(iter.hasNext()) {
            GWCTask task = iter.next().getValue();
            
            if(task.getJobId() == jobId) {
                if(foundTask) {
                    tilesDone += task.getTilesDone();
                } else {
                    foundTask = true;
                    timeSpent = task.getTimeSpent();
                    timeRemaining = task.getTimeRemaining();
                    tilesDone = task.getTilesDone();
                    tilesTotal = task.getTilesTotal();

                    state = task.getState();
                    
                    if(task instanceof SeedTask) {
                        failedTileCount = ((SeedTask)task).getSharedFailureCounter();
                        isRunning = isRunning || task.getState() == STATE.RUNNING; // if any task is running, the job is running
                    } else if(task instanceof TruncateTask) {
                        ;
                    }
                }
            }
        }
        
        if(isRunning) {
            state = STATE.RUNNING;
        }
    }

    /**
     * Update the job to match the details of this task.
     * One task can be used to garner some information about the job, but not all.
     * @param task
     */
    public void update(GWCTask task) throws GeoWebCacheException {
        // rationalise state from the running tasks
        
        if(task.getJobId() != jobId) {
            throw new GeoWebCacheException("Job ID Mismatch - can't update. Job " + jobId + " expected but a task for Job " + task.getJobId() + " was provided.");
        } else {
            timeSpent = task.getTimeSpent();
            timeRemaining = 0;

            state = task.getState();
                    
            if(task instanceof SeedTask) {
                failedTileCount = ((SeedTask)task).getSharedFailureCounter();
                
                // make the assumption that total tiles - failed tiles = done tiles
                tilesDone = tilesTotal - failedTileCount;
            }
        }
    }

    public long getJobId() {
        return jobId;
    }

    public void setJobId(long jobId) {
        this.jobId = jobId;
    }

    public String getLayerName() {
        return layerName;
    }

    public void setLayerName(String layerName) {
        this.layerName = layerName;
    }

    public STATE getState() {
        return state;
    }

    public void setState(STATE state) {
        this.state = state;
    }

    public long getTimeSpent() {
        return timeSpent;
    }

    public void setTimeSpent(long timeSpent) {
        this.timeSpent = timeSpent;
    }

    public long getTimeRemaining() {
        return timeRemaining;
    }

    public void setTimeRemaining(long timeRemaining) {
        this.timeRemaining = timeRemaining;
    }

    public long getTilesDone() {
        return tilesDone;
    }

    public void setTilesDone(long tilesDone) {
        this.tilesDone = tilesDone;
    }

    public long getTilesTotal() {
        return tilesTotal;
    }

    public void setTilesTotal(long tilesTotal) {
        this.tilesTotal = tilesTotal;
    }

    public long getFailedTileCount() {
        return failedTileCount;
    }

    public void setFailedTileCount(long failedTileCount) {
        this.failedTileCount = failedTileCount;
    }

    public BoundingBox getBounds() {
        return bounds;
    }

    public void setBounds(BoundingBox bounds) {
        this.bounds = bounds;
    }

    public String getGridSetId() {
        return gridSetId;
    }

    public void setGridSetId(String gridSetId) {
        this.gridSetId = gridSetId;
    }

    public SRS getSrs() {
        return srs;
    }

    public void setSrs(SRS srs) {
        this.srs = srs;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    public int getZoomStart() {
        return zoomStart;
    }

    public void setZoomStart(int zoomStart) {
        this.zoomStart = zoomStart;
    }

    public int getZoomStop() {
        return zoomStop;
    }

    public void setZoomStop(int zoomStop) {
        this.zoomStop = zoomStop;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public TYPE getJobType() {
        return jobType;
    }

    public void setJobType(TYPE jobType) {
        this.jobType = jobType;
    }

    public int getMaxThroughput() {
        return maxThroughput;
    }

    public void setMaxThroughput(int maxThroughput) {
        this.maxThroughput = maxThroughput;
    }

    public PRIORITY getPriority() {
        return priority;
    }

    public void setPriority(PRIORITY priority) {
        this.priority = priority;
    }

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }

    public Timestamp getTimeFirstStart() {
        return timeFirstStart;
    }

    public void setTimeFirstStart(Timestamp timeFirstStart) {
        this.timeFirstStart = timeFirstStart;
    }

    public Timestamp getTimeLatestStart() {
        return timeLatestStart;
    }

    public void setTimeLatestStart(Timestamp timeLatestStart) {
        this.timeLatestStart = timeLatestStart;
    }

    public String getType() {
        return OBJECT_TYPE;
    }
    public String toString() {
        return "[" + jobId + "," + jobType + ":" + "," + layerName + "," + gridSetId + "]";
    }
}
