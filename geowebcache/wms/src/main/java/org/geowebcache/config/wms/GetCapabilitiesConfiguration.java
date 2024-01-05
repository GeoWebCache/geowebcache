/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 */
package org.geowebcache.config.wms;

import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.geotools.http.HTTPClientFinder;
import org.geotools.ows.ServiceException;
import org.geotools.ows.wms.CRSEnvelope;
import org.geotools.ows.wms.Layer;
import org.geotools.ows.wms.StyleImpl;
import org.geotools.ows.wms.WMSCapabilities;
import org.geotools.ows.wms.WebMapServer;
import org.geotools.ows.wms.xml.Dimension;
import org.geotools.ows.wms.xml.Extent;
import org.geotools.util.PreventLocalEntityResolver;
import org.geotools.util.logging.Logging;
import org.geotools.xml.XMLHandlerHints;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.config.DefaultingConfiguration;
import org.geowebcache.config.GridSetConfiguration;
import org.geowebcache.config.TileLayerConfiguration;
import org.geowebcache.config.legends.LegendRawInfo;
import org.geowebcache.config.legends.LegendsRawInfo;
import org.geowebcache.config.wms.parameters.NaiveWMSDimensionFilter;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.filter.parameters.RegexParameterFilter;
import org.geowebcache.filter.parameters.StringParameterFilter;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSetFactory;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.grid.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.meta.LayerMetaInformation;
import org.geowebcache.layer.meta.MetadataURL;
import org.geowebcache.layer.wms.WMSHttpHelper;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.util.URLs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class GetCapabilitiesConfiguration implements TileLayerConfiguration, GridSetConfiguration {

    private static Logger log =
            Logging.getLogger(
                    org.geowebcache.config.wms.GetCapabilitiesConfiguration.class.getName());

    // regex patterns used to parse legends urls parameters
    private static final Pattern LEGEND_WIDTH_PATTERN =
            Pattern.compile(".*width=(\\d+).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEGEND_HEIGHT_PATTERN =
            Pattern.compile(".*height=(\\d+).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEGEND_FORMAT_PATTERN =
            Pattern.compile(".*format=([^&]+).*", Pattern.CASE_INSENSITIVE);

    private GridSetBroker gridSetBroker;

    private String url = null;

    private int backendTimeout = 120;

    private String mimeTypes = null;

    private String metaTiling = null;

    private String vendorParameters = null;

    private Map<String, String> cachedParameters = null;

    private boolean allowCacheBypass = false;

    private final HashMap<String, TileLayer> layers;

    private DefaultingConfiguration primaryConfig;

    private Map<String, GridSet> generatedGridSets = new HashMap<>();

    public GetCapabilitiesConfiguration(
            GridSetBroker gridSetBroker,
            String url,
            String mimeTypes,
            String metaTiling,
            String allowCacheBypass) {
        this.gridSetBroker = gridSetBroker;
        this.url = url;
        this.mimeTypes = mimeTypes;
        this.metaTiling = metaTiling;
        this.layers = new HashMap<>();
        if (Boolean.parseBoolean(allowCacheBypass)) {
            this.allowCacheBypass = true;
        }
        log.info("Constructing from url " + url);
    }

    public GetCapabilitiesConfiguration(
            GridSetBroker gridSetBroker,
            String url,
            String mimeTypes,
            String metaTiling,
            String vendorParameters,
            String allowCacheBypass) {
        this.gridSetBroker = gridSetBroker;
        this.url = url;
        this.mimeTypes = mimeTypes;
        this.metaTiling = metaTiling;
        this.vendorParameters = vendorParameters;
        this.layers = new HashMap<>();
        if (Boolean.parseBoolean(allowCacheBypass)) {
            this.allowCacheBypass = true;
        }
        log.info("Constructing from url " + url);
    }

    public GetCapabilitiesConfiguration(
            GridSetBroker gridSetBroker,
            String url,
            String mimeTypes,
            String metaTiling,
            String vendorParameters,
            Map<String, String> cachedParameters,
            String allowCacheBypass) {
        this(gridSetBroker, url, mimeTypes, metaTiling, vendorParameters, allowCacheBypass);
        this.cachedParameters = cachedParameters;
    }

    /** Optionally used by Spring */
    public void setBackendTimeout(int backendTimeout) {
        this.backendTimeout = backendTimeout;
    }

    /**
     * Identifier for this TileLayerConfiguration instance
     *
     * @return the URL given to the constructor
     */
    @Override
    public String getIdentifier() {
        return url;
    }

    /**
     * Gets the XML document and parses it, creates WMSLayers for the relevant
     *
     * @return the layers described at the given URL
     */
    private synchronized List<TileLayer> getTileLayers(boolean reload) throws GeoWebCacheException {

        final WebMapServer wms;

        try {
            wms = getWMS();
        } catch (ServiceException | IOException e) {
            throw new ConfigurationException(
                    "Could not retrieve (or parse) GetCapaibilities "
                            + this.url
                            + " :"
                            + e.getMessage(),
                    e);
        }
        String wmsUrl = getWMSUrl(wms);
        log.info("Using GetCapabilities " + wmsUrl + " to generate URLs for WMS requests");

        String urlVersion = parseVersion(url);

        final List<TileLayer> layers = getLayers(wms, wmsUrl, urlVersion);

        if (layers == null || layers.isEmpty()) {
            log.log(Level.SEVERE, "Unable to find any layers based on " + url);
        } else {
            log.info("Loaded " + layers.size() + " layers from " + url);
        }

        return layers;
    }

    /**
     * Finds URL to WMS service and attempts to slice away the service parameter, since we will add
     * that anyway.
     */
    private String getWMSUrl(WebMapServer wms) {
        // // http://sigma.openplans.org:8080/geoserver/wms?SERVICE=WMS&
        String wmsUrl = wms.getCapabilities().getRequest().getGetCapabilities().getGet().toString();
        int queryStart = wmsUrl.lastIndexOf("?");
        if (queryStart > 0) {

            String preQuery = wmsUrl.substring(queryStart);
            if (preQuery.equalsIgnoreCase("?service=wms&")) {
                wmsUrl = wmsUrl.substring(0, wmsUrl.lastIndexOf("?"));
            }
        }
        return wmsUrl;
    }

    private List<TileLayer> getLayers(WebMapServer wms, String wmsUrl, String urlVersion)
            throws GeoWebCacheException {
        List<TileLayer> layers = new LinkedList<>();

        WMSCapabilities capabilities = wms.getCapabilities();
        if (capabilities == null) {
            throw new ConfigurationException("Unable to get capabitilies from " + wmsUrl);
        }

        WMSHttpHelper sourceHelper = new WMSHttpHelper();

        List<Layer> layerList = capabilities.getLayerList();
        Iterator<Layer> layerIter = layerList.iterator();

        while (layerIter.hasNext()) {
            Layer layer = layerIter.next();
            String name = layer.getName();
            String stylesStr = "";

            String title = layer.getTitle();

            String description = layer.get_abstract();

            LayerMetaInformation layerMetaInfo = null;
            if (title != null || description != null) {
                layerMetaInfo = new LayerMetaInformation(title, description, null, null);
            }
            boolean queryable = layer.isQueryable();

            if (name != null) {

                LinkedList<ParameterFilter> paramFilters = new LinkedList<>();
                List<StyleImpl> styles = layer.getStyles();

                StringBuffer buf = new StringBuffer();
                if (styles != null) {
                    Iterator<StyleImpl> iter = styles.iterator();
                    boolean hasOne = false;
                    while (iter.hasNext()) {
                        if (hasOne) {
                            buf.append(",");
                        }
                        buf.append(iter.next().getName());
                        hasOne = true;
                    }
                    stylesStr = buf.toString();
                    // set styles parameters
                    StringParameterFilter stylesParameterFilter = new StringParameterFilter();
                    stylesParameterFilter.setKey("STYLES");
                    stylesParameterFilter.setValues(
                            styles.stream().map(StyleImpl::getName).collect(Collectors.toList()));
                    paramFilters.add(stylesParameterFilter);
                }

                double minX = layer.getLatLonBoundingBox().getMinX();
                double minY = layer.getLatLonBoundingBox().getMinY();
                double maxX = layer.getLatLonBoundingBox().getMaxX();
                double maxY = layer.getLatLonBoundingBox().getMaxY();

                BoundingBox bounds4326 = new BoundingBox(minX, minY, maxX, maxY);

                log.info(
                        "Found layer: "
                                + layer.getName()
                                + " with LatLon bbox "
                                + bounds4326.toString());

                BoundingBox bounds3785 =
                        new BoundingBox(
                                longToSphericalMercatorX(minX),
                                latToSphericalMercatorY(minY),
                                longToSphericalMercatorX(maxX),
                                latToSphericalMercatorY(maxY));

                String[] wmsUrls = {wmsUrl};

                for (Dimension dimension : layer.getDimensions().values()) {
                    Extent dimExtent = layer.getExtent(dimension.getName());
                    paramFilters.add(new NaiveWMSDimensionFilter(dimension, dimExtent));
                }

                if (this.cachedParameters != null) {
                    for (Map.Entry<String, String> entry : this.cachedParameters.entrySet()) {
                        if (!"".equals(entry.getKey())) {
                            RegexParameterFilter f = new RegexParameterFilter();
                            f.setRegex(".*");
                            f.setKey(entry.getKey());
                            f.setDefaultValue(entry.getValue());
                            paramFilters.add(f);
                        }
                    }
                }

                WMSLayer wmsLayer = null;
                try {
                    wmsLayer =
                            getLayer(
                                    name,
                                    wmsUrls,
                                    bounds4326,
                                    bounds3785,
                                    stylesStr,
                                    queryable,
                                    layer.getBoundingBoxes(),
                                    paramFilters);
                } catch (GeoWebCacheException gwc) {
                    log.log(
                            Level.SEVERE,
                            "Error creating " + layer.getName() + ": " + gwc.getMessage());
                }

                if (wmsLayer != null) {

                    // Finalize with some defaults
                    wmsLayer.setCacheBypassAllowed(allowCacheBypass);
                    wmsLayer.setBackendTimeout(backendTimeout);

                    wmsLayer.setMetaInformation(layerMetaInfo);

                    if (urlVersion != null) {
                        wmsLayer.setVersion(urlVersion);
                    } else {
                        String wmsVersion = capabilities.getVersion();
                        if (wmsVersion != null && wmsVersion.length() > 0) {
                            wmsLayer.setVersion(wmsVersion);
                        }
                    }
                    wmsLayer.setSourceHelper(sourceHelper);

                    List<org.geotools.ows.wms.xml.MetadataURL> metadataURLs =
                            layer.getMetadataURL();
                    if (metadataURLs != null && !metadataURLs.isEmpty()) {
                        List<MetadataURL> convertedMetadataURLs = new ArrayList<>();
                        for (org.geotools.ows.wms.xml.MetadataURL metadataURL : metadataURLs) {
                            convertedMetadataURLs.add(
                                    new MetadataURL(
                                            metadataURL.getType(),
                                            metadataURL.getFormat(),
                                            metadataURL.getUrl()));
                        }
                        wmsLayer.setMetadataURLs(convertedMetadataURLs);
                    }

                    // add styles legend information
                    wmsLayer.setLegends(extractLegendsInfo(styles));

                    layers.add(wmsLayer);
                }
            }
        }

        return layers;
    }

    /** Helper method that extracts from a legend url the width, height and format parameters. */
    private LegendsRawInfo extractLegendsInfo(List<StyleImpl> styles) {
        LegendsRawInfo legendsRawInfo = new LegendsRawInfo();
        // setting some acceptable default values
        legendsRawInfo.setDefaultWidth(20);
        legendsRawInfo.setDefaultHeight(20);
        legendsRawInfo.setDefaultFormat("image/png");
        for (StyleImpl style : styles) {
            // extracting legend information from each style
            LegendRawInfo legendRawInfo = new LegendRawInfo();
            legendRawInfo.setStyle(style.getName());
            List legendUrls = style.getLegendURLs();
            if (legendUrls != null && !legendUrls.isEmpty()) {
                String legendUrl = (String) legendUrls.get(0);
                // let's see if we can extract width, height and format from the style legend url
                legendRawInfo.setWidth(extractIntegerParameter(legendUrl, LEGEND_WIDTH_PATTERN));
                legendRawInfo.setHeight(extractIntegerParameter(legendUrl, LEGEND_HEIGHT_PATTERN));
                legendRawInfo.setFormat(extractParameter(legendUrl, LEGEND_FORMAT_PATTERN));
                // setting the complete legend url
                legendRawInfo.setCompleteUrl(legendUrl);
            }
            legendsRawInfo.addLegendRawInfo(legendRawInfo);
        }
        return legendsRawInfo;
    }

    /** Helper method that simply extracts from the provided url a certain parameter. */
    private String extractParameter(String url, Pattern pattern) {
        Matcher matcher = pattern.matcher(url);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }

    /** Helper method that simply extracts from the provided url a certain integer parameter. */
    private Integer extractIntegerParameter(String url, Pattern pattern) {
        String value = extractParameter(url, pattern);
        if (value == null) {
            return null;
        }
        return Integer.valueOf(value);
    }

    private WMSLayer getLayer(
            String name,
            String[] wmsurl,
            BoundingBox bounds4326,
            BoundingBox bounds3785,
            String stylesStr,
            boolean queryable,
            Map<String, CRSEnvelope> additionalBounds,
            List<ParameterFilter> paramFilters)
            throws GeoWebCacheException {

        Map<String, GridSubset> grids = new HashMap<>(2);
        grids.put(
                gridSetBroker.getWorldEpsg4326().getName(),
                GridSubsetFactory.createGridSubSet(
                        gridSetBroker.getWorldEpsg4326(), bounds4326, 0, 30));
        grids.put(
                gridSetBroker.getWorldEpsg3857().getName(),
                GridSubsetFactory.createGridSubSet(
                        gridSetBroker.getWorldEpsg3857(), bounds3785, 0, 30));

        if (additionalBounds != null && !additionalBounds.isEmpty()) {
            Iterator<CRSEnvelope> iter = additionalBounds.values().iterator();
            while (iter.hasNext()) {
                CRSEnvelope env = iter.next();
                SRS srs = null;
                if (env.getEPSGCode() != null) {
                    srs = SRS.getSRS(env.getEPSGCode());
                }

                if (srs == null) {
                    log.log(Level.SEVERE, env.toString() + " has no EPSG code");
                } else if (srs.getNumber() == 4326
                        || srs.getNumber() == 900913
                        || srs.getNumber() == 3857) {
                    log.fine("Skipping " + srs.toString() + " for " + name);
                } else {
                    String gridSetName = name + ":" + srs.toString();
                    BoundingBox extent =
                            new BoundingBox(
                                    env.getMinX(), env.getMinY(), env.getMaxX(), env.getMaxY());

                    GridSet gridSet =
                            GridSetFactory.createGridSet(
                                    gridSetName,
                                    srs,
                                    extent,
                                    false,
                                    25,
                                    null,
                                    GridSetFactory.DEFAULT_PIXEL_SIZE_METER,
                                    256,
                                    256,
                                    false);
                    grids.put(gridSetName, GridSubsetFactory.createGridSubSet(gridSet));
                }
            }
        }

        List<String> mimeFormats = null;
        if (this.mimeTypes != null) {
            String[] mimeFormatArray = this.mimeTypes.split(",");
            mimeFormats = new ArrayList<>(mimeFormatArray.length);

            // This is stupid... but oh well, we're only doing it once
            for (String s : mimeFormatArray) {
                mimeFormats.add(s);
            }
        } else {
            mimeFormats = new ArrayList<>(3);
            mimeFormats.add("image/png");
            mimeFormats.add("image/png8");
            mimeFormats.add("image/jpeg");
        }

        String[] metaStrings = this.metaTiling.split("x");

        int[] metaWidthHeight = {
            Integer.parseInt(metaStrings[0]), Integer.parseInt(metaStrings[1])
        };

        return new WMSLayer(
                name,
                wmsurl,
                stylesStr,
                name,
                mimeFormats,
                grids,
                paramFilters,
                metaWidthHeight,
                this.vendorParameters,
                queryable,
                null);
    }

    WebMapServer getWMS() throws IOException, ServiceException {
        Map<String, Object> hints = new HashMap<>();
        hints.put(XMLHandlerHints.ENTITY_RESOLVER, PreventLocalEntityResolver.INSTANCE);
        return new WebMapServer(URLs.of(url), HTTPClientFinder.createClient(), hints);
    }

    private String parseVersion(String url) {
        String tmp = url.toLowerCase();
        int start = tmp.indexOf("version=");
        if (start == -1) {
            return null;
        }

        start += "version=".length();

        int stop = tmp.indexOf("&", start);
        if (stop > 0) {
            return tmp.substring(start, stop);
        } else {
            return tmp.substring(start);
        }
    }

    private double longToSphericalMercatorX(double x) {
        return (x / 180.0) * 20037508.34;
    }

    private double latToSphericalMercatorY(double y) {
        if (y > 85.05112) {
            y = 85.05112;
        }

        if (y < -85.05112) {
            y = -85.05112;
        }

        y = (Math.PI / 180.0) * y;
        double tmp = Math.PI / 4.0 + y / 2.0;
        return 20037508.34 * Math.log(Math.tan(tmp)) / Math.PI;
    }

    @Override
    public void afterPropertiesSet() throws GeoWebCacheException {
        List<TileLayer> tileLayers = getTileLayers(true);
        Set<String> brokerNames = gridSetBroker.getGridSetNames();
        for (TileLayer layer : tileLayers) {
            layer.initialize(gridSetBroker);
            if (primaryConfig != null) {
                primaryConfig.setDefaultValues(layer);
            } else if (log.isLoggable(Level.SEVERE)) {
                log.log(
                        Level.SEVERE,
                        "GetCapabilitiesConfiguration could not initialize a layer with default "
                                + "values as it does not have a global configuration to delegate to.");
            }
            layers.put(layer.getName(), layer);

            Map<String, GridSet> generatedForLayer =
                    Sets.difference(layer.getGridSubsets(), brokerNames).stream()
                            .map(layer::getGridSubset)
                            .map(GridSubset::getGridSet)
                            .collect(Collectors.toMap(GridSet::getName, UnaryOperator.identity()));
            generatedGridSets.putAll(generatedForLayer);
        }
    }

    /** @see TileLayerConfiguration#getLayers() */
    @Override
    public Collection<? extends TileLayer> getLayers() {
        return Collections.unmodifiableList(new ArrayList<>(layers.values()));
    }

    /** @see TileLayerConfiguration#getLayerNames() */
    @Override
    public Set<String> getLayerNames() {
        return new HashSet<>(layers.keySet());
    }

    /** @see TileLayerConfiguration#containsLayer(java.lang.String) */
    @Override
    public boolean containsLayer(String layerName) {
        return getLayer(layerName) != null;
    }

    /** @see TileLayerConfiguration#getTileLayer(java.lang.String) */
    @Override
    public Optional<TileLayer> getLayer(String layerName) {
        return Optional.ofNullable(layers.get(layerName));
    }

    /** @see TileLayerConfiguration#getLayerCount() */
    @Override
    public int getLayerCount() {
        return layers.size();
    }

    /** @see TileLayerConfiguration#removeLayer(java.lang.String) */
    // TODO: Why doesn't this throw an IllegalArgument exception: read-only?
    @Override
    public void removeLayer(String layerName) throws NoSuchElementException {
        if (layers.remove(layerName) == null) {
            throw new NoSuchElementException("Layer " + layerName + " does not exist");
        }
    }

    /** @see TileLayerConfiguration#modifyLayer(org.geowebcache.layer.TileLayer) */
    @Override
    public void modifyLayer(TileLayer tl) throws NoSuchElementException {
        throw new UnsupportedOperationException(
                "modifyLayer is not supported by " + getClass().getSimpleName());
    }

    /** @see TileLayerConfiguration#renameLayer(String, String) */
    @Override
    public void renameLayer(String oldName, String newName)
            throws NoSuchElementException, IllegalArgumentException {
        throw new UnsupportedOperationException(
                "renameLayer is not supported by " + getClass().getSimpleName());
    }

    /**
     * @return {@code false}
     * @see TileLayerConfiguration#canSave(org.geowebcache.layer.TileLayer)
     */
    @Override
    public boolean canSave(TileLayer tl) {
        return false;
    }

    /** @see TileLayerConfiguration#addLayer(org.geowebcache.layer.TileLayer) */
    @Override
    public void addLayer(TileLayer tl) throws IllegalArgumentException {
        if (tl == null) {
            throw new NullPointerException();
        }
        throw new IllegalArgumentException(
                "This is a read only configuration object, can't add tile layer " + tl.getName());
    }

    /** Get the global configuration delegated to. */
    protected DefaultingConfiguration getPrimaryConfig() {
        return primaryConfig;
    }

    /** Set the global configuration object to delegate to. */
    public void setPrimaryConfig(DefaultingConfiguration primaryConfig) {
        this.primaryConfig = primaryConfig;
    }

    @Override
    public String getLocation() {
        return this.url;
    }

    @Override
    public void addGridSet(GridSet gridSet) throws IllegalArgumentException {
        if (gridSet == null) {
            throw new NullPointerException();
        }
        throw new UnsupportedOperationException(
                "This is a read only configuration object, can't add gridset " + gridSet.getName());
    }

    @Override
    public void removeGridSet(String gridSetName) {
        throw new UnsupportedOperationException(
                "This is a read only configuration object, can't add gridset " + gridSetName);
    }

    @Override
    public Optional<GridSet> getGridSet(String name) throws NoSuchElementException {
        return Optional.ofNullable(generatedGridSets.get(name)).map(GridSet::new);
    }

    @Override
    public Collection<GridSet> getGridSets() {
        return generatedGridSets.values().stream().map(GridSet::new).collect(Collectors.toList());
    }

    @Override
    public void modifyGridSet(GridSet gridSet)
            throws NoSuchElementException, IllegalArgumentException, UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void renameGridSet(String oldName, String newName)
            throws NoSuchElementException, IllegalArgumentException, UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean canSave(GridSet gridset) {
        return false;
    }

    @Autowired
    @Override
    public void setGridSetBroker(@Qualifier("gwcGridSetBroker") GridSetBroker broker) {
        this.gridSetBroker = broker;
    }

    @Override
    public void deinitialize() throws Exception {
        this.generatedGridSets.clear();
        this.layers.clear();
    }
}
