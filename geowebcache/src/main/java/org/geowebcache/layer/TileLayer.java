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

package org.geowebcache.layer;

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
import org.geowebcache.cachekey.CacheKey;
import org.geowebcache.cachekey.CacheKeyFactory;
import org.geowebcache.service.wms.WMSParameters;

public class TileLayer {
    private static Log log = LogFactory
            .getLog(org.geowebcache.layer.TileLayer.class);

    String name;

    LayerProfile profile;

    Cache cache;

    CacheKey cacheKey;

    ImageFormat[] formats = null;

    HashMap procQueue = new HashMap();

    boolean debugHeaders = false;

    Integer cacheLockWait = -1;

    Seeder seeder = null;

    public TileLayer(String layerName, Properties props) throws CacheException {
        name = layerName;
        setParametersFromProperties(props);
    }

    /**
     * Rough checks to see whether the request is within bounds and whether we
     * support the format.
     * 
     * @param wmsparams
     * @return null if okay, error message otherwise.
     */
    public String covers(WMSParameters wmsparams) {
        if (!wmsparams.getSrs().equalsIgnoreCase(profile.srs)) {
            return "Unexpected SRS: " + wmsparams.getSrs() + " , expected "
                    + profile.srs;
        }

        if (!supports(wmsparams.getImagemime().getMime())) {
            return "Unsupported MIME type requested: "
                    + wmsparams.getImagemime().getMime();
        }

        BBOX reqbox = wmsparams.getBBOX();
        if (!reqbox.isSane()) {
            return "The requested bounding box " + reqbox.getReadableString()
                    + " is not sane";
        }

        if (!profile.gridBase.contains(reqbox)) {
            return "The layers grid box "
                    + profile.gridBase.getReadableString()
                    + " does not cover the requested bounding box "
                    + reqbox.getReadableString();
        }
        // All good
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
    public byte[] getData(WMSParameters wmsparams, HttpServletResponse response)
            throws IOException {
        String debugHeadersStr = null;

        // Internal representation
        int[] gridLoc = profile.gridCalc.gridLocation(wmsparams.getBBOX());

        // Final preflight check
        if (profile.gridCalc.isInRange(gridLoc) != null) {
            String complaint = "Adjusted request ("
                    + profile.gridCalc.bboxFromGridLocation(gridLoc).toString() + ")"
                    + " falls outside of the bounding box (" + profile.bbox.toString() + "),"
                    + " error: " +profile.gridCalc.isInRange(gridLoc);
            log.error(complaint);
            response.sendError(400, complaint);
            return null;
        }
        // System.out.println("orig: "+wmsparams.getBBOX().getReadableString());
        // System.out.println("recreated:
        // "+profile.recreateBbox(gridLoc).getReadableString());

        MetaTile metaTile = new MetaTile(profile.gridCalc.getGridBounds(gridLoc[2]), 
                gridLoc, profile.metaWidth, profile.metaHeight);
        
        int[] metaGridLoc = metaTile.getMetaGridPos();

        /** ****************** Acquire lock ******************* */
        waitForQueue(metaGridLoc);

        ImageFormat imageFormat = ImageFormat.createFromMimeType(wmsparams
                .getImagemime().getMime());
        Object ck = cacheKey.createKey(gridLoc[0], gridLoc[1], gridLoc[2],
                imageFormat.getExtension());

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
                log.error("Failed to get " + wmsparams.toString()
                        + " from cache");
                ce.printStackTrace();
            }
        }
        /** ****************** Request metatile ******************* */
        String requestURL = metaTile.doRequest(profile, imageFormat.getMimeType());
        if (metaTile.failed) {
            removeFromQueue(metaGridLoc);
            log.error("MetaTile failed.");
            return null;
        }
        saveExpirationInformation(metaTile);
        metaTile.createTiles(profile);
        int[][] gridPositions = metaTile.getTilesGridPositions();

