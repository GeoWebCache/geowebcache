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

import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.cache.Cache;
import org.geowebcache.cache.CacheException;
import org.geowebcache.cache.CacheKey;
import org.geowebcache.mime.MimeType;
import org.geowebcache.tile.Tile;
import org.geowebcache.util.wms.BBOX;
import org.geowebcache.cache.CacheFactory;

public abstract class TileLayer {

    protected String name;

    protected List<String> mimeFormats;

    protected List<Grid> grids;

    /**
     * Sets the layer name
     * 
     * @param name
     */

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Then name of the layer
     * 
     * @return
     */
    public String getName() {
        return this.name;
    }

    /**
     * Adds another Grid to this layer
     * 
     * @param grid
     */

    public void addGrid(Grid grid) {
        this.grids.add(grid);
    }

    /**
     * Retrieves a list of Grids for this layer
     * 
     * @return
     */
    public List<Grid> getGrids() {
        return this.grids;
    }

    /**
     * Adds another format string to the list of supported formats
     * 
     * @param format
     */
    public void addFormat(String format) {
        this.mimeFormats.add(format);
    }

    /**
     * Checks whether the layer has been initialized, otherwise initializes it.
     * 
     * @return
     */
    public abstract Boolean isInitialized();

    /**
     * Initializes the layer, creating internal structures for calculating grid
     * location and so forth.
     */
    public abstract Boolean initialize();

    /**
     * Whether the layer supports the given projection
     * 
     * @param srs
     *            Name of projection, for example "EPSG:4326"
     * @return
     * @throws GeoWebCacheException
     */
    public boolean supportsProjection(SRS srs) throws GeoWebCacheException {
        for (Grid g : grids)
            if (srs.equals(g.getProjection()))
                return true;
        throw new GeoWebCacheException("SRS " + srs.toString()
                + " is not supported by " + this.getName());
    }

    /**
     * Whether the layer supports the given format string
     * 
     * @param formatStr
     * @return
     * @throws GeoWebCacheException
     */
    public boolean supportsFormat(String strFormat) throws GeoWebCacheException {
        if (strFormat == null)
            return true;
        for (String format : mimeFormats) {
            if (strFormat.equalsIgnoreCase(format)) {
                return true;
            }
        }

        throw new GeoWebCacheException("Format " + strFormat
                + " is not supported by " + this.getName());
    }

    /**
     * Whether the layer supports the given projection and bounding box
     * combination
     * 
     * @param srs
     * @param bounds
     * @return null if ok, error message otherwise
     * @throws GeoWebCacheException
     */
    public String supportsBbox(SRS srs, BBOX reqBounds)
            throws GeoWebCacheException {
        this.supportsProjection(srs);

        if (!reqBounds.isSane()) {
            return "The requested bounding box "
                    + reqBounds.getReadableString() + " is not sane";
        }

        if (!(grids.get(getSRSIndex(srs)).getGridBounds()).contains(reqBounds)) {
            return "The layers grid box "
                    + (grids.get(getSRSIndex(srs)).getGridBounds())
                            .getReadableString()
                    + " does not cover the requested bounding box "
                    + reqBounds.getReadableString();
        }

        // All ok
        return null;
    }

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
    public abstract Tile getResponse(Tile tile) throws GeoWebCacheException,
            IOException;

    /**
     * This is a more direct way of requesting a tile without invoking
     * metatiling, and should not be used in general. The method was exposed to
     * let the KML service traverse the tree ahead of the client, to avoid
     * linking to empty tiles.
     * 
     * @param gridLoc
     * @param idx
     * @param formatStr
     * @return
     * @throws GeoWebCacheException
     */
    public abstract Tile doNonMetatilingRequest(Tile tile)
            throws GeoWebCacheException;

    /**
     * 
     * @return the array of supported projections
     */
    public SRS[] getProjections() {
        SRS[] projections = new SRS[grids.size()];
        for (Grid g : grids)
            projections[grids.indexOf(g)] = g.getProjection();
        return projections;
    }

