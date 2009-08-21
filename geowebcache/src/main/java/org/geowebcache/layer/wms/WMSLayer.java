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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.filter.request.RequestFilter;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubSet;
import org.geowebcache.grid.GridSubSetFactory;
import org.geowebcache.grid.XMLOldGrid;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.grid.SRS;
import org.geowebcache.grid.XMLSubGrid;
import org.geowebcache.layer.GridLocObj;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.mime.ErrorMime;
import org.geowebcache.mime.FormatModifier;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.TileObject;
import org.geowebcache.util.GWCVars;
import org.geowebcache.util.ServletUtils;

public class WMSLayer extends TileLayer {
    // needed in configuration object written to xml
    
    private String[] wmsUrl = null;
    
    private Integer concurrency = null;

    private String wmsLayers = null;
    
    private String wmsStyles = null;
    
    private int[] metaWidthHeight = null;
    
    protected Integer gutter;

    private String errorMime;

    private String wmsVersion;

    //Not used, should be removed through XSL
    @SuppressWarnings("unused")
    private Boolean tiled;

    private Boolean transparent;
    
    private String bgColor;

    private String palette;
    
    private String vendorParameters;
    
    //Not used, should be removed through XSL
    @SuppressWarnings("unused")
    private String cachePrefix;
    
    private String expireCache;
    
    private String expireClients;
    
    protected Integer backendTimeout;
    
    protected Boolean cacheBypassAllowed;
    
    protected Boolean queryable;
    
    protected List<ParameterFilter> parameterFilters;
    
    private transient int expireCacheInt = -1;

    private transient int expireClientsInt = -1;

    private transient int curWmsURL;

    private transient boolean saveExpirationHeaders;

    private transient Lock layerLock;

    private transient boolean layerLocked;

    private transient Condition layerLockedCond;

    private transient Condition[] gridLocConds;

    private transient List<MimeType> formats;

    private transient HashMap<GridLocObj, Boolean> procQueue;
    
    private transient ParameterFilter[] sortedModParams;
    
    private transient String[] sortedModParamsKeys;
    
    private transient boolean stylesIsModParam = false;

    private static transient Log log;
    
    /** 
     * Note XStream uses reflection, this is only used for testing
     * and loading from getCapabilities
     * 
     * @param layerName
     * @param cacheFactory
     * @param wmsURL
     * @param wmsStyles
     * @param wmsLayers
     * @param mimeFormats
     * @param grids
     * @param metaWidthHeight
     * @param vendorParams
     */
    public WMSLayer(String layerName,
            String[] wmsURL, String wmsStyles, String wmsLayers, 
            List<String> mimeFormats, Hashtable<String,GridSubSet> grids, 
            int[] metaWidthHeight, String vendorParams, boolean queryable) {
     
        name = layerName;
        this.wmsUrl = wmsURL;
        this.wmsLayers = wmsLayers;
        this.wmsStyles = wmsStyles;
        this.mimeFormats = mimeFormats;
        this.gridSubSets = grids;
        this.metaWidthHeight = metaWidthHeight;
        this.expireClientsInt = GWCVars.CACHE_USE_WMS_BACKEND_VALUE;
        this.expireCacheInt = GWCVars.CACHE_NEVER_EXPIRE;
        this.vendorParameters = vendorParams;
        this.transparent = true;
        //this.bgColor = "0x000000";
        //this.palette = "test.png";
        this.queryable = queryable;
    }

