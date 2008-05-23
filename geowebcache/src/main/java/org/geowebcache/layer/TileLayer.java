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

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.cache.Cache;
import org.geowebcache.cache.CacheKey;
import org.geowebcache.mime.MimeType;
import org.geowebcache.service.ServiceRequest;
import org.geowebcache.util.wms.BBOX;

public interface TileLayer {

    /**
     * Checks whether the layer has been initialized, otherwise initilizes it.
     * 
     * @return
     */
    public Boolean isInitialized();
    
    /**
     * Initializes the layer, creating internal structures for
     * calculating grid location and so forth.
     */
    public Boolean initialize();
    
    /**
     * Whether the layer supports the given projection 
     * 
     * @param srs
     * @return
     * @throws GeoWebCacheException
     */
    public boolean supportsProjection(SRS srs) throws GeoWebCacheException;
    
    /**
     * Whether the layer supports the given format string
     * 
     * @param formatStr
     * @return 
     * @throws GeoWebCacheException
     */
    public boolean supportsFormat(String formatStr) throws GeoWebCacheException;
    
    /**
     * Whether the layer supports the given projection and bounding box combination
     * 
     * @param srs
     * @param bounds
     * @return
     * @throws GeoWebCacheException
     */
    public String supportsBbox(SRS srs, BBOX bounds) throws GeoWebCacheException;
    
    /**
     * The normal way of getting a single tile from the layer. Under the hood,
     * this may result in several tiles being requested and stored before 
     * returning.
     *  
     * @param tileRequest
     * @param servReq
     * @param response
     * @return
     * @throws GeoWebCacheException
     * @throws IOException
     */
    public TileResponse getResponse(TileRequest tileRequest, 
           ServiceRequest servReq, HttpServletResponse response) 
    throws GeoWebCacheException, IOException;
    
    /**
     * This is a more direct way of requesting a tile without invoking metatiling,
     * and should not be used in general. The method was exposed to let the 
     * KML service traverse the tree ahead of the client, to avoid linking to
     * empty tiles.
     * 
     * @param gridLoc
     * @param idx
     * @param formatStr
     * @return
     * @throws GeoWebCacheException
     */
    public TileResponse doNonMetatilingRequest(int[] gridLoc, int idx, String formatStr)
    throws GeoWebCacheException;
    
    /**
     * 
     * @return the array of supported projections
     */
    public SRS[] getProjections();
    
    /**
     * 
     * @param reqSRS
     * @return the internal index of the provided spatial reference system
     */
    public int getSRSIndex(SRS reqSRS);
    
    /**
     * 
     * @param srsIdx
     * @return the bounds of the layer for the given spatial reference system
     */
    public BBOX getBounds(int srsIdx);
    
    /**
     * 
     * @param srsIdx
     * @return the resolutions (units/pixel) for the layer
     */
    public double[] getResolutions(int srsIdx);
    
    /**
     * 
     * @return the styles configured for the layer, may be null
     */
    public String getStyles();
    
    /**
     * 
     * @return the {x,y} metatiling factors
     */
    public int[] getMetaTilingFactors();
    
    /**
     * Provides a 2-dim array with the bounds
     * 
     * {z , {minx,miny,maxx,maxy}}
     * @param srsIdx
     * @param bounds
     * @return 
     */
    public int[][] getCoveredGridLevels(int srsIdx, BBOX bounds);
    
    /**
     * 
     * @return array with supported MIME types
     */
    public MimeType[] getMimeTypes();
    
    /**
     * The default MIME type is the first one in the configuration
     * 
     * @return
     */
    public MimeType getDefaultMimeType();
    
    /**
     * The name of the layer. 
     * 
     * @return
     */
    public String getName();
    
    /**
     * Used for reloading servlet
     */
    public void destroy();
    
    /**
     * 
     * Converts the given bounding box into the closest location on the grid
     * supported by the reference system.
     * 
     * @param srsIdx
     * @param bounds
     * @return
     * @throws GeoWebCacheException
     */
    public int[] getGridLocForBounds(int srsIdx, BBOX bounds) throws GeoWebCacheException;
    
    /**
     * 
     * @param srsIdx
     * @param gridLoc
     * @return
     */
    public BBOX getBboxForGridLoc(int srsIdx, int[] gridLoc);
    
    /**
     * The starting zoomlevel (inclusive)
     * @return
     */
    public int getZoomStart();
    
    /**
     * The stopping zoomlevel (inclusive)
     * @return
     */
    public int getZoomStop();
    
    /**
     * Returns an array with four grid locations,
     * the result of zooming in one level on the provided grid location. 
     * [4][x,y,z]
     * 
     * If the location is not within the bounds, the z value will be negative
     * 
     * @param srsIdx
     * @param gridLoc
     * @return
     */
    public int[][] getZoomInGridLoc(int srsIdx, int[] gridLoc);
    
    /**
     * The furthest zoomed in grid location that returns the entire layer
     * on a single tile.
     * 
     * If there is no single tile, say your layer is in EPSG:4326 and covers
     * both the western and eastern hemissphere, then zoomlevel will be set
     * to -1.
     * 
     * @param srsIdx
     * @return {x,y,z}
     */
    public int[] getZoomedOutGridLoc(int srsIdx);
    
    /**
     * Get the prefix for the cache
     * 
     * @return
     */
    public String getCachePrefix();
    
    /**
     * Get an example of a cachekey object
     * 
     * @return
     */
    public CacheKey getCacheKey();
    
    /**
     * Get the cache for the layer
     * 
     * @return
     */
    public Cache getCache();
    
    /**
     * Acquire the global lock for the layer, primarily used for truncating
     *
     */
    public void acquireLayerLock();
    
    /**
     * Release the global lock for the layer
     *
     */
    public void releaseLayerLock();
}
