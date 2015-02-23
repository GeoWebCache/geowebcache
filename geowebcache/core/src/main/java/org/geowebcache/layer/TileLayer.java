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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridUtil;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.grid.SRS;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.meta.LayerMetaInformation;
import org.geowebcache.layer.meta.MetadataURL;
import org.geowebcache.layer.updatesource.UpdateSourceDefinition;
import org.geowebcache.mime.FormatModifier;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;
import org.geowebcache.util.GWCVars;
import org.geowebcache.util.ServletUtils;

/**
 * "Pure virtual" base class for Layers.
 * <p>
 * Represents at the same time the configuration of a tiled layer and a way to access each stored
 * tile
 * </p>
 */
public abstract class TileLayer {

    private static Log log = LogFactory.getLog(org.geowebcache.layer.TileLayer.class);

    protected static final ThreadLocal<ByteArrayResource> WMS_BUFFER = new ThreadLocal<ByteArrayResource>();

    protected static final ThreadLocal<ByteArrayResource> WMS_BUFFER2 = new ThreadLocal<ByteArrayResource>();

    // cached default parameter filter values
    protected transient Map<String, String> defaultParameterFilterValues;

    /**
     * Registers a layer listener to be notified of layer events
     * 
     * @see #getTile(ConveyorTile)
     * @see #seedTile(ConveyorTile, boolean)
     */
    public abstract void addLayerListener(TileLayerListener listener);

    /**
     * Removes a layer listener from this layer's set of listeners
     * 
     * @param listener
     * @return
     */
    public abstract boolean removeLayerListener(TileLayerListener listener);

    /**
     * The unique identifier for the layer.
     * @return
     */
    public abstract String getId();
    
    /**
     * Then name of the layer
     * 
     * @return
     */
    public abstract String getName();

    /**
     * @return {@code true} if the layer is enabled, {@code false} otherwise
     */
    public abstract boolean isEnabled();

    /**
     * @param enabled whether to enabled caching for this layer
     */
    public abstract void setEnabled(boolean enabled);

    /**
     * @return {@code true} if the layer is advertised, {@code false} otherwise
     */
    public abstract boolean isAdvertised();

    /**
     * @param advertised whether to set this layer as advertised
     */
    public abstract void setAdvertised(boolean advertised);

    /**
     * @return {@code true} if the layer is transient, {@code false} otherwise
     */
    public abstract boolean isTransientLayer();

    /**
     * @param transientLayer whether to set this layer as transient
     */
    public abstract void setTransientLayer(boolean transientLayer);

    /**
     * Layer meta information
     * 
     * @return
     */
    public abstract LayerMetaInformation getMetaInformation();

    /**
     * List of Metadata URLs for the layer, subclasses should override this method if they support
     * declaring metaddata URLs
     */
    public List<MetadataURL> getMetadataURLs() {
        return null;
    }

    /**
     * Retrieves the GridSet names for this layer.
     * <p>
     * The returned set is immutable.
     * </p>
     * 
     * @see #getGridSubset(String)
     */
    public abstract Set<String> getGridSubsets();
    
    /**
     * @return possibly empty list of update sources for this layer
     */
    public abstract List<UpdateSourceDefinition> getUpdateSources();

    /**
     * Whether to use ETags for this layer
     * 
     * @return
     */
    public abstract boolean useETags();

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

    public abstract List<FormatModifier> getFormatModifiers();

    public abstract void setFormatModifiers(List<FormatModifier> formatModifiers);

    /**
     * 
     * @return the styles configured for the layer, may be null
     */
    public abstract String getStyles();

    /**
     * The size of a metatile in tiles.
     * 
     * @return the {x,y} metatiling factors
     */
    public abstract int[] getMetaTilingFactors();

    /**
     * Whether clients may specify cache=false and go straight to source
     */
    public abstract Boolean isCacheBypassAllowed();

    public abstract void setCacheBypassAllowed(boolean allowed);

    public abstract boolean isQueryable();

    /**
     * The timeout used when querying the backend server. The same value is used for both the
     * connection and the data timeout, so in theory the timeout could be twice this value.
     */
    public abstract Integer getBackendTimeout();

    public abstract void setBackendTimeout(int seconds);

    /**
     * 
     * @return array with supported MIME types
     */
    public abstract List<MimeType> getMimeTypes();

    /**
     * 
     * @return array with supported MIME types for information
     */
    public abstract List<MimeType> getInfoMimeTypes();
    
    /**
     * Gets the expiration time to be declared to clients.
     * @param zoomLevel integer zoom level at which to consider the expiration rules
     * @return integer duration in seconds
     */
    public abstract int getExpireClients(int zoomLevel);

    /**
     * Gets the expiration time for tiles in the cache.
     * @param zoomLevel integer zoom level at which to consider the expiration rules
     * @return integer duration in seconds
     */
    public abstract int getExpireCache(int zoomLevel);

    public abstract List<ParameterFilter> getParameterFilters();

    public abstract List<RequestFilter> getRequestFilters();

    /**
     * Initializes the layer, creating internal structures for calculating grid location and so
     * forth.
     */
    public abstract boolean initialize(GridSetBroker gridSetBroker);

    /**
     * This method is deprecated, as a layer may be configured for more than one gridset with the
     * same SRS.
     * 
     * @deprecated use {@link #getGridSubsetsForSRS(SRS)} in combination with
     *             {@link GridUtil#findBestMatchingGrid} instead
     */
    public GridSubset getGridSubsetForSRS(SRS srs) {
        for (String gridSet : getGridSubsets()) {
            GridSubset gridSubset = getGridSubset(gridSet);
            SRS gridSetSRS = gridSubset.getSRS();
            if (gridSetSRS.equals(srs)) {
                return gridSubset;
            }
        }
        return null;
    }

