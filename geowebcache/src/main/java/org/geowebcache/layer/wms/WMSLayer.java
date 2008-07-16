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
 */

package org.geowebcache.layer.wms;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.cache.Cache;
import org.geowebcache.cache.CacheException;
import org.geowebcache.cache.CacheFactory;
import org.geowebcache.cache.CacheKey;
import org.geowebcache.layer.GridLocObj;
import org.geowebcache.layer.GenericTile;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileRequest;
import org.geowebcache.layer.TileResponse;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.service.Request;
import org.geowebcache.service.ServiceException;
import org.geowebcache.service.ServiceRequest;
import org.geowebcache.service.wms.WMSParameters;
import org.geowebcache.util.ServletUtils;
import org.geowebcache.util.wms.BBOX;

public class WMSLayer implements TileLayer {
    private static Log log = LogFactory
            .getLog(org.geowebcache.layer.wms.WMSLayer.class);

    public static final String WMS_MIMETYPES = "mimetypes";

    String name;

    WMSLayerProfile profile;

    Cache cache;

    CacheKey cacheKey;
    
    final Lock layerLock = new ReentrantLock();
    
    boolean layerLocked = false;
    
    Condition layerLockedCond = layerLock.newCondition();
    
    Condition[] gridLocConds = null;

    String cachePrefix = null;

    MimeType[] formats = null;

    HashMap<GridLocObj,Boolean> procQueue =  new HashMap<GridLocObj,Boolean>();

    boolean debugHeaders = true;

    Integer cacheLockWait = -1;
    
    public volatile Boolean isInitialized = null;
    
    Properties initProps = null;
    
    CacheFactory initCacheFactory = null;

    public WMSLayer(String layerName, Properties props,
            CacheFactory cacheFactory) throws GeoWebCacheException {
        name = layerName;
        initProps = props;   
        initCacheFactory = cacheFactory;

    }
    
    public Boolean isInitialized() {
        Boolean result = isInitialized;
        if (result == null) {
            synchronized (this) {
                result = isInitialized;
                if (result == null) {
                    isInitialized = result = initialize();
                }
            }
        }
        return result;
    }
    
    public Boolean initialize() {
        try {
            setParametersFromProperties(initProps, initCacheFactory);
        } catch(GeoWebCacheException gwce) {
            log.error(gwce.getMessage());
            gwce.printStackTrace();
        }
        // Create conditions for tile locking
        this.gridLocConds = new Condition[17];
        for(int i=0; i<gridLocConds.length; i++) {
            gridLocConds[i] = layerLock.newCondition();
        }
        
        // Unset variables for garbage collection
        initProps = null;
        initCacheFactory = null;
        
        return new Boolean(true);
    }

    /**
     * Rough checks to see whether the layers supports the requested projection,
     * returns error message otherwise
     * 
     * @param srs
     *            Name of projection, for example "EPSG:4326"
     * @return null if okay, error message otherwise.
     */
    public boolean supportsProjection(SRS srs) throws GeoWebCacheException {
        for (int i = 0; i < profile.srs.length; i++) {
            if (srs.equals(profile.srs[i])) {
                return true;
            }
        }
        throw new GeoWebCacheException("SRS " + srs.toString() 
                + " is not supported by " + this.getName());
    }

    /**
     * Rough checks to see whether the layers supports the requested mimeType.
     * Null assumes the default format and is supported.
     * 
     * Returns error message otherwise
     * 
     * @param mimeType
     *            MIME type or null, example "image/png"
     * @return null if okay, error message otherwise.
     */
    public boolean supportsFormat(String strFormat) throws GeoWebCacheException {
        if (strFormat == null) {
            log.trace("Format was null");
            return true;
        }

        for (int i = 0; i < formats.length; i++) {
            if (strFormat.equalsIgnoreCase(formats[i].getFormat())) {
                return true;
            }
        }
        
        throw new GeoWebCacheException("Format " + strFormat 
                + " is not supported by " + this.getName());
    }

