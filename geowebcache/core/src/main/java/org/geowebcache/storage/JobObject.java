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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
import org.geowebcache.storage.JobLogObject.LOG_LEVEL;
import org.geowebcache.util.StringUtils;

/**
 * Represents a geowebcache job - which is a collection of tasks (threads). Jobs may be complete, running, or not started yet.
 */
public class JobObject {

    private static Log log = LogFactory.getLog(JobObject.class);

    public static final String OBJECT_TYPE = "job";

    public static String NO_SCHEDULE = null;
    
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
    private float throughput = 0;
    private int maxThroughput = -1;
    
    private PRIORITY priority = PRIORITY.LOW;
    private String schedule = NO_SCHEDULE;
    private long spawnedBy = -1l;
    private boolean runOnce = false;
    private boolean filterUpdate = false;
    private String encodedParameters = null;
    
    private Timestamp timeFirstStart = null;
    private Timestamp timeLatestStart = null;
    
    private long warnCount = 0;
    private long errorCount = 0;
    
    private ConcurrentLinkedQueue<JobLogObject> newLogs = new ConcurrentLinkedQueue<JobLogObject>(); 

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
        obj.runOnce = sr.isRunOnce();
        
        obj.filterUpdate = sr.getFilterUpdate();
        obj.setParameters(sr.getParameters());
        
        // finally, change the state to ready
        obj.state = STATE.READY;
        
