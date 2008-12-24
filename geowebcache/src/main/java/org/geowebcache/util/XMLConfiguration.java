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
package org.geowebcache.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.xml.XMLConstants;
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
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.cache.CacheException;
import org.geowebcache.cache.CacheFactory;
import org.geowebcache.cache.file.FileCache;
import org.geowebcache.layer.Grid;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.wms.WMSLayer;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.web.context.WebApplicationContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomReader;
import com.thoughtworks.xstream.io.xml.DomWriter;

public class XMLConfiguration implements Configuration, ApplicationContextAware {
    private static Log log = LogFactory.getLog(org.geowebcache.util.XMLConfiguration.class);

    private static final String CONFIGURATION_FILE_NAME = "geowebcache.xml";
    
    private static final String[] CONFIGURATION_REL_PATHS = 
        { "/WEB-INF/classes", "/../resources" };
    
    private WebApplicationContext context;

    private CacheFactory cacheFactory = null;

    private String absPath = null;

    private String relPath = null;

    private File configH = null;

    private FileCache fileCache = null;
    
    /**
     * XMLConfiguration class responsible for reading/writing layer
     * configurations to and from XML file
     * 
     * @param cacheFactory
     */
    public XMLConfiguration(CacheFactory cacheFactory) {
        this.cacheFactory = cacheFactory;
    }

    public XMLConfiguration() {
    }
    
    private File findConfFile() throws GeoWebCacheException {
        if (configH == null) {
            determineConfigDirH();
        }

        File xmlFile = null;
        if (configH != null) {
            xmlFile = new File(configH.getAbsolutePath() + File.separator + CONFIGURATION_FILE_NAME);
        } else {
            throw new GeoWebCacheException("Unable to determine configuration directory.");
        }

        if (xmlFile != null) {
            log.trace("Found configuration file in "+ configH.getAbsolutePath());
        } else {
            throw new GeoWebCacheException("Found no configuration file in "+ configH.getAbsolutePath()+
            		" If you are running GWC in GeoServer this is probably not a problem.");
        }
        
        return xmlFile;
    }

    /**
     * Method responsible for loading XML configuration file
     * 
     */
    public Map<String, TileLayer> getTileLayers() throws GeoWebCacheException {
        File xmlFile = findConfFile();
        
        FileInputStream confFile = null;
        try {
            confFile = new FileInputStream(xmlFile);
        } catch (FileNotFoundException e1) {
            log.error("The file " + xmlFile.getAbsolutePath() + " could not be found. " +
            		"If you are running GWC in GeoServer this is probably not a problem.");
            return null;
        }
        
        return getTileLayers(confFile);
    }
    
    protected Map<String, TileLayer> getTileLayers(InputStream confFile) 
    throws GeoWebCacheException {
        
        HashMap<String, TileLayer> layers = new HashMap<String, TileLayer>();

        Node layersRoot = loadIntoDocument(confFile);

        if(layersRoot != null) {
            XStream xs = getConfiguredXStream(new XStream());

            NodeList allLayerNodes = layersRoot.getChildNodes();
           
            TileLayer result = null;
            for (int i = 0; i < allLayerNodes.getLength(); i++) {
                if (allLayerNodes.item(i) instanceof Element) {
                    Element e = (Element) allLayerNodes.item(i);
                    if (e.getTagName().equalsIgnoreCase("wmsLayer"))
                        result = (WMSLayer) xs.unmarshal(new DomReader(
                                (Element) allLayerNodes.item(i)));
                    result.setCacheFactory(this.cacheFactory);
                    layers.put(result.getName(), result);
                }
            }
        } else {
            throw new ConfigurationException("Root element was null");
        }

        return layers;
    }

    public static XStream getConfiguredXStream(XStream xstream) {
        XStream xs = xstream;
        xs.setMode(XStream.NO_REFERENCES);
        
        xs.alias("layer", TileLayer.class);
        xs.alias("wmsLayer", WMSLayer.class);
        //xs.aliasField("layer-name", TileLayer.class, "name");
        xs.alias("grids", new ArrayList<Grid>().getClass());
        xs.alias("grid", Grid.class);
        xs.aliasType("format", String.class);
        xs.alias("mimeFormats", new ArrayList<String>().getClass());
        xs.aliasType("wmsUrl", String.class);
        xs.aliasType("errorMime", String.class);
        xs.aliasType("cachePrefix", String.class);
        xs.alias("metaWidthHeight", new int[1].getClass());
        //xs.alias("width", Integer.class);
        //xs.alias("height", Integer.class);
        xs.aliasType("version", String.class);
        xs.alias("tiled", boolean.class);
        xs.alias("transparent", boolean.class);
        xs.alias("srs", org.geowebcache.layer.SRS.class);
        
        xs.alias("zoomStart", int.class);
        xs.alias("zoomStop", int.class);
        
        xs.alias("expireCache", int.class);
        xs.alias("expireClients", int.class);
        //xs.alias("debugheaders", boolean.class);
        
        return xs;
    }

    /**
     * Method responsible for writing out TileLayer objects
     * 
     * @param tl
     *            a new TileLayer object to be serialized to XML
     * @return true if operation succeeded, false otherwise
     */