    /**
     * Rough checks to see whether the specified bounding box is supported by
     * the current layer.
     * 
     * Returns error message if not.
     * 
     * @param srs
     *            the string representation
     * @param reqBounds
     *            the requested bounds
     * @return null if okay, error message otherwise.
     */
    public String supportsBbox(SRS srs, BBOX reqBounds) throws GeoWebCacheException {
        this.supportsProjection(srs);

        if (!reqBounds.isSane()) {
            return "The requested bounding box "
                    + reqBounds.getReadableString() + " is not sane";
        }

        if (!profile.gridBase[getSRSIndex(srs)].contains(reqBounds)) {
            return "The layers grid box "
                    + profile.gridBase[getSRSIndex(srs)].getReadableString()
                    + " does not cover the requested bounding box "
                    + reqBounds.getReadableString();
        }

        // All ok
        return null;
    }

    /**
     * The main function
     * 
     * 1) Create cache key, test whether we can retrieve without locking
     * 2) Get lock for metatile, monitor condition variable if not
     * (Recheck cache after signal)
     * 3) Create metatile request, execute
     * 4) Get tiles and save them to cache 
     * 5) Unlock metatile, signal other threads
     * 6) Set Cache-Control, return tile
     * 
     * @param wmsparams
     * @return
     */
    public TileResponse getResponse(TileRequest tileRequest, 
            ServiceRequest servReq, HttpServletResponse response) 
    throws GeoWebCacheException, IOException {        
        MimeType mime = tileRequest.mimeType;

        if (mime == null) {
            mime = this.formats[0];
        }

        int[] gridLoc = tileRequest.gridLoc;
        int idx = getSRSIndex(tileRequest.SRS);

        // Final preflight check, throws exception if necessary
        profile.gridCalc[idx].locationWithinBounds(gridLoc);
        
        // Quick and dirty... cache should take care of any locking issues
        Object ck = cacheKey.createKey(cachePrefix, gridLoc[0], gridLoc[1],
                gridLoc[2], tileRequest.SRS, mime.getFileExtension());
        
        GenericTile tile = this.tryCacheFetch(ck);
        if(tile != null) {
            return this.createTileResponse(tile.getData(), -1, mime, response);
        }
        
        if(! tileRequest.askBackend) {
            return null;
        }
            
        // Okay, so we need to go to the backend
        if(mime.supportsTiling() 
                && servReq.getFlag(ServiceRequest.SERVICE_REQUEST_METATILE)) {
            return getMetatilingReponse(
                    tileRequest, gridLoc, ck, mime, idx, response);  
        } else {
            return getNonMetatilingReponse(
                    tileRequest, gridLoc, ck, mime, idx, response);
        }
    }
    
    /**
     * Metatiling request forwarding
     * 
     * @param tileRequest
     * @param gridLoc
     * @param mime
     * @param idx
     * @param response
     * @return
     * @throws GeoWebCacheException
     */
    private TileResponse getMetatilingReponse(TileRequest tileRequest,
            int[] gridLoc, Object ck, MimeType mime, int idx, 
            HttpServletResponse response)
    throws GeoWebCacheException {
        
        WMSMetaTile metaTile = new WMSMetaTile(tileRequest.SRS,
                profile.gridCalc[idx].getGridBounds(gridLoc[2]), gridLoc,
                profile.metaWidth, profile.metaHeight);
        int[] metaGridLoc = metaTile.getMetaGridPos();
        GridLocObj metaGlo = new GridLocObj(metaGridLoc);
        int condIdx = this.calcLocCondIdx(metaGridLoc);
        
        /** ****************** Acquire lock ******************* */
        waitForQueue(metaGlo, condIdx);

        /** ****************** Check cache again ************** */
        GenericTile tile = this.tryCacheFetch(ck);
        if(tile != null) {
            // Someone got it already, return lock and we're done
            removeFromQueue(metaGlo, condIdx);
            return this.createTileResponse(tile.getData(), -1, mime, response);
        }
        
        /** ****************** No luck, Request metatile ****** */
        metaTile.doRequest(profile, tileRequest.SRS, mime.getFormat());

        boolean useJAI = true;
        if (mime == ImageMime.jpeg) {
            useJAI = false;
        }
        
        metaTile.createTiles(profile.width, profile.height, useJAI);

        int[][] gridPositions = metaTile.getTilesGridPositions();
        
        byte[] data = getTile(gridLoc, gridPositions, metaTile, mime);
        
        if (profile.expireCache != WMSLayerProfile.CACHE_NEVER) {
            saveTiles(gridPositions, metaTile, mime);
        }
        
        //TODO Should save the tiles, and unlock, in separate thread

        /** ****************** Return lock and response ****** */
        removeFromQueue(metaGlo, condIdx);
        return this.createTileResponse(data, metaTile.getStatus(), mime, response);
    }

