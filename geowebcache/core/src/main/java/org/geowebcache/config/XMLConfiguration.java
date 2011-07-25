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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
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
import org.geowebcache.config.meta.ServiceInformation;
import org.geowebcache.filter.parameters.FloatParameterFilter;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.filter.parameters.RegexParameterFilter;
import org.geowebcache.filter.parameters.StringParameterFilter;
import org.geowebcache.filter.request.CircularExtentFilter;
import org.geowebcache.filter.request.FileRasterFilter;
import org.geowebcache.filter.request.WMSRasterFilter;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.SRS;
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
import org.geowebcache.seed.SeedEstimate;
import org.geowebcache.seed.SeedRequest;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.storage.JobLogObject;
import org.geowebcache.storage.JobObject;
import org.geowebcache.storage.SettingsObject;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;
import org.geowebcache.util.ApplicationContextProvider;
import org.geowebcache.util.ISO8601DateParser;
import org.springframework.web.context.WebApplicationContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.SingleValueConverter;
import com.thoughtworks.xstream.converters.basic.IntConverter;
import com.thoughtworks.xstream.io.xml.DomReader;

/**
 * XMLConfiguration class responsible for reading/writing layer configurations to and from XML file
 */
public class XMLConfiguration implements Configuration {
    private static Log log = LogFactory.getLog(org.geowebcache.config.XMLConfiguration.class);

    private static final String CONFIGURATION_FILE_NAME = "geowebcache.xml";

    private static final String[] CONFIGURATION_REL_PATHS = { "/WEB-INF/classes", "/../resources" };

    private WebApplicationContext context;

    private GridSetBroker gridSetBroker;

    private DefaultStorageFinder defStoreFind;

    private String absPath = null;

    private String relPath = null;

    private boolean mockConfiguration = false;

    private File configH = null;

    StorageBroker storageBroker = null;

    private GeoWebCacheConfiguration gwcConfig = null;

    private transient Map<String, TileLayer> layers;

    /**
     * Constructor that will accept an absolute or relative path for finding geowebcache.xml
     * 
     * @param appCtx
     * @param gridSetBroker
     * @param defaultStorage
     * @param filePath
     */
    public XMLConfiguration(ApplicationContextProvider appCtx, GridSetBroker gridSetBroker,
            DefaultStorageFinder defaultStorage, String filePath) {

        context = appCtx.getApplicationContext();
        this.gridSetBroker = gridSetBroker;
        defStoreFind = defaultStorage;

        if(filePath.startsWith("/") || filePath.contains(":\\") || filePath.startsWith("\\\\") ) {
            this.absPath = filePath;
        } else {
            this.relPath = filePath;
        }

        log.info("Will look for geowebcache.xml in " + filePath);

        try {
            File xmlFile = findConfFile();
            if (xmlFile == null) {
                return;
            }

            loadConfiguration(xmlFile);
            initialize();

        } catch (GeoWebCacheException e) {
            e.printStackTrace();
        }
    }

    /**
     * Constructor that will just search for geowebcache.xml
     * 
     * @param appCtx
     * @param gridSetBroker
     * @param defaultStorage
     */
    public XMLConfiguration(ApplicationContextProvider appCtx, GridSetBroker gridSetBroker,
            DefaultStorageFinder defaultStorage) {

        context = appCtx.getApplicationContext();
        this.gridSetBroker = gridSetBroker;
        defStoreFind = defaultStorage;

        try {
            File xmlFile = findConfFile();
            if (xmlFile == null) {
                return;
            }

            loadConfiguration(xmlFile);
            initialize();

        } catch (GeoWebCacheException e) {
            e.printStackTrace();
        }
    }

    /**
     * Used for unit testing, since the file handle is difficult to get there
     * 
     * @param is
     */
    public XMLConfiguration(InputStream is) throws Exception {
        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        docBuilderFactory.setNamespaceAware(true);
        DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();

        XStream xs = configureXStreamForLayers(new XStream());

        gwcConfig = (GeoWebCacheConfiguration) xs.unmarshal(new DomReader(
                (Element) checkAndTransform(docBuilder.parse(is))));

        mockConfiguration = true;

        initialize();
    }

    private void initialize() {
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
    }

    private File findConfFile() throws GeoWebCacheException {
        if (configH == null) {
            determineConfigDirH();
        }

        File xmlFile = null;

        if (configH != null) {
            xmlFile = new File(configH.getAbsolutePath() + File.separator + CONFIGURATION_FILE_NAME);
        } else {
            log.debug("Unable to determine configuration directory."
                    + " If you are running GWC in GeoServer this is probably not an issue.");

            return null;
        }

        if (xmlFile != null) {
            log.info("Found configuration file in " + configH.getAbsolutePath());
        }

        return xmlFile;
    }

