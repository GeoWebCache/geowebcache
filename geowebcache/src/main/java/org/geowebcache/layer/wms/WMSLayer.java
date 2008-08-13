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
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
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
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.Grid;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.service.ServiceException;
import org.geowebcache.service.wms.WMSParameters;
import org.geowebcache.tile.Tile;
import org.geowebcache.util.ServletUtils;
import org.geowebcache.util.wms.BBOX;
import org.geowebcache.util.wms.GridCalculator;

public class WMSLayer extends TileLayer {
    // needed in configuration object written to xml
    private String WMSurl = null;

    private int[] metaWidthHeight = null;

    private String errormime;

    private int width;

    private int height;

    private String version;

    private boolean tiled;

    private boolean transparent;

    private boolean debugheaders;

    // everything here requires initialization
    public static transient String WMS_URL;

    public static transient String WMS_SRS;

    public static transient String WMS_BBOX;

    public static transient String WMS_STYLES;

    public static transient String WMS_METATILING;

    public static transient String WMS_TRANSPARENT;

    public static transient String WMS_VENDOR_PARAMS;

    public static transient int CACHE_NEVER;

    public static transient int CACHE_VALUE_UNSET;

    public static transient int CACHE_NEVER_EXPIRE;

    public static transient int CACHE_USE_WMS_BACKEND_VALUE;

    public static transient String WMS_MIMETYPES;

    protected transient GridCalculator[] gridCalc;

    private transient int zoomStart;

    private transient int zoomStop;

    private transient String request;

    private transient String bgcolor;

    private transient String palette;

    private transient String vendorParameters;

    protected transient String[] wmsURL;

    private transient int curWmsURL;

    private transient String wmsLayers;

    private transient String wmsStyles;

    private transient WMSParameters wmsparams;

    private transient boolean saveExpirationHeaders;

    private transient long expireClients;

    private transient long expireCache;

    transient Cache cache;

    transient CacheKey cacheKey;

    transient Lock layerLock;

    transient boolean layerLocked;

    transient Condition layerLockedCond;

    transient Condition[] gridLocConds;

    transient String cachePrefix;

    private transient List<MimeType> formats;

    transient HashMap<GridLocObj, Boolean> procQueue;

    transient Integer cacheLockWait;

    public transient volatile Boolean isInitialized;

    transient CacheFactory initCacheFactory;

    private static transient Log log;

    public WMSLayer(String layerName, CacheFactory cacheFactory)
            throws GeoWebCacheException {
        name = layerName;
        initCacheFactory = cacheFactory;
    }
    
    public void lazyLayerInitialization(CacheFactory cf) {
        WMS_URL = "wmsurl";
        WMS_SRS = "srs";
        WMS_BBOX = "bbox";
        WMS_STYLES = "wmsstyles";
        WMS_METATILING = "metatiling";
        WMS_TRANSPARENT = "transparent";
        WMS_VENDOR_PARAMS = "vendorparameters";
        CACHE_NEVER = 0;
        CACHE_VALUE_UNSET = -1;
        CACHE_NEVER_EXPIRE = -2;
        CACHE_USE_WMS_BACKEND_VALUE = -4;
        WMS_MIMETYPES = "mimetypes";
        zoomStart = 0;
        zoomStop = 20;
        request = "GetMap";
        curWmsURL = 0;
        wmsLayers = "topp:states";
        saveExpirationHeaders = false;
        expireClients = CACHE_USE_WMS_BACKEND_VALUE;
        expireCache = CACHE_NEVER_EXPIRE;
        layerLock = new ReentrantLock();
        layerLocked = false;
        layerLockedCond = layerLock.newCondition();
        procQueue = new HashMap<GridLocObj, Boolean>();
        cacheLockWait = -1;
        log = LogFactory.getLog(org.geowebcache.layer.wms.WMSLayer.class);
        initCacheFactory = cf;
        
        isInitialized();
            
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
            setParametersFromProperties(initCacheFactory);
        } catch (GeoWebCacheException gwce) {
            log.error(gwce.getMessage());
            gwce.printStackTrace();
        }

