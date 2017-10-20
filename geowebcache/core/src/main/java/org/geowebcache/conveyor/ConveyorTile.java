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
 * @author Arne Kepp, The Open Planning Project, Copyright 2009
 *  
 */
package org.geowebcache.conveyor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.util.Collections;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileResponseReceiver;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;

/**
 * Represents a request for a tile and carries the information needed to complete it.
 */
public class ConveyorTile extends Conveyor implements TileResponseReceiver {
    private static Log log = LogFactory.getLog(org.geowebcache.conveyor.ConveyorTile.class);

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

    public ConveyorTile(StorageBroker sb, String layerId, HttpServletRequest servletReq,
            HttpServletResponse servletResp) {
        super(layerId, sb, servletReq, servletResp);
    }

    /**
     * This constructor is used for an incoming request, with fullParameters
     */
    public ConveyorTile(StorageBroker sb, String layerId, String gridSetId, long[] tileIndex,
            MimeType mimeType, Map<String, String[]> fullParameters, Map<String, String> filteringParameters, 
            HttpServletRequest servletReq, HttpServletResponse servletResp) {
        this(sb, layerId, gridSetId, tileIndex, mimeType, filteringParameters, servletReq, servletResp);
        this.fullParameters = fullParameters;
    }
    
    /**
     * This constructor is used for an incoming request, the data is then added by the cache
     */
    public ConveyorTile(StorageBroker sb, String layerId, String gridSetId, long[] tileIndex,
            MimeType mimeType, Map<String, String> filteringParameters, 
            HttpServletRequest servletReq, HttpServletResponse servletResp) {

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

        stObj = TileObject.createQueryTileObject(layerId, idx, gridSetId, mimeType.getFormat(),
                filteringParameters);
    }

    @Deprecated
    public Map<String, String> getFullParameters() {
        return getFilteringParameters();
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

    /**
     * The time that the stored tile resource was created
     * @return
     */
    public long getTSCreated() {
        return stObj.getCreated();
    }

    public int getStatus() {
        return (int) status;
    }

    public void setStatus(int status) {
        this.status = (long) status;
    }

    public String getErrorMessage() {
        return this.errorMsg;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMsg = errorMessage;
    }

    public Map<String, String> getParameters() {
        return ((TileObject) stObj).getParameters();
    }

    public long[] getTileIndex() {
        return stObj.getXYZ();
    }

    public synchronized GridSubset getGridSubset() {
        if (gridSubset == null) {
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

    /**
     * @deprecated as of 1.2.4a, use {@link #getBlob()}, keeping it for backwards compatibility as
     *             there are geoserver builds pegged at a given geoserver revision but building gwc
     *             from trunk. Ok to remove at 1.2.5
     */
    @Deprecated
    public byte[] getContent() {
        Resource blob = getBlob();
        if (blob instanceof ByteArrayResource) {
            return ((ByteArrayResource) blob).getContents();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) blob.getSize());
        try {
            blob.transferTo(Channels.newChannel(out));
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Resource getBlob() {
        return stObj.getBlob();
    }

    /**
     * @deprecated as of 1.2.4a, use {@link #setBlob(Resource)}, keeping it for backwards
     *             compatibility as there are geoserver builds pegged at a given geoserver revision
     *             but building gwc from trunk. Ok to remove at 1.2.5
     */
    @Deprecated
    public void setContent(byte[] payload) {
        setBlob(new ByteArrayResource(payload));
    }

    public void setBlob(Resource payload) {
        stObj.setBlob(payload);
    }

    public TileObject getStorageObject() {
        return stObj;
    }

    public boolean persist() throws GeoWebCacheException {
        try {
            return storageBroker.put((TileObject) stObj);
        } catch (StorageException e) {
            throw new GeoWebCacheException(e);
        }
    }

    public boolean retrieve(long maxAge) throws GeoWebCacheException {
        try {
            if (isMetaTileCacheOnly) {
                boolean cached = storageBroker.getTransient((TileObject) stObj);
                this.setCacheResult(cached ? CacheResult.HIT : CacheResult.MISS);
                return cached;
            }
            boolean ret = storageBroker.get((TileObject) stObj);

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
            log.warn(se.getMessage());
            return false;
        }
    }

    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("ConveyorTile[");
        long[] idx = stObj.getXYZ();

        if (idx != null && idx.length == 3) {
            str.append("{" + idx[0] + "," + idx[1] + "," + idx[2] + "} ");
        }

        if (getLayer() != null) {
            str.append(getLayerId()).append(" ");
        }

        if (this.gridSetId != null) {
            str.append(gridSetId).append(" ");
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
