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
 * @author Arne Kepp, Marius Suta,  The Open Planning Project, Copyright 2008
 */
package org.geowebcache.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.meta.ServiceInformation;
import org.geowebcache.filter.parameters.FloatParameterFilter;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.filter.parameters.RegexParameterFilter;
import org.geowebcache.filter.parameters.StringParameterFilter;
import org.geowebcache.filter.request.CircularExtentFilter;
import org.geowebcache.filter.request.FileRasterFilter;
import org.geowebcache.filter.request.WMSRasterFilter;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.XMLGridSet;
import org.geowebcache.grid.XMLGridSubset;
import org.geowebcache.grid.XMLOldGrid;
import org.geowebcache.layer.ExpirationRule;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.meta.ContactInformation;
import org.geowebcache.layer.meta.LayerMetaInformation;
import org.geowebcache.layer.updatesource.GeoRSSFeedDefinition;
import org.geowebcache.layer.wms.WMSHttpHelper;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.mime.FormatModifier;
import org.geowebcache.seed.SeedRequest;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.util.ApplicationContextProvider;
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
 */
public class XMLConfiguration implements Configuration {

    private static Log log = LogFactory.getLog(org.geowebcache.config.XMLConfiguration.class);

    private static final String DEFAULT_CONFIGURATION_FILE_NAME = "geowebcache.xml";

    /**
     * Web app context, used to look up {@link XMLConfigurationProvider}s. Will be null if used the
     * {@link #XMLConfiguration(File)} constructor
     */
    private final WebApplicationContext context;

    /**
     * Set by {@link #initialize(GridSetBroker)}
     */
    private GridSetBroker gridSetBroker;

    /**
     * Location of the configuration file
     */
    private final File configDirectory;

    /**
     * Name of the configuration file
     */
    private final String configFileName;

    private GeoWebCacheConfiguration gwcConfig;

    private transient Map<String, TileLayer> layers;

    private String templateLocation;

    /**
     * @deprecated use {@link #XMLConfiguration(ApplicationContextProvider, DefaultStorageFinder)}
     */
    public XMLConfiguration(final ApplicationContextProvider appCtx,
            final GridSetBroker gridSetBroker, final DefaultStorageFinder storageDirFinder)
            throws ConfigurationException {
        this(appCtx, storageDirFinder);
        log.warn("This constructor is deprecated");
    }

    /**
     * Constructor that will look for {@code geowebcache.xml} at the directory defined by
     * {@code storageDirFinder}
     * 
     * @param appCtx
     * @param defaultStorage
     * @throws ConfigurationException
     */
    public XMLConfiguration(final ApplicationContextProvider appCtx,
            final DefaultStorageFinder storageDirFinder) throws ConfigurationException {

        Assert.notNull(appCtx);
        Assert.notNull(storageDirFinder);

        this.context = appCtx.getApplicationContext();
        this.configFileName = DEFAULT_CONFIGURATION_FILE_NAME;
        this.configDirectory = new File(storageDirFinder.getDefaultPath());
        this.templateLocation = "/" + DEFAULT_CONFIGURATION_FILE_NAME;

        log.info("Will look for geowebcache.xml in '" + configDirectory + "'");
    }

    /**
     * @deprecated use {@link #XMLConfiguration(ApplicationContextProvider, String)}
     */
    public XMLConfiguration(final ApplicationContextProvider appCtx,
            final GridSetBroker gridSetBroker, final String configFileDirectory)
            throws ConfigurationException {

        this(appCtx, configFileDirectory);
        log.warn("This constructor is deprecated");
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

        Assert.notNull(appCtx);
        Assert.notNull(configFileDirectory);

        this.context = appCtx.getApplicationContext();
        this.configFileName = DEFAULT_CONFIGURATION_FILE_NAME;
        this.templateLocation = "/" + DEFAULT_CONFIGURATION_FILE_NAME;

        if (configFileDirectory.startsWith("/") || configFileDirectory.contains(":\\")
                || configFileDirectory.startsWith("\\\\")) {

            log.info("Provided cache directory as absolute path '" + configFileDirectory + "'");
            this.configDirectory = new File(configFileDirectory);
        } else {

            String baseDir = context.getServletContext().getRealPath("");
            log.info("Provided cache directory relative to servlet context '" + baseDir + "': "
                    + configFileDirectory);
            this.configDirectory = new File(baseDir, configFileDirectory);
        }

        log.info("Will look for geowebcache.xml in '" + configFileDirectory + "'");
    }