    /**
     * Non-metatiling forward to backend
     * 
     * @param tileRequest
     * @param gridLoc
     * @param mime
     * @param idx
     * @param response
     * @return
     */
    private TileResponse getNonMetatilingReponse(TileRequest tileRequest,
            int[] gridLoc, Object ck,
            MimeType mime, int idx, HttpServletResponse response) 
    throws GeoWebCacheException {

        //String debugHeadersStr = null;
        int condIdx = this.calcLocCondIdx(gridLoc);
        GridLocObj glo = new GridLocObj(gridLoc);
        
        /** ****************** Acquire lock ******************* */
        waitForQueue(glo, condIdx);

        /** ****************** Check cache again ************** */
        GenericTile tile = this.tryCacheFetch(ck);
        if(tile != null) {
            // Someone got it already, return lock and we're done
            removeFromQueue(glo, condIdx);
                   
            return this.createTileResponse(tile.getData(), -1, mime, response);
        }
        
        /** ****************** Tile ******************* */
        //String requestURL = null;
        TileResponse tr = doNonMetatilingRequest(gridLoc, idx, mime.getFormat());
       
        tile = new GenericTile(tr.data);
        
        if (tr.status > 299 || profile.expireCache != WMSLayerProfile.CACHE_NEVER) {
            cache.set(ck, tile, profile.expireCache); 
        }

        /** ****************** Return lock and response ****** */
        removeFromQueue(glo, condIdx);
        return this.createTileResponse(tr.data, tr.status, mime, response);
    }
    
    public GenericTile tryCacheFetch(Object cacheKey) {
        GenericTile tile = null;
        if (profile.expireCache != WMSLayerProfile.CACHE_NEVER) {
            try {
                tile = (GenericTile) cache.get(cacheKey, profile.expireCache);
            } catch (CacheException ce) {
                ce.printStackTrace();
            }
        }
        return tile;
    }
    
    private TileResponse createTileResponse(byte[] data, int status, MimeType mime, 
            HttpServletResponse response) {
        if(response != null) {
            setExpirationHeader(response);
        }
                
        return new TileResponse(data, mime, status);
    }
    
    public int purge(OutputStream os) throws GeoWebCacheException {
        // Loop over directories
        // Not implemented
        throw new GeoWebCacheException("purge() has not been implemented yet."
                +" Maybe you want to sponsor it? ;) ");
        //return 0;
    }

    /**
     * Uses the HTTP 1.1 spec to set expiration headers
     * 
     * @param response
     */
    public void setExpirationHeader(HttpServletResponse response) {
        if (profile.expireClients == WMSLayerProfile.CACHE_VALUE_UNSET) {
            return;
        }

        //TODO move to TileResponse
        if (profile.expireClients > 0) {
            response.setHeader("Cache-Control", "max-age="
                    + (profile.expireClients / 1000) + ", must-revalidate");
        } else if (profile.expireClients == WMSLayerProfile.CACHE_NEVER_EXPIRE) {
            long oneYear = 3600 * 24 * 365;
            response.setHeader("Cache-Control", "max-age=" + oneYear);
        } else if (profile.expireClients == WMSLayerProfile.CACHE_NEVER) {
            response.setHeader("Cache-Control", "no-cache");
        } else if (profile.expireCache == WMSLayerProfile.CACHE_USE_WMS_BACKEND_VALUE) {
            response.setHeader("geowebcache-error",
                    "No CacheControl information available");
        }
    }

