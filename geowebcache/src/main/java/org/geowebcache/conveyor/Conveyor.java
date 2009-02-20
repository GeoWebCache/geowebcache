package org.geowebcache.conveyor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageObject;

public abstract class Conveyor {
    public static enum RequestHandler {LAYER, SERVICE};
    
    // Internal routing
    public RequestHandler reqHandler = RequestHandler.LAYER;
    
    // Set this flag to true when request has been completed
    //protected boolean complete = false;
    
    // Set this flag to true if an error has been encountered
    protected boolean error = false;
    
    // Store an Expires header
    protected long expiresHeader = -1;
    
    // Store an error message 
    protected String errorMsg = null;
    
    // Used in special cases, like getCapabilities
    protected String hint = null;
    
    public HttpServletRequest servletReq = null;
    public HttpServletResponse servletResp = null;
    protected StorageBroker storageBroker = null;
    
    protected MimeType mimeType = null;
    
    protected StorageObject stObj = null;
    
    protected long status = 0;
    
    protected Conveyor(StorageBroker sb, HttpServletRequest srq, HttpServletResponse srp) {
        storageBroker = sb;
        servletReq = srq;
        servletResp = srp;
    }
    
    public void setError() {
        this.error = true;
    }
    
    public boolean getError() {
        return error;
    }
    
    public String getErrorMsg() {
        return errorMsg;  
    }
    
    public void setErrorMsg(String errorMsg) {
        this.error = true;
        this.errorMsg = errorMsg;
    }

    public long getExpiresHeader() {
        return this.expiresHeader;
    }

    public void setExpiresHeader(long seconds) {
        this.expiresHeader = seconds;      
    }
    
    public String getHint() {
        return this.hint;
    }
    
    public void setHint(String hint) {
        this.hint = hint;
    }
    
    public MimeType getMimeType() {
        return mimeType;
    }
    
    public void setMimeType(MimeType mimeType) {
        this.mimeType = mimeType;
    }
    
    public RequestHandler getRequestHandler() {
        return reqHandler;
    }

    public void setRequestHandler(RequestHandler reqHandler) {
        this.reqHandler = reqHandler;
    }
    
    public abstract boolean persist() throws GeoWebCacheException;
    //{
    //    return storageBroker.put(stObj);
    //}
    
    public abstract boolean retrieve(int maxAge) throws GeoWebCacheException;
    //{
    //    //TODO hook up maxAge
    //    if(stObj instanceof TileObject) {
    //    return storageBroker.get(stObj);
    //}
    
    public StorageBroker getStorageBroker() {
        return storageBroker;
    }
    
    public byte[] getContent() {
        return stObj.getBlob();
    }
    
    public void setContent(byte[] payload) {
        stObj.setBlob(payload);
    }
}
