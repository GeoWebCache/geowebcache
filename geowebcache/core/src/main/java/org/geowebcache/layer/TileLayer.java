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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.filter.request.RequestFilter;
import org.geowebcache.filter.request.RequestFilterException;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridMismatchException;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.grid.SRS;
import org.geowebcache.grid.XMLGridSubset;
import org.geowebcache.grid.XMLOldGrid;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.meta.LayerMetaInformation;
import org.geowebcache.layer.updatesource.UpdateSourceDefinition;
import org.geowebcache.mime.FormatModifier;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;
import org.geowebcache.util.GWCVars;
import org.geowebcache.util.ServletUtils;

/**
 * Represents at the same time the configuration of a tiled layer and a way to access each stored
 * tile
 */
public abstract class TileLayer {

    private static Log log = LogFactory.getLog(org.geowebcache.layer.TileLayer.class);

    protected static final ThreadLocal<ByteArrayResource> WMS_BUFFER = new ThreadLocal<ByteArrayResource>();

    protected static final ThreadLocal<ByteArrayResource> WMS_BUFFER2 = new ThreadLocal<ByteArrayResource>();

    private static final int[] DEFAULT_METATILING_FACTORS = { 1, 1 };

    protected String name;

    protected LayerMetaInformation metaInformation;

    protected List<String> mimeFormats;

    protected List<FormatModifier> formatModifiers;

    protected List<XMLGridSubset> gridSubsets;

    protected List<ParameterFilter> parameterFilters;

    //cached default parameter filter values
    private transient Map<String, String> defaultParams;

    // 1.1.x compatibility
    protected Hashtable<SRS, XMLOldGrid> grids;

    protected List<RequestFilter> requestFilters;

    protected List<UpdateSourceDefinition> updateSources;

    protected Boolean useETags;

    protected ArrayList<ExpirationRule> expireCacheList;

    protected String expireClients;

    protected ArrayList<ExpirationRule> expireClientsList;

    protected String expireCache;

    protected Boolean cacheBypassAllowed;

    protected Boolean queryable;

    protected int[] metaWidthHeight;

    protected transient boolean saveExpirationHeaders;

    protected transient List<MimeType> formats;

    protected transient Hashtable<String, GridSubset> subSets;

    private transient LayerListenerList listeners;

    protected Integer backendTimeout;

    protected String wmsStyles = null;

    // Styles?

    /**
     * Registers a layer listener to be notified of layer events
     * 
     * @see #getTile(ConveyorTile)
     * @see #seedTile(ConveyorTile, boolean)
     */
    public void addLayerListener(TileLayerListener listener) {
        if (listeners == null) {
            listeners = new LayerListenerList();
        }
        listeners.addListener(listener);
    }

    /**
     * Removes a layer listener from this layer's set of listeners
     * 
     * @param listener
     * @return
     */
    public boolean removeLayerListener(TileLayerListener listener) {
        return listeners == null ? false : listeners.removeListener(listener);
    }

    protected final void sendTileRequestedEvent(ConveyorTile tile) {
        if (listeners != null) {
            listeners.sendTileRequested(this, tile);
        }
    }

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
     * @return {@code true} if the layer is enabled, {@code false} otherwise
     */
    public abstract boolean isEnabled();

    /**
     * @param enabled
     *            whether to enabled caching for this layer
     */
    public abstract void setEnabled(boolean enabled);

    /**
     * Layer meta information
     * 
     * @return
     */
    public LayerMetaInformation getMetaInformation() {
        return this.metaInformation;
    }