    public boolean createLayer(TileLayer tl) throws GeoWebCacheException {
        File xmlFile = findConfFile();

        // load configurations into Document
        Node layersRoot = null;
        try {
            layersRoot = loadIntoDocument(new FileInputStream(xmlFile));
        } catch (FileNotFoundException e1) {
            log.error("The file " + xmlFile.getAbsolutePath() + " could not be found.");
        }
        
        // create the XStream for serializing tileLayers to XML
        XStream xs = getConfiguredXStream(new XStream());
        // sent to XML
        xs.marshal(tl, new DomWriter((Element) layersRoot));

        org.w3c.dom.Document doc = createDocument();
        
        try {
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(xmlFile);

            // write the DOM to the file
            Transformer xformer = TransformerFactory.newInstance().newTransformer();
            xformer.transform(source, result);
            
        } catch (TransformerConfigurationException e) {
            log.error(e.getMessage());
        } catch (TransformerException e) {
            log.error(e.getMessage());
        }

        return true;

    }

    
    private org.w3c.dom.Document createDocument() {
        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        docBuilderFactory.setNamespaceAware(true);

        // Added to do XSD validation
        // docBuilderFactory.setAttribute(
        // "http://java.sun.com/xml/jaxp/properties/schemaSource",
        // "file:./test.xsd");
        // docBuilderFactory.setValidating(true);
        // docBuilderFactory.setAttribute(
        // "http://java.sun.com/xml/jaxp/properties/schemaLanguage",
        // "http://www.w3.org/2001/XMLSchema");

        DocumentBuilder docBuilder = null;
        try {
            docBuilder = docBuilderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        return docBuilder.newDocument();
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

    public boolean modifyLayer(String currentLayer, TileLayer tl) throws GeoWebCacheException {

        return deleteLayer(currentLayer) && createLayer(tl);

    }

    /**
     * Method responsible for deleting existing layers
     * 
     * @param layerName
     *            the name of the layer to be deleted
     * @return true if operation succeeded, false otherwise
     */
    public boolean deleteLayer(String layerName) {
        if (configH == null) {
            determineConfigDirH();
        }

        File xmlFile = null;
        if (configH == null) {
            log.error("deleteLayer() - Missing XML configuration file?");
            return false;
        } else {
            // Find the configuration file
            xmlFile = new File(configH.getAbsolutePath() + File.separator
                    + "geowebcache.xml");
        }

        if (xmlFile != null) {
            log.trace("Found configuration file in "
                    + configH.getAbsolutePath());
        } else {
            log.error("Found no configuration file in "
                    + configH.getAbsolutePath());
            return false;
        }

        // load configurations into Document
        Node layersNode = null;
        try {
            layersNode = loadIntoDocument(new FileInputStream(xmlFile));
        } catch (ConfigurationException e1) {
            log.error("Exception occured while creating documet from file " + xmlFile.getAbsolutePath());;
        } catch (FileNotFoundException e) {
            log.error("The file " + xmlFile.getAbsolutePath() + " could not be found.");
        }

        NodeList wmsLayerNodes = layersNode.getChildNodes();

        // Lets go looking for the node, XPath anyone?
        DONE: if (wmsLayerNodes.getLength() == 0)
            return false;
        else {
            for (int i = 0; i < wmsLayerNodes.getLength(); i++) {
                Node layerNode = wmsLayerNodes.item(i);
                NodeList layerFields = layerNode.getChildNodes();
                for (int j = 0; j < layerFields.getLength(); j++) {
                    layerFields.item(j).getNodeName().equals("name");
                    if (layerFields.item(j).getNodeValue().equals(layerName)) {
                        layersNode.removeChild(layerNode);
                        break DONE;
                    }
                }
            }
        }
        
        org.w3c.dom.Document doc = createDocument();

        try {
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(xmlFile);

            // write the DOM to the file
            Transformer xformer = TransformerFactory.newInstance()
                    .newTransformer();
            xformer.transform(source, result);
        } catch (TransformerConfigurationException e) {
            log.error("XMLConfiguration encountered " + e.getMessage());
        } catch (TransformerException e) {
            log.error("XMLConfiguration encountered " + e.getMessage());
        }
        return true;
    }

    /**
     * Method responsible for loading xml configuration file and parsing it into
     * a W3C DOM Document
     * 
     * @param file
     *            the file contaning the layer configurations
     * @return W3C DOM Document
     */
    private org.w3c.dom.Node loadIntoDocument(InputStream xmlFile) throws ConfigurationException {
        org.w3c.dom.Node layersNode = null;
        try {
            //http://www.javaworld.com/javaworld/jw-08-2005/jw-0808-xml.html?page=3
            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
            docBuilderFactory.setNamespaceAware(true);
            
            // Added to do XSD validation
            //docBuilderFactory.setAttribute("http://java.sun.com/xml/jaxp/properties/schemaSource", "file:./test.xsd");
            //docBuilderFactory.setValidating(true);
            //docBuilderFactory.setAttribute("http://java.sun.com/xml/jaxp/properties/schemaLanguage", "http://www.w3.org/2001/XMLSchema");

            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();

            layersNode = checkAndTransform(docBuilder.parse(xmlFile));
            
        } catch (ParserConfigurationException pce) {
            log.error(pce.getMessage());
            pce.printStackTrace();
        } catch (IOException ei) {
            throw new ConfigurationException("Error parsing file");
        } catch (SAXException saxe) {
            log.error(saxe.getMessage());
        }
        return layersNode;
    }
    
    private org.w3c.dom.Node checkAndTransform(org.w3c.dom.Document doc)
            throws ConfigurationException {
        Node rootNode = doc.getDocumentElement();

        if (rootNode.getNodeName().equals("gwcConfiguration")) {
            // This file should be okay
            rootNode = doc.getDocumentElement();
        } else {
            log.info("The configuration file is of the old type, trying to convert.");

            DOMResult result = new DOMResult();
            Transformer transformer;
            
            InputStream is = XMLConfiguration.class.getResourceAsStream("geowebcache_pre10.xsl");
            
            try {
                transformer = TransformerFactory.newInstance().newTransformer(new StreamSource(is));
                transformer.transform(new DOMSource(rootNode), result);
            } catch (TransformerConfigurationException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (TransformerFactoryConfigurationError e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (TransformerException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
            rootNode = result.getNode().getFirstChild();
        }

        if (!rootNode.getNodeName().equals("gwcConfiguration")) {
            log.error("Unable to parse file, expected gwcConfiguration at root after transform.");
            throw new ConfigurationException("Unable to parse after transform.");
        } else {
            // Perform validation
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            InputStream is = XMLConfiguration.class.getResourceAsStream("geowebcache.xsd");

            // parsing the schema file
            try {
                Schema schema = factory.newSchema(new StreamSource(is));
                Validator validator = schema.newValidator();
                validator.validate(new DOMSource(rootNode));
                log.info("Configuration file validated fine.");
            } catch (SAXException e) {
                log.error(e.getMessage());
                log.info("Will try to use configuration anyway.");
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
 
            Node levelOne = rootNode.getFirstChild();
            while (levelOne != null) {
                if (levelOne.getNodeName().equals("layers")) {
                    return levelOne;
                }

                levelOne = levelOne.getNextSibling();
            }
        }

        return null;

    }
    
    public void determineConfigDirH() {
        String baseDir = context.getServletContext().getRealPath("");
        
        /*
         * Try 
         * 1) absolute path (specified in bean defn)
         * 2) relative path (specified in bean defn)
         * 3) environment variables
         * 4) standard paths
         */
        if (absPath != null) {
            configH = new File(absPath);
        } else if (relPath != null) {
            configH = new File(baseDir + relPath);
            log.info("Configuration directory set to: "
                    + configH.getAbsolutePath());
        } else if (relPath == null) {
            // Try env variables
            File tmpPath = null;

            if (fileCache != null) {
                try {
                    // Careful, this appends a separator
                    tmpPath = new File(fileCache.getDefaultPrefix(CONFIGURATION_FILE_NAME));

                    if (tmpPath.exists() && tmpPath.canRead()) {
                        String filePath = tmpPath.getAbsolutePath();
                        configH = new File(filePath.substring(0, 
                                filePath.length()
                                - CONFIGURATION_FILE_NAME.length() - 1));
                    }
                } catch (CacheException ce) {
                    // Ignore
                }
            }

            // Finally, try "standard" paths if we have to.
            if (configH == null) {
                for (int i = 0; i < CONFIGURATION_REL_PATHS.length; i++) {
                    relPath = CONFIGURATION_REL_PATHS[i];
                    if (File.separator.equals("\\")) {
                        relPath = relPath.replace("/", "\\");
                    }

                    tmpPath = new File(baseDir + relPath + File.separator
                            + CONFIGURATION_FILE_NAME);

                    if (tmpPath.exists() && tmpPath.canRead()) {
                        log.info("No configuration directory was specified, using "
                                        + tmpPath.getAbsolutePath());
                        configH = new File(baseDir + relPath);
                    }
                }
            }
        }
       
        if(configH == null) {
            log.error("Failed to find geowebcache.xml");
        } else {
            log.info("Configuration directory set to: "+ configH.getAbsolutePath());
        
            if (!configH.exists() || !configH.canRead()) {
                log.error("Configuration file cannot be read or does not exist!");
            }
        }
    }

    public String getIdentifier() throws GeoWebCacheException {
        if(configH == null) {
            this.findConfFile();           
        }
        
        // Try again
        if(configH != null) {
            return configH.getAbsolutePath();
        }
        
        return null;
    }

    public void setRelativePath(String relPath) {
        this.relPath = relPath;
    }

    public void setAbsolutePath(String absPath) {
        this.absPath = absPath;
    }
    
    public void setFileCache(FileCache fileCache) {
        this.fileCache = fileCache;
    }

    public void setApplicationContext(ApplicationContext arg0)
            throws BeansException {
        context = (WebApplicationContext) arg0;
    }

    public CacheFactory getCacheFactory() {
        return this.cacheFactory;
    }

}
