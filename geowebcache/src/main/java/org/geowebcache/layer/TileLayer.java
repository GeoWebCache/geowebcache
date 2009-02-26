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
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.mime.MimeType;
import org.geowebcache.util.wms.BBOX;

public abstract class TileLayer {
    private static Log log = LogFactory.getLog(org.geowebcache.layer.TileLayer.class);

    protected String name;

    protected List<String> mimeFormats;

    protected Hashtable<SRS,Grid> grids;
    
    // Styles?

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

    public void addGrid(SRS srs,Grid grid) {
        this.grids.put(srs,grid);
    }

    /**
     * Retrieves a list of Grids for this layer
     * 
     * @return
     */
    public Hashtable<SRS,Grid> getGrids() {
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
    protected abstract Boolean initialize();

    /**
     * Whether the layer supports the given projection
     * 
     * @param srs
     *            Name of projection, for example "EPSG:4326"
     * @return
     * @throws GeoWebCacheException
     */
    public boolean supportsSRS(SRS srs) throws GeoWebCacheException {
        if(this.isInitialized() && this.grids.containsKey(srs)) {
            return true;
        }
        return false;
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
     * @param dataBounds
     * @return null if ok, error message otherwise
     * @throws GeoWebCacheException
     */
    public String supportsBbox(SRS srs, BBOX reqBounds)
            throws GeoWebCacheException {
        this.supportsSRS(srs);

        if (!reqBounds.isSane()) {
            return "The requested bounding box "
                    + reqBounds.getReadableString() + " is not sane";
        }

        if(! grids.get(srs).dataBounds.contains(reqBounds)) {
            return "The layers grid box " 
                + grids.get(srs).dataBounds.getReadableString()
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
    public abstract ConveyorTile getTile(ConveyorTile tile) 
    throws GeoWebCacheException, IOException;

    
    /**
     * Makes a non-metatiled request to backend, bypassing
     * the cache before and after
     * 
     * @param tile
     * @param requestTiled whether to use tiled=true or not
     * @return
     * @throws GeoWebCacheException
     * @throws IOException
     */
    public abstract ConveyorTile getNoncachedTile(ConveyorTile tile, boolean requestTiled) 
    throws GeoWebCacheException;
    
    /**
     * 
     * @param tile
     * @param tryCache
     * @throws GeoWebCacheException
     * @throws IOException
     */
    public abstract void seedTile(ConveyorTile tile, boolean tryCache) 
    throws GeoWebCacheException, IOException;
    
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
    public abstract ConveyorTile doNonMetatilingRequest(ConveyorTile tile)
            throws GeoWebCacheException;

    /**
     * 
     * @return the array of supported projections
     */
    //public SRS[] getProjections() {
    //    return (SRS[]) this.grids.keySet().toArray();
    //}

    
    public Grid getGrid(SRS srs) {
        return grids.get(srs);
    }
    //public BBOX getBounds(int srsIdx) {
    //    return this.grids.get(srsIdx).getBounds();
    //}

    /**
     * 
     * @param srsIdx
     * @return the resolutions (units/pixel) for the layer
     */
    public double[] getResolutions(SRS srs) throws GeoWebCacheException {
        return grids.get(srs).getGridCalculator().getResolutions();
    }

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
     * Whether clients may specify cache=false and go straight to source
     */
    public abstract Boolean isCacheBypassAllowed();
    public abstract void isCacheBypassAllowed(boolean allowed);
    
    /**
     * The timeout used when querying the backend server. The same value is used
     * for both the connection and the data timeout, so in theory the timeout
     * could be twice this value.
     */
    public abstract Integer getBackendTimeout();
    public abstract void setBackendTimeout(int seconds);
    
    /**
     * Provides a 2-dim array with the bounds
     * 
     * {z , {minx,miny,maxx,maxy}}
     * 
     * @param srsIdx
     * @param bounds
     * @return
     * @throws GeoWebCacheException 
     */
    public abstract int[][] getCoveredGridLevels(SRS srs, BBOX bounds) throws GeoWebCacheException;

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
     * @throws GeoWebCacheException
     */
    public abstract int[] getGridLocForBounds(SRS srs, BBOX bounds)
            throws BadTileException, GeoWebCacheException;

    /**
     * 
     * @param srsIdx
     * @param gridLoc
     * @return
     * @throws GeoWebCacheException 
     */
    public abstract BBOX getBboxForGridLoc(SRS srs, int[] gridLoc) throws GeoWebCacheException;

    /**
     * The starting zoomlevel (inclusive)
     * 
     * @return
     */
    public int getZoomStart(SRS srs) {
        return grids.get(srs).getZoomStart();
    }

    /**
     * The stopping zoomlevel (inclusive)
     * 
     * @return
     */
    public int getZoomStop(SRS srs) {
        return grids.get(srs).getZoomStop();
    }

    /**
     * Returns an array with four grid locations, the result of zooming in one
     * level on the provided grid location. [4][x,y,z]
     * 
     * If the location is not within the bounds, the z value will be negative
     * 
     * @param srsIdx
     * @param gridLoc
     * @return
     * @throws GeoWebCacheException 
     */
    // TODO this is generic
    public abstract int[][] getZoomInGridLoc(SRS srs, int[] gridLoc) throws GeoWebCacheException;

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
     * @throws GeoWebCacheException 
     */
    public abstract int[] getZoomedOutGridLoc(SRS srs) throws GeoWebCacheException;

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
    public abstract void putTile(ConveyorTile tile) throws GeoWebCacheException;

    public abstract void setExpirationHeader(HttpServletResponse response);
    
    /**
     * Merges the information of the the passed in layer into this layer. 
     * In cases where both layers have grid definitions for the same SRS the 
     * definition associated with the layer in the argument prevails. 
     * 
     * @param otherLayer
     */
    public void mergeWith(TileLayer otherLayer) throws GeoWebCacheException {
        log.warn("Merging grids, formats and filters of " + this.name);

        if (otherLayer.mimeFormats != null) {
            Iterator<String> iter = otherLayer.mimeFormats.iterator();
            while (iter.hasNext()) {
                String format = iter.next();
                if (!this.supportsFormat(format)) {
                    this.addFormat(format);
                }
            }
        }

        if (otherLayer.grids != null && otherLayer.grids.size() > 0) {
            Iterator<Entry<SRS, Grid>> iter = otherLayer.grids.entrySet().iterator();

            // We are just adding or overwriting as needed
            while (iter.hasNext()) {
                Entry<SRS, Grid> entry = iter.next();
                this.grids.put(entry.getKey(), entry.getValue());
            }
        }
        
        if(this instanceof WMSLayer) {
            WMSLayer thisWMSLayer = (WMSLayer) this;
            thisWMSLayer.mergeWith((WMSLayer) otherLayer);
        }
    }
}