    /**
     * Method responsible for loading XML configuration file
     * 
     */
    private synchronized List<TileLayer> getTileLayers(boolean reload) throws GeoWebCacheException {
        if (reload && !mockConfiguration) {
            File xmlFile = findConfFile();
            if (xmlFile != null && !xmlFile.exists()) {
                log.info("Found no configuration file in " + configH.getAbsolutePath()
                        + " If you are running GWC in GeoServer this is probably not an issue.");
                return Collections.emptyList();
            }
            loadConfiguration(xmlFile);
            initialize();
        }

        List<TileLayer> layers = Collections.emptyList();
        if (gwcConfig != null && gwcConfig.layers != null) {
            layers = gwcConfig.layers;
        }
        return layers;
    }

    public boolean isRuntimeStatsEnabled() {
        if (gwcConfig == null || gwcConfig.runtimeStats == null) {
            return true;
        } else {
            return gwcConfig.runtimeStats;
        }
    }

    public String getBasemapConfig() {
        if (gwcConfig == null || gwcConfig.basemapConfig == null) {
            return null;
        } else {
            return gwcConfig.basemapConfig;
        }
    }
    
    public Integer getJobUpdateFrequency() {
        if (gwcConfig == null || gwcConfig.jobUpdateFrequency == null) {
            return null;
        } else {
            return gwcConfig.jobUpdateFrequency;
        }
    }

