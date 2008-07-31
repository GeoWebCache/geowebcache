
package org.geowebcache.util;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.web.context.WebApplicationContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.cache.CacheFactory;
import org.geowebcache.cache.*;
import org.geowebcache.layer.Grid;
import org.geowebcache.mime.MimeType;

import com.thoughtworks.xstream.*;
import com.thoughtworks.xstream.io.xml.DomReader;
import com.thoughtworks.xstream.io.xml.DomWriter;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class XMLConfiguration implements Configuration, ApplicationContextAware {
    private static Log log = LogFactory
            .getLog(org.geowebcache.util.XMLConfiguration.class);

    private WebApplicationContext context;

    private CacheFactory cacheFactory = null;

    private String absPath = null;

    private String relPath = null;

    private File configDirH = null;

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

    /**
     * Method responsible for loading XML configuration file
     * 
     */
    public Map<String, TileLayer> getTileLayers() throws GeoWebCacheException {
        if (configDirH == null) {
            determineConfigDirH();
        }

        File propFile = null;
        if (configDirH != null) {
            // Find the property file
            propFile = findPropFile(configDirH);
        }

        if (propFile != null) {
            log
                    .trace("Found  property file in "
                            + configDirH.getAbsolutePath());
        } else {
            log.error("Found no property file in "
                    + configDirH.getAbsolutePath());
            return null;
        }

        HashMap<String, TileLayer> layers = new HashMap<String, TileLayer>();

        // load configurations into Document
        Document docc = loadIntoDocument(propFile);
        Element root = docc.getDocumentElement();
        NodeList allLayerNodes = root.getChildNodes();

        XStream xs = getConfiguredXStream(new XStream());
        
        TileLayer result = null;
        for (int i = 0; i < allLayerNodes.getLength(); i++) {
            if (allLayerNodes.item(i) instanceof Element) {
                Element e = (Element) allLayerNodes.item(i);
                if (e.getTagName().equalsIgnoreCase("wmslayer")){
                    result = (WMSLayer) xs.unmarshal(new DomReader(
                            (Element) allLayerNodes.item(i)));
                }
                else {
                    result = (TileLayer) xs.unmarshal(new DomReader(
                            (Element) allLayerNodes.item(i)));
                }
                result.setInitCacheFactory( this.getCacheFactory() );
                System.out.println(result.getName());
                layers.put(result.getName(), result);
            }
        }

        return layers;
    }
    
    public XStream getConfiguredXStream(XStream xstream){
    	XStream xs = xstream;
    	
    	xs.alias("layer", TileLayer.class);
        xs.alias("wmslayer", WMSLayer.class);
        xs.aliasField("layer-name", TileLayer.class, "name");
        xs.alias("grid", Grid.class);
        xs.alias("format", String.class);
    	
        //omits for the TileLayer.class
    	xs.omitField(log.getClass(), "log");
    	xs.omitField(Boolean.class, "isInitialized");
    	xs.omitField((new Condition[1]).getClass(), "gridLocConds");
    	xs.omitField(Lock.class, "layerLock");
    	xs.omitField(CacheFactory.class, "initCacheFactory");
    	xs.omitField(Cache.class, "cache");
    	xs.omitField(CacheKey.class, "cacheKey");
    	xs.omitField(String.class, "cachePrefix");
    	xs.omitField(ArrayList.class, "formats");
    	
    	//omits for the WMSLayer.class
    	xs.omitField(int.class, "zoomStart");
    	xs.omitField(int.class, "zoomStop");
    	xs.omitField((new org.geowebcache.util.wms.GridCalculator[1]).getClass(), "gridCalc");
    	xs.omitField(int.class, "CACHE_NEVER");
    	xs.omitField(int.class, "CACHE_VALUE_UNSET");
    	xs.omitField(int.class, "CACHE_NEVER_EXPIRE");
    	xs.omitField(int.class, "CACHE_USE_WMS_BACKEND_VALUE");
    	xs.omitField(boolean.class, "saveExpirationHeaders");
    	xs.omitField(long.class, "expireClients");
    	xs.omitField(long.class, "expireCache");
    	xs.omitField(org.geowebcache.service.wms.WMSParameters.class, "wmsparams");
    	xs.omitField(String.class,"request");
    	xs.omitField(String.class,"bgcolor");
    	xs.omitField(String.class,"palette");
    	xs.omitField(String.class,"vendorParameters");
    	xs.omitField(String.class,"wmsStyles");
    	xs.omitField((new String[1]).getClass(),"wmsURL");
    	xs.omitField(int.class,"curWmsURL");
    	xs.omitField(Lock.class, "layerLock");
    	xs.omitField(boolean.class, "layerLocked");
    	xs.omitField(Condition.class, "layerLockedCond");
    	xs.omitField((new HashMap<org.geowebcache.layer.GridLocObj, Boolean>()).getClass(), "procQueue");
    	xs.omitField(Integer.class, "cacheLockWait");
    	
    	return xs;
    }
    
    
    
    /**
     * Method responsible for writing out TileLayer objects
     * 
     * @param tl
     *            a new TileLayer object to be serialized to XML
     * @return true if operation succeeded, false otherwise
     */

    public boolean createLayer(TileLayer tl) {
        if (configDirH == null) {
            determineConfigDirH();
        }

        File propFile = null;
        if (configDirH != null) {
            propFile = findPropFile(configDirH);
        }

        if (propFile != null) {
            log.trace("Found configuration file in "
                    + configDirH.getAbsolutePath());
        } else {
            log.error("Found no configuration file in "
                    + configDirH.getAbsolutePath());
            return false;
        }

        // load configurations into Document
        Document docc = loadIntoDocument(propFile);
        Element root = docc.getDocumentElement();

        // create the XStream for serializing tileLayers to XML
        XStream xs = new XStream();
        xs.alias("layer", TileLayer.class);
        xs.alias("wmslayer", WMSLayer.class);
        xs.aliasField("layer-name", TileLayer.class, "name");
        xs.alias("grid", Grid.class);
        xs.alias("format", String.class);
        // sent to XML
        xs.marshal(tl, new DomWriter((Element) root));

        try {
            DOMSource source = new DOMSource(docc);
            StreamResult result = new StreamResult(propFile);

            // write the DOM to the file
            Transformer xformer = TransformerFactory.newInstance()
                    .newTransformer();
            xformer.transform(source, result);
        } catch (TransformerConfigurationException e) {
        } catch (TransformerException e) {
        }

        return true;

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

    public boolean modifyLayer(String currentLayer, TileLayer tl) {

        return deleteLayer(currentLayer) && createLayer(tl);

    }

    /**
     * Method responsible for deleting existing layers
     * 
     * @param layerName
     *            the name of the layer to be deleted
     * @returntrue if operation succeeded, false otherwise
     */
    public boolean deleteLayer(String layerName) {
        if (configDirH == null) {
            determineConfigDirH();
        }

        File propFile = null;
        if (configDirH != null) {
            // Find the property file and process each one into a TileLayer
            propFile = findPropFile(configDirH);
        }

        if (propFile != null) {
            log.trace("Found configuration file in "
                    + configDirH.getAbsolutePath());
        } else {
            log.error("Found no configuration file in "
                    + configDirH.getAbsolutePath());
            return false;
        }

        // load configurations into Document
        Document docc = loadIntoDocument(propFile);
        Element root = docc.getDocumentElement();
        // find the layer to delete This assumes that ALL layer names are
        // distinct
        NodeList nl = docc.getElementsByTagName("layer-name");

        if (nl.getLength() == 0)
            return false;
        else {
            Element toDelete = null;
            for (int i = 0; i < nl.getLength(); i++) {
                Node tmp = (Node) nl.item(i).getFirstChild();
                if (tmp.getNodeValue().equals(layerName)) {
                    toDelete = (Element) nl.item(i);
                    break;
                } else
                    continue;
            }

            root.removeChild(toDelete.getParentNode());

            try {
                DOMSource source = new DOMSource(docc);
                StreamResult result = new StreamResult(propFile);

                // write the DOM to the file
                Transformer xformer = TransformerFactory.newInstance()
                        .newTransformer();
                xformer.transform(source, result);
            } catch (TransformerConfigurationException e) {
            } catch (TransformerException e) {
            }
            return true;
        }
    }

    /**
     * Method responsible for loading xml configuration file and parsing it into
     * a W3C DOM Document
     * 
     * @param file
     *            the file contaning the layer configurations
     * @return W3C DOM Document
     */
    private org.w3c.dom.Document loadIntoDocument(File file) {
        org.w3c.dom.Document document = null;
        try {
            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory
                    .newInstance();
            docBuilderFactory.setNamespaceAware(true);
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            document = docBuilder.parse(file);
        } catch (ParserConfigurationException pce) {
            System.err.println(pce.getMessage());
            pce.printStackTrace();
        } catch (IOException ei) {
            System.err
                    .println("Exception occured while creating documet from file");
            ei.printStackTrace(System.err);
        } catch (SAXException saxe) {
            System.err.println(saxe.getMessage());
            saxe.printStackTrace();
        }
        return document;
    }

    /**
     * 
     * Method finds the layer configuration XML file
     * 
     * @param configDirH
     * @return
     */
    private File findPropFile(File configDirH) {
        FilenameFilter select = new ExtensionFileLister("layer", "xml");
        File[] f = configDirH.listFiles(select);

        if (f == null) {
            log.error("Unable to find configuration file in "
                    + this.configDirH.getAbsolutePath() + " !! ");
        }

        return f[0];
    }

    public CacheFactory getCacheFactory(){
    	return this.cacheFactory;
    }
    
    public void determineConfigDirH() {
        if (absPath != null) {
            configDirH = new File(absPath);
            return;
        }

        /* Only keep going for relative directory */
        if (relPath == null) {
            log.warn("No configuration directory was specified,"
                    + " reverting to default: ");
            relPath = "";
        } else {
            if (File.separator.equals("\\")
                    && relPath.equals("/WEB-INF/classes")) {
                log
                        .warn("You seem to be running on windows, changing search path to \\WEB-INF\\classes");
                relPath = "\\WEB-INF\\classes";
            }
        }

        String baseDir = context.getServletContext().getRealPath("");

        configDirH = new File(baseDir + relPath);

        log.info("Configuration directory set to: "
                + configDirH.getAbsolutePath());

        if (!configDirH.exists() || !configDirH.canRead()) {
            log.error(configDirH.getAbsoluteFile()
                    + " cannot be read or does not exist!");
        }
    }

    public String getIdentifier() {
        return configDirH.getAbsolutePath();
    }

    public void setRelativePath(String relPath) {
        this.relPath = relPath;
    }

    public void setAbsolutePath(String absPath) {
        this.absPath = absPath;
    }

    public void setApplicationContext(ApplicationContext arg0)
            throws BeansException {
        context = (WebApplicationContext) arg0;
    }

}