    /**
     * Loops over the gridPositions, generates cache keys and saves to cache
     * 
     * @param gridPositions
     * @param metaTile
     * @param imageFormat
     */
    protected void saveTiles(int[][] gridPositions, WMSMetaTile metaTile,
            MimeType mimeType) {

        for (int i = 0; i < gridPositions.length; i++) {
            int[] gridPos = gridPositions[i];

            Object ck = cacheKey.createKey(cachePrefix, gridPos[0], gridPos[1],
                    gridPos[2], metaTile.getSRS(), mimeType.getFileExtension());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            
            try {
                boolean completed = 
                    metaTile.writeTileToStream(i, mimeType.getInternalName(),out);
                if (!completed) {
                    log.error("metaTile.writeTileToStream returned false,"
                            +" no tiles saved");
                }
            } catch (IOException ioe) {
                log.error("Unable to write image tile to "
                           + "ByteArrayOutputStream: " + ioe.getMessage());
                ioe.printStackTrace();
            }

            GenericTile tile = new GenericTile(out.toByteArray());

            try {
                cache.set(ck, tile, profile.expireCache);
            } catch (CacheException ce) {
                log.error("Unable to save data to cache, stack trace follows: "
                        + ce.getMessage());
                ce.printStackTrace();
            }
        }
    }

    /**
     * Get a particular tile out of a metatile. This is only used for layers
     * that are not to be cached
     * 
     * @param gridPos
     * @param gridPositions
     * @param metaTile
     * @param imageFormat
     * @return
     */
    private byte[] getTile(int[] gridPos, int[][] gridPositions,
            WMSMetaTile metaTile, MimeType imageFormat) 
        throws GeoWebCacheException {
        for (int i = 0; i < gridPositions.length; i++) {
            int[] curPos = gridPositions[i];

            if (curPos[0] == gridPos[0] 
                && curPos[1] == gridPos[1]
                && curPos[2] == gridPos[2]) {
                
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try {
                    metaTile.writeTileToStream(i,
                            imageFormat.getInternalName(), out);
                } catch (IOException ioe) {
                    log.error("Unable to write image tile "
                            +"to ByteArrayOutputStream: "+ioe.getMessage());
                    ioe.printStackTrace();
                }
                
                byte[] data = out.toByteArray();
                return data;
            }
        }
        throw new GeoWebCacheException(
            "Bug: WMSLayer.getTile() didn't have tile...");
    }

    
    public TileResponse doNonMetatilingRequest(int[] gridLoc, 
            int idx, String formatStr) 
    throws GeoWebCacheException {
        WMSParameters wmsparams = profile.getWMSParamTemplate();
        
        // Fill in the blanks
        wmsparams.setFormat(formatStr);
        wmsparams.setSrs(profile.srs[idx].toString());
        wmsparams.setWidth(profile.width);
        wmsparams.setHeight(profile.height);
        BBOX bbox = profile.gridCalc[idx].bboxFromGridLocation(gridLoc);
        bbox.adjustForGeoServer(profile.srs[idx]);
        wmsparams.setBBOX(bbox);

        byte[] buffer = null;
        String contentType = null;
        int responseCode = -99;
        
        // Ask the WMS server, saves returned information into buffer
        String backendURL = "";
        int backendTries = 0; // keep track of how many backends we have tried
        while (buffer == null && backendTries < profile.wmsURL.length) {
            backendURL = profile.nextWmsURL();

            //boolean saveExpiration = (
            //        profile.expireCache == WMSLayerProfile.CACHE_USE_WMS_BACKEND_VALUE 
            //        || profile.expireClients == WMSLayerProfile.CACHE_USE_WMS_BACKEND_VALUE);

            HttpURLConnection wmsBackendCon = null;
            try {
                // Create an outgoing WMS request to the server
                Request wmsrequest = new Request(backendURL, wmsparams);
                URL wmsBackendUrl = new URL(wmsrequest.toString());
                wmsBackendCon = (HttpURLConnection) wmsBackendUrl.openConnection();

                buffer = ServletUtils.readStream(
                        wmsBackendCon.getInputStream(),-1,-1);
                
                contentType = wmsBackendCon.getContentType();
                responseCode = wmsBackendCon.getResponseCode();

            } catch (ConnectException ce) {
                throw new GeoWebCacheException(
                        "Error forwarding request, " + backendURL
                        + wmsparams.toString() + " " + ce.getMessage());
            } catch (IOException ioe) {
                throw new GeoWebCacheException(
                        "Error forwarding request, " + backendURL
                        + wmsparams.toString() + " " + ioe.getMessage());
            } finally {
            	wmsBackendCon.disconnect();
            }

            backendTries++;
        }

        if (responseCode == -99) {
            throw new GeoWebCacheException(
                   "Error forwarding request, " + backendURL);
        }
        
        MimeType mt = null;
        if(contentType != null) {
            mt = MimeType.createFromFormat(contentType);
        }
        
        TileResponse tr = new TileResponse(buffer, mt, responseCode);
        return tr;
    }

