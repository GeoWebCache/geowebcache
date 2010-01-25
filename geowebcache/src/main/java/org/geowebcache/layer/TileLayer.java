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
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.filter.request.RequestFilter;
import org.geowebcache.filter.request.RequestFilterException;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.XMLOldGrid;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.grid.SRS;
import org.geowebcache.grid.XMLGridSubset;
import org.geowebcache.layer.meta.LayerMetaInformation;
import org.geowebcache.layer.updatesource.UpdateSourceDefinition;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.mime.FormatModifier;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;

public abstract class TileLayer {
    private static Log log = LogFactory.getLog(org.geowebcache.layer.TileLayer.class);

    protected String name;
    
    protected LayerMetaInformation layerMetaInfo;

    protected List<String> mimeFormats;
    
    protected List<FormatModifier> formatModifiers;
    
    protected List<XMLGridSubset> gridSubsets;

    // 1.1.x compatibility
    protected Hashtable<SRS,XMLOldGrid> grids;
    
    protected List<RequestFilter> requestFilters;
    
    protected List<UpdateSourceDefinition> updateSources;
    
    protected transient List<MimeType> formats;
    
    protected transient Hashtable<String,GridSubset> subSets;
   
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
     * Layer meta information
     * 
     * @return
     */
    public LayerMetaInformation getMetaInformation() {
        return this.layerMetaInfo;
    }

    /**
     * Adds another Grid to this layer
     * 
     * @param grid
     */

    public void addGridSet(String gridSetId, GridSubset gridSubset) {
        this.subSets.put(gridSetId, gridSubset);
    }

    /**
     * Retrieves a list of Grids for this layer
     * 
     * @return
     */
    public Hashtable<String,GridSubset> getGridSubsets() {
        return this.subSets;
    }

    /**
     * Adds another format string to the list of supported formats
     * 
     * @param format
     * @throws MimeException 
     */
    public void addFormat(String format) throws MimeException {
        MimeType mime = MimeType.createFromFormat(format);
        this.formats.add(mime);
    }

    /**
     * Initializes the layer, creating internal structures for calculating grid
     * location and so forth.
     */
    public abstract boolean initialize(GridSetBroker gridSetBroker);

    /**
     * Whether the layer supports the given projection
     * 
     * @param srs
     *            Name of projection, for example "EPSG:4326"
     * @return
     * @throws GeoWebCacheException
     */
    public GridSubset getGridSubsetForSRS(SRS srs) {
        Iterator<GridSubset> iter = this.subSets.values().iterator();
        while (iter.hasNext()) {
            GridSubset gridSubset = iter.next();
            if (gridSubset.getSRS().equals(srs)) {
                return gridSubset;
            }
        }

        return null;
    }
    
    public List<UpdateSourceDefinition> getUpdateSources() {
        return this.updateSources;
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
        
        for (MimeType mime : formats) {
            if (strFormat.equalsIgnoreCase(mime.getFormat())) {
                return true;
            }
        }

        throw new GeoWebCacheException("Format " + strFormat
                + " is not supported by " + this.getName());
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
     * @throws OutsideCoverageException 
     */
    public abstract ConveyorTile getTile(ConveyorTile tile) 
    throws GeoWebCacheException, IOException, OutsideCoverageException;

    
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
    public abstract ConveyorTile getNoncachedTile(ConveyorTile tile) 
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
     * @param srsIdx
     * @return the resolutions (units/pixel) for the layer
     */
    public double[] getResolutions(String gridSetId) throws GeoWebCacheException {
        return subSets.get(gridSetId).getResolutions();
    }
    

    public FormatModifier getFormatModifier(MimeType responseFormat) {
        if(this.formatModifiers == null || formatModifiers.size() == 0) {
            return null;
        }
        
        Iterator<FormatModifier> iter = formatModifiers.iterator();
        while(iter.hasNext()) {
            FormatModifier mod = iter.next();
            if(mod.getResponseFormat() == responseFormat) {
                return mod;
            }
        }
        
        return null;
    }
    
    public List<FormatModifier> getFormatModifiers() {
        return formatModifiers;
    }
    
    public void setFormatModifiers(List<FormatModifier> formatModifiers) {
        this.formatModifiers = formatModifiers;       
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
    public abstract void setCacheBypassAllowed(boolean allowed);
    
    /**
     * The timeout used when querying the backend server. The same value is used
     * for both the connection and the data timeout, so in theory the timeout
     * could be twice this value.
     */
    public abstract Integer getBackendTimeout();
    public abstract void setBackendTimeout(int seconds);

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
    public abstract long[] indexFromBounds(String gridSetId, BoundingBox bounds)
            throws BadTileException, GeoWebCacheException;

    /**
     * 
     * @param srsIdx
     * @param gridLoc
     * @return
     * @throws GeoWebCacheException 
     */
    public abstract BoundingBox boundsFromIndex(String gridSetId, long[] gridLoc) 
    throws GeoWebCacheException;

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

    public abstract void setExpirationHeader(HttpServletResponse response, int zoomLevel);
    
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
        
        if(otherLayer.formatModifiers != null) {
            if(formatModifiers == null) {
                formatModifiers = otherLayer.formatModifiers;
            } else {
                Iterator<FormatModifier> iter = otherLayer.formatModifiers.iterator();
                while(iter.hasNext()) {
                    FormatModifier mod = iter.next();
                    formatModifiers.add(mod);
                }
            }
        }

        if (otherLayer.subSets != null && otherLayer.subSets.size() > 0) {
            Iterator<Entry<String, GridSubset>> iter = otherLayer.subSets.entrySet().iterator();
            while (iter.hasNext()) {
                Entry<String, GridSubset> entry = iter.next();
                this.subSets.put(entry.getKey(), entry.getValue());
            }
        }
        
        if(otherLayer.requestFilters != null) {
            if(requestFilters == null)
                requestFilters = new ArrayList<RequestFilter>();
            
            Iterator<RequestFilter> iter = otherLayer.requestFilters.iterator();
            
            while(iter.hasNext()) {
                this.requestFilters.add(iter.next());
            }
        }
        
        if(this instanceof WMSLayer) {
            WMSLayer thisWMSLayer = (WMSLayer) this;
            thisWMSLayer.mergeWith((WMSLayer) otherLayer);
        }
    }
    
    /**
     * Loops over all the request filters and applies them successively.
     * 
     * @param convTile
     * @throws RequestFilterException
     */
    public void applyRequestFilters(ConveyorTile convTile) throws RequestFilterException {
        if(requestFilters == null)
            return;
        
        Iterator<RequestFilter> iter = requestFilters.iterator();
        while(iter.hasNext()) {
            RequestFilter filter = iter.next();
            filter.apply(convTile);
        }
    }
    
    public List<RequestFilter> getRequestFilters() {
        return requestFilters;
    }

    public GridSubset getGridSubset(String gridSetId) {
        return this.subSets.get(gridSetId);
    }
}
