package org.geowebcache.conveyor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.StorageBroker;

/**
 * Represents a request against a tile service and carries the information needed to complete it.
 */
public abstract class Conveyor {

    /**
     * Should the request represented by this conveyor be handled by the service or the layer
     */
    public static enum RequestHandler {
        LAYER, SERVICE
    };

    public static enum CacheResult {
        HIT, MISS, WMS, OTHER
    };

    private String layerId = null;

    // Internal routing
    public RequestHandler reqHandler = RequestHandler.LAYER;

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

    protected long status = 0;

    protected CacheResult cacheResult;

    protected Conveyor(String layerId, StorageBroker sb, HttpServletRequest srq,
            HttpServletResponse srp) {
        this.layerId = layerId;
        storageBroker = sb;
        servletReq = srq;
        servletResp = srp;
    }

    public String getLayerId() {
        return this.layerId;
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

    public CacheResult getCacheResult() {
        return this.cacheResult;
    }

    public void setCacheResult(CacheResult cacheResult) {
        this.cacheResult = cacheResult;
    }

    // public abstract boolean persist() throws GeoWebCacheException;

    // public abstract boolean retrieve(int maxAge) throws GeoWebCacheException;

    public StorageBroker getStorageBroker() {
        return storageBroker;
    }
}
