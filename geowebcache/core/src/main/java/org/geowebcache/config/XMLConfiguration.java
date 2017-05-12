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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
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
public class XMLConfiguration implements Configuration, InitializingBean {
    
    public static final String DEFAULT_CONFIGURATION_FILE_NAME = "geowebcache.xml";

    private static Log log = LogFactory.getLog(org.geowebcache.config.XMLConfiguration.class);

    /**
     * Web app context, used to look up {@link XMLConfigurationProvider}s. Will be null if used the
     * {@link #XMLConfiguration(File)} constructor
     */
    private final WebApplicationContext context;
    
    private final ConfigurationResourceProvider resourceProvider;

    private GeoWebCacheConfiguration gwcConfig;

    private transient Map<String, TileLayer> layers;

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
     * @param defaultStorage
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
     * @deprecated use {@link #XMLFileConfiguration(ApplicationContextProvider, DefaultStorageFinder)}
     */
    @Deprecated
    public XMLConfiguration(final ApplicationContextProvider appCtx,
            final GridSetBroker gridSetBroker, final DefaultStorageFinder storageDirFinder)
            throws ConfigurationException {
        this(appCtx, storageDirFinder);
        log.warn("This constructor is deprecated");
    }

    /**
     * @deprecated use {@link #XMLFileConfiguration(ApplicationContextProvider, String)}
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
            this.gwcConfig = loadConfiguration();
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
            gwcConfig = loadConfiguration(is);
        } catch (IOException e) {
            throw new ConfigurationException(e.getMessage(), e);
        }
    }
    
    public void setTemplate(String template) {
       resourceProvider.setTemplate(template);
    }

    public String getConfigLocation() throws ConfigurationException {
        try {
            return resourceProvider.getLocation();
        } catch (IOException e) {
            throw new ConfigurationException(e.getMessage(), e);
        }
    }

    public boolean isRuntimeStatsEnabled() {
        if (gwcConfig == null || gwcConfig.getRuntimeStats() == null) {
            return true;
        } else {
            return gwcConfig.getRuntimeStats();
        }
    }

    public synchronized ServiceInformation getServiceInformation() {
        return gwcConfig.getServiceInformation();
    }

    /**
     * Configuration objects lacking their own defaults can delegate to this
     * @param layer
     */
    public void setDefaultValues(TileLayer layer) {
        // Additional values that can have defaults set
        if (layer.isCacheBypassAllowed() == null) {
            if (gwcConfig.getCacheBypassAllowed() != null) {
                layer.setCacheBypassAllowed(gwcConfig.getCacheBypassAllowed());
            } else {
                layer.setCacheBypassAllowed(false);
            }
        }

        if (layer.getBackendTimeout() == null) {
            if (gwcConfig.getBackendTimeout() != null) {
                layer.setBackendTimeout(gwcConfig.getBackendTimeout());
            } else {
                layer.setBackendTimeout(120);
            }
        }

        if (layer.getFormatModifiers() == null) {
            if (gwcConfig.getFormatModifiers() != null) {
                layer.setFormatModifiers(gwcConfig.getFormatModifiers());
            }
        }

        if (layer instanceof WMSLayer) {
            WMSLayer wl = (WMSLayer) layer;

            URL proxyUrl = null;
            try {
                if (gwcConfig.getProxyUrl() != null) {
                    proxyUrl = new URL(gwcConfig.getProxyUrl());
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
            } else if (gwcConfig.getHttpUsername() != null) {
                sourceHelper = new WMSHttpHelper(gwcConfig.getHttpUsername(),
                        gwcConfig.getHttpPassword(), proxyUrl);
                log.debug("Using global HTTP credentials for " + wl.getName());
            } else {
                sourceHelper = new WMSHttpHelper(null, null, proxyUrl);
                log.debug("Not using HTTP credentials for " + wl.getName());
            }

            wl.setSourceHelper(sourceHelper);
            wl.setLockProvider(gwcConfig.getLockProvider());
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

    /**
     * @see org.geowebcache.config.Configuration#save()
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
            xs.allowTypeHierarchy(org.geowebcache.config.Configuration.class);
            
            // Allow anything that's part of GWC
            // TODO: replace this with a more narrow whitelist
            xs.allowTypesByWildcard(new String[]{"org.geowebcache.**"});
        }
        
        xs.setMode(XStream.ID_REFERENCES);

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
            gwcConfig.setVersion(currentSchemaVersion);

            writer.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
            xs.toXML(gwcConfig, writer);
        } catch (UnsupportedEncodingException uee) {
            uee.printStackTrace();
            throw new IOException(uee.getMessage());
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
     * @see org.geowebcache.config.Configuration#canSave(org.geowebcache.layer.TileLayer)
     */
    public boolean canSave(TileLayer tl) {
        return tl instanceof WMSLayer && !tl.isTransientLayer();
    }

    /**
     * @param tl
     *            the layer to add to this configuration
     * @return
     * @throws IllegalArgumentException
     *             if a layer named the same than {@code tl} already exists
     * @see org.geowebcache.config.Configuration#addLayer(org.geowebcache.layer.TileLayer)
     */
    public synchronized void addLayer(TileLayer tl) throws IllegalArgumentException {
        if (tl == null) {
            throw new NullPointerException();
        }
        if (!(tl instanceof WMSLayer)) {
            throw new IllegalArgumentException("Can't add layers of type "
                    + tl.getClass().getName());
        }
        if (null != getTileLayer(tl.getName())) {
            throw new IllegalArgumentException("Layer '" + tl.getName() + "' already exists");
        }

        initialize(tl);
        gwcConfig.getLayers().add(tl);
        updateLayers();
    }

    /**
     * Method responsible for modifying an existing layer.
     * 
     * @param tl
     *            the new layer to overwrite the existing layer
     * @throws NoSuchElementException
     * @see org.geowebcache.config.Configuration#modifyLayer(org.geowebcache.layer.TileLayer)
     */
    public synchronized void modifyLayer(TileLayer tl) throws NoSuchElementException {
        TileLayer previous = getTileLayer(tl.getName());
        if (null == previous) {
            throw new NoSuchElementException("Layer " + tl.getName() + " does not exist");
        }

        gwcConfig.getLayers().remove(previous);
        initialize(tl);
        gwcConfig.getLayers().add(tl);
        updateLayers();
    }

    /**
     * @return {@code true} if the layer was removed, {@code false} if no such layer exists
     * @see org.geowebcache.config.Configuration#removeLayer(java.lang.String)
     */
    public synchronized boolean removeLayer(final String layerName) {
        final TileLayer tileLayer = getTileLayer(layerName);
        if (tileLayer == null) {
            return false;
        }

        boolean removed = false;
        removed = gwcConfig.getLayers().remove(tileLayer);
        if (removed) {
            updateLayers();
            
        }
        return removed;
    }

    /**
     * @param gridSet
     * @throws GeoWebCacheException
     */
    public synchronized void addOrReplaceGridSet(final XMLGridSet gridSet)
            throws IllegalArgumentException {
        final String gridsetName = gridSet.getName();

        List<XMLGridSet> gridSets = gwcConfig.getGridSets();

        for (Iterator<XMLGridSet> it = gridSets.iterator(); it.hasNext();) {
            XMLGridSet gset = it.next();
            if (gridsetName.equals(gset.getName())) {
                it.remove();
            }
        }
        gridSets.add(gridSet);
    }

    /**
     * Removes and returns the gridset configuration named {@code gridsetName}.
     * 
     * @param gridsetName
     *            the name of the gridset to remove
     * @return the removed griset, or {@code null} if no such gridset exists
     */
    public synchronized XMLGridSet removeGridset(final String gridsetName) {
        List<XMLGridSet> gridSets = gwcConfig.getGridSets();
        for (Iterator<XMLGridSet> it = gridSets.iterator(); it.hasNext();) {
            XMLGridSet gset = it.next();
            if (gridsetName.equals(gset.getName())) {
                it.remove();
                return gset;
            }
        }
        return null;
    }

    /**
     * Method responsible for loading xml configuration file and parsing it into a W3C DOM Document
     * 
     * @param file
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
                log.info("Configuration file validated fine.");
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
        } catch (TransformerConfigurationException e) {
            e.printStackTrace();
        } catch (TransformerFactoryConfigurationError e) {
            e.printStackTrace();
        } catch (TransformerException e) {
            e.printStackTrace();
        }

        return result.getNode();
    }

    /**
     * @see org.geowebcache.config.Configuration#initialize(org.geowebcache.grid.GridSetBroker)
     */
    public int initialize(final GridSetBroker gridSetBroker) throws GeoWebCacheException {

        this.gridSetBroker = gridSetBroker;

        if (this.reloadConfigOnInit && resourceProvider.hasInput()) {
            this.gwcConfig = loadConfiguration();
        }

        log.info("Initializing GridSets from " + getIdentifier());

        contributeGridSets(gridSetBroker);

        log.info("Initializing layers from " + getIdentifier());

        // Loop over the layers and set appropriate values
        for (TileLayer layer : gwcConfig.getLayers()) {
            if (layer == null) {
                throw new IllegalStateException(getIdentifier() + " contains a null layer");
            }
            initialize(layer);
        }

        updateLayers();
        
        this.reloadConfigOnInit = true;

        return getTileLayerCount();
    }

    private void updateLayers() {
        Map<String, TileLayer> buff = new HashMap<String, TileLayer>();
        for (TileLayer layer : gwcConfig.getLayers()) {
            buff.put(layer.getName(), layer);
        }
        this.layers = buff;
    }

    private void contributeGridSets(final GridSetBroker gridSetBroker) {
        if (gwcConfig.getGridSets() != null) {
            Iterator<XMLGridSet> iter = gwcConfig.getGridSets().iterator();
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

    private void initialize(final TileLayer layer) {
        log.info("Initializing TileLayer '" + layer.getName() + "'");
        setDefaultValues(layer);
        layer.initialize(gridSetBroker);
    }

    /**
     * @see org.geowebcache.config.Configuration#getIdentifier()
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
     * @see org.geowebcache.config.Configuration#getTileLayers()
     */
    public List<TileLayer> getTileLayers() {
        return Collections.unmodifiableList(gwcConfig.getLayers());
    }

    /**
     * @see org.geowebcache.config.Configuration#getLayers()
     */
    public Iterable<TileLayer> getLayers() {
        return Collections.unmodifiableList(gwcConfig.getLayers());
    }

    /**
     * @see org.geowebcache.config.Configuration#getTileLayer(java.lang.String)
     */
    public TileLayer getTileLayer(String layerName) {
        return layers.get(layerName);
    }

    /**
     * @see org.geowebcache.config.Configuration#getTileLayerById(String)
     */
    public TileLayer getTileLayerById(String layerId) {
        // this configuration does not differentiate between identifier and identity yet
        return layers.get(layerId);
    }

    /**
     * @see org.geowebcache.config.Configuration#containsLayer(java.lang.String)
     */
    public boolean containsLayer(String layerId) {
        return layers.containsKey(layerId);
    }

    /**
     * @see org.geowebcache.config.Configuration#getTileLayerCount()
     */
    public int getTileLayerCount() {
        return layers.size();
    }

    /**
     * @see org.geowebcache.config.Configuration#getTileLayerNames()
     */
    public Set<String> getTileLayerNames() {
        Set<String> names = Collections.unmodifiableSet(this.layers.keySet());
        return names;
    }

    public String getVersion() {
        return gwcConfig.getVersion();
    }
    
    /**
     * Used for getting the "fullWMS" parameter from GeoWebCacheConfigration
     * @return
     */
    public Boolean getfullWMS(){
        if(gwcConfig!=null){
            return gwcConfig.getFullWMS();
        }
        return null;        
    }

    public List<BlobStoreConfig> getBlobStores() {
        return gwcConfig.getBlobStores();
    }
    
    public LockProvider getLockProvider() {
        return gwcConfig.getLockProvider();
    }

}