    /**
     * Returns an immutable list of all the layer's {@link GridSubset} whose {@link GridSet} has a
     * SRS equal to {@code srs} (may be an alias), or the empty list of none matches.
     */
    public List<GridSubset> getGridSubsetsForSRS(SRS srs) {
        List<GridSubset> matches = Collections.emptyList();

        for (String gridSet : getGridSubsets()) {
            GridSubset gridSubset = getGridSubset(gridSet);
            SRS gridSetSRS = gridSubset.getSRS();
            if (gridSetSRS.equals(srs)) {
                if (matches.isEmpty()) {
                    matches = new ArrayList<GridSubset>(2);
                }
                matches.add(gridSubset);
            }
        }
        return matches.isEmpty() ? matches : Collections.unmodifiableList(matches);
    }

    /**
     * Whether the layer supports the given format string
     * 
     * @param formatStr
     * @return
     * @throws GeoWebCacheException
     */
    public boolean supportsFormat(String strFormat) throws GeoWebCacheException {
        if (strFormat == null) {
            return true;// what the heck?!
        }

        for (MimeType mime : getMimeTypes()) {
            if (strFormat.equalsIgnoreCase(mime.getFormat())) {
                return true;
            }
        }

        // REVISIT: why not simply return false if this is a query method?!
        throw new GeoWebCacheException("Format " + strFormat + " is not supported by "
                + this.getName());
    }

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
     * 
     * @param srsIdx
     * @return the resolutions (units/pixel) for the layer
     */
    public double[] getResolutions(String gridSetId) throws GeoWebCacheException {
        return getGridSubset(gridSetId).getResolutions();
    }

    /**
     * Get the FormatModifier applicable to the given format.
     * @param responseFormat MimeType of the format to consider
     * @return FormatModifier describing the parameters for output to the given format
     */
    public FormatModifier getFormatModifier(MimeType responseFormat) {
        List<FormatModifier> formatModifiers = getFormatModifiers();
        if (formatModifiers == null || formatModifiers.size() == 0) {
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

    /**
     * The default MIME type is the first one in the configuration
     * 
     * @return
     */
    public MimeType getDefaultMimeType() {
        return getMimeTypes().get(0);
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
        return getGridSubset(gridSetId).closestIndex(tileBounds);
    }

    /**
     * 
     * @param srsIdx
     * @param gridLoc
     * @return
     * @throws GeoWebCacheException
     */
    public BoundingBox boundsFromIndex(String gridSetId, long[] gridLoc) {
        return getGridSubset(gridSetId).boundsFromIndex(gridLoc);
    }

    /**
     * Uses the HTTP 1.1 spec to set expiration headers
     * 
     * @param response
     */
    public void setExpirationHeader(HttpServletResponse response, int zoomLevel) {
        int expireValue = getExpireClients(zoomLevel);

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

    /**
     * Loops over all the request filters and applies them successively.
     * 
     * @param convTile
     * @throws RequestFilterException
     */
    public void applyRequestFilters(ConveyorTile convTile) throws RequestFilterException {
        List<RequestFilter> requestFilters = getRequestFilters();
        if (requestFilters == null)
            return;

        Iterator<RequestFilter> iter = requestFilters.iterator();
        while (iter.hasNext()) {
            RequestFilter filter = iter.next();
            filter.apply(convTile);
        }
    }

    /**
     * @return default parameter filters, with keys normalized to upper case, or an empty map if no
     *         parameter filters are defined
     */
    public Map<String, String> getDefaultParameterFilters() {
        if (defaultParameterFilterValues == null) {
            List<ParameterFilter> parameterFilters = getParameterFilters();
            if (parameterFilters == null || parameterFilters.size() == 0) {
                defaultParameterFilterValues = Collections.emptyMap();
            } else {
                Map<String, String> defaults = new HashMap<String, String>();
                for (ParameterFilter parameterFilter : parameterFilters) {
                    String key = parameterFilter.getKey().toUpperCase();
                    String defaultValue = decodeDimensionValue(parameterFilter.getDefaultValue());
                    defaults.put(key, defaultValue);
                }
                defaultParameterFilterValues = Collections.unmodifiableMap(defaults);
            }
        }
        return defaultParameterFilterValues;
    }

    /**
     * 
     * @param map
     *            keys are parameter names, values are either a single string or an array of strings
     *            as they come form httpservletrequest
     * @return Set of parameter filter keys and values, with keys normalized to upper case, or empty
     *         map if they match the layer's parameter filters default values
     * @throws GeoWebCacheException
     *             if {@link ParameterFilter#apply(String)} does
     */
    public Map<String, String> getModifiableParameters(Map<String, ?> map, String encoding)
            throws GeoWebCacheException {
        final List<ParameterFilter> parameterFilters = getParameterFilters();
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

    /**
     * @param gridSetId
     *            the name of the {@link GridSet}
     * @return the {@link GridSubset} this layer contains for the given GridSet
     */
    public abstract GridSubset getGridSubset(String gridSetId);

    public abstract GridSubset removeGridSubset(String gridSetId);

    public abstract void addGridSubset(GridSubset gridSubset);

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
     * @param metaTile
     * @param tileProto
     * @param requestTime
     */
    protected void saveTiles(MetaTile metaTile, ConveyorTile tileProto, long requestTime) throws GeoWebCacheException {

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
                        tile.setCreated(requestTime);

                        try {
                            if (tileProto.isMetaTileCacheOnly()) {
                                tileProto.getStorageBroker().putTransient(tile);
                            } else {
                                tileProto.getStorageBroker().put(tile);
                            }
                            tileProto.getStorageObject().setCreated(tile.getCreated());
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
