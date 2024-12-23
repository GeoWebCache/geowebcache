/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 */
package org.geowebcache.layer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.XMLGridSubset;
import org.geowebcache.config.XMLOldGrid;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.filter.request.RequestFilter;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.grid.SRS;
import org.geowebcache.layer.meta.LayerMetaInformation;
import org.geowebcache.layer.meta.MetadataURL;
import org.geowebcache.layer.updatesource.UpdateSourceDefinition;
import org.geowebcache.mime.FormatModifier;
import org.geowebcache.mime.MimeType;
import org.geowebcache.util.GWCVars;

/** Default statefull base class for {@link TileLayer} implementations */
public abstract class AbstractTileLayer extends TileLayer {

    private static Logger log = Logging.getLogger(AbstractTileLayer.class.getName());

    private static final int[] DEFAULT_METATILING_FACTORS = {1, 1};

    @Nullable
    protected String blobStoreId;

    protected Boolean enabled;

    protected Boolean advertised;

    protected Boolean transientLayer;

    protected String name;

    protected LayerMetaInformation metaInformation;

    protected List<MetadataURL> metadataURLs;

    protected List<String> mimeFormats;

    protected List<String> infoMimeFormats;

    protected List<FormatModifier> formatModifiers;

    // 1.1.x compatibility
    protected Map<SRS, XMLOldGrid> grids;

    protected List<XMLGridSubset> gridSubsets;

    protected List<UpdateSourceDefinition> updateSources;

    protected List<RequestFilter> requestFilters;

    protected Boolean useETags;

    protected int[] metaWidthHeight;

    protected String expireCache;

    protected ArrayList<ExpirationRule> expireCacheList;

    protected String expireClients;

    protected ArrayList<ExpirationRule> expireClientsList;

    protected Integer backendTimeout;

    protected Boolean cacheBypassAllowed;

    protected Boolean queryable;

    protected List<ParameterFilter> parameterFilters;

    protected transient boolean saveExpirationHeaders;

    protected transient List<MimeType> formats;

    protected transient List<MimeType> infoFormats;

    protected transient Map<String, GridSubset> subSets;

    private transient LayerListenerList listeners;

    // Styles?

    protected Object readResolve() {
        if (gridSubsets == null) {
            gridSubsets = new ArrayList<>();
        }
        return this;
    }

    /**
     * Registers a layer listener to be notified of layer events
     *
     * @see #getTile(ConveyorTile)
     * @see #seedTile(ConveyorTile, boolean)
     */
    @Override
    public void addLayerListener(TileLayerListener listener) {
        if (listeners == null) {
            listeners = new LayerListenerList();
        }
        listeners.addListener(listener);
    }

    /** Removes a layer listener from this layer's set of listeners */
    @Override
    public boolean removeLayerListener(TileLayerListener listener) {
        return listeners == null ? false : listeners.removeListener(listener);
    }

    protected final void sendTileRequestedEvent(ConveyorTile tile) {
        if (listeners != null) {
            listeners.sendTileRequested(this, tile);
        }
    }

    @Override
    public String getId() {
        return getName();
    }

    @Override
    @Nullable
    public String getBlobStoreId() {
        return blobStoreId;
    }

    @Override
    public void setBlobStoreId(@Nullable String blobStoreId) {
        this.blobStoreId = blobStoreId;
    }