    /**
     * 
     * @param reqSRS
     * @return the internal index of the provided spatial reference system
     */
    public int getSRSIndex(SRS reqSRS) {
        for (Grid g : grids) {
            if (reqSRS.equals(g.getProjection()))
                return grids.indexOf(g);
        }
        return -1;
    }

    /**
     * 
     * @param srsIdx
     * @return the bounds of the layer for the given spatial reference system
     */
    public BBOX getBounds(int srsIdx) {
        return this.grids.get(srsIdx).getBounds();
    }

    /**
     * 
     * @param srsIdx
     * @return the resolutions (units/pixel) for the layer
     */
    public abstract double[] getResolutions(int srsIdx);

    /**
     * 
     * @return the styles configured for the layer, may be null
     */
    public abstract String getStyles();

    /**
     * 
     * @return the {x,y} metatiling factors
     */
    public abstract int[] getMetaTilingFactors();

    /**
     * Provides a 2-dim array with the bounds
     * 
     * {z , {minx,miny,maxx,maxy}}
     * 
     * @param srsIdx
     * @param bounds
     * @return
     */
    public abstract int[][] getCoveredGridLevels(int srsIdx, BBOX bounds);

    /**
     * 
     * @return array with supported MIME types
     */
    public abstract List<MimeType> getMimeTypes();

    /**
     * The default MIME type is the first one in the configuration
     * 
     * @return
     */
    public abstract MimeType getDefaultMimeType();

    /**
     * Used for reloading servlet
     */
    public abstract void destroy();

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
    public abstract int[] getGridLocForBounds(int srsIdx, BBOX bounds)
            throws GeoWebCacheException;

    /**
     * 
     * @param srsIdx
     * @param gridLoc
     * @return
     */
    public abstract BBOX getBboxForGridLoc(int srsIdx, int[] gridLoc);

    /**
     * The starting zoomlevel (inclusive)
     * 
     * @return
     */
    public abstract int getZoomStart();

    /**
     * The stopping zoomlevel (inclusive)
     * 
     * @return
     */
    public abstract int getZoomStop();

    /**
     * Returns an array with four grid locations, the result of zooming in one
     * level on the provided grid location. [4][x,y,z]
     * 
     * If the location is not within the bounds, the z value will be negative
     * 
     * @param srsIdx
     * @param gridLoc
     * @return
     */
    // TODO this is generic
    public abstract int[][] getZoomInGridLoc(int srsIdx, int[] gridLoc);

    /**
     * The furthest zoomed in grid location that returns the entire layer on a
     * single tile.
     * 
     * If there is no single tile, say your layer is in EPSG:4326 and covers
     * both the western and eastern hemissphere, then zoomlevel will be set to
     * -1.
     * 
     * @param srsIdx
     * @return x,y,z}
     */
    public abstract int[] getZoomedOutGridLoc(int srsIdx);

    /**
     * Quick, non-synchronized way of trying the cache
     * 
     * @param cacheKey
     * @return
     */
    public abstract boolean tryCacheFetch(Tile tile);

    /**
     * Get the prefix for the cache
     * 
     * @return
     */
    public abstract String getCachePrefix();

    /**
     * Get an example of a cachekey object
     * 
     * @return
     */
    public abstract CacheKey getCacheKey();

    /**
     * Get the cache for the layer
     * 
     * @return
     */
    public abstract Cache getCache();

    /**
     * Acquire the global lock for the layer, primarily used for truncating
     * 
     */
    public abstract void acquireLayerLock();

    /**
     * Release the global lock for the layer
     * 
     */
    public abstract void releaseLayerLock();

    /**
     * Backdoor to put stuff into the cache from services
     * 
     * @param tile
     * @param gridLoc
     * @param srs
     * @param mime
     * @throws CacheException
     */
    public abstract void putTile(Tile tile) throws GeoWebCacheException;

    public abstract void setExpirationHeader(HttpServletResponse response);

    public abstract void lazyLayerInitialization(CacheFactory cf);
}