    /**
     * 
     * @param props
     * @throws CacheException
     */
    private void setParametersFromProperties(Properties props,
            CacheFactory cacheFactory) throws GeoWebCacheException {
        profile = new WMSLayerProfile(this.name, props);

        // Cache and CacheKey
        String propCacheBeanId = props.getProperty("cachebeanid");
        if (propCacheBeanId != null) {
            cache = cacheFactory.getCache(propCacheBeanId);
            if (cache == null) {
                log.error("Unable to create cache for bean id "
                        + propCacheBeanId);
            }
        } else {
            cache = cacheFactory.getDefaultCache();
            if(cache == null) {
                log.error("Unable to get default cache.");
            }
        }

        String propCacheKeyBeanId = props.getProperty("cachebeanid");
        if (propCacheKeyBeanId == null) {
            cacheKey = cacheFactory.getCacheKeyFactory().getCacheKey(
                    cache.getDefaultKeyBeanId());
        } else {
            cacheKey = cacheFactory.getCacheKeyFactory().getCacheKey(
                    propCacheKeyBeanId);
        }

        String propCachePrefix = props.getProperty("cacheprefix");
        if (propCachePrefix == null) {
            String propFileCachePath = props.getProperty("filecachepath");
            if(propFileCachePath == null) {
                String sanitizedName = name.replace(':', '_');
                cachePrefix = cache.getDefaultPrefix(sanitizedName);

                log.warn("cachePrefix not defined for layer " + name
                        + ", using default prefifx and name instead: "
                        + cachePrefix);
            } else {
                cachePrefix = propFileCachePath;
                
                log.warn("Using deprecated filecachepath,"
                        +" please rename to cacheprefix");
            }

        } else {
            cachePrefix = propCachePrefix;
            log.info("Using cache prefix " + cachePrefix 
                    + " for layer " + name);
        }

        // Initialize the cache
        cache.setUp(cachePrefix);

        // Check whether the configuration specifies what MIME types are legal
        String propImageMIME = props.getProperty("mimetypes");
        if (propImageMIME != null) {
            String[] mimeStrs = propImageMIME.split(",");
            formats = new MimeType[mimeStrs.length];
            for (int i = 0; i < mimeStrs.length; i++) {
                formats[i] = MimeType.createFromFormat(mimeStrs[i]);
                if(formats[i] == null) {
                    log.error("Unable to match " + mimeStrs[i]
                            + " to a supported format.");
                }
            }
        }

        // Set default to image/png, if none were specified or acceptable
        if (formats == null || formats[0] == null) {
            log.error( "Unable not determine supported MIME types"
                   + " based on configuration, falling back to image/png");
            formats = new ImageMime[1];
            formats[0] = ImageMime.createFromFormat("image/png");
        }

        // Whether to include debug headers with every returned tile
        String propDebugHeaders = props.getProperty("debugheaders");
        if (propDebugHeaders != null) {
            debugHeaders = Boolean.valueOf(propDebugHeaders);
        }

        // How long the system should wait before assuming a thread,
        // that was trying to get a tile from cache or backend,
        // is dead.
        String propCacheLockWait = props.getProperty("cachelockwait");
        if (propCacheLockWait != null) {
            cacheLockWait = Integer.valueOf(propCacheLockWait);
        }
    }