        return obj;
    }
    
    /**
     * Copies the configuration of an existing job to make a new one.
     * Because this isn't a deep clone operation not all items are copied.
     * Essentially the copied job is in a state ready to be executed. 
     * @param job
     * @return
     */
    public static JobObject createJobObject(JobObject job) {
        JobObject obj = new JobObject();
        
        // obj.jobId = job.jobId;
        obj.layerName = job.layerName;
        // obj.state = job.state;
        // obj.timeSpent = job.timeSpent;
        // obj.timeRemaining = job.timeRemaining;
        // obj.tilesDone = job.tilesDone;
        // obj.tilesTotal = job.tilesTotal;
        // obj.failedTileCount = job.failedTileCount;

        obj.bounds = job.bounds;
        obj.gridSetId = job.gridSetId;
        obj.srs = job.srs;
        obj.threadCount = job.threadCount;
        obj.zoomStart = job.zoomStart;
        obj.zoomStop = job.zoomStop;
        obj.format = job.format;
        obj.jobType = job.jobType;
        // obj.throughput = job.throughput;
        obj.maxThroughput = job.maxThroughput;
        
        obj.priority = job.priority;
        obj.schedule = job.schedule;
        obj.runOnce = job.runOnce;
        obj.filterUpdate = job.filterUpdate;
        obj.encodedParameters = job.encodedParameters;
        
        // obj.timeFirstStart = job.timeFirstStart;
        // obj.timeLatestStart = job.timeLatestStart;
        
        // obj.warnCount = job.warnCount;
        // obj.errorCount = job.errorCount;
        
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

                    if(task instanceof SeedTask) {
                        throughput += ((SeedTask)task).getThroughput();
                    }
                    
                    addLogs(task.getNewLogs());
                } else {
                    foundTask = true;
                    timeSpent = task.getTimeSpent();
                    timeRemaining = task.getTimeRemaining();
                    tilesDone = task.getTilesDone();
                    tilesTotal = task.getTilesTotal();

                    state = task.getState();
                    
                    addLogs(task.getNewLogs());

                    if(task instanceof SeedTask) {
                        failedTileCount = ((SeedTask)task).getSharedFailureCounter();
                        throughput = ((SeedTask)task).getThroughput();
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
            addLogs(task.getNewLogs());
                    
            if(task instanceof SeedTask && task.getState() == STATE.DONE) {
                failedTileCount = ((SeedTask)task).getSharedFailureCounter();
                
                // make the assumption that total tiles - failed tiles = done tiles
                tilesDone = tilesTotal - failedTileCount;
            }

            if (task.getState() == STATE.DONE) {
                newLogs.add(JobLogObject.createInfoLog(jobId, "Job Completed", "Job finished with a status of DONE."));
            } else if (task.getState() == STATE.DEAD) { 
                newLogs.add(JobLogObject.createInfoLog(jobId, "Job Dead", "Job finished with a status of DEAD. This means the job did not complete successfully and due to problems during execution should not be reattempted."));
            } else if (task.getState() == STATE.KILLED) { 
                newLogs.add(JobLogObject.createInfoLog(jobId, "Job Killed", "Job finished with a status of KILLED. This usually means a user has intentionally stopped the job."));
            } else if (task.getState() == STATE.INTERRUPTED) { 
                newLogs.add(JobLogObject.createInfoLog(jobId, "Job Completed", "Job finished with a status of INTERRUPTED. This usually means the GeoWebCache was forced to shut down while jobs were running. Jobs may be restarted automatically when GeoWebCache restarts."));
            }
        }
    }

    /**
     * Log an error, warning or info for this job.
     * All new warnings and errors for a job should come through this call to be persisted in a thread safe manner and keep error and warning counts accurate. 
     * @param joblog The job to log. This call ensures the log is associate to this job.
     */
    protected void log(JobLogObject joblog) {
        joblog.setJobId(jobId);
        if(joblog.getLogLevel() == LOG_LEVEL.ERROR) {
            errorCount++;
        } else if(joblog.getLogLevel() == LOG_LEVEL.WARN) {
            warnCount++;
        }
        synchronized(newLogs) { 
            newLogs.add(joblog);
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
        
        // an empty string is interpreted as no schedule, make sure it's set properly
        if(this.schedule != null && this.schedule.equals("")) {
            this.schedule = NO_SCHEDULE;
        }
    }

    public boolean isRunOnce() {
        return runOnce;
    }

    public void setRunOnce(boolean runOnce) {
        this.runOnce = runOnce;
    }

    public boolean isFilterUpdate() {
        return filterUpdate;
    }

    public void setFilterUpdate(boolean filterUpdate) {
        this.filterUpdate = filterUpdate;
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

    public String getEncodedParameters() {
        return encodedParameters;
    }

    public void setEncodedParameters(String encodedParameters) {
        this.encodedParameters = encodedParameters;
    }
    
    public float getThroughput() {
        return throughput;
    }

    public void setThroughput(float throughput) {
        this.throughput = throughput;
    }

    /**
     * Gets parameters as a map of key/value pairs.
     * Internally the parameters are stored as a single URL encoded querystring.
     * Getters and setters for parameters handle the conversion.
     * Because a map is returned, multiple parameters with the same name isn't supported. 
     * @return
     */
    public Map<String, String> getParameters() {
        Map<String, String> map = new HashMap<String, String>();
        
        if(encodedParameters != null && encodedParameters != "") {
            try {
                for (String param : encodedParameters.split("&")) {
                    String pair[] = param.split("=");
                    String key = URLDecoder.decode(pair[0], "UTF-8");
                    String value = URLDecoder.decode(pair[1], "UTF-8");
                    map.put(key, value);
                }
            }catch (UnsupportedEncodingException e) {
                log.warn("Couldn't interpret parameters, they won't be used. Parameters were:\n" + encodedParameters, e);
            }
        }        
        
        return(map);
    }
    
    public void setParameters(Map<String, String> map) {
        if(map == null) {
            encodedParameters = null;
        } else {
            try {
                List<String> list = new ArrayList<String>();
                for (Entry<String, String>entry : map.entrySet()) {
                    String key = URLEncoder.encode(entry.getKey(), "UTF-8");
                    String value = URLEncoder.encode(entry.getValue(), "UTF-8");
                    
                    list.add(key + "=" + value);
                }
                encodedParameters = StringUtils.join(list, "&"); 
                
            } catch (UnsupportedEncodingException e) {
                log.warn("Couldn't encode parameters, they won't be used. Parameters were:\n" + map.toString(), e);
            }
        }
    }

    public long getWarnCount() {
        return warnCount;
    }

    public void setWarnCount(long warnCount) {
        this.warnCount = warnCount;
    }

    public long getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(long errorCount) {
        this.errorCount = errorCount;
    }
    
    public long getSpawnedBy() {
        return spawnedBy;
    }

    public void setSpawnedBy(long spawnedBy) {
        this.spawnedBy = spawnedBy;
    }

    public ConcurrentLinkedQueue<JobLogObject> getNewLogs() {
        return newLogs;
    }

    public void addLog(JobLogObject joblog) {
        synchronized(newLogs) {
            newLogs.add(joblog);
        }
    }

    private void addLogs(ConcurrentLinkedQueue<JobLogObject> logs) {
        JobLogObject joblog;
        while(!logs.isEmpty()) {
            synchronized(logs) {
                joblog = logs.poll();
            }
            synchronized(newLogs) {
                newLogs.add(joblog);
            }
        }
    }
    
    public String getType() {
        return OBJECT_TYPE;
    }
    public String toString() {
        return "[" + jobId + "," + jobType + ":" + "," + layerName + "," + gridSetId + "]";
    }

    public boolean isScheduled() {
        return (schedule != NO_SCHEDULE);
    }
    
    /**
     * Deal with ensuring a deserialized job is in a valid state.
     * Between extJS and XStream, some of the JSON isn't converted properly. 
     * This method makes sure the job is in a state the system would expect it to be in.
     * It's mainly stuff that was null turning into stuff that is an empty string - and it's extJS doing it.
     * @return
     */
    private Object readResolve() {
        if(this.schedule != null && this.schedule.equals("")) {
            this.schedule = null;
        }

        if(this.encodedParameters != null && this.encodedParameters.equals("")) {
            this.encodedParameters = null;
        }

        newLogs = new ConcurrentLinkedQueue<JobLogObject>();
        
        return this;
    }
}
