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

import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileResponseReceiver;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.TileObject;

public class ConveyorTile extends Conveyor implements TileResponseReceiver {    
    // Shared request information, this is stored by the cache key
    protected int[] tileIndex = null;
    protected String layerId = null;
    protected SRS srs = null;
    
    protected TileLayer tileLayer = null;
    
    // Shared metadata, this is stored in the tile
    //protected byte version = 0;
    //protected byte type = 0;
    //protected byte length = -1;
    //protected long tsCreated = 0;
    //protected long tsExpire = 0;

    
    public ConveyorTile(StorageBroker sb, String layerId, HttpServletRequest servletReq, HttpServletResponse servletResp) {
        super(sb, servletReq, servletResp);
        this.layerId = layerId;
    }
    
    /**
     * This constructor is used for an incoming request, the data is
     * then added by the cache
     */
    public ConveyorTile(StorageBroker sb, String layerId, SRS srs, int[] tileIndex, MimeType mimeType, String parameters, 
            HttpServletRequest servletReq, HttpServletResponse servletResp) {
        super(sb, servletReq, servletResp);
        this.layerId = layerId;
        this.srs = srs;
        this.tileIndex = tileIndex.clone();
        super.mimeType = mimeType;
        
        long[] idx = {tileIndex[0], tileIndex[1], tileIndex[2]};
        
        stObj = TileObject.createQueryTileObject(layerId, idx, srs.getNumber(), mimeType.getFormat(), parameters);
    }
    
    /**
     * This constructor is used by metatile code to create a data tile
     */
    //public ConveyorTile(StorageBroker sb, TileLayer layer, SRS srs, int[] tileIndex, MimeType mimeType, 
    //        long status, byte[] payload) {
    //    super(sb, null, null);
    //    this.layerId = layer.getName();
    //    this.tileLayer = layer;
    //    this.srs = srs;
    //    this.tileIndex = tileIndex.clone();
    //    super.mimeType = mimeType;
    //    this.status = status;
    //    
    //    long[] idx = {tileIndex[0], tileIndex[1], tileIndex[2]};
    //    stObj = TileObject.createQueryTileObject(layerId, idx, srs.getNumber(), mimeType.getFormat(), null);
    //    stObj.setBlob(payload);
    //}
    
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
    
    //public long getTSExpire() {
    //    return tsExpire;
    //}
    
    //public void setTSExpire(long ts){
    //    this.tsExpire = ts;
    //}
        
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
    
    public int[] getTileIndex() {
        return tileIndex;
    }
    
    public void setTileIndex(int[] tileIndex) {
        this.tileIndex = tileIndex.clone();
    }
    
    public SRS getSRS() {
        return srs;
    }
    
    public void setSRS(SRS srs) {
        this.srs = srs;
    }
}