    /**
     * Returns the default image format if strFormat is unset
     * 
     * @param strFormat
     * @return ImageFormat equivalent, or default ImageFormat
     */
    public MimeType getImageFormat(String strFormat) throws MimeException {
        if (strFormat == null) {
            return this.formats[0];
        } else {
            return MimeType.createFromFormat(strFormat);
        }
    }

    public WMSParameters getWMSParamTemplate() {
        WMSParameters ret = profile.getWMSParamTemplate();
        ret.setFormat(formats[0].getFormat());
        return ret;
    }

    public void destroy() {
        cache.destroy();
        // Not that it really matters:
        procQueue.clear();
    }

    public BBOX getBounds(int srsIdx) {
        return this.profile.bbox[srsIdx];
    }

    public int[][] getCoveredGridLevels(int srsIdx, BBOX bounds) {
        BBOX adjustedBounds = bounds;
        if (!this.profile.bbox[srsIdx].contains(bounds)) {
            adjustedBounds = BBOX.intersection(this.profile.bbox[srsIdx],
                    bounds);
            log.warn("Adjusting bounds from " + bounds.toString() + " to "
                    + adjustedBounds.toString());
        }
        return this.profile.gridCalc[srsIdx].coveredGridLevels(adjustedBounds);
    }

    public int[] getMetaTilingFactors() {
        int[] factorArray = { profile.metaWidth, profile.metaHeight };
        return factorArray;
    }

    public String getName() {
        return this.name;
    }

    public int[] getGridLocForBounds(int srsIdx, BBOX tileBounds) throws ServiceException {
        return profile.gridCalc[srsIdx].gridLocation(tileBounds);
    }

    public MimeType getDefaultMimeType() {
        return formats[0];
    }

    public SRS[] getProjections() {
        return profile.srs;
    }

    /**
     * Returns the array index for the given SRS.
     * This value is used to look up corresponding bounding boxes,
     * grids etc. 
     * 
     * @param reqSRS
     * @return the array index for this SRS, -1 otherwise
     */
    public int getSRSIndex(SRS reqSRS) {
        return profile.getSRSIndex(reqSRS);
    }

    public BBOX getBboxForGridLoc(int srsIdx, int[] gridLoc) {
        return profile.gridCalc[srsIdx].bboxFromGridLocation(gridLoc);
    }

    public int[][] getZoomInGridLoc(int srsIdx, int[] gridLoc) {
        return profile.gridCalc[srsIdx].getZoomInGridLoc(gridLoc);
    }

    public int[] getZoomedOutGridLoc(int srsIdx) {
        return profile.gridCalc[srsIdx].getZoomedOutGridLoc();
    }
    
    public Cache getCache() {
        return this.cache;
    }
    
    public CacheKey getCacheKey() {
        return this.cacheKey;
    }

    public String getCachePrefix() {
        return new String(this.cachePrefix);
    }
    
