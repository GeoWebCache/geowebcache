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

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.seed.SeedRequest;
import org.geowebcache.seed.TileBreeder;
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
    
    public void update(TileBreeder tb) {
        // TODO rationalise state from the running tasks

//        state = task.getState();
//        timeSpent = task.getTimeSpent();
//        timeRemaining = task.getTimeRemaining();
//        tilesDone = task.getTilesDone();
//        tilesTotal = task.getTilesTotal();
//          failedTilesCount = task.getFailedTilesCount();
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
