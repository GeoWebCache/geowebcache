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
 * @author Marius Suta / The Open Planning Project 2008
 * @author Arne Kepp / The Open Planning Project 2009
 */
package org.geowebcache.seed;

import java.util.Map;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.SRS;
import org.geowebcache.seed.GWCTask.TYPE;

/** Stores the information for a Seed Request. */
public class SeedRequest {
    private static Logger log = Logging.getLogger(SeedRequest.class.getName());

    private String name = null;

    private BoundingBox bounds = null;

    private String gridSetId;

    private SRS srs;

    private Integer threadCount = null;

    private Integer zoomStart = null;

    private Integer zoomStop = null;

    private String format = null;

    private String type = null; //  TODO: This appears to do nothing as it is never changed from being null

    private TYPE enumType = null;

    private Map<String, String> parameters = null;

    private Boolean filterUpdate = null;

    private int tileFailureRetryCount = TileBreeder.TILE_FAILURE_RETRY_COUNT_DEFAULT;

    private long tileFailureRetryWaitTime = TileBreeder.TILE_FAILURE_RETRY_WAIT_TIME_DEFAULT;

    private long totalFailuresBeforeAborting = TileBreeder.TOTAL_FAILURES_BEFORE_ABORTING_DEFAULT;

    public SeedRequest() {
        // do nothing, i guess
    }

    /**
     * Create a new SeedRequest
     *
     * <p>Used by SeedPageResource
     *
     * @param layerName name of the tile layer
     * @param bounds bounds for the requested region
     * @param gridSetId the grid set id for this request
     * @param threadCount the number of threads that should be used for this seed request
     * @param zoomStart the zoom start level for this seed request
     * @param zoomStop the zoom stop level for this seed request
     * @param mimeFormat the MIME format requested
     */
    public SeedRequest(
            String layerName,
            BoundingBox bounds,
            String gridSetId,
            int threadCount,
            int zoomStart,
            int zoomStop,
            String mimeFormat,
            GWCTask.TYPE type,
            Map<String, String> parameters) {
        this.name = layerName;
        this.bounds = bounds;
        this.gridSetId = gridSetId;
        this.threadCount = threadCount;
        this.zoomStart = zoomStart;
        this.zoomStop = zoomStop;
        this.format = mimeFormat;
        this.enumType = type;
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

    /** Whether any request filters should be updated after this seed request completes. */
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
     * Method returns the MIME format requested
     *
     * @return the format in String form
     */
    public String getMimeFormat() {
        return this.format;
    }

    /** Used to handle 1.1.x-style seed requests */
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
     *
     * <ul>
     *   <li>seed - (default) seeds new tiles
     *   <li>reseed - seeds new tiles and replaces old ones
     *   <li>truncate - removes tiles
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
                log.warning("Unknown type \"" + type + "\", assuming seed");
                return TYPE.SEED;
            }
        }

        return enumType;
    }

    /**
     * The settings for the modifiable parameters
     *
     * @return the modifiable parameters
     */
    public Map<String, String> getParameters() {
        return parameters;
    }

    /**
     * Number of retries to build a tile before giving up on it. -1 disables also the wait and total failures counters.
     */
    public int getTileFailureRetryCount() {
        return tileFailureRetryCount;
    }

    public void setTileFailureRetryCount(int tileFailureRetryCount) {
        this.tileFailureRetryCount = tileFailureRetryCount;
    }

    /** Time to wait between tile computation failures, in milliseconds */
    public long getTileFailureRetryWaitTime() {
        return tileFailureRetryWaitTime;
    }

    public void setTileFailureRetryWaitTime(long tileFailureRetryWaitTime) {
        this.tileFailureRetryWaitTime = tileFailureRetryWaitTime;
    }

    /**
     * Total amount of failures before stopping the seeding process, computed across all threads in the seed request.
     */
    public long getTotalFailuresBeforeAborting() {
        return totalFailuresBeforeAborting;
    }

    public void setTotalFailuresBeforeAborting(long totalFailuresBeforeAborting) {
        this.totalFailuresBeforeAborting = totalFailuresBeforeAborting;
    }
}