    protected Boolean initialize(GridSetBroker gridSetBroker) {
        log = LogFactory.getLog(org.geowebcache.layer.wms.WMSLayer.class);
        curWmsURL = 0;

        if (expireClients != null) {
            expireClientsInt = Integer.parseInt(expireClients);
        } else {
            saveExpirationHeaders = true;
            expireClientsInt = GWCVars.CACHE_USE_WMS_BACKEND_VALUE;
        }

        if (expireCache != null) {
            expireCacheInt = Integer.parseInt(expireCache);
        } else {
            expireCacheInt = GWCVars.CACHE_NEVER_EXPIRE;
        }

        layerLock = new ReentrantLock();
        layerLockedCond = layerLock.newCondition();
        procQueue = new HashMap<GridLocObj, Boolean>();

        try {
            initParameters();
        } catch (GeoWebCacheException gwce) {
            log.error(gwce.getMessage());
            gwce.printStackTrace();
        }

        if (this.metaWidthHeight == null || this.metaWidthHeight.length != 2) {
            this.metaWidthHeight = new int[2];
            this.metaWidthHeight[0] = 3;
            this.metaWidthHeight[1] = 3;
        }

        if (gridSubSets == null) {
            gridSubSets = new Hashtable<String, GridSubSet>();
        }

        if (this.subGrids != null) {
            Iterator<XMLSubGrid> iter = subGrids.iterator();
            while (iter.hasNext()) {
                GridSubSet gridSubSet = iter.next().getGridSubSet(gridSetBroker);
                gridSubSets.put(gridSubSet.getName(), gridSubSet);
            }
        }

        if (this.gridSubSets.size() == 0) {
            gridSubSets.put(gridSetBroker.WORLD_EPSG4326.getName(),
                    GridSubSetFactory.createGridSubSet(gridSetBroker.WORLD_EPSG4326));
            gridSubSets.put(gridSetBroker.WORLD_EPSG3857.getName(),
                    GridSubSetFactory.createGridSubSet(gridSetBroker.WORLD_EPSG3857));
        }

        // Convert version 1.1.x and 1.0.x grid objects
        if (grids != null && !grids.isEmpty()) {
            Iterator<XMLOldGrid> iter = grids.values().iterator();
            while (iter.hasNext()) {
                GridSubSet converted = iter.next().convertToGridSubset(gridSetBroker);
                gridSubSets.put(converted.getSRS().toString(), converted);
            }

            // Null it for the garbage collector
            grids = null;
        }

        // Create conditions for tile locking
        if (concurrency == null) {
            concurrency = 32;
        }

        // TODO There should be a WMSServer object and it should be on that
        this.gridLocConds = new Condition[concurrency];

        for (int i = 0; i < gridLocConds.length; i++) {
            gridLocConds[i] = layerLock.newCondition();
        }

        if (this.parameterFilters != null && this.parameterFilters.size() > 0) {
            Iterator<ParameterFilter> iter = parameterFilters.iterator();
            TreeMap<String, ParameterFilter> tree = new TreeMap<String, ParameterFilter>();

            while (iter.hasNext()) {
                ParameterFilter modParam = iter.next();
                String key = modParam.getKey();

                // STYLES is special because it is mandatory, so we need to make
                // a special case
                if (key.equalsIgnoreCase("STYLES")) {
                    stylesIsModParam = true;
                }
                tree.put(modParam.getKey(), modParam);
            }

            // this.sortedModParams = new
            // ModifiableParameter[this.modifiableParameters.size()];
            int arSize = tree.values().size();
            Iterator<ParameterFilter> sortedIter = tree.values().iterator();
            this.sortedModParams = new ParameterFilter[arSize];
            for (int i = 0; i < arSize; i++) {
                sortedModParams[i] = sortedIter.next();
            }

            this.sortedModParamsKeys = new String[sortedModParams.length];

            for (int i = 0; i < sortedModParams.length; i++) {
                sortedModParamsKeys[i] = sortedModParams[i].getKey();
            }

        }

        for (int i = 0; i < wmsUrl.length; i++) {
            String url = wmsUrl[i];
            if (!url.endsWith("?")) {
                wmsUrl[i] = url + "?";
            }
        }

        if (gutter == null) {
            gutter = Integer.valueOf(0);
        }

        if (this.requestFilters != null) {
            Iterator<RequestFilter> iter = requestFilters.iterator();
            while (iter.hasNext()) {
                try {
                    iter.next().initialize(this);
                } catch (GeoWebCacheException e) {
                    log.error(e.getMessage());
                }
            }
        }

        // Use this constructor for volatile
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
     * @throws OutsideCoverageException 
     */
    public ConveyorTile getTile(ConveyorTile tile) 
    throws GeoWebCacheException, IOException, OutsideCoverageException {
        MimeType mime = tile.getMimeType();

        if (mime == null) {
            mime = this.formats.get(0);
        }

        String tileGridSetId = tile.getGridSetId();

        long[] gridLoc = tile.getTileIndex();

        // Final preflight check, throws exception if necessary
        getGridSubSet(tileGridSetId).checkCoverage(gridLoc);
        
        if (tryCacheFetch(tile)) {
            return finalizeTile(tile);
        }

        // Okay, so we need to go to the backend
        if (mime.supportsTiling()) {
            return getMetatilingReponse(tile, true);
        } else {
            return getNonMetatilingReponse(tile, true);
        }
    }
    
    
    /**
     * Used for seeding
     */
    public void seedTile(ConveyorTile tile, boolean tryCache) throws GeoWebCacheException, IOException {     
        if (tile.getMimeType().supportsTiling()) {
            getMetatilingReponse(tile, tryCache);
        } else {
            getNonMetatilingReponse(tile, tryCache);
        }
    }
    

    /**
     * Metatiling request forwarding
     * 
     * @param tile the Tile with all the information
     * @param tryCache whether to try the cache, or seed
     * @throws GeoWebCacheException
     */
    private ConveyorTile getMetatilingReponse(ConveyorTile tile, boolean tryCache) 
    throws GeoWebCacheException {
        //int idx = this.getSRSIndex(tile.getSRS());
        long[] gridLoc = tile.getTileIndex();
        
        GridSubSet gridSubSet = tile.getGridSubSet();
        
        //GridCalculator gridCalc = getGrid(tile.getSRS()).getGridCalculator();

        WMSMetaTile metaTile = new WMSMetaTile(this, gridSubSet, 
                tile.getMimeType(), this.getFormatModifier(tile.getMimeType()),
                gridLoc, metaWidthHeight[0], metaWidthHeight[1],
                tile.getFullParameters());

        // Leave a hint to save expiration, if necessary
        if (saveExpirationHeaders) {
            metaTile.setExpiresHeader(GWCVars.CACHE_USE_WMS_BACKEND_VALUE);
        }

        long[] metaGridLoc = metaTile.getMetaGridPos();
        GridLocObj metaGlo = new GridLocObj(metaGridLoc, this.gridLocConds.length);

        /** ****************** Acquire lock ******************* */
        waitForQueue(metaGlo);
        try {
            /** ****************** Check cache again ************** */
            if (tryCache && tryCacheFetch(tile)) {
                // Someone got it already, return lock and we're done
                removeFromQueue(metaGlo);
                return finalizeTile(tile);
            }

            /** ****************** No luck, Request metatile ****** */
            // Leave a hint to save expiration, if necessary
            if (saveExpirationHeaders) {
                metaTile.setExpiresHeader(GWCVars.CACHE_USE_WMS_BACKEND_VALUE);
            }

            byte[] response = WMSHttpHelper.makeRequest(metaTile);

            if (metaTile.getError() || response == null) {
                throw new GeoWebCacheException(
                        "Empty metatile, error message: "
                                + metaTile.getErrorMessage());
            }

            if (saveExpirationHeaders) {
                // Converting to seconds
                saveExpirationInformation((int) (tile.getExpiresHeader() / 1000));
            }

            metaTile.setImageBytes(response);

            boolean useJAI = true;
            if (tile.getMimeType() == ImageMime.jpeg) {
                useJAI = false;
            }

            metaTile.createTiles(gridSubSet.getTileHeight(), gridSubSet.getTileWidth(), useJAI);

            long[][] gridPositions = metaTile.getTilesGridPositions();

            tile.setContent(getTile(gridLoc, gridPositions, metaTile));

            // TODO separate thread
            if (expireCacheInt != GWCVars.CACHE_DISABLE_CACHE) {
                saveTiles(gridPositions, metaTile, tile);
            }

            /** ****************** Return lock and response ****** */
        } finally {
            removeFromQueue(metaGlo);
        }
        return finalizeTile(tile);
    }

    /**
     * Non-metatiling forward to backend
     * 
     * @param tile the Tile with all the information
     * @param tryCache whether to try the cache, or seed
     * @throws GeoWebCacheException
     */
    private ConveyorTile getNonMetatilingReponse(ConveyorTile tile, boolean tryCache) 
    throws GeoWebCacheException {
        // String debugHeadersStr = null;
        long[] gridLoc = tile.getTileIndex();
        GridLocObj glo = new GridLocObj(gridLoc, this.gridLocConds.length);

        /** ****************** Acquire lock ******************* */
        waitForQueue(glo);
        try {
            /** ****************** Check cache again ************** */
            if (tryCache && tryCacheFetch(tile)) {
                // Someone got it already, return lock and we're done
                removeFromQueue(glo);
                return tile;
                // return this.createTileResponse(tile.getData(), -1, mime,
                // response);
            }

            /** ****************** Tile ******************* */
            // String requestURL = null;
            // Leave a hint to save expiration, if necessary
            if (saveExpirationHeaders) {
                tile.setExpiresHeader(GWCVars.CACHE_USE_WMS_BACKEND_VALUE);
            }

            tile = doNonMetatilingRequest(tile);

            if (tile.getStatus() > 299 || expireCacheInt != GWCVars.CACHE_DISABLE_CACHE) {
                tile.persist();
            }

            if (saveExpirationHeaders) {
                // Converting to seconds in the process
                saveExpirationInformation((int) (tile.getExpiresHeader() / 1000));
            }

            /** ****************** Return lock and response ****** */
        } finally {
            removeFromQueue(glo);
        }
        return finalizeTile(tile);
    }

    public boolean tryCacheFetch(ConveyorTile tile) {
        if (expireCacheInt != GWCVars.CACHE_DISABLE_CACHE) {
            try {
                return tile.retrieve(expireCacheInt * 1000);
            //} catch (StorageException ce) {
            //    log.error(ce.getMessage());
            //    tile.setError(ce.getMessage());
            //    return false;
            } catch (GeoWebCacheException gwce) {
                log.error(gwce.getMessage());
                tile.setErrorMsg(gwce.getMessage());
                return false;
            }
        }
        return false;
    }

    /**
     * Uses the HTTP 1.1 spec to set expiration headers
     * 
     * @param response
     */
    public void setExpirationHeader(HttpServletResponse response) {
        if (expireClientsInt == GWCVars.CACHE_VALUE_UNSET) {
            return;
        }

        // TODO move to TileResponse
        if (expireClientsInt > 0) {
            response.setHeader("Cache-Control", "max-age=" + expireClients + ", must-revalidate");
            response.setHeader("Expires", ServletUtils.makeExpiresHeader(expireClientsInt));
        } else if (expireClientsInt == GWCVars.CACHE_NEVER_EXPIRE) {
            long oneYear = 3600 * 24 * 365;
            response.setHeader("Cache-Control", "max-age=" + oneYear);
            response.setHeader("Expires", ServletUtils.makeExpiresHeader((int) oneYear));
        } else if (expireClientsInt == GWCVars.CACHE_DISABLE_CACHE) {
            response.setHeader("Cache-Control", "no-cache");
        } else if (expireClientsInt == GWCVars.CACHE_USE_WMS_BACKEND_VALUE) {
            int seconds = 3600;
            response.setHeader("geowebcache-error", "No real CacheControl information available");
            response.setHeader("Cache-Control", "max-age=" + seconds);
            response.setHeader("Expires", ServletUtils.makeExpiresHeader(seconds));
        }
    }
    
    public void setTileIndexHeader(ConveyorTile tile) {
        tile.servletResp.addHeader("geowebcache-tile-index", Arrays.toString(tile.getTileIndex()));
    }

    /**
     * Loops over the gridPositions, generates cache keys and saves to cache
     * 
     * @param gridPositions
     * @param metaTile
     * @param imageFormat
     */
    protected void saveTiles(long[][] gridPositions, WMSMetaTile metaTile,
            ConveyorTile tileProto) throws GeoWebCacheException {

        for (int i = 0; i < gridPositions.length; i++) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            try {
                boolean completed = metaTile.writeTileToStream(i, out);
                if (!completed) {
                    log.error("metaTile.writeTileToStream returned false, no tiles saved");
                }
            } catch (IOException ioe) {
                log.error("Unable to write image tile to "
                        + "ByteArrayOutputStream: " + ioe.getMessage());
                ioe.printStackTrace();
            }

            long[] idx = {gridPositions[i][0],gridPositions[i][1],gridPositions[i][2]};
            TileObject tile = TileObject.createCompleteTileObject(this.getName(), idx, tileProto.getGridSetId(), 
                    tileProto.getMimeType().getFormat(), tileProto.getParameters(), out.toByteArray());
            
            tileProto.getStorageBroker().put(tile);
            //ConveyorTile tile = new ConveyorTile(storageBroker,this, tileProto.getSRS(), gridPos,
            //        tileProto.getMimeType(), metaTile.getStatus(), out.toByteArray());
            //tile.setTileLayer(this);
            //tile.persist();
            
            //cache.set(this.cacheKey, tile, expireCacheInt);
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
    private byte[] getTile(long[] gridPos, long[][] gridPositions,
            WMSMetaTile metaTile) throws GeoWebCacheException {
        for (int i = 0; i < gridPositions.length; i++) {
            long[] curPos = gridPositions[i];

            // Skip all but the tile we're interested in
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

    public ConveyorTile doNonMetatilingRequest(ConveyorTile tile) throws GeoWebCacheException {
        byte[] response = WMSHttpHelper.makeRequest(tile);

        if (tile.getError() || response == null) {
            throw new GeoWebCacheException("Empty tile, error message: "
                    + tile.getErrorMessage());
        }

        tile.setContent(response);
        return tile;
    }

    private ConveyorTile finalizeTile(ConveyorTile tile) {
        if (tile.getStatus() == 0 && !tile.getError()) {
            tile.setStatus(200);
        }
        
        if (tile.servletResp != null) {
            setExpirationHeader(tile.servletResp);
            setTileIndexHeader(tile);
        }
        
        return tile;
    }

    /**
     * 
     * @param props
     * @throws CacheException
     */
    private void initParameters() throws GeoWebCacheException {
        // everything that happens in profile construction should happen here

        if (expireCacheInt == GWCVars.CACHE_USE_WMS_BACKEND_VALUE
                || expireClientsInt == GWCVars.CACHE_USE_WMS_BACKEND_VALUE) {
            this.saveExpirationHeaders = true;
        }
        
        // mimetypes
        this.formats = new ArrayList<MimeType>();
        if(mimeFormats != null) {
            for (String fmt : mimeFormats) {
                formats.add(MimeType.createFromFormat(fmt));
            }
        }
        if(formats.size() == 0) {
            formats.add(0, ImageMime.createFromFormat("image/png"));
            formats.add(1, ImageMime.createFromFormat("image/jpeg"));
        }
    }

    protected void saveExpirationInformation(int backendExpire) {
        this.saveExpirationHeaders = false;
        try {
            if (expireCacheInt == GWCVars.CACHE_USE_WMS_BACKEND_VALUE) {
                if(backendExpire == -1) {
                    expireCacheInt = 7200;
                    log.error("Layer profile wants MaxAge from backend,"
                        + " but backend does not provide this. Setting to 7200 seconds.");
                } else {
                    expireCacheInt = backendExpire;
                }
                log.trace("Setting expireCache to: " + expireCache);
            }
            if (expireClientsInt == GWCVars.CACHE_USE_WMS_BACKEND_VALUE) {
                if(backendExpire == -1) {
                    expireClientsInt = 7200;
                    log.error("Layer profile wants MaxAge from backend,"
                            + " but backend does not provide this. Setting to 7200 seconds.");
                } else {
                    expireClientsInt = backendExpire;
                }
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

    public String getWMSRequestTemplate(MimeType responseFormat) {
        FormatModifier mod = getFormatModifier(responseFormat);
        
        StringBuilder strBuilder = new StringBuilder();
        strBuilder.append("SERVICE=WMS");
        strBuilder.append("&REQUEST=GetMap");
        
        strBuilder.append("&VERSION=");
        if(wmsVersion != null) {
            strBuilder.append(wmsVersion);
        } else {
            strBuilder.append("1.1.0");
        }

        strBuilder.append("&LAYERS=");
        if(this.wmsLayers != null && this.wmsLayers.length() != 0) {
            strBuilder.append(wmsLayers);
        } else {
            strBuilder.append(name);
        }
        
        strBuilder.append("&EXCEPTIONS=");
        if(errorMime != null) {
            strBuilder.append(errorMime);
        } else {
            strBuilder.append(ErrorMime.vnd_ogc_se_inimage.getMimeType());
        }
        
        if (!stylesIsModParam) {
            strBuilder.append("&STYLES=");
            if (wmsStyles != null && wmsStyles.length() != 0) {
                strBuilder.append(wmsStyles);
            }
        }

        Boolean tmpTransparent = transparent;
        if (mod != null && mod.getTransparent() != null) {
            tmpTransparent = mod.getTransparent();
        }
        if (tmpTransparent != null) {
            if (tmpTransparent) {
                strBuilder.append("&TRANSPARENT=").append("TRUE");
            } else {
                strBuilder.append("&TRANSPARENT=").append("FALSE");
            }
        }
        

        String tmpBgColor = bgColor;
        if (mod != null && mod.getBgColor() != null) {
            tmpBgColor = mod.getBgColor();
        }
        
        if (tmpBgColor != null && tmpBgColor.length() != 0) {
            strBuilder.append("&BGCOLOR=").append(tmpBgColor);
        }
        
        String tmpPalette = palette;
        if (mod != null && mod.getPalette() != null) {
            tmpPalette = mod.getPalette();
        }
        if (tmpPalette != null && tmpPalette.length() != 0) {
            strBuilder.append("&PALETTE=").append(tmpPalette);
        }
        
        if (vendorParameters != null && vendorParameters.length() != 0) {
            if(! vendorParameters.startsWith("&"))
                strBuilder.append("&");
            
            strBuilder.append(vendorParameters);
        }
        
        return strBuilder.toString();
    }

    /**
     * Get the WMS backend URL that should be used next according to the round
     * robin.
     * 
     * @return the next URL
     */
    protected String nextWmsURL() {
        curWmsURL = (curWmsURL + 1) % wmsUrl.length;
        return wmsUrl[curWmsURL];
    }

    public void destroy() {
        // Not that it really matters:
        procQueue.clear();
    }

    /* public int[][] getCoveredGridLevels(SRS srs, BBOX bounds)
    throws GeoWebCacheException {
        BBOX adjustedBounds = bounds;
        GridSubSet grid = gridSets.get(srs);
        
        if (! grid.getBounds().contains(bounds) ) {
            adjustedBounds = BBOX.intersection(grid.getBounds(), bounds);
            log.warn("Adjusting bounds from " + bounds.toString() + " to "
                    + adjustedBounds.toString());
        }
        return grid.getGridCalculator().coveredGridLevels(adjustedBounds);
    } */

    public int[] getMetaTilingFactors() {
        return metaWidthHeight;
    }

    public long[] indexFromBounds(String gridSetId, BoundingBox tileBounds) {
        return gridSubSets.get(gridSetId).closestIndex(tileBounds);
    }

    public MimeType getDefaultMimeType() {
        return formats.get(0);
    }

    // TODO Move these to TileLayer
    public BoundingBox boundsFromIndex(String gridSetId, long[] gridLoc) {
        return gridSubSets.get(gridSetId).boundsFromIndex(gridLoc);
    }

    public long[][] getZoomedInGridLoc(String gridSetId, long[] gridLoc)
    throws GeoWebCacheException {
        return null;
    }

    //public long[] getZoomedOutIndex(String gridSetId)
    //throws GeoWebCacheException {
    //    return gridSubSets.get(gridSetId).getCoverageBestFit();
    //}
    
    //public BBOX getZoomedOutBbox(SRS srs)
    //throws GeoWebCacheException {
    //    int[] loc = getZoomedOutGridLoc();
    //    if(loc[2] == -1) {
    //        if(srs.equals(SRS.getEPSG4326())) {
    //            return BBOX.WORLD4326;
    //        } else {
    //            int[] fourTuple = calc.getGridBounds()[0];
    //            int[] fiveTuple = {fourTuple[0], fourTuple[1], fourTuple[2], fourTuple[3], 0};
    //            return calc.bboxFromGridBounds(fiveTuple);
    //        }
    //    } else {
    //        return calc.bboxFromGridLocation(loc);
    //    }
    //}

    /**
     * Acquires lock for the entire layer, returns only after all other requests
     * that could write to the queue have finished
     */
    public void acquireLayerLock() {
        if(layerLock == null) {
            layerLocked = true;
            return;
        }
        
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
        if(layerLock == null) {
            layerLocked = false;
            return;
        }
        
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
    protected boolean waitForQueue(GridLocObj glo) {
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
                    this.gridLocConds[glo.hashCode()].await();
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
    protected void removeFromQueue(GridLocObj glo) {
        layerLock.lock();
        try {
            // System.out.println(Thread.currentThread().getId()
            // + " DONE, SIGNALLING "+glo.toString() + " convar " + condIdx);
            this.procQueue.remove(glo);
            this.gridLocConds[glo.hashCode()].signalAll();
        } finally {
            layerLock.unlock();
        }
    }

    public List<MimeType> getMimeTypes() {
        return formats;
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
    public void putTile(ConveyorTile tile) throws GeoWebCacheException {
        GridLocObj glo = new GridLocObj(tile.getTileIndex(), this.gridLocConds.length);

        /** ****************** Acquire lock ******************* */
        waitForQueue(glo);

        /** ****************** Tile ******************* */
        if (expireCacheInt != GWCVars.CACHE_DISABLE_CACHE) {
            tile.persist();
        }

        /** ****************** Return lock and response ****** */
        removeFromQueue(glo);
    }

    public void setErrorMime(String errormime) {
        this.errorMime = errormime;
    }

    public void addMetaWidthHeight(int w, int h) {
        this.metaWidthHeight[0] = w;
        this.metaWidthHeight[1] = h;
    }

    public void setWMSurl(String[] wmsurl) {
        this.wmsUrl = wmsurl;
    }

    public String[] getWMSurl() {
        return this.wmsUrl;
    }

    public Boolean isCacheBypassAllowed() {
        return cacheBypassAllowed;
    }

    public void setCacheBypassAllowed(boolean allowed) {
        cacheBypassAllowed = Boolean.valueOf(allowed);
    }
    
    public boolean isQueryable() {
        if(queryable != null && queryable) {
            return true;
        } else {
            return false;
        }
    }
    
    public Integer getBackendTimeout() {
        return backendTimeout;
    }

    public void setBackendTimeout(int seconds) {
        backendTimeout = seconds;
    }
    
    public int getExpireClients() {
        if(expireClientsInt > 0) {
            return expireClientsInt;
        } else if(expireClientsInt == GWCVars.CACHE_USE_WMS_BACKEND_VALUE|| expireClientsInt == 0) {
            return 7200;
        } else if(expireClientsInt == GWCVars.CACHE_NEVER_EXPIRE) {
            return 3600*24*365;
        } else {
            return -1;
        }
    }
    
    public void setVersion(String version) {
        this.wmsVersion = version;
    }

    public void setTiled(boolean tiled) {
        this.tiled = tiled;
    }

    public void setTransparent(boolean transparent) {
        this.transparent = transparent;
    }

    public ConveyorTile getNoncachedTile(ConveyorTile tile, boolean requestTiled)
            throws GeoWebCacheException {

        byte[] data = WMSHttpHelper.makeRequest(tile,requestTiled);        
        tile.setContent(data);

        return tile;
    }

    /**
     * 
     * @param map
     * @return {full query string with default, query string with modifiers} 
     * @throws GeoWebCacheException
     */
    public String[] getModifiableParameters(Map<String,String[]> map) throws GeoWebCacheException {
        String[] paramStrs = new String[2];
        
        if(sortedModParamsKeys == null)
            return null;
        
        String[] values = ServletUtils.selectedStringsFromMap(map, sortedModParamsKeys);
        
        StringBuilder strModifiers = new StringBuilder();
        StringBuilder strFull = new StringBuilder();
        
        for(int i = 0; i<values.length; i++) {
            String value = values[i];
            ParameterFilter modParam = this.sortedModParams[i];
            
            if(value == null || value.length() == 0) {
                strFull.append("&").append(modParam.getKey()).append("=").append(modParam.getDefaultValue());
            } else {
                String filteredValue = modParam.apply(value);
                strModifiers.append("&").append(modParam.getKey()).append("=").append(filteredValue);
            }
        }
        
        paramStrs[1] = strModifiers.toString();
        paramStrs[0] = strFull.append(strModifiers).toString();
        
        return paramStrs;
    }
        
    public void mergeWith(WMSLayer otherLayer) throws GeoWebCacheException {
        if (otherLayer.parameterFilters != null) {
            if (this.parameterFilters != null) {
                Iterator<ParameterFilter> iter = otherLayer.parameterFilters.iterator();
                while (iter.hasNext()) {
                    this.parameterFilters.add(iter.next());
                }
            } else {
                this.parameterFilters = otherLayer.parameterFilters;
            }
        }
    }

    @Override
    public int[][] getCoveredGridLevels(SRS srs, BoundingBox bounds)
            throws GeoWebCacheException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public long[][] getZoomedInIndexes(String gridSetId, long[] gridLoc)
            throws GeoWebCacheException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public BoundingBox getZoomedOutBounds(SRS srs) throws GeoWebCacheException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int[] getZoomedOutIndex(SRS srs) throws GeoWebCacheException {
        // TODO Auto-generated method stub
        return null;
    }
    
}
