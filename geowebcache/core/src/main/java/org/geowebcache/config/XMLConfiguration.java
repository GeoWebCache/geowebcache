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
 * @author Arne Kepp, Marius Suta, The Open Planning Project, Copyright 2008
 */
package org.geowebcache.config;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.config.ContextualConfigurationProvider.Context;
import org.geowebcache.config.legends.LegendsRawInfo;
import org.geowebcache.config.legends.LegendsRawInfoConverter;
import org.geowebcache.config.meta.ServiceInformation;
import org.geowebcache.filter.parameters.*;
import org.geowebcache.filter.request.CircularExtentFilter;
import org.geowebcache.filter.request.FileRasterFilter;
import org.geowebcache.filter.request.WMSRasterFilter;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.io.GeoWebCacheXStream;
import org.geowebcache.layer.ExpirationRule;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.meta.ContactInformation;
import org.geowebcache.layer.meta.LayerMetaInformation;
import org.geowebcache.layer.updatesource.GeoRSSFeedDefinition;
import org.geowebcache.layer.wms.WMSHttpHelper;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.mime.FormatModifier;
import org.geowebcache.seed.SeedRequest;
import org.geowebcache.seed.TruncateAllRequest;
import org.geowebcache.seed.TruncateLayerRequest;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.storage.UnsuitableStorageException;
import org.geowebcache.util.ApplicationContextProvider;
import org.geowebcache.util.ExceptionUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.Assert;
import org.springframework.web.context.WebApplicationContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * XMLConfiguration class responsible for reading/writing layer configurations to and from XML file
 *
 * <p>NOTE {@link #setGridSetBroker(GridSetBroker)} MUST have been called before any other method is
 * used, otherwise this configuration is in an inconsistent and unpredictable state, and will throw
 * an {@link IllegalStateException}. This is set automatically by Spring through the use of {@link
 * Autowired}
 */
public class XMLConfiguration
        implements TileLayerConfiguration,
                InitializingBean,
                DefaultingConfiguration,
                ServerConfiguration,
                BlobStoreConfiguration,
                GridSetConfiguration {

    public static final String DEFAULT_CONFIGURATION_FILE_NAME = "geowebcache.xml";

    private static Log log = LogFactory.getLog(org.geowebcache.config.XMLConfiguration.class);

    /** Web app context, used to look up {@link XMLConfigurationProvider}s. */
    private final WebApplicationContext context;

    private final ConfigurationResourceProvider resourceProvider;

    private volatile GeoWebCacheConfiguration gwcConfig;

    private transient Map<String, TileLayer> layers;

    private transient Map<String, GridSet> gridSets;

    private GridSetBroker gridSetBroker;

    private ListenerCollection<BlobStoreConfigurationListener> blobStoreListeners =
            new ListenerCollection<>();

    /**
     * Base Constructor with custom ConfiguratioNResourceProvider
     *
     * @param appCtx use to lookup {@link XMLConfigurationProvider} extensions, may be {@code null}
     * @param inFac
     */
    public XMLConfiguration(
            final ApplicationContextProvider appCtx, final ConfigurationResourceProvider inFac) {
        this.context = appCtx == null ? null : appCtx.getApplicationContext();
        this.resourceProvider = inFac;
    }

    /**
     * File System based Constructor
     *
     * @param appCtx use to lookup {@link XMLConfigurationProvider} extensions, may be {@code null}
     * @param configFileDirectory
     * @param storageDirFinder
     * @throws ConfigurationException
     */
    public XMLConfiguration(
            final ApplicationContextProvider appCtx,
            final String configFileDirectory,
            final DefaultStorageFinder storageDirFinder)
            throws ConfigurationException {
        this(
                appCtx,
                new XMLFileResourceProvider(
                        DEFAULT_CONFIGURATION_FILE_NAME,
                        appCtx,
                        configFileDirectory,
                        storageDirFinder));
        resourceProvider.setTemplate("/" + DEFAULT_CONFIGURATION_FILE_NAME);
    }

    /**
     * Constructor that will look for {@code geowebcache.xml} at the directory defined by {@code
     * storageDirFinder}
     *
     * @param appCtx use to lookup {@link XMLConfigurationProvider} extenions, may be {@code null}
     * @param storageDirFinder
     * @throws ConfigurationException
     */
    public XMLConfiguration(
            final ApplicationContextProvider appCtx, final DefaultStorageFinder storageDirFinder)
            throws ConfigurationException {
        this(
                appCtx,
                new XMLFileResourceProvider(
                        DEFAULT_CONFIGURATION_FILE_NAME, appCtx, storageDirFinder));
        resourceProvider.setTemplate("/" + DEFAULT_CONFIGURATION_FILE_NAME);
    }

    /**
     * Constructor that will accept an absolute or relative path for finding {@code geowebcache.xml}
     *
     * @param appCtx
     * @param configFileDirectory
     * @throws ConfigurationException
     */
    public XMLConfiguration(
            final ApplicationContextProvider appCtx, final String configFileDirectory)
            throws ConfigurationException {
        this(appCtx, configFileDirectory, null);
    }

    /**
     * Path to template to use when there is no config file.
     *
     * @param template
     */
    public void setTemplate(String template) {
        resourceProvider.setTemplate(template);
    }

    /**
     * @return The root path where configuration is stored
     * @throws ConfigurationException
     */
    public String getConfigLocation() throws ConfigurationException {
        try {
            return resourceProvider.getLocation();
        } catch (IOException e) {
            throw new ConfigurationException(e.getMessage(), e);
        }
    }

    /** @see ServerConfiguration#isRuntimeStatsEnabled() */
    public Boolean isRuntimeStatsEnabled() {
        if (getGwcConfig() == null || getGwcConfig().getRuntimeStats() == null) {
            return true;
        } else {
            return getGwcConfig().getRuntimeStats();
        }
    }

    /**
     * @see ServerConfiguration#setRuntimeStatsEnabled(Boolean)
     * @param isEnabled
     */
    public void setRuntimeStatsEnabled(Boolean isEnabled) throws IOException {
        getGwcConfig().setRuntimeStats(isEnabled);
        save();
    }

    /** @see ServerConfiguration#getServiceInformation() */
    public synchronized ServiceInformation getServiceInformation() {
        return getGwcConfig().getServiceInformation();
    }

    /**
     * @see ServerConfiguration#setServiceInformation(ServiceInformation);
     * @param serviceInfo
     */
    public void setServiceInformation(ServiceInformation serviceInfo) throws IOException {
        getGwcConfig().setServiceInformation(serviceInfo);
        save();
    }

    /**
     * TileLayerConfiguration objects lacking their own defaults can delegate to this
     *
     * @param layer
     */
    @Override
    public void setDefaultValues(TileLayer layer) {
        // Additional values that can have defaults set
        if (layer.isCacheBypassAllowed() == null) {
            if (getGwcConfig().getCacheBypassAllowed() != null) {
                layer.setCacheBypassAllowed(getGwcConfig().getCacheBypassAllowed());
            } else {
                layer.setCacheBypassAllowed(false);
            }
        }

        if (layer.getBackendTimeout() == null) {
            if (getGwcConfig().getBackendTimeout() != null) {
                layer.setBackendTimeout(getGwcConfig().getBackendTimeout());
            } else {
                layer.setBackendTimeout(120);
            }
        }

        if (layer.getFormatModifiers() == null) {
            if (getGwcConfig().getFormatModifiers() != null) {
                layer.setFormatModifiers(getGwcConfig().getFormatModifiers());
            }
        }

        if (layer instanceof WMSLayer) {
            WMSLayer wl = (WMSLayer) layer;

            URL proxyUrl = null;
            try {
                if (getGwcConfig().getProxyUrl() != null) {
                    proxyUrl = new URL(getGwcConfig().getProxyUrl());
                    log.debug("Using proxy " + proxyUrl.getHost() + ":" + proxyUrl.getPort());
                } else if (wl.getProxyUrl() != null) {
                    proxyUrl = new URL(wl.getProxyUrl());
                    log.debug("Using proxy " + proxyUrl.getHost() + ":" + proxyUrl.getPort());
                }
            } catch (MalformedURLException e) {
                log.error(
                        "could not parse proxy URL "
                                + wl.getProxyUrl()
                                + " ! continuing WITHOUT proxy!",
                        e);
            }

            final WMSHttpHelper sourceHelper;

            if (wl.getHttpUsername() != null) {
                sourceHelper =
                        new WMSHttpHelper(wl.getHttpUsername(), wl.getHttpPassword(), proxyUrl);
                log.debug(
                        "Using per-layer HTTP credentials for "
                                + wl.getName()
                                + ", "
                                + "username "
                                + wl.getHttpUsername());
            } else if (getGwcConfig().getHttpUsername() != null) {
                sourceHelper =
                        new WMSHttpHelper(
                                getGwcConfig().getHttpUsername(),
                                getGwcConfig().getHttpPassword(),
                                proxyUrl);
                log.debug("Using global HTTP credentials for " + wl.getName());
            } else {
                sourceHelper = new WMSHttpHelper(null, null, proxyUrl);
                log.debug("Not using HTTP credentials for " + wl.getName());
            }

            wl.setSourceHelper(sourceHelper);
            wl.setLockProvider(getGwcConfig().getLockProvider());
        }
    }

    private GeoWebCacheConfiguration loadConfiguration() throws ConfigurationException {
        Assert.isTrue(resourceProvider.hasInput(), "Resource provider must have an input");
        InputStream in;
        try {
            in = resourceProvider.in();
            try {
                return loadConfiguration(in);
            } finally {
                in.close();
            }
        } catch (IOException e) {
            throw new ConfigurationException(
                    "Error parsing config file " + resourceProvider.getId(), e);
        }
    }

    private GeoWebCacheConfiguration loadConfiguration(InputStream xmlFile)
            throws IOException, ConfigurationException {
        Node rootNode = loadDocument(xmlFile);
        XStream xs = getConfiguredXStreamWithContext(new GeoWebCacheXStream(), Context.PERSIST);

        GeoWebCacheConfiguration config;
        config = (GeoWebCacheConfiguration) xs.unmarshal(new DomReader((Element) rootNode));
        return config;
    }

    private synchronized void save() throws IOException {
        if (!resourceProvider.hasOutput()) {
            return;
        }

        try {
            resourceProvider.backup();
        } catch (Exception e) {
            log.warn("Error creating back up of configuration file " + resourceProvider.getId(), e);
        }

        persistToFile();
    }

    public XStream getConfiguredXStream(XStream xs) {
        return getConfiguredXStreamWithContext(
                xs, this.context, (ContextualConfigurationProvider.Context) null);
    }

    public static XStream getConfiguredXStream(XStream xs, WebApplicationContext context) {
        return getConfiguredXStreamWithContext(
                xs, context, (ContextualConfigurationProvider.Context) null);
    }

    public XStream getConfiguredXStreamWithContext(
            XStream xs, ContextualConfigurationProvider.Context providerContext) {
        return getConfiguredXStreamWithContext(xs, this.context, providerContext);
    }

    public static XStream getConfiguredXStreamWithContext(
            XStream xs,
            WebApplicationContext context,
            ContextualConfigurationProvider.Context providerContext) {

        {
            // Allow any implementation of these extension points
            xs.allowTypeHierarchy(org.geowebcache.layer.TileLayer.class);
            xs.allowTypeHierarchy(org.geowebcache.filter.parameters.ParameterFilter.class);
            xs.allowTypeHierarchy(org.geowebcache.filter.request.RequestFilter.class);
            xs.allowTypeHierarchy(org.geowebcache.config.BlobStoreInfo.class);
            xs.allowTypeHierarchy(TileLayerConfiguration.class);

            // Allow anything that's part of GWC
            // TODO: replace this with a more narrow whitelist
            xs.allowTypesByWildcard(new String[] {"org.geowebcache.**"});
        }

        xs.setMode(XStream.NO_REFERENCES);

        xs.addDefaultImplementation(ArrayList.class, List.class);

        xs.alias("gwcConfiguration", GeoWebCacheConfiguration.class);
        xs.useAttributeFor(GeoWebCacheConfiguration.class, "xmlns_xsi");
        xs.aliasField("xmlns:xsi", GeoWebCacheConfiguration.class, "xmlns_xsi");
        xs.useAttributeFor(GeoWebCacheConfiguration.class, "xsi_schemaLocation");
        xs.aliasField("xsi:schemaLocation", GeoWebCacheConfiguration.class, "xsi_schemaLocation");
        xs.useAttributeFor(GeoWebCacheConfiguration.class, "xmlns");

        // xs.alias("layers", List.class);
        xs.alias("wmsLayer", WMSLayer.class);

        // configuration for legends info
        xs.registerConverter(new LegendsRawInfoConverter());
        xs.alias("legends", LegendsRawInfo.class);

        xs.alias("blobStores", new ArrayList<BlobStoreInfo>().getClass());
        xs.alias("FileBlobStore", FileBlobStoreInfo.class);
        xs.aliasAttribute(BlobStoreInfo.class, "_default", "default");
        // Alias added to retain XML backwards-compatibility.
        // TODO: Would be nice to be able to use name for consistency
        xs.aliasField("id", BlobStoreInfo.class, "name");

        // These two are for 1.1.x compatibility
        xs.alias("grids", new ArrayList<XMLOldGrid>().getClass());
        xs.alias("grid", XMLOldGrid.class);

        xs.alias("gridSet", XMLGridSet.class);
        xs.alias("gridSubset", XMLGridSubset.class);

        xs.alias("mimeFormats", new ArrayList<String>().getClass());
        xs.alias("formatModifiers", new ArrayList<FormatModifier>().getClass());
        xs.alias("srs", org.geowebcache.grid.SRS.class);
        xs.alias("parameterFilters", new ArrayList<ParameterFilter>().getClass());
        xs.alias("parameterFilter", ParameterFilter.class);
        xs.alias("seedRequest", SeedRequest.class);

        xs.processAnnotations(CaseNormalizer.class);
        xs.processAnnotations(StringParameterFilter.class);
        xs.processAnnotations(RegexParameterFilter.class);
        xs.processAnnotations(FloatParameterFilter.class);
        xs.processAnnotations(IntegerParameterFilter.class);

        xs.alias("formatModifier", FormatModifier.class);

        xs.alias("circularExtentFilter", CircularExtentFilter.class);
        xs.alias("wmsRasterFilter", WMSRasterFilter.class);
        xs.alias("fileRasterFilter", FileRasterFilter.class);

        xs.alias("expirationRule", ExpirationRule.class);
        xs.useAttributeFor(ExpirationRule.class, "minZoom");
        xs.useAttributeFor(ExpirationRule.class, "expiration");

        xs.alias("geoRssFeed", GeoRSSFeedDefinition.class);

        xs.alias("metaInformation", LayerMetaInformation.class);

        xs.alias("serviceInformation", ServiceInformation.class);
        xs.alias("contactInformation", ContactInformation.class);

        xs.omitField(ServiceInformation.class, "citeCompliant");

        xs.processAnnotations(TruncateLayerRequest.class);
        xs.processAnnotations(TruncateAllRequest.class);

        if (context != null) {
            /*
             * Look up XMLConfigurationProvider extension points and let them contribute to the
             * configuration
             */
            List<XMLConfigurationProvider> configExtensions =
                    GeoWebCacheExtensions.extensions(XMLConfigurationProvider.class, context);
            for (XMLConfigurationProvider extension : configExtensions) {
                // Check if the provider is context dependent
                if (extension instanceof ContextualConfigurationProvider
                        &&
                        // Check if the context is applicable for the provider
                        (providerContext == null
                                || !((ContextualConfigurationProvider) extension)
                                        .appliesTo(providerContext))) {
                    // If so, try the next one
                    continue;
                }

                xs = extension.getConfiguredXStream(xs);
            }
        }
        return xs;
    }

    /**
     * Method responsible for writing out the entire GeoWebCacheConfiguration object
     *
     * <p>throws an exception if it does not succeed
     */
    private void persistToFile() throws IOException {
        Assert.isTrue(resourceProvider.hasOutput(), "Resource provider must have an output");
        // create the XStream for serializing the configuration
        XStream xs = getConfiguredXStreamWithContext(new GeoWebCacheXStream(), Context.PERSIST);

        try (OutputStreamWriter writer = new OutputStreamWriter(resourceProvider.out(), "UTF-8")) {
            // set version to latest
            String currentSchemaVersion = getCurrentSchemaVersion();
            getGwcConfig().setVersion(currentSchemaVersion);

            writer.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
            xs.toXML(getGwcConfig(), writer);
        } catch (UnsupportedEncodingException uee) {
            throw new IOException(uee.getMessage(), uee);
        } catch (FileNotFoundException fnfe) {
            throw fnfe;
        } catch (IOException e) {
            throw (IOException)
                    new IOException(
                                    "Error writing to "
                                            + resourceProvider.getId()
                                            + ": "
                                            + e.getMessage())
                            .initCause(e);
        }

        log.info("Wrote configuration to " + resourceProvider.getId());
    }

    /**
     * @return {@code true} only if {@code tl instanceof WMSLayer}
     * @see TileLayerConfiguration#canSave(org.geowebcache.layer.TileLayer)
     */
    public boolean canSave(TileLayer tl) {
        if (tl.isTransientLayer()) {
            return false;
        }
        return canSaveIfNotTransient(tl);
    }

    protected boolean canSaveIfNotTransient(TileLayer tl) {
        if (tl instanceof WMSLayer) {
            return true;
        }
        return GeoWebCacheExtensions.extensions(XMLConfigurationProvider.class, this.context)
                .stream()
                .anyMatch(provider -> provider.canSave(tl));
    }

    /**
     * @param tl the layer to add to this configuration
     * @return
     * @throws IllegalArgumentException if a layer named the same than {@code tl} already exists
     * @see TileLayerConfiguration#addLayer(org.geowebcache.layer.TileLayer)
     */
    public synchronized void addLayer(TileLayer tl) throws IllegalArgumentException {
        if (tl == null) {
            throw new NullPointerException();
        }
        if (!canSaveIfNotTransient(tl)) {
            throw new IllegalArgumentException(
                    "Can't add layers of type " + tl.getClass().getName());
        }
        if (getLayer(tl.getName()).isPresent()) {
            throw new IllegalArgumentException("Layer '" + tl.getName() + "' already exists");
        }

        initialize(tl);
        getGwcConfig().getLayers().add(tl);
        updateLayers();
        try {
            save();
        } catch (IOException e) {
            // If save fails, try to revert the change to maintain a consistent state.
            if (getGwcConfig().getLayers().remove(tl)) {
                updateLayers();
            }
            throw new ConfigurationPersistenceException("Unable to add layer " + tl.getName(), e);
        }
    }

    /**
     * Method responsible for modifying an existing layer.
     *
     * @param tl the new layer to overwrite the existing layer
     * @throws NoSuchElementException
     * @see TileLayerConfiguration#modifyLayer(org.geowebcache.layer.TileLayer)
     */
    public synchronized void modifyLayer(TileLayer tl) throws NoSuchElementException {
        TileLayer previous = findLayer(tl.getName());
        if (!canSaveIfNotTransient(tl)) {
            throw new IllegalArgumentException(
                    "Can't add layers of type " + tl.getClass().getName());
        }

        getGwcConfig().getLayers().remove(previous);
        initialize(tl);
        getGwcConfig().getLayers().add(tl);
        updateLayers();
        try {
            save();
        } catch (IOException e) {
            // If save fails, try to revert the change to maintain a consistent state.
            getGwcConfig().getLayers().remove(tl);
            initialize(previous);
            getGwcConfig().getLayers().add(previous);
            updateLayers();
            throw new IllegalArgumentException("Unable to modify layer " + tl.getName(), e);
        }
    }

    protected TileLayer findLayer(String layerName) throws NoSuchElementException {
        TileLayer layer =
                getLayer(layerName)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Layer " + layerName + " does not exist"));
        return layer;
    }

    /** @see TileLayerConfiguration#renameLayer(String, String) */
    public void renameLayer(String oldName, String newName)
            throws NoSuchElementException, IllegalArgumentException {
        throw new UnsupportedOperationException(
                "renameLayer is not supported by " + getClass().getSimpleName());
    }

    /** @see TileLayerConfiguration#removeLayer(java.lang.String) */
    public synchronized void removeLayer(final String layerName)
            throws NoSuchElementException, IllegalArgumentException {
        final TileLayer tileLayer = findLayer(layerName);
        if (tileLayer == null) {
            throw new NoSuchElementException("Layer " + layerName + " does not exist");
        }

        boolean removed = getGwcConfig().getLayers().remove(tileLayer);
        if (removed) {
            updateLayers();
        } else {
            throw new NoSuchElementException("Layer " + tileLayer.getName() + " does not exist");
        }
        try {
            save();
        } catch (IOException e) {
            // If save fails, try to revert the removal to maintain a consistent state.
            if (getGwcConfig().getLayers().add(tileLayer)) {
                updateLayers();
            }
            throw new IllegalArgumentException("Unable to remove layer " + tileLayer, e);
        }
    }

    /**
     * @param gridSet
     * @throws GeoWebCacheException
     */
    private synchronized void addOrReplaceGridSet(final XMLGridSet gridSet)
            throws IllegalArgumentException {
        final String gridsetName = gridSet.getName();

        List<XMLGridSet> xmlGridSets = getGwcConfig().getGridSets();

        xmlGridSets.removeIf(xgs -> gridsetName.equals(xgs.getName()));

        xmlGridSets.add(gridSet);
    }

    /**
     * Method responsible for loading xml configuration file and parsing it into a W3C DOM Document
     *
     * @param xmlFile the file contaning the layer configurations
     * @return W3C DOM Document
     */
    static Node loadDocument(InputStream xmlFile) throws ConfigurationException, IOException {
        Node topNode = null;
        try {
            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
            docBuilderFactory.setNamespaceAware(true);
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            topNode = checkAndTransform(docBuilder.parse(xmlFile));
        } catch (Exception e) {
            throw (IOException) new IOException(e.getMessage()).initCause(e);
        }

        return topNode;
    }

    private static Node checkAndTransform(Document doc) throws ConfigurationException {
        Node rootNode = doc.getDocumentElement();

        // debugPrint(rootNode);

        if (!rootNode.getNodeName().equals("gwcConfiguration")) {
            log.info("The configuration file is of the pre 1.0 type, trying to convert.");
            rootNode = applyTransform(rootNode, "geowebcache_pre10.xsl").getFirstChild();
        }

        // debugPrint(rootNode);

        if (rootNode.getNamespaceURI().equals("http://geowebcache.org/schema/1.0.0")) {
            log.info("Updating configuration from 1.0.0 to 1.0.1");
            rootNode = applyTransform(rootNode, "geowebcache_100.xsl").getFirstChild();
        }

        // debugPrint(rootNode);

        if (rootNode.getNamespaceURI().equals("http://geowebcache.org/schema/1.0.1")) {
            log.info("Updating configuration from 1.0.1 to 1.0.2");
            rootNode = applyTransform(rootNode, "geowebcache_101.xsl").getFirstChild();
        }

        // debugPrint(rootNode);

        if (rootNode.getNamespaceURI().equals("http://geowebcache.org/schema/1.0.2")) {
            log.info("Updating configuration from 1.0.2 to 1.1.0");
            rootNode = applyTransform(rootNode, "geowebcache_102.xsl").getFirstChild();
        }

        if (rootNode.getNamespaceURI().equals("http://geowebcache.org/schema/1.1.0")) {
            log.info("Updating configuration from 1.1.0 to 1.1.3");
            rootNode = applyTransform(rootNode, "geowebcache_110.xsl").getFirstChild();
        }

        if (rootNode.getNamespaceURI().equals("http://geowebcache.org/schema/1.1.3")) {
            log.info("Updating configuration from 1.1.3 to 1.1.4");
            rootNode = applyTransform(rootNode, "geowebcache_113.xsl").getFirstChild();
        }

        if (rootNode.getNamespaceURI().equals("http://geowebcache.org/schema/1.1.4")) {
            log.info("Updating configuration from 1.1.4 to 1.1.5");
            rootNode = applyTransform(rootNode, "geowebcache_114.xsl").getFirstChild();
        }

        if (rootNode.getNamespaceURI().equals("http://geowebcache.org/schema/1.1.5")) {
            log.info("Updating configuration from 1.1.5 to 1.2.0");
            rootNode = applyTransform(rootNode, "geowebcache_115.xsl").getFirstChild();
        }

        if (rootNode.getNamespaceURI().equals("http://geowebcache.org/schema/1.2.0")) {
            log.info("Updating configuration from 1.2.0 to 1.2.1");
            rootNode = applyTransform(rootNode, "geowebcache_120.xsl").getFirstChild();
        }

        if (rootNode.getNamespaceURI().equals("http://geowebcache.org/schema/1.2.1")) {
            log.info("Updating configuration from 1.2.1 to 1.2.2");
            rootNode = applyTransform(rootNode, "geowebcache_121.xsl").getFirstChild();
        }

        if (rootNode.getNamespaceURI().equals("http://geowebcache.org/schema/1.2.2")) {
            log.info("Updating configuration from 1.2.2 to 1.2.4");
            rootNode = applyTransform(rootNode, "geowebcache_122.xsl").getFirstChild();
        }

        if (rootNode.getNamespaceURI().equals("http://geowebcache.org/schema/1.2.4")) {
            log.info("Updating configuration from 1.2.4 to 1.2.5");
            rootNode = applyTransform(rootNode, "geowebcache_124.xsl").getFirstChild();
        }

        if (rootNode.getNamespaceURI().equals("http://geowebcache.org/schema/1.2.5")) {
            log.info("Updating configuration from 1.2.5 to 1.2.6");
            rootNode = applyTransform(rootNode, "geowebcache_125.xsl").getFirstChild();
        }

        if (rootNode.getNamespaceURI().equals("http://geowebcache.org/schema/1.2.6")) {
            log.info("Updating configuration from 1.2.6 to 1.5.0");
            rootNode = applyTransform(rootNode, "geowebcache_126.xsl").getFirstChild();
        }

        if (rootNode.getNamespaceURI().equals("http://geowebcache.org/schema/1.5.0")) {
            log.info("Updating configuration from 1.5.0 to 1.5.1");
            rootNode = applyTransform(rootNode, "geowebcache_150.xsl").getFirstChild();
        }

        if (rootNode.getNamespaceURI().equals("http://geowebcache.org/schema/1.5.1")) {
            log.info("Updating configuration from 1.5.1 to 1.6.0");
            rootNode = applyTransform(rootNode, "geowebcache_151.xsl").getFirstChild();
        }

        // Check again after transform
        if (!rootNode.getNodeName().equals("gwcConfiguration")) {
            log.error("Unable to parse file, expected gwcConfiguration at root after transform.");
            throw new ConfigurationException("Unable to parse after transform.");
        } else {
            // Parsing the schema file
            try {
                validate(rootNode);
                log.info("TileLayerConfiguration file validated fine.");
            } catch (SAXException e) {
                String msg = "*** GWC configuration validation error: " + e.getMessage();
                char[] c = new char[4 + msg.length()];
                Arrays.fill(c, '*');
                String warndecoration = new String(c).substring(0, 80);
                log.warn(warndecoration);
                log.warn(msg);
                log.warn(
                        "*** Will try to use configuration anyway. Please check the order of declared elements against the schema.");
                log.warn(warndecoration);
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        return rootNode;
    }

    static void validate(Node rootNode) throws SAXException, IOException {
        // Perform validation
        // TODO dont know why this one suddenly failed to look up, revert to
        // XMLConstants.W3C_XML_SCHEMA_NS_URI
        SchemaFactory factory = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");
        InputStream is = XMLConfiguration.class.getResourceAsStream("geowebcache.xsd");

        Schema schema = factory.newSchema(new StreamSource(is));
        Validator validator = schema.newValidator();

        // debugPrint(rootNode);

        DOMSource domSrc = new DOMSource(rootNode);
        validator.validate(domSrc);
    }

    static String getCurrentSchemaVersion() {

        Document dom;
        try (InputStream is = XMLConfiguration.class.getResourceAsStream("geowebcache.xsd"); ) {
            dom = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        String version = dom.getDocumentElement().getAttribute("version");
        if (null == version || version.trim().length() == 0) {
            throw new IllegalStateException("Schema doesn't define version");
        }
        return version.trim();
    }

    private static Node applyTransform(Node oldRootNode, String xslFilename) {
        DOMResult result = new DOMResult();
        Transformer transformer;

        InputStream is = XMLConfiguration.class.getResourceAsStream(xslFilename);

        try {
            transformer = TransformerFactory.newInstance().newTransformer(new StreamSource(is));
            transformer.transform(new DOMSource(oldRootNode), result);
        } catch (TransformerFactoryConfigurationError | TransformerException e) {
            log.debug(e);
        }

        return result.getNode();
    }

    public void afterPropertiesSet() throws GeoWebCacheException {

        if (gridSetBroker == null) {
            throw new IllegalStateException("GridSetBroker has not been set");
        }

        if (resourceProvider.hasInput()) {
            this.setGwcConfig(loadConfiguration());
        }

        log.info("Initializing GridSets from " + getIdentifier());

        getGridSetsInternal();

        log.info("Initializing layers from " + getIdentifier());

        // Loop over the layers and set appropriate values
        for (TileLayer layer : getGwcConfig().getLayers()) {
            if (layer == null) {
                throw new IllegalStateException(getIdentifier() + " contains a null layer");
            }
            initialize(layer);
        }

        updateLayers();
    }

    private void updateLayers() {
        Map<String, TileLayer> buff = new HashMap<String, TileLayer>();
        for (TileLayer layer : getGwcConfig().getLayers()) {
            buff.put(layer.getName(), layer);
        }
        this.layers = buff;
    }

    private void loadGridSets() {
        if (getGwcConfig().getGridSets() != null) {
            this.gridSets =
                    getGwcConfig()
                            .getGridSets()
                            .stream()
                            .map(
                                    (xmlGridSet) -> {
                                        if (log.isDebugEnabled()) {
                                            log.debug("Reading " + xmlGridSet.getName());
                                        }

                                        GridSet gridSet = xmlGridSet.makeGridSet();

                                        log.info("Read GridSet " + gridSet.getName());
                                        return gridSet;
                                    })
                            .collect(
                                    Collectors.toMap(
                                            GridSet::getName,
                                            Function.identity(),
                                            (GridSet x, GridSet y) -> {
                                                throw new IllegalStateException(
                                                        "Gridsets with duplicate name "
                                                                + x.getName());
                                            },
                                            HashMap::new));
        }
    }

    private void initialize(final TileLayer layer) {
        log.info("Initializing TileLayer '" + layer.getName() + "'");
        setDefaultValues(layer);
        layer.initialize(gridSetBroker);
    }

    /** @see TileLayerConfiguration#getIdentifier() */
    public String getIdentifier() {
        return resourceProvider.getId();
    }

    public void setRelativePath(String relPath) {
        log.error(
                "Specifying the relative path as a property is deprecated. "
                        + "Please pass it as the 4th argument to the constructor.");
    }

    public void setAbsolutePath(String absPath) {
        log.error(
                "Specifying the absolute path as a property is deprecated. "
                        + "Please pass it as the 4th argument to the constructor.");
    }

    /** @see TileLayerConfiguration#getLayers() */
    public Collection<TileLayer> getLayers() {
        return Collections.unmodifiableList(getGwcConfig().getLayers());
    }

    /** @see TileLayerConfiguration#getLayer(java.lang.String) */
    public Optional<TileLayer> getLayer(String layerName) {
        return Optional.ofNullable(layers.get(layerName));
    }

    /** @see TileLayerConfiguration#getTileLayer(java.lang.String) */
    @Deprecated
    public @Nullable TileLayer getTileLayer(String layerName) {
        return getLayer(layerName).orElse(null);
    }

    /** @see TileLayerConfiguration#getTileLayerById(String) */
    @Deprecated
    public @Nullable TileLayer getTileLayerById(String layerId) {
        // this configuration does not differentiate between identifier and identity yet
        return layers.get(layerId);
    }

    /** @see TileLayerConfiguration#containsLayer(java.lang.String) */
    public boolean containsLayer(String layerId) {
        return layers.containsKey(layerId);
    }

    /** @see TileLayerConfiguration#getLayerCount() */
    public int getLayerCount() {
        return layers.size();
    }

    /** @see TileLayerConfiguration#getLayerNames() */
    public Set<String> getLayerNames() {
        return Collections.unmodifiableSet(this.layers.keySet());
    }

    public String getVersion() {
        return getGwcConfig().getVersion();
    }

    /** @see ServerConfiguration#isFullWMS() */
    @Override
    public Boolean isFullWMS() {
        if (getGwcConfig() != null) {
            return getGwcConfig().getFullWMS();
        }
        return null;
    }

    /**
     * @see ServerConfiguration#setFullWMS(Boolean)
     * @param isFullWMS
     */
    @Override
    public void setFullWMS(Boolean isFullWMS) throws IOException {
        getGwcConfig().setFullWMS(isFullWMS);
        save();
    }

    /** @see BlobStoreConfiguration#getBlobStores() */
    @Override
    public List<BlobStoreInfo> getBlobStores() {
        // need to return an unmodifiable list of unmodifiable BlobStoreInfos
        return Collections.unmodifiableList(
                getGwcConfig()
                        .getBlobStores()
                        .stream()
                        .map(
                                (info) -> {
                                    return (BlobStoreInfo) info.clone();
                                })
                        .collect(Collectors.toList()));
    }

    /** @see BlobStoreConfiguration#addBlobStore(org.geowebcache.config.BlobStoreInfo) */
    @Override
    public synchronized void addBlobStore(BlobStoreInfo info) {
        if (info.getName() == null) {
            throw new IllegalArgumentException(
                    "Failed to add BlobStoreInfo. A BlobStoreInfo name cannot be null");
        }
        // ensure there isn't a BlobStoreInfo with the same name already
        if (getBlobStoreNames().contains(info.getName())) {
            throw new IllegalArgumentException(
                    String.format(
                            "Failed to add BlobStoreInfo. A BlobStoreInfo with name \"%s\" already exists",
                            info.getName()));
        }
        // add the BlobStoreInfo
        final List<BlobStoreInfo> blobStores = getGwcConfig().getBlobStores();
        blobStores.add(info);
        // try to save the config
        try {
            save();
        } catch (IOException ioe) {
            // save failed, roll back the add
            blobStores.remove(info);
            throw new ConfigurationPersistenceException(
                    String.format("Unable to add BlobStoreInfo \"%s\"", info), ioe);
        }
        try {
            blobStoreListeners.safeForEach(
                    listener -> {
                        listener.handleAddBlobStore(info);
                    });
        } catch (IOException | GeoWebCacheException e) {
            if (ExceptionUtils.isOrSuppresses(e, UnsuitableStorageException.class)) {
                // Can't store here, roll back
                blobStores.remove(info);
                throw new ConfigurationPersistenceException(
                        String.format("Unable to add BlobStoreInfo \"%s\"", info), e);
            }
            throw new ConfigurationPersistenceException(e);
        }
    }

    /** @see BlobStoreConfiguration#removeBlobStore(java.lang.String) */
    @Override
    public synchronized void removeBlobStore(String name) {
        // ensure there is a BlobStoreInfo with the name
        final BlobStoreInfo infoToRemove =
                getBlobStore(name)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                String.format(
                                                        "Failed to remove BlobStoreInfo. A BlobStoreInfo with name \"%s\" does not exist.",
                                                        name)));
        // remove the BlobStoreInfo
        final List<BlobStoreInfo> blobStores = getGwcConfig().getBlobStores();
        blobStores.remove(infoToRemove);
        // try to save
        try {
            save();
        } catch (IOException ioe) {
            // save failed, roll back the delete
            blobStores.add(infoToRemove);
            throw new ConfigurationPersistenceException(
                    String.format("Unable to remove BlobStoreInfo \"%s\"", name), ioe);
        }
        try {
            blobStoreListeners.safeForEach(
                    listener -> {
                        listener.handleRemoveBlobStore(infoToRemove);
                    });
        } catch (IOException | GeoWebCacheException e) {
            throw new ConfigurationPersistenceException(e);
        }
    }

    /** @see BlobStoreConfiguration#modifyBlobStore(org.geowebcache.config.BlobStoreInfo) */
    @Override
    public synchronized void modifyBlobStore(BlobStoreInfo info) {
        if (info.getName() == null) {
            throw new IllegalArgumentException("BlobStoreInfo name must not be null");
        }
        // ensure there is a BlobStoreInfo with the name
        final Optional<BlobStoreInfo> optionalInfo = getBlobStore(info.getName());
        if (!optionalInfo.isPresent()) {
            throw new NoSuchElementException(
                    String.format(
                            "Failed to modify BlobStoreInfo. A BlobStoreInfo with name \"%s\" does not exist.",
                            info.getName()));
        }
        // remove existing and add the new one
        final List<BlobStoreInfo> blobStores = getGwcConfig().getBlobStores();
        final BlobStoreInfo infoToRemove = optionalInfo.get();
        blobStores.remove(infoToRemove);
        blobStores.add(info);
        // try to save
        try {
            save();
        } catch (IOException ioe) {
            // save failed, roll back the modify
            blobStores.remove(info);
            blobStores.add(infoToRemove);
            throw new ConfigurationPersistenceException(
                    String.format("Unable to modify BlobStoreInfo \"%s\"", info.getName()), ioe);
        }
        try {
            blobStoreListeners.safeForEach(
                    listener -> {
                        listener.handleModifyBlobStore(info);
                    });
        } catch (IOException | GeoWebCacheException e) {
            if (ExceptionUtils.isOrSuppresses(e, UnsuitableStorageException.class)) {
                // Can't store here, roll back
                blobStores.remove(info);
                blobStores.add(infoToRemove);
                throw new ConfigurationPersistenceException(
                        String.format("Unable to modify BlobStoreInfo \"%s\"", info), e);
            }
            throw new ConfigurationPersistenceException(e);
        }
    }

    /** @see BlobStoreConfiguration#getBlobStoreCount() */
    @Override
    public int getBlobStoreCount() {
        return getGwcConfig().getBlobStores().size();
    }

    /** @see BlobStoreConfiguration#getBlobStoreNames() */
    @Override
    public Set<String> getBlobStoreNames() {
        return getGwcConfig()
                .getBlobStores()
                .stream()
                .map(
                        (info) -> {
                            return info.getName();
                        })
                .collect(Collectors.toSet());
    }

    /** @see BlobStoreConfiguration#getBlobStore(java.lang.String) */
    @Override
    public Optional<BlobStoreInfo> getBlobStore(String name) {
        for (BlobStoreInfo info : getGwcConfig().getBlobStores()) {
            if (info.getName().equals(name)) {
                return Optional.of((BlobStoreInfo) info.clone());
            }
        }
        return Optional.empty();
    }

    /** @see BlobStoreConfiguration#canSave(org.geowebcache.config.BlobStoreInfo) */
    @Override
    public boolean canSave(BlobStoreInfo info) {
        // if the resourceProvider has output, then it should be saveable. NOTE, this does not
        // guarantee that there are
        // sufficient write permissions to the underlying resource.
        return resourceProvider.hasOutput();
    }

    /** @see BlobStoreConfiguration#renameBlobStore(java.lang.String, java.lang.String) */
    @Override
    public void renameBlobStore(String oldName, String newName)
            throws NoSuchElementException, IllegalArgumentException {
        // if a BlobStoreInfo with newName already exists, throw IllegalArgumentException
        final Optional<BlobStoreInfo> newInfo = getBlobStore(newName);
        if (newInfo.isPresent()) {
            throw new IllegalArgumentException(
                    "BlobStoreInfo rename unsuccessful. A BlobStoreInfo with name \""
                            + newName
                            + "\" already exists.");
        }
        // get the list of BlobStoreInfos
        final List<BlobStoreInfo> blobStoreInfos = getGwcConfig().getBlobStores();
        // find the one to rename
        final BlobStoreInfo blobStoreInfoToRename;
        Iterator<BlobStoreInfo> infos = blobStoreInfos.iterator();
        {
            BlobStoreInfo foundInfo = null;
            while (infos.hasNext() && foundInfo == null) {
                final BlobStoreInfo info = infos.next();
                if (info.getName().equals(oldName)) {
                    // found the one to rename
                    // remove from the iterator
                    infos.remove();
                    foundInfo = info;
                }
            }
            blobStoreInfoToRename = foundInfo;
        }
        // if we didn't remove one, it wasn't in there to be removed
        if (blobStoreInfoToRename == null) {
            throw new NoSuchElementException(
                    "BlobStoreInfo rename unsuccessful. No BlobStoreInfo with name \""
                            + oldName
                            + "\" exists.");
        }
        // rename it and add it back to the list
        // for BlobStoreInfo instances, "name" and "id" are the same thing.
        blobStoreInfoToRename.setName(newName);
        blobStoreInfos.add(blobStoreInfoToRename);
        // persist the info
        try {
            save();

            if (log.isTraceEnabled()) {
                log.trace(
                        String.format(
                                "BlobStoreInfo rename from \"%s\" to \"%s\" successful.",
                                oldName, newName));
            }
        } catch (IOException ioe) {
            // save didn't work, need to roll things back
            infos = blobStoreInfos.iterator();
            BlobStoreInfo blobStoreInfoToRevert = null;
            while (infos.hasNext() && blobStoreInfoToRevert == null) {
                final BlobStoreInfo info = infos.next();
                if (info.getName().equals(newName)) {
                    // found the one to roll back
                    infos.remove();
                    blobStoreInfoToRevert = info;
                }
            }
            if (blobStoreInfoToRevert == null) {
                // we're really messed up now as we couldn't find the BlobStoreInfo that was just
                // renamed.
                throw new ConfigurationPersistenceException(
                        String.format(
                                "Error reverting BlobStoreInfo modification. Could not revert rename from \"%s\" to \"%s\"",
                                oldName, newName));
            }
            // revert the name and add it back to the list
            blobStoreInfoToRevert.setName(oldName);
            blobStoreInfos.add(blobStoreInfoToRevert);
            throw new ConfigurationPersistenceException(
                    String.format(
                            "Unable to rename BlobStoreInfo from \"%s\" to \"%s\"",
                            oldName, newName),
                    ioe);
        }
        try {
            blobStoreListeners.safeForEach(
                    listener -> {
                        listener.handleRenameBlobStore(oldName, blobStoreInfoToRename);
                    });
        } catch (IOException | GeoWebCacheException e) {
            throw new ConfigurationPersistenceException(
                    String.format(
                            "Exception while handling listeners for renaming blobstore \"%s\" to \"%s\"",
                            oldName, newName),
                    e);
        }
    }

    /** @see BlobStoreConfiguration#containsBlobStore(java.lang.String) */
    @Override
    public boolean containsBlobStore(String name) {
        if (name != null) {
            return getBlobStore(name).isPresent();
        }
        return false;
    }

    @Override
    public void addBlobStoreListener(BlobStoreConfigurationListener listener) {
        blobStoreListeners.add(listener);
    }

    @Override
    public void removeBlobStoreListener(BlobStoreConfigurationListener listener) {
        blobStoreListeners.remove(listener);
    }

    /** @see ServerConfiguration#getLockProvider() */
    @Override
    public LockProvider getLockProvider() {
        return getGwcConfig().getLockProvider();
    }

    /**
     * @see ServerConfiguration#setLockProvider(LockProvider)
     * @param lockProvider
     */
    @Override
    public void setLockProvider(LockProvider lockProvider) throws IOException {
        getGwcConfig().setLockProvider(lockProvider);
        save();
    }

    private GeoWebCacheConfiguration getGwcConfig() {
        try {
            if (gwcConfig == null) {
                synchronized (this) {
                    if (gwcConfig == null) {
                        gwcConfig = this.loadConfiguration();
                    }
                }
            }
        } catch (ConfigurationException e) {
            throw new IllegalStateException(
                    "Configuration "
                            + getIdentifier()
                            + " is not fully initialized and lazy initialization failed",
                    e);
        }
        return gwcConfig;
    }

    private void setGwcConfig(GeoWebCacheConfiguration gwcConfig) {
        this.gwcConfig = gwcConfig;
    }

    @Override
    public Boolean isWmtsCiteCompliant() {
        if (gwcConfig == null) {
            // if there is not configuration available we consider CITE strict compliance to be
            // deactivated
            return false;
        }
        // return whatever CITE compliance mode is defined
        return gwcConfig.isWmtsCiteCompliant();
    }

    /**
     * Can be used to force WMTS service implementation to be strictly compliant with the
     * correspondent CITE tests.
     *
     * @param wmtsCiteStrictCompliant TRUE or FALSE, activating or deactivation CITE strict
     *     compliance mode for WMTS
     */
    public void setWmtsCiteCompliant(Boolean wmtsCiteStrictCompliant) throws IOException {
        if (gwcConfig != null) {
            // activate or deactivate CITE strict compliance mode for WMTS implementation
            gwcConfig.setWmtsCiteCompliant(wmtsCiteStrictCompliant);
        }
        save();
    }

    /** @see ServerConfiguration#getBackendTimeout() */
    @Override
    public Integer getBackendTimeout() {
        return gwcConfig.getBackendTimeout();
    }

    /** @see ServerConfiguration#setBackendTimeout(Integer) */
    @Override
    public void setBackendTimeout(Integer backendTimeout) throws IOException {
        gwcConfig.setBackendTimeout(backendTimeout);
        save();
    }

    /** @see ServerConfiguration#isCacheBypassAllowed() */
    @Override
    public Boolean isCacheBypassAllowed() {
        return gwcConfig.getCacheBypassAllowed();
    }

    /** @see ServerConfiguration#setCacheBypassAllowed(Boolean) */
    @Override
    public void setCacheBypassAllowed(Boolean cacheBypassAllowed) throws IOException {
        gwcConfig.setCacheBypassAllowed(cacheBypassAllowed);
        save();
    }

    @Override
    public String getLocation() {
        try {
            return this.resourceProvider.getLocation();
        } catch (IOException e) {
            log.error("Could not get config location", e);
            return "Error, see log for details";
        }
    }

    @Override
    public synchronized void addGridSet(GridSet gridSet) {

        validateGridSet(gridSet);

        GridSet old = getGridSetsInternal().get(gridSet.getName());
        if (old != null) {
            throw new IllegalArgumentException("GridSet " + gridSet.getName() + " already exists");
        }

        assert getGwcConfig()
                .getGridSets()
                .stream()
                .noneMatch(xgs -> xgs.getName().equals(gridSet.getName()));

        try {
            saveGridSet(gridSet);
        } catch (IOException e) {
            throw new ConfigurationPersistenceException(e);
        }
        getGridSetsInternal().put(gridSet.getName(), gridSet);
    }

    private void validateGridSet(GridSet gridSet) {
        if (Objects.isNull(gridSet.getName())) {
            throw new IllegalArgumentException("GridSet name is not set");
        }
        if (gridSet.getNumLevels() == 0) {
            throw new IllegalArgumentException("GridSet has no levels");
        }
    }

    private void saveGridSet(GridSet gridSet) throws IOException {
        addOrReplaceGridSet(new XMLGridSet(gridSet));
        save();
    }

    @Override
    public synchronized void removeGridSet(String gridSetName) {
        GridSet gsRemoved = getGridSetsInternal().remove(gridSetName);
        XMLGridSet xgsRemoved = null;
        for (Iterator<XMLGridSet> it = getGwcConfig().getGridSets().iterator(); it.hasNext(); ) {
            XMLGridSet xgs = it.next();
            if (gridSetName.equals(xgs.getName())) {
                it.remove();
                xgsRemoved = xgs;
                break;
            }
        }

        assert Objects.isNull(gsRemoved) == Objects.isNull(xgsRemoved);

        if (Objects.isNull(gsRemoved)) {
            throw new NoSuchElementException(
                    "Could not remeove GridSet " + gridSetName + " as it does not exist");
        }

        try {
            save();
        } catch (IOException ex) {
            getGridSetsInternal().put(gridSetName, gsRemoved);
            getGwcConfig().getGridSets().add(xgsRemoved);
            throw new ConfigurationPersistenceException(
                    "Could not persist removal of Gridset " + gridSetName, ex);
        }
    }

    @Override
    public Optional<GridSet> getGridSet(String name) {
        return Optional.ofNullable(getGridSetsInternal().get(name)).map(GridSet::new);
    }

    protected Map<String, GridSet> getGridSetsInternal() {
        // Lazy init because we might have
        if (gridSets == null) {
            synchronized (this) {
                if (gridSets == null) {
                    loadGridSets();
                }
            }
        }
        return gridSets;
    }

    @Override
    public Collection<GridSet> getGridSets() {
        return getGridSetsInternal()
                .values()
                .stream()
                .map(GridSet::new)
                .collect(Collectors.toList());
    }

    @Override
    public synchronized void modifyGridSet(GridSet gridSet)
            throws NoSuchElementException, IllegalArgumentException, UnsupportedOperationException {
        validateGridSet(gridSet);

        GridSet old = getGridSetsInternal().get(gridSet.getName());
        if (old == null) {
            throw new NoSuchElementException("GridSet " + gridSet.getName() + " does not exist");
        }

        assert getGwcConfig()
                .getGridSets()
                .stream()
                .anyMatch(xgs -> xgs.getName().equals(gridSet.getName()));

        try {
            saveGridSet(gridSet);
        } catch (IOException e) {
            throw new ConfigurationPersistenceException(e);
        }
        this.getGridSetsInternal().put(gridSet.getName(), gridSet);
    }

    @Override
    public void renameGridSet(String oldName, String newName)
            throws NoSuchElementException, IllegalArgumentException, UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean canSave(GridSet gridset) {
        // TODO Exceptions are expensive so do something else.
        try {
            validateGridSet(gridset);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    @Autowired
    @Override
    public void setGridSetBroker(@Qualifier("gwcGridSetBroker") GridSetBroker broker) {
        this.gridSetBroker = broker;
    }

    @Override
    public void deinitialize() throws Exception {
        this.gridSets = null;
        this.layers = null;
        this.gwcConfig = null;
    }
}