        // Create conditions for tile locking
        this.gridLocConds = new Condition[17];
        for (int i = 0; i < gridLocConds.length; i++) {
            gridLocConds[i] = layerLock.newCondition();
        }

        // Unset variables for garbage collection
        initCacheFactory = null;

        return new Boolean(true);
    }

    /**
     * The main function
     * 
     * 1) Create cache key, test whether we can retrieve without locking 2) Get
     * lock for metatile, monitor condition variable if not (Recheck cache after
     * signal) 3) Create metatile request, execute 4) Get tiles and save them to
     * cache 5) Unlock metatile, signal other threads 6) Set Cache-Control,
     * return tile
     * 
     * @param wmsparams
     * @return
     */
    public Tile getResponse(Tile tile) throws GeoWebCacheException, IOException {
        MimeType mime = tile.getMimeType();

        if (mime == null) {
            mime = this.formats.get(0);
        }

        SRS tileSrs = tile.getSRS();

        int[] gridLoc = tile.getTileIndex();
        int idx = getSRSIndex(tileSrs);

        // Final preflight check, throws exception if necessary
        gridCalc[idx].locationWithinBounds(gridLoc);

        if (tryCacheFetch(tile)) {
            return finalizeTile(tile);
        }

        // Okay, so we need to go to the backend
        if (mime.supportsTiling()) {
            return getMetatilingReponse(tile);
        } else {
            return getNonMetatilingReponse(tile);
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
    private Tile getMetatilingReponse(Tile tile) throws GeoWebCacheException {

        int idx = this.getSRSIndex(tile.getSRS());
        int[] gridLoc = tile.getTileIndex();

        WMSMetaTile metaTile = new WMSMetaTile(this, tile.getSRS(), tile
                .getMimeType(), gridCalc[idx].getGridBounds(gridLoc[2]),
                gridLoc, metaWidthHeight[0], metaWidthHeight[1]);

        // Leave a hint to save expiration, if necessary
        if (saveExpirationHeaders) {
            metaTile.setExpiresHeader(CACHE_USE_WMS_BACKEND_VALUE);
        }

        int[] metaGridLoc = metaTile.getMetaGridPos();
        GridLocObj metaGlo = new GridLocObj(metaGridLoc);
        int condIdx = this.calcLocCondIdx(metaGridLoc);

        /** ****************** Acquire lock ******************* */
        waitForQueue(metaGlo, condIdx);
        try {
            /** ****************** Check cache again ************** */
            if (tryCacheFetch(tile)) {
                // Someone got it already, return lock and we're done
                removeFromQueue(metaGlo, condIdx);
                return finalizeTile(tile);
            }

            /** ****************** No luck, Request metatile ****** */
            // Leave a hint to save expiration, if necessary
            if (saveExpirationHeaders) {
                metaTile.setExpiresHeader(CACHE_USE_WMS_BACKEND_VALUE);
            }

            byte[] response = WMSHttpHelper.makeRequest(metaTile);

            if (metaTile.getError() || response == null) {
                throw new GeoWebCacheException(
                        "Empty metatile, error message: "
                                + metaTile.getErrorMessage());
            }

            if (saveExpirationHeaders) {
                saveExpirationInformation(tile.getExpiresHeader());
            }

            metaTile.setImageBytes(response);

            boolean useJAI = true;
            if (tile.getMimeType() == ImageMime.jpeg) {
                useJAI = false;
            }

            metaTile.createTiles(width, height, useJAI);

            int[][] gridPositions = metaTile.getTilesGridPositions();

            tile.setContent(getTile(gridLoc, gridPositions, metaTile));

            // TODO separate thread
            if (expireCache != CACHE_NEVER) {
                saveTiles(gridPositions, metaTile, tile);
            }

            /** ****************** Return lock and response ****** */
        } finally {
            removeFromQueue(metaGlo, condIdx);
        }
        return finalizeTile(tile);
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
    private Tile getNonMetatilingReponse(Tile tile) throws GeoWebCacheException {
        // String debugHeadersStr = null;
        int[] gridLoc = tile.getTileIndex();
        int condIdx = this.calcLocCondIdx(gridLoc);
        GridLocObj glo = new GridLocObj(gridLoc);

        /** ****************** Acquire lock ******************* */
        waitForQueue(glo, condIdx);
        try {
            /** ****************** Check cache again ************** */
            if (tryCacheFetch(tile)) {
                // Someone got it already, return lock and we're done
                removeFromQueue(glo, condIdx);
                return tile;
                // return this.createTileResponse(tile.getData(), -1, mime,
                // response);
            }

            /** ****************** Tile ******************* */
            // String requestURL = null;
            // Leave a hint to save expiration, if necessary
            if (saveExpirationHeaders) {
                tile.setExpiresHeader(CACHE_USE_WMS_BACKEND_VALUE);
            }

            tile = doNonMetatilingRequest(tile);

            if (tile.getStatus() > 299 || expireCache != CACHE_NEVER) {
                cache.set(cacheKey, tile, expireCache);
            }

            if (saveExpirationHeaders) {
                saveExpirationInformation(tile.getExpiresHeader());
            }

            /** ****************** Return lock and response ****** */
        } finally {
            removeFromQueue(glo, condIdx);
        }
        return finalizeTile(tile);
    }

    public boolean tryCacheFetch(Tile tile) {
        if (expireCache != CACHE_NEVER) {
            try {
                return cache.get(this.cacheKey, tile, expireCache);
            } catch (CacheException ce) {
                log.error(ce.getMessage());
                tile.setError(ce.getMessage());
                return false;
            } catch (GeoWebCacheException gwce) {
                log.error(gwce.getMessage());
                tile.setError(gwce.getMessage());
                return false;
            }
        }
        return false;
    }

    public int purge(OutputStream os) throws GeoWebCacheException {
        // Loop over directories
        // Not implemented
        throw new GeoWebCacheException("purge() has not been implemented yet."
                + " Maybe you want to sponsor it? ;) ");
        // return 0;
    }

    /**
     * Uses the HTTP 1.1 spec to set expiration headers
     * 
     * @param response
     */
    public void setExpirationHeader(HttpServletResponse response) {
        if (expireClients == CACHE_VALUE_UNSET) {
            return;
        }

        // TODO move to TileResponse
        if (expireClients > 0) {
            int seconds = (int) (expireClients / 1000);
            response.setHeader("Cache-Control", "max-age=" + seconds
                    + ", must-revalidate");
            response.setHeader("Expires", ServletUtils
                    .makeExpiresHeader(seconds));
        } else if (expireClients == CACHE_NEVER_EXPIRE) {
            long oneYear = 3600 * 24 * 365;
            response.setHeader("Cache-Control", "max-age=" + oneYear);
            response.setHeader("Expires", ServletUtils
                    .makeExpiresHeader((int) oneYear));
        } else if (expireClients == CACHE_NEVER) {
            response.setHeader("Cache-Control", "no-cache");
        } else if (expireClients == CACHE_USE_WMS_BACKEND_VALUE) {
            int seconds = 36000;
            response.setHeader("geowebcache-error",
                    "No real CacheControl information available");
            response.setHeader("Cache-Control", "max-age=" + seconds);
            response.setHeader("Expires", ServletUtils
                    .makeExpiresHeader(seconds));
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
            Tile tileProto) throws GeoWebCacheException {

        for (int i = 0; i < gridPositions.length; i++) {
            int[] gridPos = gridPositions[i];

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            try {
                boolean completed = metaTile.writeTileToStream(i, out);
                if (!completed) {
                    log.error("metaTile.writeTileToStream returned false,"
                            + " no tiles saved");
                }
            } catch (IOException ioe) {
                log.error("Unable to write image tile to "
                        + "ByteArrayOutputStream: " + ioe.getMessage());
                ioe.printStackTrace();
            }

            Tile tile = new Tile(this, tileProto.getSRS(), gridPos, tileProto
                    .getMimeType(), metaTile.getStatus(), out.toByteArray());
            tile.setTileLayer(this);
            cache.set(this.cacheKey, tile, expireCache);
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
            WMSMetaTile metaTile) throws GeoWebCacheException {
        for (int i = 0; i < gridPositions.length; i++) {
            int[] curPos = gridPositions[i];

            if (curPos[0] == gridPos[0] && curPos[1] == gridPos[1]
                    && curPos[2] == gridPos[2]) {

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try {
                    metaTile.writeTileToStream(i, out);
                } catch (IOException ioe) {
                    log.error("Unable to write image tile "
                            + "to ByteArrayOutputStream: " + ioe.getMessage());
                    ioe.printStackTrace();
                }

                byte[] data = out.toByteArray();
                return data;
            }
        }
        throw new GeoWebCacheException(
                "Bug: WMSLayer.getTile() didn't have tile...");
    }

    public Tile doNonMetatilingRequest(Tile tile) throws GeoWebCacheException {
        byte[] response = WMSHttpHelper.makeRequest(tile);

        if (tile.getError() || response == null) {
            throw new GeoWebCacheException("Empty tile, error message: "
                    + tile.getErrorMessage());
        }

        tile.setContent(response);
        return tile;
    }

    private Tile finalizeTile(Tile tile) {
        if (tile.getStatus() == 0 && !tile.getError()) {
            tile.setStatus(200);
        }
        if (tile.servletResp != null) {
            this.setExpirationHeader(tile.servletResp);
        }
        return tile;
    }

    /**
     * 
     * @param props
     * @throws CacheException
     */
    private void setParametersFromProperties(CacheFactory cacheFactory)
            throws GeoWebCacheException {
        // everything that happens in profile construction should happen here

        if (expireCache == CACHE_USE_WMS_BACKEND_VALUE
                || expireClients == CACHE_USE_WMS_BACKEND_VALUE) {
            this.saveExpirationHeaders = true;
        }
        // wms urls
        wmsURL = WMSurl.split(",");

        // mimetypes
        this.formats = new ArrayList<MimeType>();
        for (String fmt : mimeFormats) {
            formats.add(MimeType.createFromFormat(fmt));
        }
        if (formats.get(0) == null)
            formats.add(0, ImageMime.createFromFormat("image/png"));

        double[] maxTileWidth = new double[this.grids.size()];
        double[] maxTileHeight = new double[this.grids.size()];

        for (Grid g : this.grids) {
            SRS curSrs = g.getProjection();
            int index = this.grids.indexOf(g);

            if (curSrs.getNumber() == 4326) {
                maxTileWidth[index] = 180.0;
                maxTileHeight[index] = 180.0;
            } else if (curSrs.getNumber() == 900913) {
                maxTileWidth[index] = 20037508.34 * 2;
                maxTileHeight[index] = 20037508.34 * 2;
            } else {
                // TODO some fancy code to guess other SRSes, throw exception
                // for now
                maxTileWidth[index] = 20037508.34 * 2;
                maxTileHeight[index] = 20037508.34 * 2;
                log
                        .error("GeoWebCache only handles EPSG:4326 and EPSG:900913!");
                throw new GeoWebCacheException(
                        "GeoWebCache only handles EPSG:4326 and EPSG:900913!");
            }
        }

        // Create a grid calculator based on this information
        gridCalc = new GridCalculator[this.grids.size()];
        for (Grid g : this.grids) {
            int i = this.grids.indexOf(g);
            gridCalc[i] = new GridCalculator(g.getGridBounds(), g.getBounds(),
                    zoomStart, zoomStop, metaWidthHeight[0],
                    metaWidthHeight[1], maxTileWidth[i], maxTileHeight[i]);
        }

        // Cache and CacheKey

        cache = cacheFactory.getDefaultCache();
        if (cache == null) {
            log.error("Unable to get default cache.");
        }

        cacheKey = cacheFactory.getCacheKeyFactory().getCacheKey(
                cache.getDefaultKeyBeanId());
        String sanitizedName = name.replace(':', '_');
        cachePrefix = cache.getDefaultPrefix(sanitizedName);
        log.warn("cachePrefix not defined for layer " + name
                + ", using default prefifx and name instead: " + cachePrefix);

        // Initialize the cache
        cache.setUp(cachePrefix);

    }

    protected void saveExpirationInformation(long backendExpire) {
        this.saveExpirationHeaders = false;
        try {
            if (expireCache == CACHE_USE_WMS_BACKEND_VALUE) {
                Long expire = backendExpire;
                if (expire == null || expire == -1) {
                    expire = Long.valueOf(7200 * 1000);
                } else {
                    // Go from seconds to milliseconds
                    expire = expire * 1000;
                }
                log
                        .error("Layer profile wants MaxAge from backend,"
                                + " but backend does not provide this. Setting to 7200 seconds.");
                expireCache = expire.longValue();
                log.trace("Setting expireCache to: " + expireCache);
            }
            if (expireClients == CACHE_USE_WMS_BACKEND_VALUE) {
                Long expire = backendExpire;
                if (expire == null || expire == -1) {
                    expire = Long.valueOf(7200 * 1000);
                } else {
                    // Go from seconds to milliseconds
                    expire = expire * 1000;
                }
                log
                        .error("Layer profile wants MaxAge from backend,"
                                + " but backend does not provide this. Setting to 7200 seconds.");
                expireClients = expire.longValue();
                log.trace("Setting expireClients to: " + expireClients);
            }
        } catch (Exception e) {
            // Sometimes this doesn't work (network conditions?),
            // and it's really not worth getting caught up on it.
            e.printStackTrace();
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
            return this.formats.get(0);
        } else {
            return MimeType.createFromFormat(strFormat);
        }
    }

    public WMSParameters getWMSParamTemplate() {
        wmsparams = new WMSParameters();
        wmsparams.setRequest(request);
        wmsparams.setVersion(version);
        wmsparams.setLayer(this.name);
        wmsparams.setErrorMime(errormime);

        wmsparams.setIsTransparent(transparent);
        wmsparams.setIsTiled(tiled);

        if (bgcolor != null) {
            wmsparams.setBgColor(bgcolor);
        }
        if (palette != null) {
            wmsparams.setPalette(palette);
        }
        if (vendorParameters != null) {
            wmsparams.setVendorParams(vendorParameters);
        }
        if (wmsStyles != null) {
            wmsparams.setStyles(wmsStyles);
        }
        wmsparams.setFormat(formats.get(0).getFormat());
        return wmsparams;
    }

    /**
     * Get the WMS backend URL that should be used next according to the round
     * robin.
     * 
     * @return the next URL
     */
    protected String nextWmsURL() {
        curWmsURL = (curWmsURL + 1) % wmsURL.length;
        return wmsURL[curWmsURL];
    }

    public void destroy() {
        cache.destroy();
        // Not that it really matters:
        procQueue.clear();
    }

    public int[][] getCoveredGridLevels(int srsIdx, BBOX bounds) {
        BBOX adjustedBounds = bounds;
        if (!this.grids.get(srsIdx).getBounds().contains(bounds)) {
            adjustedBounds = BBOX.intersection(this.grids.get(srsIdx)
                    .getBounds(), bounds);
            log.warn("Adjusting bounds from " + bounds.toString() + " to "
                    + adjustedBounds.toString());
        }
        return this.gridCalc[srsIdx].coveredGridLevels(adjustedBounds);
    }

    public int[] getMetaTilingFactors() {
        return metaWidthHeight;
    }

    public int[] getGridLocForBounds(int srsIdx, BBOX tileBounds)
            throws ServiceException {
        return gridCalc[srsIdx].gridLocation(tileBounds);
    }

    public MimeType getDefaultMimeType() {
        return formats.get(0);
    }

    public BBOX getBboxForGridLoc(int srsIdx, int[] gridLoc) {
        return gridCalc[srsIdx].bboxFromGridLocation(gridLoc);
    }

    public int[][] getZoomInGridLoc(int srsIdx, int[] gridLoc) {
        return gridCalc[srsIdx].getZoomInGridLoc(gridLoc);
    }

    public int[] getZoomedOutGridLoc(int srsIdx) {
        return gridCalc[srsIdx].getZoomedOutGridLoc();
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
     * Acquires lock for the entire layer, returns only after all other requests
     * that could write to the queue have finished
     */
    public void acquireLayerLock() {
        boolean wait = true;
        // Wait until the queue is free
        while (wait) {
            try {
                layerLock.lock();
                this.layerLocked = true;
                if (this.procQueue == null || this.procQueue.size() == 0) {
                    wait = false;
                }
            } finally {
                layerLock.unlock();
            }
        }
    }

    /**
     * Releases lock for the entire layer, signals threads that have been kept
     * waiting
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
        // int condIdx = getLocCondIdx(gridLoc);

        while (retry) {
            layerLock.lock();
            try {
                // Check for global lock
                if (layerLocked) {
                    this.layerLockedCond.await();
                } else if (this.procQueue.containsKey(glo)) {
                    // System.out.println(Thread.currentThread().getId()
                    // + " WAITING FOR "+glo.toString()+ " convar " + condIdx);
                    hasWaited = true;
                    this.gridLocConds[condIdx].await();
                    // System.out.println(Thread.currentThread().getId()
                    // + " WAKING UP "+glo.toString()+ " convar " + condIdx);
                } else {
                    this.procQueue.put(glo, true);
                    retry = false;
                    // System.out.println(Thread.currentThread().getId()
                    // + " CONTINUES "+glo.toString()+ " convar " + condIdx);
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
     * @param gridLoc
     *            the grid positions of the tile (bottom left of metatile)
     * @return
     */
    protected void removeFromQueue(GridLocObj glo, int condIdx) {
        layerLock.lock();
        try {
            // System.out.println(Thread.currentThread().getId()
            // + " DONE, SIGNALLING "+glo.toString() + " convar " + condIdx);
            this.procQueue.remove(glo);
            this.gridLocConds[condIdx].signalAll();
        } finally {
            layerLock.unlock();
        }
    }

    private int calcLocCondIdx(int[] gridLoc) {
        return (gridLoc[0] * 7 + gridLoc[1] * 13 + gridLoc[2] * 5)
                % gridLocConds.length;
    }

    public int getZoomStart() {
        return this.zoomStart;
    }

    public int getZoomStop() {
        return this.zoomStop;
    }

    public List<MimeType> getMimeTypes() {
        return formats;
    }

    public double[] getResolutions(int srsIdx) {
        return gridCalc[srsIdx].getResolutions(this.width);
    }

    public String getStyles() {
        return wmsStyles;
    }

    /**
     * 
     * @param tile
     * @param ck
     * @param gridLoc
     * @throws CacheException
     */
    public void putTile(Tile tile) throws GeoWebCacheException {
        int condIdx = this.calcLocCondIdx(tile.getTileIndex());
        GridLocObj glo = new GridLocObj(tile.getTileIndex());

        /** ****************** Acquire lock ******************* */
        waitForQueue(glo, condIdx);

        /** ****************** Tile ******************* */
        if (expireCache != CACHE_NEVER) {
            cache.set(this.cacheKey, tile, expireCache);
        }

        /** ****************** Return lock and response ****** */
        removeFromQueue(glo, condIdx);
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public void setErrorMime(String errormime) {
        this.errormime = errormime;
    }

    public void addMetaWidthHeight(int w, int h) {
        this.metaWidthHeight[0] = w;
        this.metaWidthHeight[1] = h;
    }

    public void setWMSurl(String wmsurl) {
        this.WMSurl = wmsurl;
    }

    public String getWMSurl() {
        return this.WMSurl;
    }

    public void setWidthHeight(int w, int h) {
        this.width = h;
        this.height = h;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setTiled(boolean tiled) {
        this.tiled = tiled;
    }

    public void setTransparent(boolean transparent) {
        this.transparent = transparent;
    }

    public void setDebugHeaders(boolean debugheaders) {
        this.debugheaders = debugheaders;
    }
}
