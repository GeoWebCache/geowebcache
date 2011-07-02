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

import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.SRS;
import org.geowebcache.seed.GWCTask;
import org.geowebcache.seed.GWCTask.PRIORITY;
import org.geowebcache.seed.GWCTask.STATE;
import org.geowebcache.seed.GWCTask.TYPE;

/**
 * Represents a geowebcache task. Tasks may be complete, running, or not started yet.
 */
public class TaskObject extends StorageObject {

    public static final String OBJECT_TYPE = "task";

    private long id = -1l;
    private String layerName = null;
    private STATE state = STATE.UNSET;
    private long timeSpent = -1l;
    private long timeRemaining = -1l;
    private long tilesDone = -1l;
    private long tilesTotal = -1l;

    private BoundingBox bounds = null;
    private String gridSetId = null;
    private SRS srs = null;
    private int threadCount = -1;
    private int zoomStart = -1;
    private int zoomStop = -1;
    private String format = null;
    private TYPE taskType = TYPE.UNSET;
    private int maxThroughput = -1;
    private PRIORITY priority = PRIORITY.LOW;
    private String schedule = null;
    
    private Timestamp timeFirstStart = null;
    private Timestamp timeLatestStart = null;
    

    public static TaskObject createTaskObject(GWCTask task) {
        TaskObject obj = new TaskObject();

        //TODO missing some fields... revisit when GWCTask and SeedRequest are reconciled 

        // obj.taskId = task.getTaskId();

        obj.layerName = task.getLayerName();
        obj.state = task.getState();
        obj.timeSpent = task.getTimeSpent();
        obj.timeRemaining = task.getTimeRemaining();
        obj.tilesDone = task.getTilesDone();
        obj.tilesTotal = task.getTilesTotal();

        // BoundingBox bounds = null;
        // String gridSetId task.;
        // SRS srs;
        obj.threadCount = task.getThreadCount();
        // obj.zoomStart = null;
        // obj.zoomStop = null;
        // obj.format = null;
        obj.taskType = task.getType();
        // obj.parameters = null;
        // obj.maxThroughput = -1;
        obj.priority = task.getPriority();
        // obj.schedule = null;
        
        return obj;
    }


    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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

    public TYPE getTaskType() {
        return taskType;
    }

    public void setTaskType(TYPE taskType) {
        this.taskType = taskType;
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
        return "[" + id + "," + taskType + ":" + "," + layerName + "," + gridSetId + "]";
    }
}
