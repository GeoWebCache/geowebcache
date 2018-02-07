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
 * @author Arne Kepp, Marius Suta,  The Open Planning Project, Copyright 2008 - 2015
 */
package org.geowebcache.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import org.geowebcache.filter.parameters.CaseNormalizer;
import org.geowebcache.filter.parameters.FloatParameterFilter;
import org.geowebcache.filter.parameters.IntegerParameterFilter;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.filter.parameters.RegexParameterFilter;
import org.geowebcache.filter.parameters.StringParameterFilter;
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
import org.geowebcache.seed.TruncateLayerRequest;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.util.ApplicationContextProvider;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import org.springframework.web.context.WebApplicationContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomReader;

/**
 * XMLConfiguration class responsible for reading/writing layer configurations to and from XML file
 * <p>
 * NOTE {@link #initialize(GridSetBroker)} MUST have been called before any other method is used,
 * otherwise this configuration is in an inconsistent and unpredictable state.
 * </p>
 */
public class XMLConfiguration implements TileLayerConfiguration, InitializingBean, DefaultingConfiguration, ServerConfiguration, BlobStoreConfigurationCatalog, GridSetConfiguration {
    
    public static final String DEFAULT_CONFIGURATION_FILE_NAME = "geowebcache.xml";

    private static Log log = LogFactory.getLog(org.geowebcache.config.XMLConfiguration.class);

    /**
     * Web app context, used to look up {@link XMLConfigurationProvider}s. Will be null if used the
     * {@link #XMLConfiguration(InputStream)} constructor
     */
    private final WebApplicationContext context;
    
    private final ConfigurationResourceProvider resourceProvider;

    private GeoWebCacheConfiguration gwcConfig;

    private transient Map<String, TileLayer> layers;
    
    private transient Map<String, GridSet> gridSets;

    private GridSetBroker gridSetBroker;

    /**
     * A flag for whether the config needs to be loaded at {@link #initialize(GridSetBroker)}. If
     * the constructor loads the configuration, will set it to false, then each call to initialize()
     * will reset this flag to true
     */
    private boolean reloadConfigOnInit = true;