    public void setMetaInformation(LayerMetaInformation metaInfo) {
        this.metaInformation = metaInfo;
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
    public Hashtable<String, GridSubset> getGridSubsets() {
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
     * Initializes the layer, creating internal structures for calculating grid location and so
     * forth.
     */
    public final boolean initialize(GridSetBroker gridSetBroker) {

        if (this.expireCacheList == null) {
            this.expireCacheList = new ArrayList<ExpirationRule>(1);

            if (this.expireCache == null) {
                expireCacheList.add(new ExpirationRule(0, GWCVars.CACHE_NEVER_EXPIRE));
            } else {
                int expireCacheInt = Integer.parseInt(expireCache);
                if (expireCacheInt == GWCVars.CACHE_USE_WMS_BACKEND_VALUE) {
                    saveExpirationHeaders = true;
                }
                expireCacheList.add(new ExpirationRule(0, expireCacheInt));
            }
        }

        if (this.expireClientsList == null) {
            this.expireClientsList = new ArrayList<ExpirationRule>(1);

            if (this.expireClients == null) {
                expireClientsList.add(new ExpirationRule(0, 7200));
            } else {
                int expireClientsInt = Integer.parseInt(expireClients);

                if (expireClientsInt == GWCVars.CACHE_USE_WMS_BACKEND_VALUE) {
                    saveExpirationHeaders = true;
                } else if (expireClientsInt == GWCVars.CACHE_NEVER_EXPIRE) {
                    // One year should do
                    expireClientsInt = 3600 * 24 * 365;
                }
                expireClientsList.add(new ExpirationRule(0, expireClientsInt));
            }
        }

        try {
            // mimetypes
            this.formats = new ArrayList<MimeType>();
            if (mimeFormats != null) {
                for (String fmt : mimeFormats) {
                    formats.add(MimeType.createFromFormat(fmt));
                }
            }
            if (formats.size() == 0) {
                formats.add(0, MimeType.createFromFormat("image/png"));
                formats.add(1, MimeType.createFromFormat("image/jpeg"));
            }
        } catch (GeoWebCacheException gwce) {
            log.error(gwce.getMessage());
            gwce.printStackTrace();
        }

        if (subSets == null) {
            subSets = new Hashtable<String, GridSubset>();
        }

        if (this.gridSubsets != null) {
            Iterator<XMLGridSubset> iter = gridSubsets.iterator();
            while (iter.hasNext()) {
                XMLGridSubset xmlGridSubset = iter.next();
                GridSubset gridSubset = xmlGridSubset.getGridSubSet(gridSetBroker);

                if (gridSubset == null) {
                    log.error(xmlGridSubset.getGridSetName()
                            + " is not known by the GridSetBroker, skipping for layer " + name);
                } else {
                    subSets.put(gridSubset.getName(), gridSubset);
                }

            }

            this.gridSubsets = null;
        }

        // Convert version 1.1.x and 1.0.x grid objects
        if (grids != null && !grids.isEmpty()) {
            Iterator<XMLOldGrid> iter = grids.values().iterator();
            while (iter.hasNext()) {
                GridSubset converted = iter.next().convertToGridSubset(gridSetBroker);
                subSets.put(converted.getSRS().toString(), converted);
            }

            // Null it for the garbage collector
            grids = null;
        }

        if (this.subSets.size() == 0) {
            subSets.put(gridSetBroker.WORLD_EPSG4326.getName(),
                    GridSubsetFactory.createGridSubSet(gridSetBroker.WORLD_EPSG4326));
            subSets.put(gridSetBroker.WORLD_EPSG3857.getName(),
                    GridSubsetFactory.createGridSubSet(gridSetBroker.WORLD_EPSG3857));
        }

        return initializeInternal(gridSetBroker);
    }

    protected abstract boolean initializeInternal(GridSetBroker gridSetBroker);

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

    /**
     * @return possibly empty list of update sources for this layer
     */
    public List<UpdateSourceDefinition> getUpdateSources() {
        List<UpdateSourceDefinition> sources;
        if (updateSources == null) {
            sources = Collections.emptyList();
        } else {
            sources = updateSources;
        }
        return sources;
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

        throw new GeoWebCacheException("Format " + strFormat + " is not supported by "
                + this.getName());
    }

    /**
     * Whether to use ETags for this layer
     * 
     * @return
     */
    public boolean useETags() {
        if (useETags != null && useETags) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * The normal way of getting a single tile from the layer. Under the hood, this may result in
     * several tiles being requested and stored before returning.
     * 
     * @param tileRequest
     * @param servReq
     * @param response
     * @return
     * @throws GeoWebCacheException
     * @throws IOException
     * @throws OutsideCoverageException
     */
    public abstract ConveyorTile getTile(ConveyorTile tile) throws GeoWebCacheException,
            IOException, OutsideCoverageException;


    /**
     * GetFeatureInfo template, throws exception, subclasses must override if supported.
     * 
     * @param convTile
     * @param bbox
     * @param height
     * @param width
     * @param x
     * @param y
     * @return
     * @throws GeoWebCacheException
     */
    public Resource getFeatureInfo(ConveyorTile convTile, BoundingBox bbox, int height, int width,
            int x, int y) throws GeoWebCacheException {
        throw new GeoWebCacheException("GetFeatureInfo is not supported by this layer ("
                + getName() + ")");
    }

    /**
     * Makes a non-metatiled request to backend, bypassing the cache before and after
     * 
     * @param tile
     * @param requestTiled
     *            whether to use tiled=true or not
     * @return
     * @throws GeoWebCacheException
     * @throws IOException
     */
    public abstract ConveyorTile getNoncachedTile(ConveyorTile tile) throws GeoWebCacheException;

    /**
     * 
     * @param tile
     * @param tryCache
     * @throws GeoWebCacheException
     * @throws IOException
     */
    public abstract void seedTile(ConveyorTile tile, boolean tryCache) throws GeoWebCacheException,
            IOException;

    /**
     * This is a more direct way of requesting a tile without invoking metatiling, and should not be
     * used in general. The method was exposed to let the KML service traverse the tree ahead of the
     * client, to avoid linking to empty tiles.
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
        if (this.formatModifiers == null || formatModifiers.size() == 0) {
            return null;
        }

        Iterator<FormatModifier> iter = formatModifiers.iterator();
        while (iter.hasNext()) {
            FormatModifier mod = iter.next();
            if (mod.getResponseFormat() == responseFormat) {
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
    public String getStyles() {
        return wmsStyles;
    }

    /**
     * 
     * @return the {x,y} metatiling factors
     */
    public int[] getMetaTilingFactors() {
        return metaWidthHeight == null ? DEFAULT_METATILING_FACTORS : metaWidthHeight;
    }

    /**
     * Whether clients may specify cache=false and go straight to source
     */
    public Boolean isCacheBypassAllowed() {
        return cacheBypassAllowed;
    }

    public void setCacheBypassAllowed(boolean allowed) {
        cacheBypassAllowed = Boolean.valueOf(allowed);
    }

    public boolean isQueryable() {
        if (queryable != null && queryable) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * The timeout used when querying the backend server. The same value is used for both the
     * connection and the data timeout, so in theory the timeout could be twice this value.
     */
    public Integer getBackendTimeout() {
        return backendTimeout;
    }

    public void setBackendTimeout(int seconds) {
        backendTimeout = seconds;
    }

    /**
     * 
     * @return array with supported MIME types
     */
    public List<MimeType> getMimeTypes() {
        return formats;
    }

    /**
     * The default MIME type is the first one in the configuration
     * 
     * @return
     */
    public MimeType getDefaultMimeType() {
        return formats.get(0);
    }

    /**
     * 
     * Converts the given bounding box into the closest location on the grid supported by the
     * reference system.
     * 
     * @param srsIdx
     * @param bounds
     * @return
     * @throws GeoWebCacheException
     * @throws GeoWebCacheException
     * @throws GridMismatchException
     */
    public long[] indexFromBounds(String gridSetId, BoundingBox tileBounds)
            throws GridMismatchException {
        return subSets.get(gridSetId).closestIndex(tileBounds);
    }

    /**
     * 
     * @param srsIdx
     * @param gridLoc
     * @return
     * @throws GeoWebCacheException
     */
    public BoundingBox boundsFromIndex(String gridSetId, long[] gridLoc) {
        return subSets.get(gridSetId).boundsFromIndex(gridLoc);
    }

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
     * Uses the HTTP 1.1 spec to set expiration headers
     * 
     * @param response
     */
    public void setExpirationHeader(HttpServletResponse response, int zoomLevel) {
        int expireValue = this.getExpireClients(zoomLevel);

        // Fixed value
        if (expireValue == GWCVars.CACHE_VALUE_UNSET) {
            return;
        }

        // TODO move to TileResponse
        if (expireValue > 0) {
            response.setHeader("Cache-Control", "max-age=" + expireValue + ", must-revalidate");
            response.setHeader("Expires", ServletUtils.makeExpiresHeader(expireValue));
        } else if (expireValue == GWCVars.CACHE_NEVER_EXPIRE) {
            long oneYear = 3600 * 24 * 365;
            response.setHeader("Cache-Control", "max-age=" + oneYear);
            response.setHeader("Expires", ServletUtils.makeExpiresHeader((int) oneYear));
        } else if (expireValue == GWCVars.CACHE_DISABLE_CACHE) {
            response.setHeader("Cache-Control", "no-cache");
        } else if (expireValue == GWCVars.CACHE_USE_WMS_BACKEND_VALUE) {
            int seconds = 3600;
            response.setHeader("geowebcache-error", "No real CacheControl information available");
            response.setHeader("Cache-Control", "max-age=" + seconds);
            response.setHeader("Expires", ServletUtils.makeExpiresHeader(seconds));
        }
    }

    public int getExpireClients(int zoomLevel) {
        return getExpiration(this.expireClientsList, zoomLevel);
    }

    public int getExpireCache(int zoomLevel) {
        return getExpiration(this.expireCacheList, zoomLevel);
    }

    private int getExpiration(ArrayList<ExpirationRule> list, int zoomLevel) {
        int retVal;

        int length = list.size();
        if (length == 1) {
            retVal = list.get(0).getExpiration();
        } else {
            int i;
            for (i = 1; i < length;) {
                if (list.get(i).getMinZoom() > zoomLevel) {
                    break;
                }
                i++;
            }
            retVal = list.get(i - 1).getExpiration();
        }

        if (retVal == GWCVars.CACHE_USE_WMS_BACKEND_VALUE) {
            return 7200;
        }

        return retVal;
    }

    /**
     * Merges the information of the the passed in layer into this layer. In cases where both layers
     * have grid definitions for the same SRS the definition associated with the layer in the
     * argument prevails.
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

        if (otherLayer.formatModifiers != null) {
            if (formatModifiers == null) {
                formatModifiers = otherLayer.formatModifiers;
            } else {
                Iterator<FormatModifier> iter = otherLayer.formatModifiers.iterator();
                while (iter.hasNext()) {
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

        if (otherLayer.requestFilters != null) {
            if (requestFilters == null)
                requestFilters = new ArrayList<RequestFilter>();

            Iterator<RequestFilter> iter = otherLayer.requestFilters.iterator();

            while (iter.hasNext()) {
                this.requestFilters.add(iter.next());
            }
        }

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

    /**
     * Loops over all the request filters and applies them successively.
     * 
     * @param convTile
     * @throws RequestFilterException
     */
    public void applyRequestFilters(ConveyorTile convTile) throws RequestFilterException {
        if (requestFilters == null)
            return;

        Iterator<RequestFilter> iter = requestFilters.iterator();
        while (iter.hasNext()) {
            RequestFilter filter = iter.next();
            filter.apply(convTile);
        }
    }

    public List<ParameterFilter> getParameterFilters() {
        return parameterFilters;
    }
    
    public void setParameterFilters(List<ParameterFilter> filters) {
        this.parameterFilters = filters == null? null : new ArrayList<ParameterFilter>(filters);
        this.defaultParams = null;
    }

    /**
     * @return default parameter filters, with keys normalized to upper case, or an empty map if no
     *         parameter filters are defined
     */
    public synchronized Map<String, String> getDefaultParameterFilters() {
        if (defaultParams == null) {
            if (parameterFilters == null || parameterFilters.size() == 0) {
                defaultParams = Collections.emptyMap();
            } else {
                Map<String, String> defaults = new HashMap<String, String>();
                for (ParameterFilter parameterFilter : parameterFilters) {
                    String key = parameterFilter.getKey().toUpperCase();
                    String defaultValue = decodeDimensionValue(parameterFilter.getDefaultValue());
                    defaults.put(key, defaultValue);
                }
                defaultParams = Collections.unmodifiableMap(defaults);
            }
        }
        return defaultParams;
    }

    /**
     * 
     * @param map
     *            keys are parameter names, values are either a single string or an array of strings
     *            as they come form httpservletrequest
     * @return Set of parameter filter keys and values, with keys normalized to upper case, or empty
     *         map if they match the layer's parameter filters default values
     * @throws GeoWebCacheException
     */
    public Map<String, String> getModifiableParameters(Map<String, ?> map, String encoding)
            throws GeoWebCacheException {
        if (parameterFilters == null) {
            return Collections.emptyMap();
        }

        Map<String, String> fullParameters = new HashMap<String, String>();

        final String[] keys = new String[parameterFilters.size()];
        for (int i = 0; i < parameterFilters.size(); i++) {
            ParameterFilter parameterFilter = parameterFilters.get(i);
            keys[i] = parameterFilter.getKey();
        }

        final Map<String, String> requestValues;
        requestValues = ServletUtils.selectedStringsFromMap(map, encoding, keys);

        final Map<String, String> defaultValues = getDefaultParameterFilters();

        for (ParameterFilter parameterFilter : parameterFilters) {
            String key = parameterFilter.getKey().toUpperCase();
            String value = requestValues.get(key);
            value = decodeDimensionValue(value);

            String defaultValue = defaultValues.get(key);
            if (value == null || value.length() == 0
                    || (defaultValue != null && defaultValue.equals(value))) {
                fullParameters.put(key, defaultValue);
            } else {
                String appliedValue = parameterFilter.apply(value);
                fullParameters.put(key, appliedValue);
            }
        }
        if (defaultValues.equals(fullParameters)) {
            return Collections.emptyMap();
        }
        return fullParameters;
    }

    protected static String decodeDimensionValue(String value) {
        if (value != null && value.startsWith("_")) {
            if (value.equals("_null")) {
                return null;
            } else if (value.equals("_empty")) {
                return "";
            } else {
                return value;
            }
        } else {
            return value;
        }
    }

    public static String encodeDimensionValue(String value) {
        if (value == null) {
            return "_null";
        } else if (value.length() == 0) {
            return "_empty";
        } else {
            return value;
        }
    }

    public List<RequestFilter> getRequestFilters() {
        return requestFilters;
    }

    public GridSubset getGridSubset(String gridSetId) {
        return this.subSets.get(gridSetId);
    }

    protected ByteArrayResource getImageBuffer(ThreadLocal<ByteArrayResource> tl) {
        ByteArrayResource buffer = tl.get();
        if (buffer == null) {
            buffer = new ByteArrayResource(16 * 1024);
            tl.set(buffer);
        }
        buffer.truncate();
        return buffer;
    }

    /**
     * Loops over the gridPositions, generates cache keys and saves to cache
     * 
     * @param gridPositions
     * @param metaTile
     * @param imageFormat
     */
    protected void saveTiles(MetaTile metaTile, ConveyorTile tileProto) throws GeoWebCacheException {

        final long[][] gridPositions = metaTile.getTilesGridPositions();
        final long[] gridLoc = tileProto.getTileIndex();
        final GridSubset gridSubset = getGridSubset(tileProto.getGridSetId());

        final int zoomLevel = (int) gridLoc[2];
        final boolean store = this.getExpireCache(zoomLevel) != GWCVars.CACHE_DISABLE_CACHE;

        Resource resource;
        boolean encode;
        for (int i = 0; i < gridPositions.length; i++) {
            final long[] gridPos = gridPositions[i];
            if (Arrays.equals(gridLoc, gridPos)) {
                // Is this the one we need to save? then don't use the buffer or it'll be overridden
                // by the next tile
                resource = getImageBuffer(WMS_BUFFER2);
                tileProto.setBlob(resource);
                encode = true;
            } else {
                resource = getImageBuffer(WMS_BUFFER);
                encode = store;
            }

            if (encode) {
                if (!gridSubset.covers(gridPos)) {
                    // edge tile outside coverage, do not store it
                    continue;
                }

                try {
                    boolean completed = metaTile.writeTileToStream(i, resource);
                    if (!completed) {
                        log.error("metaTile.writeTileToStream returned false, no tiles saved");
                    }

                    if (store) {
                        long[] idx = { gridPos[0], gridPos[1], gridPos[2] };

                        TileObject tile = TileObject.createCompleteTileObject(this.getName(), idx,
                                tileProto.getGridSetId(), tileProto.getMimeType().getFormat(),
                                tileProto.getParameters(), resource);

                        try {
                            tileProto.getStorageBroker().put(tile);
                        } catch (StorageException e) {
                            throw new GeoWebCacheException(e);
                        }
                    }
                } catch (IOException ioe) {
                    log.error("Unable to write image tile to " + "ByteArrayOutputStream: "
                            + ioe.getMessage());
                    ioe.printStackTrace();
                }
            }
        }
    }

}
