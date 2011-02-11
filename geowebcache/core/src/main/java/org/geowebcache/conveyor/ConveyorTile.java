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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileResponseReceiver;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;

public class ConveyorTile extends Conveyor implements TileResponseReceiver {
    private static Log log = LogFactory.getLog(org.geowebcache.conveyor.ConveyorTile.class);
    
    // Shared request information, this is stored by the cache key
    //protected long[] tileIndex = null;
    
    protected String layerId = null;
    //protected SRS srs = null;
    protected String gridSetId = null;
    
    protected GridSubset gridSubset = null;
    
    protected TileLayer tileLayer = null;
    
    TileObject stObj = null;
    
    String fullParameters;
    
    public ConveyorTile(StorageBroker sb, String layerId, HttpServletRequest servletReq, HttpServletResponse servletResp) {
        super(sb, servletReq, servletResp);
        this.layerId = layerId;
    }
    
    /**
     * This constructor is used for an incoming request, the data is
     * then added by the cache
     */
    public ConveyorTile(StorageBroker sb, String layerId, String gridSetId, long[] tileIndex, MimeType mimeType, 
            String fullParameters, String modifiedParameters,
            HttpServletRequest servletReq, HttpServletResponse servletResp) {
        super(sb, servletReq, servletResp);
        this.layerId = layerId;
        this.gridSetId = gridSetId;
        
        long[] idx = new long[3];
        
        if(tileIndex != null) {
            idx[0] = tileIndex[0]; idx[1] = tileIndex[1]; idx[2] = tileIndex[2];
        }
        
        super.mimeType = mimeType;
               
        this.fullParameters = fullParameters;
        
        stObj = TileObject.createQueryTileObject(layerId, idx, gridSetId, mimeType.getFormat(), modifiedParameters);
    }
    
    public String getFullParameters() {
        if(this.fullParameters == null)
            return "";
            
        return fullParameters;
    }
    
    public String getLayerId() {
        return this.layerId;
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
    
    public long getTSCreated() {
        return stObj.getCreated();
    }
        
    public int getStatus() {
        return (int) status;
    }
    
    public void setStatus(int status) {
        this.status = (long) status ;
    }
    
    public String getErrorMessage() {
        return this.errorMsg;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMsg = errorMessage;
    }
    
    public String getParameters() {
        return ((TileObject) stObj).getParameters();
    }
    
    public long[] getTileIndex() {
        return stObj.getXYZ();
    }
    
    public synchronized GridSubset getGridSubset() {
        if(gridSubset == null) {
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
    
    public byte[] getContent() {
        return stObj.getBlob();
    }
    
    public void setContent(byte[] payload) {
        stObj.setBlob(payload);
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
            boolean ret = storageBroker.get((TileObject) stObj);
            
            // Has the tile been explicitly marked as old?
            if(ret && stObj.getCreated() == -1) {
                ret = false;
            } else
            // Do we use expiration, and if so, is the tile recent enough ?
            if(ret && maxAge > 0 
                    && stObj.getCreated() + maxAge < System.currentTimeMillis()) {
                ret = false;
            }
            
            if(ret) {
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

        if (layerId != null) {
            str.append(this.layerId).append(" ");
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
    
}
