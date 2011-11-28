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
 * @author Marius Suta / The Open Planning Project 2008
 * @author Arne Kepp / The Open Planning Project 2009 
 */
package org.geowebcache.seed;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.SRS;
import org.geowebcache.seed.GWCTask.PRIORITY;
import org.geowebcache.seed.GWCTask.TYPE;

public class SeedRequest {
    private static Log log = LogFactory.getLog(org.geowebcache.seed.SeedRequest.class);

    private String name = null;

    private BoundingBox bounds = null;

    private String gridSetId;

    private SRS srs;

    private Integer threadCount = null;

    private Integer zoomStart = null;

    private Integer zoomStop = null;

    private String format = null;

    private String type = null;

    private TYPE enumType = null;

    private Map<String, String> parameters = null;

    private Boolean filterUpdate = null;

    protected int maxThroughput = -1;

    protected PRIORITY priority = PRIORITY.LOW;

    protected String schedule = null;
    
    protected boolean runOnce = false;

    public SeedRequest() {
        // do nothing, i guess
        System.out.println("New SeedRequest");
    }

    /**
     * Used by SeedPageResource
     * 
     * @param name
     * @param bounds
     * @param srs
     * @param threadCount
     * @param zoomStart
     * @param zoomStop
     * @param format
     * @param type
     */
    public SeedRequest(String name, BoundingBox bounds, String gridSetId, int threadCount,
            int zoomStart, int zoomStop, String format, GWCTask.TYPE type,
            PRIORITY priority, String schedule, boolean runOnce, int maxThroughput,
            Map<String, String> parameters) {
        this.name = name;
        this.bounds = bounds;
        this.gridSetId = gridSetId;
        this.threadCount = threadCount;
        this.zoomStart = zoomStart;
        this.zoomStop = zoomStop;
        this.format = format;
        this.enumType = type;
        this.priority = priority;
        this.schedule = schedule;
        this.runOnce = runOnce;
        this.maxThroughput = maxThroughput;
        this.parameters = parameters;
    }

    /**
     * Method returns the name of the tileLayer that was requested
     * 
     * @return name of the requested tile layer
     */
    public String getLayerName() {
        return this.name;
    }

    /**
     * Method gets the bounds for the requested region
     * 
     * @return a BBOX
     */
    public BoundingBox getBounds() {
        return this.bounds;
    }

    /**
     * Whether any request filters should be updated after this seed request completes.
     * 
     * @return
     */
    public boolean getFilterUpdate() {
        if (filterUpdate != null) {
            return filterUpdate;
        } else {
            return false;
        }
    }

    /**
     * Method returns the grid set id for this request
     * 
     * @return String
     */
    public String getGridSetId() {
        return this.gridSetId;
    }

    /**
     * Method returns the format requested
     * 
     * @return the format in String form
     */
    public String getMimeFormat() {
        return this.format;
    }

    /**
     * Used to handle 1.1.x-style seed requests
     * 
     * @return
     */
    public SRS getSRS() {
        return this.srs;
    }

    /**
     * Method returns the zoom start level for this seed request
     * 
     * @return integer representing zoom start level
     */
    public Integer getZoomStart() {
        return this.zoomStart;
    }

    /**
     * Method returns the zoom stop level for this seed request
     * 
     * @return integer representing zoom stop level
     */
    public Integer getZoomStop() {
        return this.zoomStop;
    }

    /**
     * Method returns the number of threads that should be used for this seed request
     * 
     * @return integer representing number of threads
     */
    public Integer getThreadCount() {
        return threadCount;
    }

    /**
     * Method returns the type of seed, namely one of
     * <ul>
     * <li>seed - (default) seeds new tiles</li>
     * <li>reseed - seeds new tiles and replaces old ones</li>
     * <li>truncate - removes tiles</li>
     * </ul>
     * 
     * @return type of seed
     */
    public TYPE getType() {
        if (enumType == null) {
            if (type == null || type.equalsIgnoreCase("seed")) {
                return TYPE.SEED;
            } else if (type.equalsIgnoreCase("reseed")) {
                return TYPE.RESEED;
            } else if (type.equalsIgnoreCase("truncate")) {
                return TYPE.TRUNCATE;
            } else {
                log.warn("Unknown type \"" + type + "\", assuming seed");
                return TYPE.SEED;
            }
        }

        return enumType;
    }

    /**
     * Schedule information for this task in the form of a CRON string. Tasks can be scheduled and
     * will not execute immediately.
     * 
     * @return Valid CRON string or null for no schedule.
     */
    public String getSchedule() {
        return schedule;
    }
    
    /**
     * If true, the task will only run once the next time the specified CRON would fire.
     * 
     * @return 
     */
    public boolean isRunOnce() {
        return runOnce;
    }

    /**
     * Maximum number of tiles per second allowed to seed / truncate. May not be honoured for
     * truncation tasks.
     * 
     * @return Throughput throttling for tile seeding / truncating. -1 means no throughput
     *         throttling.
     */
    public int getMaxThroughput() {
        return maxThroughput;
    }

    /**
     * Controls the priority of threads. Default is PRIORITY.LOW
     * 
     * @return Priority of this request as a PRIORITY enum value.
     */
    public PRIORITY getPriority() {
        return priority;
    }

    /**
     * The settings for the modifiable parameters
     * 
     * @return the modifiable parameters
     */
    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setLayerName(String name) {
        this.name = name;        
    }
}