    /** Then name of the layer */
    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public boolean isEnabled() {
        return enabled == null ? true : enabled.booleanValue();
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isAdvertised() {
        return advertised == null ? true : advertised.booleanValue();
    }

    @Override
    public void setAdvertised(boolean advertised) {
        this.advertised = advertised;
    }

    @Override
    public boolean isTransientLayer() {
        return transientLayer == null ? false : transientLayer.booleanValue();
    }

    @Override
    public void setTransientLayer(boolean transientLayer) {
        this.transientLayer = transientLayer;
    }

    /** Layer meta information */
    @Override
    public LayerMetaInformation getMetaInformation() {
        return this.metaInformation;
    }

    @Override
    public List<MetadataURL> getMetadataURLs() {
        return metadataURLs == null ? null : new ArrayList<>(metadataURLs);
    }

    /** Retrieves a list of Grids for this layer */
    @Override
    public Set<String> getGridSubsets() {
        return Collections.unmodifiableSet(this.subSets.keySet());
    }

    /**
     * Initializes the layer, creating internal structures for calculating grid location and so forth.
     *
     * <p>Subclasses shall implement {@link #initializeInternal(GridSetBroker)} for anything else
     */
    @Override
    public final boolean initialize(GridSetBroker gridSetBroker) {

        if (this.expireCacheList == null) {
            this.expireCacheList = new ArrayList<>(1);

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
            this.expireClientsList = new ArrayList<>(1);

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
            this.formats = new ArrayList<>();
            if (mimeFormats != null) {
                for (String fmt : mimeFormats) {
                    formats.add(MimeType.createFromFormat(fmt));
                }
            }
            if (formats.isEmpty()) {
                formats.add(0, MimeType.createFromFormat("image/png"));
                formats.add(1, MimeType.createFromFormat("image/jpeg"));
            }
        } catch (GeoWebCacheException gwce) {
            log.log(Level.SEVERE, gwce.getMessage(), gwce);
        }

        try {
            // mimetypes for info
            this.infoFormats = new ArrayList<>();
            if (infoMimeFormats != null) {
                for (String fmt : infoMimeFormats) {
                    infoFormats.add(MimeType.createFromFormat(fmt));
                }
            }
            if (infoFormats.isEmpty()) {
                infoFormats.add(MimeType.createFromFormat("text/plain"));
                infoFormats.add(MimeType.createFromFormat("text/html"));
                infoFormats.add(MimeType.createFromFormat("application/vnd.ogc.gml"));
            }
        } catch (GeoWebCacheException gwce) {
            log.log(Level.SEVERE, gwce.getMessage(), gwce);
        }

        if (subSets == null) {
            subSets = new HashMap<>();
        }

        if (this.gridSubsets == null) {
            this.gridSubsets = new ArrayList<>();
        }

        for (XMLGridSubset xmlGridSubset : gridSubsets) {
            GridSubset gridSubset = xmlGridSubset.getGridSubSet(gridSetBroker);

            if (gridSubset == null) {
                log.log(
                        Level.SEVERE,
                        xmlGridSubset.getGridSetName()
                                + " is not known by the GridSetBroker, skipping for layer "
                                + name);
            } else {
                subSets.put(gridSubset.getName(), gridSubset);
            }
        }

        // Convert version 1.1.x and 1.0.x grid objects
        if (grids != null && !grids.isEmpty()) {
            Iterator<XMLOldGrid> iter = grids.values().iterator();
            while (iter.hasNext()) {
                GridSubset converted = iter.next().convertToGridSubset(gridSetBroker);
                subSets.put(converted.getSRS().toString(), converted);
                // hold it in case the layer is to be saved again
                gridSubsets.add(new XMLGridSubset(converted));
            }
            // Null it for the garbage collector
            grids = null;
        }

        if (this.subSets.isEmpty()) {
            subSets.put(
                    gridSetBroker.getWorldEpsg4326().getName(),
                    GridSubsetFactory.createGridSubSet(gridSetBroker.getWorldEpsg4326()));
            subSets.put(
                    gridSetBroker.getWorldEpsg3857().getName(),
                    GridSubsetFactory.createGridSubSet(gridSetBroker.getWorldEpsg3857()));
        }

        return initializeInternal(gridSetBroker);
    }

    protected abstract boolean initializeInternal(GridSetBroker gridSetBroker);

    /** @return possibly empty list of update sources for this layer */
    @Override
    public List<UpdateSourceDefinition> getUpdateSources() {
        List<UpdateSourceDefinition> sources;
        if (updateSources == null) {
            sources = Collections.emptyList();
        } else {
            sources = updateSources;
        }
        return sources;
    }

    /** Whether to use ETags for this layer */
    @Override
    public boolean useETags() {
        return useETags == null ? false : useETags.booleanValue();
    }

    @Override
    public List<FormatModifier> getFormatModifiers() {
        return formatModifiers;
    }

    @Override
    public void setFormatModifiers(List<FormatModifier> formatModifiers) {
        this.formatModifiers = formatModifiers;
    }

    /** @return the styles configured for the layer, may be null */
    @Override
    public abstract String getStyles();

    /** @return the {x,y} metatiling factors */
    @Override
    public int[] getMetaTilingFactors() {
        return metaWidthHeight == null ? DEFAULT_METATILING_FACTORS : metaWidthHeight;
    }

    /** Whether clients may specify cache=false and go straight to source */
    @Override
    public Boolean isCacheBypassAllowed() {
        return cacheBypassAllowed;
    }

    @Override
    public void setCacheBypassAllowed(boolean allowed) {
        cacheBypassAllowed = Boolean.valueOf(allowed);
    }

    @Override
    public boolean isQueryable() {
        return queryable == null ? false : queryable.booleanValue();
    }

    /**
     * The timeout used when querying the backend server. The same value is used for both the connection and the data
     * timeout, so in theory the timeout could be twice this value.
     */
    @Override
    public Integer getBackendTimeout() {
        return backendTimeout;
    }

    @Override
    public void setBackendTimeout(int seconds) {
        backendTimeout = seconds;
    }

    public List<String> getMimeFormats() {
        return mimeFormats == null ? null : new ArrayList<>(mimeFormats);
    }

    /** @return array with supported MIME types */
    @Override
    public List<MimeType> getMimeTypes() {
        return formats;
    }

    public List<String> getInfoMimeFormats() {
        return infoMimeFormats == null ? null : new ArrayList<>(infoMimeFormats);
    }

    /** @return array with supported MIME types for information */
    @Override
    public List<MimeType> getInfoMimeTypes() {
        return infoFormats;
    }

    @Override
    public int getExpireClients(int zoomLevel) {
        return getExpiration(this.expireClientsList, zoomLevel);
    }

    @Override
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
            for (i = 1; i < length; ) {
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

    @Override
    public List<ParameterFilter> getParameterFilters() {
        return parameterFilters;
    }

    @Override
    public List<RequestFilter> getRequestFilters() {
        return requestFilters;
    }

    @Override
    public GridSubset getGridSubset(String gridSetId) {
        return subSets.get(gridSetId);
    }

    @Override
    public synchronized GridSubset removeGridSubset(String gridSetId) {
        for (Iterator<XMLGridSubset> it = gridSubsets.iterator(); it.hasNext(); ) {
            XMLGridSubset configSubset = it.next();
            if (gridSetId.equals(configSubset.getGridSetName())) {
                it.remove();
                break;
            }
        }
        return subSets.remove(gridSetId);
    }

    @Override
    public synchronized void addGridSubset(GridSubset gridSubset) {
        removeGridSubset(gridSubset.getName());
        gridSubsets.add(new XMLGridSubset(gridSubset));
        subSets.put(gridSubset.getName(), gridSubset);
    }
}
