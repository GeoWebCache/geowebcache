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
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 *  
 */
package org.geowebcache.tile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.mime.MimeType;
import org.geowebcache.util.ByteUtils;
import org.geowebcache.util.ServletUtils;

/**
 * Facilitates use with JCS and other backends that only deal with objects.
 * 
 * @author Arne Kepp, The Open Planning Project
 */
public class Tile implements Serializable {
    public static enum RequestHandler {LAYER, SERVICE};
    
    private static final long serialVersionUID = -5171595780192211809L;
    
    private static Log log = LogFactory.getLog(org.geowebcache.tile.Tile.class);
    
    // Internal routing
    public RequestHandler reqHandler = RequestHandler.LAYER;
    
    // Set this flag to true when request has been completed
    public boolean complete = false;
    
    // Set this flag to true if an error has been encountered
    public boolean error = false;
    
    // Store an error message 
    public String errorMsg = null;
    
    // Used in special cases, like getCapabilities
    public String hint = null;
    
    public HttpServletRequest servletReq = null;
    public HttpServletResponse servletResp = null;
    
    // Shared request information, this is stored by the cache key
    protected MimeType mimeType = null;
    protected MimeType wrapperMimeType = null;
    protected int[] tileIndex = null;
    protected String layerId = null;
    protected SRS srs = null;
    
    protected TileLayer tileLayer = null;
    
    // Shared metadata, this is stored in the tile
    protected byte version = 0;
    protected byte type = 0;
    protected byte length = -1;
    protected long status = 0;
    protected long tsCreated = 0;
    protected long tsExpire = 0;
    
    // The actual data
    protected byte[] data = null;
    
    public Tile(String layerId, HttpServletRequest servletReq, HttpServletResponse servletResp) {
        this.layerId = layerId;
        this.servletReq = servletReq;
        this.servletResp = servletResp;
    }
    
    /**
     * This constructor is used for an incoming request, the data is
     * then added by the cache
     */
    public Tile(String layerId, SRS srs, int[] tileIndex, MimeType mimeType, 
            HttpServletRequest servletReq, HttpServletResponse servletResp) {
        this.layerId = layerId;
        this.srs = srs;
        this.tileIndex = tileIndex;
        this.mimeType = mimeType;
        this.servletReq = servletReq;
        this.servletResp = servletResp;
    }
    
    /**
     * This constructor is used by metatile code to create a data tile
     */
    public Tile(TileLayer layer, SRS srs, int[] tileIndex, MimeType mimeType, 
            long status, byte[] payload) {
        this.layerId = layer.getName();
        this.tileLayer = layer;
        this.srs = srs;
        this.tileIndex = tileIndex;
        this.mimeType = mimeType;
        this.status = status;
        this.data = payload;
    }
    
//    /**
//     * This constructor is used when a 
//     * 
//     * @param payload
//     * @param status
//     * @param mimeType
//     * @param tsCreated
//     * @param tsExpire
//     */
//    public Tile(byte[] payload, byte status, MimeType mimeType, long tsCreated, long tsExpire) {
//        this.data = payload;
//        this.status = status;
//        this.mimeType = mimeType;
//        this.tsCreated = tsCreated;
//        this.tsExpire = tsExpire;
//    }
    
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
    
    public RequestHandler getRequestHandler() {
        return reqHandler;
    }

    public void setRequestHandler(RequestHandler reqHandler) {
        this.reqHandler = reqHandler;
    }

    public byte[] getContent() {
        return data;
    }
    
    public void setContent(byte[] payload) {
        this.data = payload;
    }
    
    public long getTSCreated() {
        return tsCreated;
    }
    
    public void setTSCreated(long ts){
        this.tsCreated = ts;
    }
    
    public long getTSExpire() {
        return tsExpire;
    }
    
    public void setTSExpire(long ts){
        this.tsExpire = ts;
    }
    
    public int getStatus() {
        return (int) status;
    }
    
    public void setStatus(long status) {
        this.status = status ;
    }
    
    public int[] getTileIndex() {
        return tileIndex;
    }
    
    public void setTileIndex(int[] tileIndex) {
        this.tileIndex = tileIndex;
    }
    
    public SRS getSRS() {
        return srs;
    }
    
    public void setSRS(SRS srs) {
        this.srs = srs;
    }
    
    public MimeType getMimeType() {
        return mimeType;
    }
    