        byte[] data = null;
        if (profile.expireCache == LayerProfile.CACHE_NEVER) {
            // Mostly for completeness, don't laugh
            data = getTile(gridLoc, gridPositions, metaTile, imageFormat);

        } else {
            saveTiles(gridPositions, metaTile, imageFormat);
            
            // Try the cache again
            try {
                tile = (RawTile) cache.get(ck, profile.expireCache);
            } catch (CacheException ce) {
                log.error("Failed to get " + wmsparams.toString()
                        + " from cache, after first seeding cache.");
                ce.printStackTrace();
            }
            if (tile != null) {
                data = tile.getData();
            } else {
                log.error("The cache returned null even after forwarding the request."
                        +" Check the WMS and cache backends, you may also have found a bug.");
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

    public int seed(int zoomStart, int zoomStop, String format, BBOX bounds,
            HttpServletResponse response) throws IOException {
        if (seeder == null) {
            seeder = new Seeder(this);
        }

        String complaint = null;

        // Check that we support this
        if (bounds == null) {
            bounds = profile.bbox;
        } else {
            if (!profile.bbox.contains(bounds)) {
                complaint = "Request to seed outside of bounds: "
                        + bounds.toString();
                log.error(complaint);
                response.sendError(400, complaint);
                return -1;
            }
        }
        if (format == null) {
            log
                    .info("User did not specify format for seeding, assuming image/png.");
            format = "image/png";
        }

        ImageFormat imageFormat = ImageFormat.createFromMimeType(format);

        if (imageFormat == null || !supports(imageFormat.getMimeType())) {
            complaint = "Imageformat " + format + " is not supported by layer";
            log.error(complaint);
            response.sendError(400, complaint);
            return -1;
        }

        if (profile.expireCache == LayerProfile.CACHE_NEVER) {
            complaint = "Layers is configured to never cache!";
            log.error(complaint);
            response.sendError(400, complaint);
            return -1;
        }

        if (zoomStart < 0 || zoomStop < 0) {
            complaint = "start(" + zoomStart + ") and stop(" + zoomStop
                    + ") have to greater than zero";
            log.error(complaint);
            response.sendError(400, complaint);
            return -1;
        }
        if (zoomStart < profile.zoomStart) {
            complaint = "start(" + zoomStart
                    + ") should be greater than or equal to "
                    + profile.zoomStart;
            log.error(complaint);
            response.sendError(400, complaint);
            return -1;
        }
        if (zoomStop > profile.zoomStop) {
            complaint = "stop(" + zoomStop
                    + ") should be less than or equal to " + profile.zoomStop;
            log.error(complaint);
            response.sendError(400, complaint);
            return -1;
        }

        log.info("seeder.doSeed(" + zoomStart + "," + zoomStop + ","
                + imageFormat.getMimeType() + "," + bounds.toString()
                + ",stream)");

        int retVal = seeder.doSeed(zoomStart, zoomStop, imageFormat, bounds,
                response);

        return retVal;
    }

    public int purge(OutputStream os) {
        // Loop over directories
        // Not implemented
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
    protected void saveTiles(int[][] gridPositions, MetaTile metaTile,
            ImageFormat imageFormat) {
        
        for (int i = 0; i < gridPositions.length; i++) {
            int[] gridPos = gridPositions[i];

            Object ck = cacheKey.createKey(gridPos[0], gridPos[1], gridPos[2],
                    imageFormat.getExtension());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                if( ! metaTile.writeTileToStream(i, imageFormat.getJavaName(), out)) {
                	log.error("metaTile.writeTileToStream returned false, no tiles saved");
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
            MetaTile metaTile, ImageFormat imageFormat) {
        for (int i = 0; i < gridPositions.length; i++) {
            int[] curPos = gridPositions[i];

            if (curPos.equals(gridPos)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try {
                    metaTile.writeTileToStream(i, imageFormat.getJavaName(),
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

    protected void saveExpirationInformation(MetaTile metaTile) {
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

        String propImageMIME = props.getProperty("imagemimes");
        if (propImageMIME != null) {
            String[] mimes = propImageMIME.split(",");
            formats = new ImageFormat[mimes.length];
            for (int i = 0; i < mimes.length; i++) {
                formats[i] = ImageFormat.createFromMimeType(mimes[i]);
            }
        }

        String propDebugHeaders = props.getProperty("debugheaders");
        if (propDebugHeaders != null) {
            debugHeaders = Boolean.valueOf(propDebugHeaders);
        }

        String propCacheLockWait = props.getProperty("cachelockwait");
        if (propCacheLockWait != null) {
            cacheLockWait = Integer.valueOf(propCacheLockWait);
        }
    }

    /**
     * Checks to see whether we accept the given mimeType
     * 
     * Typically this list should be so short that a linear search will be
     * faster than hashing.
     * 
     * @param imageMime
     * @return
     */
    private boolean supports(String imageMime) {
        for (int i = 0; i < formats.length; i++) {
            if (formats[i].getMimeType().equalsIgnoreCase(imageMime)) {
                return true;
            }
        }
        return false;
    }

    public WMSParameters getWMSParamTemplate() {
        WMSParameters ret = profile.getWMSParamTemplate();
        try {
            ret.setImagemime(formats[0].getMimeType());
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return ret;
    }

    public void destroy() {
        cache.destroy();
        // Not that it really matters:
        procQueue.clear();
    }
}