    public synchronized ServiceInformation getServiceInformation() throws GeoWebCacheException {
        if (!mockConfiguration && this.gwcConfig == null) {
            File xmlFile = findConfFile();
            if (xmlFile == null || !xmlFile.exists()) {
                return null;
            }
            loadConfiguration(xmlFile);
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

    private void loadConfiguration(File xmlFile) throws GeoWebCacheException {
        Node rootNode = loadDocument(xmlFile);
        XStream xs = configureXStreamForLayers(new XStream());

        gwcConfig = (GeoWebCacheConfiguration) xs.unmarshal(new DomReader((Element) rootNode));

        gwcConfig.init();
    }

    private void writeConfiguration() throws GeoWebCacheException {
        File xmlFile = findConfFile();
        persistToFile(xmlFile);
    }

    @SuppressWarnings("unchecked")
    public XStream configureXStreamForLayers(XStream xs) {
        commonXStreamConfig(xs);

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

        return xs;
    }

    public XStream configureXStreamForJobs(XStream xs) {
        commonXStreamConfig(xs);

        xs.alias("jobs", List.class);
        xs.alias("job", JobObject.class);

        xs.aliasField("parameters", JobObject.class, "encodedParameters");

        xs.registerConverter(new SRSConverter());
        xs.registerConverter(new TimestampConverter());
        xs.registerConverter(new BoundingBoxConverter());
        
        xs.omitField(JobObject.class, "newLogs");

        return xs;
    }
    
    public XStream configureXStreamForSeedEstimate(XStream xs) {
        commonXStreamConfig(xs);
        xs.alias("estimate", SeedEstimate.class);
        xs.registerConverter(new BoundingBoxConverter());
        return xs;
    }
    
    public XStream configureXStreamForSettings(XStream xs) {
        commonXStreamConfig(xs);
        xs.alias("settings", SettingsObject.class);
        xs.registerConverter(new BoundingBoxConverter());
        return xs;
    }

    private XStream commonXStreamConfig(XStream xs) {
        xs.setMode(XStream.NO_REFERENCES);

        xs.alias("gwcConfiguration", GeoWebCacheConfiguration.class);
        xs.useAttributeFor(GeoWebCacheConfiguration.class, "xmlns_xsi");
        xs.aliasField("xmlns:xsi", GeoWebCacheConfiguration.class, "xmlns_xsi");
        xs.useAttributeFor(GeoWebCacheConfiguration.class, "xsi_noNamespaceSchemaLocation");
        xs.aliasField("xsi:noNamespaceSchemaLocation", GeoWebCacheConfiguration.class,
                "xsi_noNamespaceSchemaLocation");
        xs.useAttributeFor(GeoWebCacheConfiguration.class, "xmlns");

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
    
    class SRSConverter extends IntConverter {
        public boolean canConvert(Class type) {
            return type.equals(SRS.class);
        }
        
        public String toString(Object obj) {
            return Integer.toString(((SRS)obj).getNumber());
        }

        public Object fromString(String val) {
            return SRS.getSRS(Integer.parseInt(val));
        }        
    }    

    class TimestampConverter implements SingleValueConverter {
        public boolean canConvert(Class type) {
            if(type.equals(Timestamp.class)) {
                return true;
            }
            return type.equals(Timestamp.class);
        }
        
        public String toString(Object obj) {
            Timestamp ts = (Timestamp)obj;
            
            return (ISO8601DateParser.toString(ts));
        }

        public Object fromString(String val) {
            Date d = null;
            
            if(val == null || val.equals("null")) {
                return null;
            } else {
                try {
                    d = ISO8601DateParser.parse(val);
                } catch(ParseException pe) {
                    log.warn("Couldn't parse date: " + val);
                }
                return new Timestamp(d.getTime());
            }
        }        
    }    

    class BoundingBoxConverter implements SingleValueConverter {
        public boolean canConvert(Class type) {
            return type.equals(BoundingBox.class);
        }
        
        public String toString(Object obj) {
            return ((BoundingBox)obj).toString();
        }

        public Object fromString(String val) {
            return new BoundingBox(val);
        }        
    }

    public XStream configureXStreamForJobLogs(XStream xs) {
        commonXStreamConfig(xs);

        xs.alias("logs", List.class);
        xs.alias("log", JobLogObject.class);

        xs.registerConverter(new TimestampConverter());
        
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
        XStream xs = configureXStreamForLayers(new XStream());

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
    private Node loadDocument(File xmlFile) throws ConfigurationException {
        Node topNode = null;
        try {
            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
            docBuilderFactory.setNamespaceAware(true);
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            topNode = checkAndTransform(docBuilder.parse(xmlFile));
        } catch (ParserConfigurationException pce) {
            log.error(pce.getMessage());
            pce.printStackTrace();
        } catch (IOException ei) {
            throw new ConfigurationException("Error parsing file " + xmlFile.getAbsolutePath());
        } catch (SAXException saxe) {
            log.error(saxe.getMessage());
        }

        if (topNode == null) {
            throw new ConfigurationException("Error parsing file " + xmlFile.getAbsolutePath()
                    + ", top node came out as null");
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
                log.info(e.getMessage());
                log.info("Will try to use configuration anyway.");
            } catch (IOException e) {
                e.printStackTrace();
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

    private void determineConfigDirH() {
        String baseDir = context.getServletContext().getRealPath("");

        /*
         * Try 1) absolute path (specified in bean defn) 2) relative path (specified in bean defn)
         * 3) environment variables 4) standard paths
         */
        if (absPath != null) {
            configH = new File(absPath);
        } else if (relPath != null) {
            configH = new File(baseDir + File.separator + relPath);
            log.debug("Configuration directory set to: " + configH.getAbsolutePath());
        } else if (relPath == null) {
            // Try env variables
            String defaultPath = null;
            try {
                defaultPath = defStoreFind.getDefaultPath();
            } catch (StorageException se) {
                // Do nothing
            }
            if (defaultPath != null) {
                File tmpPath = new File(defaultPath + File.separator + CONFIGURATION_FILE_NAME);
                if (tmpPath.exists()) {
                    configH = new File(tmpPath.getParent());
                }
            }

            // Finally, try "standard" paths if we have to.
            if (configH == null) {
                for (int i = 0; i < CONFIGURATION_REL_PATHS.length; i++) {
                    relPath = CONFIGURATION_REL_PATHS[i];
                    if (File.separator.equals("\\")) {
                        relPath = relPath.replace("/", "\\");
                    }

                    File tmpPath = new File(baseDir + relPath + File.separator
                            + CONFIGURATION_FILE_NAME);

                    if (tmpPath.exists() && tmpPath.canRead()) {
                        log.info("No configuration directory was specified, using "
                                + tmpPath.getAbsolutePath());
                        configH = new File(baseDir + relPath);
                    }
                }
            }
        }

        if (configH == null) {
            log.info("Failed to find geowebcache.xml. This is not a problem unless you are trying to use a custom XML configuration file.");
        } else {
            log.debug("Configuration directory set to: " + configH.getAbsolutePath());

            if (!configH.exists() || !configH.canRead()) {
                log.info("Configuration file cannot be read or does not exist!");
            }
        }
    }

    public String getIdentifier() {
        if (mockConfiguration) {
            return "Mock configuration";
        }

        if (configH == null) {
            try {
                this.findConfFile();
            } catch (GeoWebCacheException e) {
                throw new RuntimeException();
            }
        }

        // Try again
        if (configH != null) {
            return configH.getAbsolutePath();
        }

        return null;
    }

    public void setRelativePath(String relPath) {
        log.error("Specifying the relative path as a property is deprecated. "
                + "Please pass it as the 4th argument to the constructor.");
    }

    public void setAbsolutePath(String absPath) {
        log.error("Specifying the absolute path as a property is deprecated. "
                + "Please pass it as the 4th argument to the constructor.");
    }

    public void debugPrint(Node node) {
        if (node == null) {
            System.out.println("1: No node");
            return;
        }

        System.out.println("1: " + node.getNodeName() + " " + node.getNamespaceURI());

        node = node.getFirstChild();
        if (node != null) {
            System.out.println("2: " + node.getNodeName() + " " + node.getNamespaceURI());
            node = node.getFirstChild();
        }
        if (node != null) {
            System.out.println("3: " + node.getNodeName() + " " + node.getNamespaceURI());
        }
    }

    /**
     * @see org.geowebcache.config.Configuration#initialize(org.geowebcache.grid.GridSetBroker)
     */
    public int initialize(GridSetBroker gridSetBroker) throws GeoWebCacheException {
        // This is used by reload as well
        this.layers = new HashMap<String, TileLayer>();
        List<TileLayer> configLayers = getTileLayers(true);
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
        return getTileLayers(false);
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