    /**
     * Base Constructor with custom ConfiguratioNResourceProvider
     *  
     * @param appCtx use to lookup {@link XMLConfigurationProvider} extensions, may be {@code null}
     * @param inFac
     */
    public XMLConfiguration(final ApplicationContextProvider appCtx,
            final ConfigurationResourceProvider inFac) {
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
    public XMLConfiguration(final ApplicationContextProvider appCtx,
            final String configFileDirectory,
            final DefaultStorageFinder storageDirFinder) throws ConfigurationException {
        this(appCtx, new XMLFileResourceProvider(DEFAULT_CONFIGURATION_FILE_NAME,
                appCtx, configFileDirectory, storageDirFinder));
        resourceProvider.setTemplate("/" + DEFAULT_CONFIGURATION_FILE_NAME);
    }
    

    /**
     * Constructor that will look for {@code geowebcache.xml} at the directory defined by
     * {@code storageDirFinder}
     * 
     * @param appCtx
     *            use to lookup {@link XMLConfigurationProvider} extenions, may be {@code null}
     * @param storageDirFinder
     * @throws ConfigurationException
     */
    public XMLConfiguration(final ApplicationContextProvider appCtx,
            final DefaultStorageFinder storageDirFinder) throws ConfigurationException {
        this(appCtx, new XMLFileResourceProvider(DEFAULT_CONFIGURATION_FILE_NAME,
                appCtx, storageDirFinder));
        resourceProvider.setTemplate("/" + DEFAULT_CONFIGURATION_FILE_NAME);
    }

    /**
     * Constructor that will accept an absolute or relative path for finding {@code geowebcache.xml}
     * 
     * @param appCtx
     * @param configFileDirectory
     * @throws ConfigurationException
     */
    public XMLConfiguration(final ApplicationContextProvider appCtx,
            final String configFileDirectory) throws ConfigurationException {
        this(appCtx, configFileDirectory, null);
    }

    
    /**
     * @deprecated use {@link #XMLConfiguration(ApplicationContextProvider, DefaultStorageFinder)}
     */
    @Deprecated
    public XMLConfiguration(final ApplicationContextProvider appCtx,
            final GridSetBroker gridSetBroker, final DefaultStorageFinder storageDirFinder)
            throws ConfigurationException {
        this(appCtx, storageDirFinder);
        log.warn("This constructor is deprecated");
    }

    /**
     * @deprecated use {@link #XMLConfiguration(ApplicationContextProvider, String)}
     */
    @Deprecated
    public XMLConfiguration(final ApplicationContextProvider appCtx,
            final GridSetBroker gridSetBroker, final String configFileDirectory)
            throws ConfigurationException {

        this(appCtx, configFileDirectory);
        log.warn("This constructor is deprecated");
    }
    
    @Override
    public void afterPropertiesSet() throws Exception {
        if (resourceProvider.hasInput()) {
            this.setGwcConfig(loadConfiguration());
        }
        this.reloadConfigOnInit = false;
    }
    
    /**
     * Constructor with inputstream (only for testing)
     * @throws ConfigurationException 
     */
    public XMLConfiguration(final InputStream is) throws ConfigurationException {
        this (null, new ConfigurationResourceProvider() {
                        
            @Override
            public InputStream in() {
                throw new UnsupportedOperationException();
            }

            @Override
            public OutputStream out() throws IOException {
                throw new UnsupportedOperationException();
            }       
            
            @Override
            public void backup() throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public void setTemplate(String template) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getLocation() throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getId() {
                return "mockConfig";
            }

            @Override
            public boolean hasInput() {
                return false;
            }

            @Override
            public boolean hasOutput() {
                return false;
            }
            
        });
        try {
            setGwcConfig(loadConfiguration(is));
        } catch (IOException e) {
            throw new ConfigurationException(e.getMessage(), e);
        }
    }
    
    /**
     * Path to template to use when there is no config file.
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

    /**
     * @see ServerConfiguration#isRuntimeStatsEnabled()
     */
    public boolean isRuntimeStatsEnabled() {
        if (getGwcConfig() == null || getGwcConfig().getRuntimeStats() == null) {
            return true;
        } else {
            return getGwcConfig().getRuntimeStats();
        }
    }

    /**
     * @see ServerConfiguration#setIsRuntimeStatsEnabled(boolean)
     * @param isEnabled
     */
    public void setIsRuntimeStatsEnabled(boolean isEnabled) throws IOException {
        getGwcConfig().setRuntimeStats(isEnabled);
        save();
    }

    /**
     * @see ServerConfiguration#getServiceInformation()
     */
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
                log.error("could not parse proxy URL " + wl.getProxyUrl()
                        + " ! continuing WITHOUT proxy!", e);
            }

            final WMSHttpHelper sourceHelper;

            if (wl.getHttpUsername() != null) {
                sourceHelper = new WMSHttpHelper(wl.getHttpUsername(), wl.getHttpPassword(),
                        proxyUrl);
                log.debug("Using per-layer HTTP credentials for " + wl.getName() + ", "
                        + "username " + wl.getHttpUsername());
            } else if (getGwcConfig().getHttpUsername() != null) {
                sourceHelper = new WMSHttpHelper(getGwcConfig().getHttpUsername(),
                        getGwcConfig().getHttpPassword(), proxyUrl);
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
        Assert.isTrue(resourceProvider.hasInput());
        InputStream in;
        try {
            in = resourceProvider.in();
            try {
                return loadConfiguration(in);
            } finally {
                in.close();
            }
        } catch (IOException e) {
            throw new ConfigurationException("Error parsing config file "
                    + resourceProvider.getId(), e);
        }
    }

    private GeoWebCacheConfiguration loadConfiguration(InputStream xmlFile) throws IOException,
            ConfigurationException {
        Node rootNode = loadDocument(xmlFile);
        XStream xs = getConfiguredXStreamWithContext(new GeoWebCacheXStream(), Context.PERSIST);

        GeoWebCacheConfiguration config;
        config = (GeoWebCacheConfiguration) xs.unmarshal(new DomReader((Element) rootNode));
        return config;
    }