    /**
     * Acquires lock for the entire layer, returns only after all other
     * requests that could write to the queue have finished
     */
    public void acquireLayerLock() {   
        boolean wait = true;
        // Wait until the queue is free
        while(wait) {
            layerLock.lock();
            try {
                this.layerLocked = true;
                if(this.procQueue == null || this.procQueue.size() == 0) {
                    wait = false;
                }
            } finally {
                layerLock.unlock();
            }
        }
    }
    
    /**
     * Releases lock for the entire layer, signals threads
     * that have been kept waiting
     */
    public void releaseLayerLock() {
        layerLock.lock();
        try {
            layerLocked = false;
            // Wake everyone up
            layerLockedCond.signalAll();
        } finally {
            layerLock.unlock();
        }
    }
    

    /**
     * 
     * @param metaGridLoc
     * @return
     */
    protected boolean waitForQueue(GridLocObj glo, int condIdx) {
        boolean retry = true;
        boolean hasWaited = false;
        //int condIdx = getLocCondIdx(gridLoc);
        
        while(retry) {
            layerLock.lock();
            try {
                // Check for global lock
                if(layerLocked) {
                    this.layerLockedCond.await();
                } else if(this.procQueue.containsKey(glo)) {
                    //System.out.println(Thread.currentThread().getId() 
                    //+ " WAITING FOR "+glo.toString()+ " convar " + condIdx);
                    hasWaited = true;
                    this.gridLocConds[condIdx].await();
                    //System.out.println(Thread.currentThread().getId() 
                    //+ " WAKING UP "+glo.toString()+ " convar " + condIdx);
                } else {
                    this.procQueue.put(glo, true);
                    retry = false;
                    //System.out.println(Thread.currentThread().getId() 
                    //+ " CONTINUES "+glo.toString()+ " convar " + condIdx);
                }
            } catch (InterruptedException ie) {
                // Do we care? Maybe if the program is about to shut down
            } finally {
                layerLock.unlock();
            }
        }
        
        return hasWaited;
    }

    /**
     * Synchronization function, ensures that the same metatile is not requested
     * simultaneously by two threads.
     * 
     * @param gridLoc the grid positions of the tile (bottom left of metatile)
     * @return
     */
    protected void removeFromQueue(GridLocObj glo, int condIdx) {
        layerLock.lock();
        try {
            //System.out.println(Thread.currentThread().getId() 
            //+ " DONE, SIGNALLING "+glo.toString() + " convar " + condIdx);
            this.procQueue.remove(glo);
            this.gridLocConds[condIdx].signalAll();
        } finally {
           layerLock.unlock();
        }
    }
    
    private int calcLocCondIdx(int[] gridLoc) {
        return (gridLoc[0]*7 + gridLoc[1]*13 + gridLoc[2]*5) 
            % gridLocConds.length;
    }

    public int getZoomStart() {
        return this.profile.zoomStart;
    }

    public int getZoomStop() {
        return this.profile.zoomStop;
    }

    public MimeType[] getMimeTypes() {
        return formats;
    }

    public double[] getResolutions(int srsIdx) {
        return profile.gridCalc[srsIdx].getResolutions(this.profile.width);
    }

    public String getStyles() {
        return profile.wmsStyles;
    }
    
    /**
     * 
     * @param tile
     * @param ck
     * @param gridLoc
     * @throws CacheException
     */
    public void putTile(GenericTile tile, Object ck, int[] gridLoc) 
    throws CacheException {
        
        int condIdx = this.calcLocCondIdx(gridLoc);
        GridLocObj glo = new GridLocObj(gridLoc);
        
        /** ****************** Acquire lock ******************* */
        waitForQueue(glo, condIdx);
        
        /** ****************** Tile ******************* */        
        if (profile.expireCache != WMSLayerProfile.CACHE_NEVER) {
            cache.set(ck, tile, profile.expireCache);
        }

        /** ****************** Return lock and response ****** */
        removeFromQueue(glo, condIdx);
    }
}
