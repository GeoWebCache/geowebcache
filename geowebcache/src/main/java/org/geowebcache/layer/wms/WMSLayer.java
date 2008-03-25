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
import org.geowebcache.cache.Cache;
import org.geowebcache.cache.CacheException;
import org.geowebcache.cache.CacheFactory;
import org.geowebcache.cache.CacheKey;
import org.geowebcache.cache.CacheKeyFactory;
import org.geowebcache.layer.RawTile;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileRequest;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.mime.MimeType;
import org.geowebcache.service.wms.WMSParameters;
import org.geowebcache.util.wms.BBOX;

public class WMSLayer implements TileLayer {
    private static Log log = LogFactory
            .getLog(org.geowebcache.layer.wms.WMSLayer.class);

    String name;

    LayerProfile profile;

    Cache cache;

    CacheKey cacheKey;

    ImageMime[] mimes = null;

    HashMap procQueue = new HashMap();

    boolean debugHeaders = false;

    Integer cacheLockWait = -1;

    public WMSLayer(String layerName, Properties props) throws CacheException {
        name = layerName;
        setParametersFromProperties(props);
    }

    /**
     * Rough checks to see whether the layers supports
     * the requested projection, returns error message otherwise
     * 
     * @param srs Name of projection, for example "EPSG:4326"
     * @return null if okay, error message otherwise.
     */
    public String supportsProjection(String srs) {
        if (srs.equalsIgnoreCase(profile.srs)) {
            return null;
            
        } else {
            return "Unexpected SRS: " + srs + " , expected "
                    + profile.srs;
        }   
    }
    
    /**
     * Rough checks to see whether the layers supports
     * the requested mimeType. Null assumes the default format
     * and is supported.
     * 
     * Returns error message otherwise
     * 
     * @param mimeType MIME type or null, example "image/png"
     * @return null if okay, error message otherwise.
     */
    public String supportsMime(String mimeType) {
        if (mimeType == null) {
            log.trace("MIME type was null");
            return null;
        }
        
        for(int i=0; i<mimes.length; i++) {
            if(mimeType.equalsIgnoreCase(mimes[i].getMimeType())) {
                return null;
            }
        }
        return "MIME type " +mimeType
            + " is not supported by layer configuration";
    }
    
    /**
     * Rough checks to see whether the specified bounding box
     * is supported by the current layer.
     * 
     * Returns error message if not.
     * 
     * @param srs the string representation 
     * @param reqBounds the requested bounds
     * @return null if okay, error message otherwise.
     */
    public String supportsBbox(String srs, BBOX reqBounds) {
        String errorMsg = this.supportsProjection(srs);
        if(errorMsg != null) {
            return errorMsg;
        }

        if (!reqBounds.isSane()) {
            return "The requested bounding box " + reqBounds.getReadableString()
            + " is not sane";
        }

        if (!profile.gridBase.contains(reqBounds)) {
            return "The layers grid box "
            + profile.gridBase.getReadableString()
            + " does not cover the requested bounding box "
            + reqBounds.getReadableString();
        }
        
        // All ok
        return null;
    }
    