    public void setMimeType(MimeType mimeType) {
        this.mimeType = mimeType;
    }
    
    public MimeType getWrapperMimeType() {
        return wrapperMimeType;
    }
    
    public void setWrapperMimeType(MimeType mimeType) {
        this.wrapperMimeType = mimeType;
    }
    
    public void setError(String message) {
        this.error = true;
        this.errorMsg = message;
    }
    
    public String getHint() {
        return hint;
    }
    
    public void setHint(String hint) {
        this.hint = hint;
    }
    
    //public void setStatus(byte status) {
    //    this.status = status;
    //}
    
    protected byte[] writeHeaders(byte[] specificHeaders) {
        byte[] headers;
        if(specificHeaders != null) {
            headers = new byte[16 + specificHeaders.length];
            headers[3] = (byte) ((headers.length - 16) / 16);
            System.arraycopy(specificHeaders, 0, 
                    headers, 16, specificHeaders.length);
        } else {
            headers = new byte[16];
            headers[3] = (byte) 0;
        }
       
        headers[0] = this.version;
        headers[1] = type;

        //if(status != 0) {
            System.arraycopy(ByteUtils.uIntLongToByteWord(status), 0, 
                    headers, 4, 4);
        //}
        
        //if(tsCreated != 0) {
            System.arraycopy(ByteUtils.uIntLongToByteWord(tsCreated), 0, 
                    headers, 8, 4);
        //}
        //if(tsExpire != 0) {
            System.arraycopy(ByteUtils.uIntLongToByteWord(tsExpire), 0, 
                    headers, 12, 4);
        //}
        
        return headers;
    }
    
    public void read(InputStream is) throws IOException, GeoWebCacheException {
        BufferedInputStream bis = new BufferedInputStream(is,32768);
        
        byte[] extraHeaders = readHeaders(bis);
        
        // TODO do it properly
        if(extraHeaders != null) {
            log.error("readHeaders() returned data, but GWC does not handle them yet.");
        }
        readContent(bis);
    }
    
    private byte[] readHeaders(BufferedInputStream bis) throws IOException {
        byte[] line = new byte[16];
        bis.read(line);
        
        readCommonHeaders(line);
        
        if(length > 0) {
            byte[] ret = new byte[length*16];
            bis.read(ret);
            return ret;
        } else {
            return null;
        }
    }
    
    private void readContent(BufferedInputStream bis) throws IOException {
        data = ServletUtils.readStream(bis, 32768, 32768);
    }
    
    /**
     * Reads the content and metadata
     * 
     * @param rawTile
     */
    public void readBytes(byte[] rawTile) {
        readByteHeaders(rawTile);
        readByteContent(rawTile);
    }
    
    protected byte[] readByteHeaders(byte[] rawTile) {
        readCommonHeaders(rawTile);
        
        if(length > 0) {
            byte[] ret = new byte[length*16];
            System.arraycopy(rawTile, 16, ret, 0, length*16);
            return ret;
        } else {
            return null;
        }
        
    }
    
    protected void readByteContent(byte[] rawTile) {
        int offset = (1 + length)*16; 
        data = new byte[rawTile.length - offset];
        System.arraycopy(rawTile,offset,this.data,0,data.length);
    }
    
    /**
     * Reads the first 16 bytes
     * @param line
     */
    private void readCommonHeaders(byte[] line) {
        this.version = line[0];
        this.type = line[1];
        this.length = line[3];
        this.status = ByteUtils.bytesToUIntLong(line, 4);
        this.tsCreated = ByteUtils.bytesToUIntLong(line, 8);
        this.tsExpire = ByteUtils.bytesToUIntLong(line, 12);
    }
    
    
    /**
     * Subclass this function to provide special headers
     * 
     * @param out
     * @throws IOException
     */
    public void write(OutputStream out) throws IOException {
        out.write(writeHeaders(null));
        
        if(data != null) {
            out.write(data);
        }
    }
    
    /**
     * Writes the content and metadata into a byte[]
     * @return
     */
    public byte[] getBytes() {
        byte[] headers = writeHeaders(null);
        byte[] ret = new byte[headers.length + data.length];
        
        System.arraycopy(headers, 0, ret, 0, headers.length);
        System.arraycopy(data,0,ret,headers.length, data.length);
        
        return ret;
    }

}
