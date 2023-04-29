/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Arne Kepp, The Open Planning Project, Copyright 2009
 */
package org.geowebcache.conveyor;

import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileResponseReceiver;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;

/** Represents a request for a tile and carries the information needed to complete it. */
public class ConveyorTile extends Conveyor implements TileResponseReceiver {
    private static Logger log = Logging.getLogger(ConveyorTile.class.getName());

    // Shared request information, this is stored by the cache key
    // protected long[] tileIndex = null;

    // protected SRS srs = null;
    protected String gridSetId = null;

    protected GridSubset gridSubset = null;

    protected TileLayer tileLayer = null;

    TileObject stObj = null;

    /*
     * Stores all raw values coming form request both as path variables or request parameters
     */
    private Map<String, String[]> fullParameters;

    /*
     * Stores values coming form request both as path variable or request parameters filtered by
     * AbstractTileLayer.getParameterFilters()
     */
    private Map<String, String> filteringParameters;

    private boolean isMetaTileCacheOnly;

    public ConveyorTile(
            StorageBroker sb,
            String layerId,
            HttpServletRequest servletReq,
            HttpServletResponse servletResp) {
        super(layerId, sb, servletReq, servletResp);
    }

    /** This constructor is used for an incoming request, with fullParameters */
    public ConveyorTile(
            StorageBroker sb,
            String layerId,
            String gridSetId,
            long[] tileIndex,
            MimeType mimeType,
            Map<String, String[]> fullParameters,
            Map<String, String> filteringParameters,
            HttpServletRequest servletReq,
            HttpServletResponse servletResp) {
        this(
                sb,
                layerId,
                gridSetId,
                tileIndex,
                mimeType,
                filteringParameters,
                servletReq,
                servletResp);
        this.fullParameters = fullParameters;
    }

    /** This constructor is used for an incoming request, the data is then added by the cache */
    public ConveyorTile(
            StorageBroker sb,
            String layerId,
            String gridSetId,
            long[] tileIndex,
            MimeType mimeType,
            Map<String, String> filteringParameters,
            HttpServletRequest servletReq,
            HttpServletResponse servletResp) {

        super(layerId, sb, servletReq, servletResp);
        this.gridSetId = gridSetId;

        long[] idx = new long[3];

        if (tileIndex != null) {
            idx[0] = tileIndex[0];
            idx[1] = tileIndex[1];
            idx[2] = tileIndex[2];
        }

        super.mimeType = mimeType;

        this.filteringParameters = filteringParameters;

        stObj =
                TileObject.createQueryTileObject(
                        layerId, idx, gridSetId, mimeType.getFormat(), filteringParameters);
    }

    public Map<String, String> getFilteringParameters() {
        if (filteringParameters == null) {
            return Collections.emptyMap();
        }
        return filteringParameters;
    }

    public Map<String, String[]> getRequestParameters() {
        if (fullParameters == null) {
            return Collections.emptyMap();
        }
        return fullParameters;
    }

    public TileLayer getLayer() {
        return this.tileLayer;
    }

    public void setTileLayer(TileLayer layer) {
        this.tileLayer = layer;
    }

    public TileLayer getTileLayer() {
        return tileLayer;
    }

    /** The time that the stored tile resource was created */
    public long getTSCreated() {
        return stObj.getCreated();
    }

    @Override
    public int getStatus() {
        return (int) status;
    }

    @Override
    public void setStatus(int status) {
        this.status = status;
    }

    @Override
    public String getErrorMessage() {
        return this.errorMsg;
    }

    @Override
    public void setErrorMessage(String errorMessage) {
        this.errorMsg = errorMessage;
    }

    public Map<String, String> getParameters() {
        return stObj.getParameters();
    }

    public long[] getTileIndex() {
        return stObj.getXYZ();
    }

    public synchronized GridSubset getGridSubset() {
        if (gridSubset == null && gridSetId != null) {
            gridSubset = tileLayer.getGridSubset(gridSetId);
        }

        return gridSubset;
    }

    public String getGridSetId() {
        return gridSetId;
    }

    public void setGridSetId(String gridSetId) {
        this.gridSetId = gridSetId;
    }

    public Resource getBlob() {
        return stObj.getBlob();
    }

    public void setBlob(Resource payload) {
        stObj.setBlob(payload);
    }

    public TileObject getStorageObject() {
        return stObj;
    }

    public boolean persist() throws GeoWebCacheException {
        try {
            return storageBroker.put(stObj);
        } catch (StorageException e) {
            throw new GeoWebCacheException(e);
        }
    }

    public boolean retrieve(long maxAge) throws GeoWebCacheException {
        try {
            if (isMetaTileCacheOnly) {
                boolean cached = storageBroker.getTransient(stObj);
                this.setCacheResult(cached ? CacheResult.HIT : CacheResult.MISS);
                return cached;
            }
            boolean ret = storageBroker.get(stObj);

            // Do we use expiration, and if so, is the tile recent enough ?
            if (ret && maxAge > 0 && stObj.getCreated() + maxAge < System.currentTimeMillis()) {
                ret = false;
            }

            if (ret) {
                this.setCacheResult(CacheResult.HIT);
            } else {
                this.setCacheResult(CacheResult.MISS);
            }

            return ret;

        } catch (StorageException se) {
            log.warning(se.getMessage());
            return false;
        }
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("ConveyorTile[");
        long[] idx = stObj.getXYZ();

        if (getLayerId() != null) {
            str.append(getLayerId()).append(" ");
        }

        if (this.gridSetId != null) {
            str.append(gridSetId).append(" ");
        }

        if (idx != null && idx.length == 3) {
            str.append("{" + idx[0] + "," + idx[1] + "," + idx[2] + "} ");
        }

        if (this.mimeType != null) {
            str.append(this.mimeType.getFormat());
        }
        str.append(']');
        return str.toString();
    }

    public String getParametersId() {
        return stObj.getParametersId();
    }

    public void setMetaTileCacheOnly(boolean b) {
        this.isMetaTileCacheOnly = b;
    }

    public boolean isMetaTileCacheOnly() {
        return isMetaTileCacheOnly;
    }
}
