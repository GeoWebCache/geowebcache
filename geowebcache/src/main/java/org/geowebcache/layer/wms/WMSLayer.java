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
import java.util.Properties;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.cache.Cache;
import org.geowebcache.cache.CacheException;
import org.geowebcache.cache.CacheFactory;
import org.geowebcache.cache.CacheKey;
import org.geowebcache.layer.RawTile;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileRequest;
import org.geowebcache.layer.TileResponse;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.service.wms.WMSParameters;
import org.geowebcache.util.wms.BBOX;

public class WMSLayer implements TileLayer {
    private static Log log = LogFactory
            .getLog(org.geowebcache.layer.wms.WMSLayer.class);

    public static final String WMS_MIMETYPES = "mimetypes";

    String name;

    WMSLayerProfile profile;

    Cache cache;

    CacheKey cacheKey;

    String cachePrefix = null;

    MimeType[] formats = null;

    HashMap procQueue = new HashMap();

    boolean debugHeaders = true;

    Integer cacheLockWait = -1;

    public WMSLayer(String layerName, Properties props,
            CacheFactory cacheFactory) throws GeoWebCacheException {
        name = layerName;
        setParametersFromProperties(props, cacheFactory);
    }

    /**
     * Rough checks to see whether the layers supports the requested projection,
     * returns error message otherwise
     * 
     * @param srs
     *            Name of projection, for example "EPSG:4326"
     * @return null if okay, error message otherwise.
     */
    public String supportsProjection(SRS srs) {
        for (int i = 0; i < profile.srs.length; i++) {
            if (srs.equals(profile.srs[i])) {
                return null;
            }
        }
        return "Unexpected SRS: " + srs.toString();
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
    public String supportsFormat(String strFormat) {
        if (strFormat == null) {
            log.trace("Format was null");
            return null;
        }

        for (int i = 0; i < formats.length; i++) {
            if (strFormat.equalsIgnoreCase(formats[i].getFormat())) {
                return null;
            }
        }
        return "Format " + strFormat
                + " is not supported by layer configuration";
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
    public String supportsBbox(SRS srs, BBOX reqBounds) {
        String errorMsg = this.supportsProjection(srs);
        if (errorMsg != null) {
            return errorMsg;
        }

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
     * 1) Lock metatile 2) Check whether tile is in cache -> If so, unlock
     * metatile, set Cache-Control and return tile 3) Create metatile 4) Use
     * metatile to forward request 5) Get tiles (save them to cache) 6) Unlock
     * metatile 6) Set Cache-Control, return tile
     * 
     * @param wmsparams
     * @return
     */
    public TileResponse getResponse(TileRequest tileRequest, String requestURI,
            HttpServletResponse response) throws IOException {
        String debugHeadersStr = null;
        MimeType mime = tileRequest.mimeType;

        if (mime == null) {
            mime = this.formats[0];
        }

        int[] gridLoc = tileRequest.gridLoc;

        int idx = getSRSIndex(tileRequest.SRS);

        // Final preflight check
        // TODO move outside
        String complaint = profile.gridCalc[idx].isInRange(gridLoc);

        if (complaint != null) {
            log.debug(complaint);
            response.sendError(400,
                    "The requested tile falls outside the bounds of the layer "
                            + "(" + profile.bbox[idx].toString() + ") \n"
                            + complaint);
            return null;
        }

        // System.out.println(
        // "orig: "+wmsparams.getBBOX().getReadableString());
        // System.out.println(
        // "recreated: "+profile.recreateBbox(gridLoc).getReadableString());

        WMSMetaTile metaTile = null;

        if (mime.supportsTiling()) {
            metaTile = new WMSMetaTile(tileRequest.SRS, profile.gridCalc[idx]
                    .getGridBounds(gridLoc[2]), gridLoc, profile.metaWidth,
                    profile.metaHeight, true);
        } else {
            metaTile = new WMSMetaTile(tileRequest.SRS, profile.gridCalc[idx]
                    .getGridBounds(gridLoc[2]), gridLoc, 1, 1, false);
        }

        int[] metaGridLoc = metaTile.getMetaGridPos();

        /** ****************** Acquire lock ******************* */
        waitForQueue(metaGridLoc);

        Object ck = cacheKey.createKey(cachePrefix, gridLoc[0], gridLoc[1],
                gridLoc[2], metaTile.getSRS(), mime.getFileExtension());

        if (debugHeaders) {
            debugHeadersStr = "grid-location:" + gridLoc[0] + "," + gridLoc[1]
                    + "," + gridLoc[2] + ";" + "cachekey:" + ck.toString()
                    + ";";
        }

        /** ****************** Check cache ******************* */
        RawTile tile = null;
        if (profile.expireCache != WMSLayerProfile.CACHE_NEVER) {
            try {
                tile = (RawTile) cache.get(ck, profile.expireCache);
                if (tile != null) {

                    // Return lock
                    removeFromQueue(metaGridLoc);

                    if (debugHeaders) {
                        response.addHeader("geowebcache-debug", debugHeadersStr
                                + "from-cache:true");
                    }
                    setExpirationHeader(response);
                    return new TileResponse(tile.getData(), mime);
                }
            } catch (CacheException ce) {
                log.error("Failed to get " + requestURI + " from cache");
                ce.printStackTrace();
            }
        }
        /** ****************** Request metatile ******************* */
        String requestURL = null;
        try {
            requestURL = metaTile.doRequest(profile, tileRequest.SRS, mime.getFormat());
        } catch (GeoWebCacheException gwce) {
            log.error(gwce.toString());
        }

        if (metaTile.failed || requestURL == null) {
            removeFromQueue(metaGridLoc);
            log.error("MetaTile failed.");
            return null;
        }
        saveExpirationInformation(metaTile);

        boolean useJAI = true;
        if (mime.getMimeType().equals("image/jpeg")) {
            useJAI = false;
        }
        metaTile.createTiles(profile.width, profile.height, useJAI);

        int[][] gridPositions = metaTile.getTilesGridPositions();

        byte[] data = null;
        if (profile.expireCache == WMSLayerProfile.CACHE_NEVER) {
            // Mostly for completeness, don't laugh
            data = getTile(gridLoc, gridPositions, metaTile, mime);

        } else {
            saveTiles(gridPositions, metaTile, mime);

            // Try the cache again
            try {
                tile = (RawTile) cache.get(ck, profile.expireCache);
            } catch (CacheException ce) {
                log.error("Failed to get " + requestURI
                        + " from cache, after first seeding cache.");
                ce.printStackTrace();
            }
            if (tile != null) {
                data = tile.getData();
            }

            // Final debug check, only relevant if all tiles were within bounds
            if (data == null
                    && gridPositions.length == profile.metaHeight
                            * profile.metaWidth) {
                log
                        .error("The cache returned null even after forwarding the request \n"
                                + requestURI
                                + " to \n"
                                + requestURL
                                + "\n Please check the WMS and cache backends.");
            }
        }

        // Return lock
        removeFromQueue(metaGridLoc);

        setExpirationHeader(response);
        if (debugHeaders) {
            response.addHeader("geowebcache-debug", debugHeadersStr
                    + "from-cache:false;wmsUrl:" + requestURL);
        }
        return new TileResponse(data, mime);
    }

    public int purge(OutputStream os) {
        // Loop over directories
        // Not implemented
        log
                .error("purge() has not been implemented yet. Maybe you want to sponsor it? ;) ");
        return 0;
    }

    /**
     * Uses the HTTP 1.1 spec to set expiration headers
     * 
     * @param response
     */
    private void setExpirationHeader(HttpServletResponse response) {
        if (profile.expireClients == WMSLayerProfile.CACHE_VALUE_UNSET) {
            return;
        }

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
                if (!metaTile.writeTileToStream(i, mimeType.getInternalName(),
                        out)) {
                    log.error( "metaTile.writeTileToStream returned false, no tiles saved");
                }
            } catch (IOException ioe) {
                log
                        .error("Unable to write image tile to ByteArrayOutputStream: "
                                + ioe.getMessage());
                ioe.printStackTrace();
            }

            RawTile tile = new RawTile(out.toByteArray());

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
            WMSMetaTile metaTile, MimeType imageFormat) {
        for (int i = 0; i < gridPositions.length; i++) {
            int[] curPos = gridPositions[i];

            if (curPos.equals(gridPos)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try {
                    metaTile.writeTileToStream(i,
                            imageFormat.getInternalName(), out);
                } catch (IOException ioe) {
                    log
                            .error("Unable to write image tile to ByteArrayOutputStream: "
                                    + ioe.getMessage());
                    ioe.printStackTrace();
                }

                return out.toByteArray();
            }
        }
        return null;
    }

    protected void saveExpirationInformation(WMSMetaTile metaTile) {
        if (profile.expireCache == WMSLayerProfile.CACHE_USE_WMS_BACKEND_VALUE) {
            profile.expireCache = metaTile.getExpiration();
            log.trace("Setting expireCache based on metaTile: "
                    + profile.expireCache);
        }
        if (profile.expireClients == WMSLayerProfile.CACHE_USE_WMS_BACKEND_VALUE) {
            profile.expireClients = metaTile.getExpiration();
            log.trace("Setting expireClients based on metaTile: "
                    + profile.expireClients);
        }
    }

    /**
     * 
     * @param metaGridLoc
     * @return
     */
    protected boolean waitForQueue(int[] metaGridLoc) {
        boolean wait = addToQueue(metaGridLoc);
        while (wait) {
            if (cacheLockWait > 0) {
                try {
                    Thread.sleep(cacheLockWait);
                } catch (InterruptedException ie) {
                    log.error("Thread got interrupted... how come?");
                    ie.printStackTrace();
                }
            } else {
                Thread.yield();
            }
            Thread.yield();
            wait = addToQueue(metaGridLoc);
        }
        return true;
    }

    /**
     * Synchronization function, ensures that the same metatile is not requested
     * simultaneously by two threads.
     * 
     * TODO Should add a Long representing timestamp, to avoid dead tiles
     * 
     * @param metaGridLoc
     *            the grid positions of the tile
     * @return
     */
    private synchronized boolean addToQueue(int[] metaGridLoc) {
        if (procQueue.containsKey(metaGridLoc)) {
            return false;
        } else {
            procQueue.put(metaGridLoc, new Boolean(true));
            return true;
        }
    }

    /**
     * Synchronization function, ensures that the same metatile is not requested
     * simultaneously by two threads.
     * 
     * @param metaGridLoc
     *            the grid positions of the tile
     * @return
     */
    protected synchronized boolean removeFromQueue(int[] metaGridLoc) {
        if (procQueue.containsKey(metaGridLoc)) {
            procQueue.remove(metaGridLoc);
            return true;
        }
        return false;
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
                if (formats[i] == null) {
                    log.error("Unable to match " + mimeStrs[i]
                            + " to a supported format.");
                }
            }
        }

        // Set default to image/png, if none were specified or acceptable
        if (formats == null || formats[0] == null) {
            log.error( "Unable not determine supported MIME types"
                   + " based on configuration, falling back to image/png");
            formats = new ImageMime[0];
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

    public int[] getGridLocForBounds(int srsIdx, BBOX tileBounds) {
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
}