    /**
     * Wrapper for getData() below
     * 
     * @param wmsparams
     * @param response
     * @return
     * @throws IOException
     */
    //public byte[] getData(WMSParameters wmsParams, HttpServletResponse response)
    //throws IOException {
    //    int[] gridLoc = profile.gridCalc.gridLocation(wmsParams.getBBOX());
    //    
    //    return getData(gridLoc, wmsParams.getImageMime(), wmsParams.toString(), response);
    //}

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
    public byte[] getData(TileRequest tileRequest, String requestURI,
            HttpServletResponse response) throws IOException {
        String debugHeadersStr = null;
        ImageMime mime = null;
        
        if(tileRequest.mimeType == null) {
            mime = this.mimes[0];
        } else {
            mime = ImageMime.createFromMimeType(tileRequest.mimeType);
        }
        int[] gridLoc = tileRequest.gridLoc;
        
        // Final preflight check
        // TODO move outside
        String complaint = null;
        if (profile.gridCalc.isInRange(gridLoc) != null) {
             complaint = "Adjusted request ("
                    + profile.gridCalc.bboxFromGridLocation(gridLoc).toString() + ")"
                    + " falls outside of the bounding box (" + profile.bbox.toString() + "),"
                    + " error: " +profile.gridCalc.isInRange(gridLoc);
        } else if(mime == null) {
            complaint = "Image format cannot be null in getData()";
        }
        
        if(complaint != null) {
            log.error(complaint);
            response.sendError(400, complaint);
            return null;
        }
        
        // System.out.println(
        // "orig: "+wmsparams.getBBOX().getReadableString());
        // System.out.println(
        // "recreated: "+profile.recreateBbox(gridLoc).getReadableString());

        WMSMetaTile metaTile = new WMSMetaTile(profile.gridCalc.getGridBounds(gridLoc[2]), 
                gridLoc, profile.metaWidth, profile.metaHeight);
        
        int[] metaGridLoc = metaTile.getMetaGridPos();

        /** ****************** Acquire lock ******************* */
        waitForQueue(metaGridLoc);

        Object ck = cacheKey.createKey(gridLoc[0], gridLoc[1], gridLoc[2],
                mime.getFileExtension());

        if (debugHeaders) {
            debugHeadersStr = "grid-location:" + gridLoc[0] + "," + gridLoc[1]
                    + "," + gridLoc[2] + ";" + "cachekey:" + ck.toString()
                    + ";";
        }

        /** ****************** Check cache ******************* */
        RawTile tile = null;
        if (profile.expireCache != LayerProfile.CACHE_NEVER) {
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
                    return tile.getData();
                }
            } catch (CacheException ce) {
                log.error("Failed to get " + requestURI
                        + " from cache");
                ce.printStackTrace();
            }
        }
        /** ****************** Request metatile ******************* */
        String requestURL = metaTile.doRequest(profile, mime.getMimeType());
        if (metaTile.failed) {
            removeFromQueue(metaGridLoc);
            log.error("MetaTile failed.");
            return null;
        }
        saveExpirationInformation(metaTile);
        metaTile.createTiles(profile.width, profile.height);
        int[][] gridPositions = metaTile.getTilesGridPositions();

        byte[] data = null;
        if (profile.expireCache == LayerProfile.CACHE_NEVER) {
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
            if(data == null && gridPositions.length == profile.metaHeight * profile.metaWidth) {
            	log.error("The cache returned null even after forwarding the request \n"
            			+ requestURI
            			+ " to \n"
            			+ requestURL
                        +"\n Please check the WMS and cache backends.");
            }
        }

        // Return lock
        removeFromQueue(metaGridLoc);

        setExpirationHeader(response);
        if (debugHeaders) {
            response.addHeader("geowebcache-debug", debugHeadersStr
                    + "from-cache:false;wmsUrl:"
                    + requestURL);
        }
        return data;
    }

    public int purge(OutputStream os) {
        // Loop over directories
        // Not implemented
    	log.error("purge() has not been implemented yet. Maybe you want to sponsor it? ;) ");
        return 0;
    }

    /**
     * Uses the HTTP 1.1 spec to set expiration headers
     * 
     * @param response
     */
    private void setExpirationHeader(HttpServletResponse response) {
        if (profile.expireClients == LayerProfile.CACHE_VALUE_UNSET) {
            return;
        }

        if (profile.expireClients > 0) {
            response.setHeader("Cache-Control", "max-age="
                    + (profile.expireClients / 1000) + ", must-revalidate");
        } else if (profile.expireClients == LayerProfile.CACHE_NEVER_EXPIRE) {
            long oneYear = 3600 * 24 * 365;
            response.setHeader("Cache-Control", "max-age=" + oneYear);
        } else if (profile.expireClients == LayerProfile.CACHE_NEVER) {
            response.setHeader("Cache-Control", "no-cache");
        } else if (profile.expireCache == LayerProfile.CACHE_USE_WMS_BACKEND_VALUE) {
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
            ImageMime imageFormat) {
        
        for (int i = 0; i < gridPositions.length; i++) {
            int[] gridPos = gridPositions[i];

            Object ck = cacheKey.createKey(gridPos[0], gridPos[1], gridPos[2],
                    imageFormat.getFileExtension());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                if( ! metaTile.writeTileToStream(i, imageFormat.getInternalName(), out)) {
                	log.error("metaTile.writeTileToStream returned false, no tiles saved");
                }
            } catch (IOException ioe) {
                log.error("Unable to write image tile to ByteArrayOutputStream: "
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
            WMSMetaTile metaTile, ImageMime imageFormat) {
        for (int i = 0; i < gridPositions.length; i++) {
            int[] curPos = gridPositions[i];

            if (curPos.equals(gridPos)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try {
                    metaTile.writeTileToStream(i, imageFormat.getInternalName(),
                            out);
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
        if (profile.expireCache == LayerProfile.CACHE_USE_WMS_BACKEND_VALUE) {
            profile.expireCache = metaTile.getExpiration();
            log.trace("Setting expireCache based on metaTile: "
                    + profile.expireCache);
        }
        if (profile.expireClients == LayerProfile.CACHE_USE_WMS_BACKEND_VALUE) {
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
    private void setParametersFromProperties(Properties props)
            throws CacheException {
        profile = new LayerProfile(this.name, props);

        // Cache and CacheKey
        String propCachetype = props.getProperty("cachetype");
        if (propCachetype != null) {
            cache = CacheFactory.getCache(propCachetype, props);
        } else {
            cache = CacheFactory.getCache(
                    "org.geowebcache.cache.file.FileCache", null);
        }

        String propCacheKeytype = props.getProperty("cachekeytype");
        if (propCacheKeytype == null) {
            cacheKey = CacheKeyFactory.getCacheKey(cache
                    .getDefaultCacheKeyName(), name);
        } else {
            cacheKey = CacheKeyFactory.getCacheKey(propCacheKeytype, name);
        }

        // Check whether the configuration specifies what MIME types are legal
        String propImageMIME = props.getProperty("imagemimes");
        if (propImageMIME != null) {
            String[] mimeStrs = propImageMIME.split(",");
            mimes = new ImageMime[mimeStrs.length];
            for (int i = 0; i < mimes.length; i++) {
                mimes[i] = ImageMime.createFromMimeType(mimeStrs[i]);
                if(mimes[i] == null) {
                	log.error("Unable to match " + mimeStrs[i] + " to a supported format.");
                }
            }
        }
        
        // Set default to image/png, if none were specified or acceptable
        if(mimes == null || mimes[0] == null) {
        	log.error("Unable not determine supported MIME types based on configuration,"
        			+" falling back to image/png");
        	mimes = new ImageMime[0];
        	mimes[0] = ImageMime.createFromMimeType("image/png");
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
    public ImageMime getImageFormat(String strFormat) {
        if(strFormat == null) {
            return this.mimes[0];
        } else {
            return ImageMime.createFromMimeType(strFormat);
        }
    }
    
    public WMSParameters getWMSParamTemplate() {
        WMSParameters ret = profile.getWMSParamTemplate();
        ret.setImageMime(mimes[0].getMimeType());
        return ret;
    }

    public void destroy() {
        cache.destroy();
        // Not that it really matters:
        procQueue.clear();
    }

    public BBOX getBounds() {
        return this.profile.bbox;
    }

    public String getProjection() {
        return this.profile.srs;
    }

    public int[][] getCoveredGridLevels(BBOX bounds) {
        BBOX adjustedBounds = bounds;
        if(!this.profile.bbox.contains(bounds)) {
            adjustedBounds = BBOX.intersection(this.profile.bbox, bounds);
            log.warn("Adjusting bounds from "
                    + bounds.toString() + " to " + adjustedBounds.toString());
        }
        return this.profile.gridCalc.coveredGridLevels(adjustedBounds);
    }

    public int[] getMetaTilingFactors() {
        int[] factorArray = {profile.metaWidth , profile.metaHeight};
        return factorArray;
    }

    public String getName() {
        return this.name;
    }

    public int[] gridLocForBounds(BBOX tileBounds) {
        return profile.gridCalc.gridLocation(tileBounds);
    }

    public MimeType getDefaultMimeType() {
        return mimes[0];
    }
    
}