    /**
     * Constructor that receives the configuration file. Only used for unit testing.
     */
    public XMLConfiguration(final InputStream in) throws ConfigurationException {

        this.configDirectory = null;
        this.configFileName = null;
        this.gridSetBroker = new GridSetBroker(true, false);
        this.context = null;
        this.templateLocation = "/" + DEFAULT_CONFIGURATION_FILE_NAME;

        try {
            this.gwcConfig = loadConfiguration(in);
        } catch (IOException e) {
            throw new ConfigurationException("Error parsing config file", e);
        }
    }

    /**
     * Allows to set the location of the template file to create geowebcache.xml from when it's not
     * found in the cache directory.
     * 
     * @param templateLocation
     *            location of the template geowebcache.xml file, must be a classpath location. If
     *            not set defaults to /geowebcache.xml
     */
    public void setTemplate(final String templateLocation) {
        this.templateLocation = templateLocation;
    }

    private File findConfFile() throws ConfigurationException {
        if (!configDirectory.exists()) {
            throw new ConfigurationException("Configuration directory does not exist: '"
                    + configDirectory.getAbsolutePath() + "'");
        }
        if (!configDirectory.canWrite()) {
            throw new ConfigurationException("Configuration directory is not writable: '"
                    + configDirectory.getAbsolutePath() + "'");
        }

        File xmlFile = new File(configDirectory, configFileName);

        if (xmlFile.exists()) {
            log.info("Found configuration file in " + configDirectory.getAbsolutePath());
        } else {
            log.warn("Found no configuration file in config directory, will create one at '"
                    + xmlFile.getAbsolutePath() + "' from template "
                    + getClass().getResource(templateLocation).toExternalForm());
            // grab template from classpath
            try {
                InputStream templateStream = getClass().getResourceAsStream(templateLocation);
                try {
                    OutputStream output = new FileOutputStream(xmlFile);
                    try {
                        IOUtils.copy(templateStream, output);
                    } finally {
                        output.flush();
                        output.close();
                    }
                } finally {
                    templateStream.close();
                }
            } catch (IOException e) {
                throw new ConfigurationException("Error copying template config to "
                        + xmlFile.getAbsolutePath(), e);
            }

        }

        return xmlFile;
    }

    public boolean isRuntimeStatsEnabled() {
        if (gwcConfig == null || gwcConfig.runtimeStats == null) {
            return true;
        } else {
            return gwcConfig.runtimeStats;
        }
    }

    public synchronized ServiceInformation getServiceInformation() throws GeoWebCacheException {
        if (this.gwcConfig == null) {
            return null;
        }

        return gwcConfig.serviceInformation;
    }