    //TODO - convert to private method
    /**
     * @see TileLayerConfiguration#save()
     */
    public synchronized void save() throws IOException {
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
        return getConfiguredXStreamWithContext(xs, this.context, (ContextualConfigurationProvider.Context)null);
    }
    public static XStream getConfiguredXStream(XStream xs, WebApplicationContext context) {
        return getConfiguredXStreamWithContext(xs, context, (ContextualConfigurationProvider.Context)null);
    }
    public XStream getConfiguredXStreamWithContext(XStream xs, 
            ContextualConfigurationProvider.Context providerContext) {
        return getConfiguredXStreamWithContext(xs, this.context, providerContext);
    }
    
    public static XStream getConfiguredXStreamWithContext(XStream xs, WebApplicationContext context, 
            ContextualConfigurationProvider.Context providerContext) {
        
        {
            // Allow any implementation of these extension points
            xs.allowTypeHierarchy(org.geowebcache.layer.TileLayer.class);
            xs.allowTypeHierarchy(org.geowebcache.filter.parameters.ParameterFilter.class);
            xs.allowTypeHierarchy(org.geowebcache.filter.request.RequestFilter.class);
            xs.allowTypeHierarchy(org.geowebcache.config.BlobStoreConfig.class);
            xs.allowTypeHierarchy(TileLayerConfiguration.class);
            
            // Allow anything that's part of GWC
            // TODO: replace this with a more narrow whitelist
            xs.allowTypesByWildcard(new String[]{"org.geowebcache.**"});
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

        xs.alias("blobStores", new ArrayList<BlobStoreConfig>().getClass());
        xs.alias("FileBlobStore", FileBlobStoreConfig.class);
        xs.aliasAttribute(BlobStoreConfig.class, "_default", "default");

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

        if (context != null) {
            /*
             * Look up XMLConfigurationProvider extension points and let them contribute to the
             * configuration
             */
            List<XMLConfigurationProvider> configExtensions = GeoWebCacheExtensions.extensions(
                    XMLConfigurationProvider.class, context);
            for (XMLConfigurationProvider extension : configExtensions) {
                // Check if the provider is context dependent
                if(extension instanceof ContextualConfigurationProvider &&
                        // Check if the context is applicable for the provider
                        (providerContext==null ||
                        !((ContextualConfigurationProvider)extension).appliesTo(providerContext))) {
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
     * throws an exception if it does not succeed
     */
    private void persistToFile() throws IOException {
        Assert.isTrue(resourceProvider.hasOutput());
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
            throw (IOException) new IOException("Error writing to " + resourceProvider.getId()
                    + ": " + e.getMessage()).initCause(e);
        }

        log.info("Wrote configuration to " + resourceProvider.getId());
    }

    /**
     * @return {@code true} only if {@code tl instanceof WMSLayer}
     * @see TileLayerConfiguration#canSave(org.geowebcache.layer.TileLayer)
     */
    public boolean canSave(TileLayer tl) {
        return tl instanceof WMSLayer && !tl.isTransientLayer();
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
        if (!(tl instanceof WMSLayer)) {
            throw new IllegalArgumentException("Can't add layers of type "
                    + tl.getClass().getName());
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
            //If save fails, try to revert the change to maintain a consistent state.
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
        if (!(tl instanceof WMSLayer)) {
            throw new IllegalArgumentException("Can't add layers of type "
                    + tl.getClass().getName());
        }
        getGwcConfig().getLayers().remove(previous);
        initialize(tl);
        getGwcConfig().getLayers().add(tl);
        updateLayers();
        try {
            save();
        } catch (IOException e) {
            //If save fails, try to revert the change to maintain a consistent state.
            getGwcConfig().getLayers().remove(tl);
            initialize(previous);
            getGwcConfig().getLayers().add(previous);
            updateLayers();
            throw new IllegalArgumentException("Unable to modify layer " + tl.getName(), e);
        }
    }

    protected TileLayer findLayer(String layerName) throws NoSuchElementException {
        TileLayer layer = getLayer(layerName).orElseThrow(
                ()-> new NoSuchElementException("Layer " + layerName + " does not exist"));
        return layer;
    }

    /**
     * @see TileLayerConfiguration#renameLayer(String, String)
     */
    public void renameLayer(String oldName, String newName) throws NoSuchElementException, IllegalArgumentException {
        throw new UnsupportedOperationException("renameLayer is not supported by "
                + getClass().getSimpleName());
    }

    /**
     * @return {@code true} if the layer was removed, {@code false} if no such layer exists
     * @see TileLayerConfiguration#removeLayer(java.lang.String)
     */
    public synchronized void removeLayer(final String layerName) throws NoSuchElementException, IllegalArgumentException {
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
            //If save fails, try to revert the removal to maintain a consistent state.
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
        
        xmlGridSets.removeIf(xgs->gridsetName.equals(xgs.getName()));
        
        xmlGridSets.add(gridSet);
    }

    /**
     * Removes and returns the gridset configuration named {@code gridsetName}.
     * 
     * @param gridsetName
     *            the name of the gridset to remove
     * @return the removed griset, or {@code null} if no such gridset exists
     * @deprecated use removeGridSet
     */
    @Deprecated
    public synchronized XMLGridSet removeGridset(final String gridsetName) {
        return getGridSet(gridsetName)
            .map(g-> {removeGridSet(gridsetName); return g;})
            .map(XMLGridSet::new)
            .orElse(null);
    }

    /**
     * Method responsible for loading xml configuration file and parsing it into a W3C DOM Document
     * 
     * @param xmlFile
     *            the file contaning the layer configurations
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
                log.warn("*** Will try to use configuration anyway. Please check the order of declared elements against the schema.");
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
        InputStream is = XMLConfiguration.class.getResourceAsStream("geowebcache.xsd");
        Document dom;
        try {
            dom = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
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

    /**
     * @see TileLayerConfiguration#initialize(org.geowebcache.grid.GridSetBroker)
     */
    public int initialize(final GridSetBroker gridSetBroker) throws GeoWebCacheException {

        this.gridSetBroker = gridSetBroker;

        if (this.reloadConfigOnInit && resourceProvider.hasInput()) {
            this.setGwcConfig(loadConfiguration());
        }

        log.info("Initializing GridSets from " + getIdentifier());

        loadGridSets(gridSetBroker);

        log.info("Initializing layers from " + getIdentifier());

        // Loop over the layers and set appropriate values
        for (TileLayer layer : getGwcConfig().getLayers()) {
            if (layer == null) {
                throw new IllegalStateException(getIdentifier() + " contains a null layer");
            }
            initialize(layer);
        }

        updateLayers();
        
        this.reloadConfigOnInit = true;

        return getLayerCount();
    }

    private void updateLayers() {
        Map<String, TileLayer> buff = new HashMap<String, TileLayer>();
        for (TileLayer layer : getGwcConfig().getLayers()) {
            buff.put(layer.getName(), layer);
        }
        this.layers = buff;
    }

    private void contributeGridSets(final GridSetBroker gridSetBroker) {
        if (getGwcConfig().getGridSets() != null) {
            Iterator<XMLGridSet> iter = getGwcConfig().getGridSets().iterator();
            while (iter.hasNext()) {
                XMLGridSet xmlGridSet = iter.next();

                if (log.isDebugEnabled()) {
                    log.debug("Reading " + xmlGridSet.getName());
                }

                GridSet gridSet = xmlGridSet.makeGridSet();

                log.info("Read GridSet " + gridSet.getName());

                gridSetBroker.put(gridSet);
            }
        }
    }
    private void loadGridSets(final GridSetBroker gridSetBroker) {
        if (getGwcConfig().getGridSets() != null) {
            this.gridSets = getGwcConfig().getGridSets().stream()
                .map((xmlGridSet)->{
                        
                        if (log.isDebugEnabled()) {
                            log.debug("Reading " + xmlGridSet.getName());
                        }
                        
                        GridSet gridSet = xmlGridSet.makeGridSet();
                        
                        log.info("Read GridSet " + gridSet.getName());
                        return gridSet;
                    })
                .collect(Collectors.toMap(GridSet::getName, Function.identity(), 
                        (GridSet x,GridSet y)->{throw new IllegalStateException("Gridsets with duplicate name "+x.getName());},
                        HashMap::new));
        }
    }

    private void initialize(final TileLayer layer) {
        log.info("Initializing TileLayer '" + layer.getName() + "'");
        setDefaultValues(layer);
        layer.initialize(gridSetBroker);
    }

    /**
     * @see TileLayerConfiguration#getIdentifier()
     */
    public String getIdentifier() {
        return resourceProvider.getId();
    }

    public void setRelativePath(String relPath) {
        log.error("Specifying the relative path as a property is deprecated. "
                + "Please pass it as the 4th argument to the constructor.");
    }

    public void setAbsolutePath(String absPath) {
        log.error("Specifying the absolute path as a property is deprecated. "
                + "Please pass it as the 4th argument to the constructor.");
    }

    /**
     * @see TileLayerConfiguration#getTileLayers()
     */
    @Deprecated
    public List<TileLayer> getTileLayers() {
        return Collections.unmodifiableList(getGwcConfig().getLayers());
    }

    /**
     * @see TileLayerConfiguration#getLayers()
     */
    public Collection<TileLayer> getLayers() {
        return Collections.unmodifiableList(getGwcConfig().getLayers());
    }

    /**
     * @see TileLayerConfiguration#getLayer(java.lang.String)
     */
    public Optional<TileLayer> getLayer(String layerName) {
        return Optional.ofNullable(layers.get(layerName));
    }

    /**
     * @see TileLayerConfiguration#getTileLayer(java.lang.String)
     */
    @Deprecated
    public @Nullable TileLayer getTileLayer(String layerName) {
        return getLayer(layerName).orElse(null);
    }

    /**
     * @see TileLayerConfiguration#getTileLayerById(String)
     */
    @Deprecated
    public @Nullable TileLayer getTileLayerById(String layerId) {
        // this configuration does not differentiate between identifier and identity yet
        return layers.get(layerId);
    }

    /**
     * @see TileLayerConfiguration#containsLayer(java.lang.String)
     */
    public boolean containsLayer(String layerId) {
        return layers.containsKey(layerId);
    }

    /**
     * @see TileLayerConfiguration#getLayerCount()
     */
    public int getLayerCount() {
        return layers.size();
    }

    /**
     * @see TileLayerConfiguration#getTileLayerCount()
     */
    @Deprecated
    public int getTileLayerCount() {
        return getLayerCount();
    }

    /**
     * @see TileLayerConfiguration#getLayerNames()
     */
    public Set<String> getLayerNames() {
        return Collections.unmodifiableSet(this.layers.keySet());
    }

    /**
     * @see TileLayerConfiguration#getTileLayerNames()
     */
    @Deprecated
    public Set<String> getTileLayerNames() {
        return getLayerNames();
    }

    public String getVersion() {
        return getGwcConfig().getVersion();
    }

    /**
     * @see ServerConfiguration#getfullWMS()
     */
    @Override
    public Boolean getfullWMS(){
        if(getGwcConfig()!=null){
            return getGwcConfig().getFullWMS();
        }
        return null;        
    }

    /**
     * @see ServerConfiguration#setFullWMS(boolean)
     * @param isFullWMS
     */
    @Override
    public void setFullWMS(boolean isFullWMS) throws IOException {
        getGwcConfig().setFullWMS(isFullWMS);
        save();
    }

    @Override
    public List<BlobStoreConfig> getBlobStores() {
        return getGwcConfig().getBlobStores();
    }

    /**
     * @see ServerConfiguration#getLockProvider()
     */
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
        return gwcConfig;
    }

    private void setGwcConfig(GeoWebCacheConfiguration gwcConfig) {
        this.gwcConfig = gwcConfig;
    }

    @Override
    public boolean isWmtsCiteCompliant() {
        if (gwcConfig == null) {
            // if there is not configuration available we consider CITE strict compliance to be deactivated
            return false;
        }
        // return whatever CITE compliance mode is defined
        return gwcConfig.isWmtsCiteCompliant();
    }

    /**
     * Can be used to force WMTS service implementation to be strictly compliant with the
     * correspondent CITE tests.
     *
     * @param wmtsCiteStrictCompliant TRUE or FALSE, activating or deactivation CITE
     *                                strict compliance mode for WMTS
     */
    public void setWmtsCiteStrictCompliant(boolean wmtsCiteStrictCompliant) throws IOException {
        if (gwcConfig != null) {
            // activate or deactivate CITE strict compliance mode for WMTS implementation
            gwcConfig.setWmtsCiteCompliant(wmtsCiteStrictCompliant);
        }
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
    public synchronized void addGridSet(GridSet gridSet)  {
        
        validateGridSet(gridSet);
        
        GridSet old = gridSets.get(gridSet.getName());
        if(old!=null) {
            throw new IllegalArgumentException("GridSet " + gridSet.getName() + " already exists");
        }
        
        assert getGwcConfig().getGridSets().stream().noneMatch(xgs->xgs.getName().equals(gridSet.getName()));
        
        try {
            saveGridSet(gridSet);
        } catch (IOException e) {
            throw new ConfigurationPersistenceException(e);
        }
        this.gridSets.put(gridSet.getName(), gridSet);
    }

    private void validateGridSet(GridSet gridSet) {
        if(Objects.isNull(gridSet.getName())) {
            throw new IllegalArgumentException("GridSet name is not set");
        }
        if(Objects.isNull(gridSet.getGridLevels())) {
            throw new IllegalArgumentException("GridSet has no levels");
        }
    }

    private void saveGridSet(GridSet gridSet) throws IOException {
        addOrReplaceGridSet(new XMLGridSet(gridSet));
        save();
    }

    @Override
    public synchronized void removeGridSet(String gridSetName) {
        GridSet gsRemoved = gridSets.remove(gridSetName);
        XMLGridSet xgsRemoved = null;
        for(Iterator<XMLGridSet> it = getGwcConfig().getGridSets().iterator(); it.hasNext();) {
            XMLGridSet xgs = it.next();
            if(gridSetName.equals(xgs.getName())) {
                it.remove();
                xgsRemoved=xgs;
                break;
            }
        }
        
        assert Objects.isNull(gsRemoved)==Objects.isNull(xgsRemoved);
        
        if (Objects.isNull(gsRemoved)) {
            throw new NoSuchElementException("Could not remeove GridSet "+gridSetName+" as it does not exist");
        }
        
        try {
            save();
        } catch (IOException ex) {
            gridSets.put(gridSetName,  gsRemoved);
            getGwcConfig().getGridSets().add(xgsRemoved);
            throw new ConfigurationPersistenceException("Could not persist removal of Gridset "+gridSetName,ex);
        }
    }

    @Override
    public Optional<GridSet> getGridSet(String name) {
        return Optional.ofNullable(gridSets.get(name))
                .map(GridSet::new);
    }

    @Override
    public Collection<GridSet> getGridSets() {
        return gridSets.values().stream()
                .map(GridSet::new)
                .collect(Collectors.toList());
    }

    @Override
    public void modifyGridSet(GridSet gridSet)
            throws NoSuchElementException, IllegalArgumentException, UnsupportedOperationException {
        validateGridSet(gridSet);
        
        GridSet old = gridSets.get(gridSet.getName());
        if(old==null) {
            throw new NoSuchElementException("GridSet " + gridSet.getName() + " already exists");
        }
        
        assert getGwcConfig().getGridSets().stream().anyMatch(xgs->xgs.getName().equals(gridSet.getName()));
        
        try {
            saveGridSet(gridSet);
        } catch (IOException e) {
            throw new ConfigurationPersistenceException(e);
        }
        this.gridSets.put(gridSet.getName(), gridSet);
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
}