    private void setDefaultValues(TileLayer layer) {
        // Additional values that can have defaults set
        if (layer.isCacheBypassAllowed() == null) {
            if (gwcConfig.cacheBypassAllowed != null) {
                layer.setCacheBypassAllowed(gwcConfig.cacheBypassAllowed);
            } else {
                layer.setCacheBypassAllowed(false);
            }
        }

        if (layer.getBackendTimeout() == null) {
            if (gwcConfig.backendTimeout != null) {
                layer.setBackendTimeout(gwcConfig.backendTimeout);
            } else {
                layer.setBackendTimeout(120);
            }
        }

        if (layer.getFormatModifiers() == null) {
            if (gwcConfig.formatModifiers != null) {
                layer.setFormatModifiers(gwcConfig.formatModifiers);
            }
        }

        if (layer instanceof WMSLayer) {
            WMSLayer wl = (WMSLayer) layer;

            URL proxyUrl = null;
            try {
                if (gwcConfig.proxyUrl != null) {
                    proxyUrl = new URL(gwcConfig.proxyUrl);
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
            } else if (gwcConfig.httpUsername != null) {
                sourceHelper = new WMSHttpHelper(gwcConfig.httpUsername, gwcConfig.httpPassword,
                        proxyUrl);
                log.debug("Using global HTTP credentials for " + wl.getName());
            } else {
                sourceHelper = new WMSHttpHelper(null, null, proxyUrl);
                log.debug("Not using HTTP credentials for " + wl.getName());
            }

            wl.setSourceHelper(sourceHelper);
        }
    }

    private GeoWebCacheConfiguration loadConfiguration() throws ConfigurationException {
        File xmlFile = findConfFile();
        Assert.notNull(xmlFile);
        GeoWebCacheConfiguration config = loadConfiguration(xmlFile);
        return config;
    }

    private GeoWebCacheConfiguration loadConfiguration(File xmlFile) throws ConfigurationException {
        InputStream in;
        try {
            in = new FileInputStream(xmlFile);
            try {
                return loadConfiguration(in);
            } finally {
                in.close();
            }
        } catch (IOException e) {
            throw new ConfigurationException("Error parsing config file "
                    + xmlFile.getAbsolutePath(), e);
        }
    }

    private GeoWebCacheConfiguration loadConfiguration(InputStream xmlFile) throws IOException,
            ConfigurationException {
        Node rootNode = loadDocument(xmlFile);
        XStream xs = getConfiguredXStream(new XStream());

        GeoWebCacheConfiguration config;
        config = (GeoWebCacheConfiguration) xs.unmarshal(new DomReader((Element) rootNode));
        config.init();
        return config;
    }

    private void writeConfiguration() throws GeoWebCacheException {
        File xmlFile = findConfFile();
        persistToFile(xmlFile);
    }

    @SuppressWarnings("unchecked")
    public XStream getConfiguredXStream(XStream xs) {
        // XStream xs = xstream;
        xs.setMode(XStream.NO_REFERENCES);

        xs.alias("gwcConfiguration", GeoWebCacheConfiguration.class);
        xs.useAttributeFor(GeoWebCacheConfiguration.class, "xmlns_xsi");
        xs.aliasField("xmlns:xsi", GeoWebCacheConfiguration.class, "xmlns_xsi");
        xs.useAttributeFor(GeoWebCacheConfiguration.class, "xsi_noNamespaceSchemaLocation");
        xs.aliasField("xsi:noNamespaceSchemaLocation", GeoWebCacheConfiguration.class,
                "xsi_noNamespaceSchemaLocation");
        xs.useAttributeFor(GeoWebCacheConfiguration.class, "xmlns");

        xs.alias("keyword", String.class);
        xs.alias("layers", List.class);
        xs.alias("wmsLayer", WMSLayer.class);

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
        // xs.alias("parameterFilter", ParameterFilter.class);
        xs.alias("floatParameterFilter", FloatParameterFilter.class);
        xs.alias("regexParameterFilter", RegexParameterFilter.class);
        xs.alias("stringParameterFilter", StringParameterFilter.class);
        // xs.alias("regex", String.class);
        xs.alias("formatModifier", FormatModifier.class);

        xs.alias("circularExtentFilter", CircularExtentFilter.class);
        xs.alias("wmsRasterFilter", WMSRasterFilter.class);
        xs.alias("fileRasterFilter", FileRasterFilter.class);

        xs.alias("expirationRule", ExpirationRule.class);
        xs.useAttributeFor(ExpirationRule.class, "minZoom");
        xs.useAttributeFor(ExpirationRule.class, "expiration");

        xs.alias("geoRssFeed", GeoRSSFeedDefinition.class);

        xs.alias("metaInformation", LayerMetaInformation.class);

        xs.alias("contactInformation", ContactInformation.class);

        if (this.context != null) {
            /*
             * Look up XMLConfigurationProvider extension points and let them contribute to the
             * configuration
             */
            Collection<XMLConfigurationProvider> configExtensions;
            configExtensions = this.context.getBeansOfType(XMLConfigurationProvider.class).values();
            for (XMLConfigurationProvider extension : configExtensions) {
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

    protected void persistToFile(File xmlFile) throws GeoWebCacheException {
        // create the XStream for serializing the configuration
        XStream xs = getConfiguredXStream(new XStream());

        OutputStreamWriter writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(xmlFile), "UTF-8");
        } catch (UnsupportedEncodingException uee) {
            uee.printStackTrace();
            throw new GeoWebCacheException(uee.getMessage());
        } catch (FileNotFoundException fnfe) {
            throw new GeoWebCacheException(fnfe.getMessage());
        }

        try {
            writer.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
            xs.toXML(gwcConfig, writer);
        } catch (IOException e) {
            e.printStackTrace();
            throw new GeoWebCacheException("Error writing to " + xmlFile.getAbsolutePath() + ": "
                    + e.getMessage());
        }

        log.info("Wrote configuration to " + xmlFile.getAbsolutePath());
    }

    /**
     * Method responsible for modifying an existing layer.
     * 
     * @param currentLayer
     *            the name of the layer to be modified
     * @param tl
     *            the new layer to overwrite the existing layer
     * @return true if operation succeeded, false otherwise
     */

    public boolean modifyLayer(TileLayer tl) throws GeoWebCacheException {
        // tl.setCacheFactory(cacheFactory);
        boolean response = gwcConfig.replaceLayer(tl);

        if (response) {
            writeConfiguration();
        }
        return response;
    }

    public boolean addLayer(TileLayer tl) throws GeoWebCacheException {
        // tl.setCacheFactory(cacheFactory);

        boolean response = gwcConfig.addLayer(tl);

        if (response) {
            writeConfiguration();
        }
        return response;
    }

    /**
     * Method responsible for deleting existing layers
     * 
     * @param layerName
     *            the name of the layer to be deleted
     * @return true if operation succeeded, false otherwise
     */
    public boolean deleteLayer(TileLayer layer) throws GeoWebCacheException {
        boolean response = gwcConfig.removeLayer(layer);

        if (response) {
            writeConfiguration();
        }
        return response;
    }

    /**
     * Method responsible for loading xml configuration file and parsing it into a W3C DOM Document
     * 
     * @param file
     *            the file contaning the layer configurations
     * @return W3C DOM Document
     */
    private Node loadDocument(InputStream xmlFile) throws ConfigurationException, IOException {
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

    private Node checkAndTransform(Document doc) throws ConfigurationException {
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
            log.info("Updating configuration from 1.2.6 to 1.3.0");
            rootNode = applyTransform(rootNode, "geowebcache_126.xsl").getFirstChild();
        }

        // Check again after transform
        if (!rootNode.getNodeName().equals("gwcConfiguration")) {
            log.error("Unable to parse file, expected gwcConfiguration at root after transform.");
            throw new ConfigurationException("Unable to parse after transform.");
        } else {
            // Perform validation
            // TODO dont know why this one suddenly failed to look up, revert to
            // XMLConstants.W3C_XML_SCHEMA_NS_URI
            SchemaFactory factory = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");
            InputStream is = XMLConfiguration.class.getResourceAsStream("geowebcache.xsd");

            // Parsing the schema file
            try {
                Schema schema = factory.newSchema(new StreamSource(is));
                Validator validator = schema.newValidator();

                // debugPrint(rootNode);

                DOMSource domSrc = new DOMSource(rootNode);
                validator.validate(domSrc);
                log.info("Configuration file validated fine.");
            } catch (SAXException e) {
                String msg = "*** GWC configuration validation error: " + e.getMessage();
                char[] c = new char[4 + msg.length()];
                Arrays.fill(c, '*');
                String warndecoration = new String(c);
                log.warn(warndecoration);
                log.info(msg);
                log.warn(warndecoration);
                log.info("Will try to use configuration anyway.");
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        return rootNode;
    }

    private Node applyTransform(Node oldRootNode, String xslFilename) {
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
     * @see org.geowebcache.config.Configuration#getIdentifier()
     */
    public String getIdentifier() {
        if (configDirectory != null) {
            return configDirectory.getAbsolutePath();
        }

        return "mockConfig";
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
     * @see org.geowebcache.config.Configuration#initialize(org.geowebcache.grid.GridSetBroker)
     */
    public int initialize(final GridSetBroker gridSetBroker) throws GeoWebCacheException {
        this.gridSetBroker = gridSetBroker;

        if (this.configFileName != null) {
            this.gwcConfig = loadConfiguration();
        }

        if (gwcConfig.gridSets != null) {
            Iterator<XMLGridSet> iter = gwcConfig.gridSets.iterator();
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

        // Loop over the layers and set appropriate values
        if (gwcConfig.layers != null) {
            Iterator<TileLayer> iter = gwcConfig.layers.iterator();

            while (iter.hasNext()) {
                TileLayer layer = iter.next();
                setDefaultValues(layer);
            }
        }

        // This is used by reload as well
        this.layers = new HashMap<String, TileLayer>();
        List<TileLayer> configLayers = getTileLayers();
        log.info("Adding layers from " + getIdentifier());
        for (TileLayer layer : configLayers) {
            if (layer == null) {
                log.error("layer was null");
                continue;
            }
            log.info("Initializing TileLayer '" + layer.getName() + "'");
            layer.initialize(gridSetBroker);
            layers.put(layer.getName(), layer);
        }
        return getTileLayerCount();
    }

    /**
     * @see org.geowebcache.config.Configuration#getTileLayers()
     */
    public List<TileLayer> getTileLayers() throws GeoWebCacheException {
        List<TileLayer> layers = Collections.emptyList();
        if (gwcConfig != null && gwcConfig.layers != null) {
            layers = gwcConfig.layers;
        }
        return layers;
    }

    /**
     * @see org.geowebcache.config.Configuration#getTileLayer(java.lang.String)
     */
    public TileLayer getTileLayer(String layerIdent) {
        return layers.get(layerIdent);
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
        Set<String> names = new HashSet<String>();
        try {
            for (TileLayer tl : getTileLayers()) {
                names.add(tl.getName());
            }
        } catch (GeoWebCacheException e) {
            throw new RuntimeException(e);
        }
        return names;
    }

    public boolean remove(String layerName) {
        TileLayer tileLayer = getTileLayer(layerName);
        if (tileLayer == null) {
            return false;
        }
        boolean removed = false;
        tileLayer.acquireLayerLock();
        try {
            removed = gwcConfig.removeLayer(tileLayer);
            if (removed) {
                Map<String, TileLayer> buff = new HashMap<String, TileLayer>(this.layers);
                buff.remove(layerName);
                this.layers = buff;
            }
        } finally {
            tileLayer.releaseLayerLock();
        }
        return removed;
    }
}
