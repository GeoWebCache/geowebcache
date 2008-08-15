package org.geowebcache.layer;

import java.util.Map;
import java.util.Iterator;
import java.io.IOException;
import java.io.Reader;
import java.io.CharArrayReader;
import java.io.StringReader;
import java.io.StringWriter;

import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.DomRepresentation;
import org.restlet.resource.Resource;
import org.restlet.resource.StringRepresentation;
import org.restlet.resource.Variant;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.thoughtworks.xstream.*;
import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.xml.DomDriver;
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;
import com.thoughtworks.xstream.io.copy.HierarchicalStreamCopier;
import com.thoughtworks.xstream.io.json.JettisonMappedXmlDriver;
import com.thoughtworks.xstream.io.json.JsonHierarchicalStreamDriver;

import org.json.JSONObject;
import org.json.JSONException;
import org.restlet.ext.json.JsonRepresentation;

import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.RESTDispatcher;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * This is the TileLayer resource class required by the REST interface
 */
public class TileLayerResource extends Resource {
    private TileLayer currentLayer = null;

    private static Log log = LogFactory
            .getLog(org.geowebcache.layer.TileLayerResource.class);

    /**
     * Construct a TileLayer Resource set the return representation of the
     * tileLayer based on the requested type
     * 
     * @param context
     * @param request
     * @param response
     */
    public TileLayerResource(Context context, Request request, Response response) {
        super(context, request, response);
        Map<String, TileLayer> myallLayers = RESTDispatcher.getAllLayers();
        String remainingString = (String) request.getResourceRef()
                .getRemainingPart();
        String myReqLayerName = null;
        if (remainingString == null) {
            // request was made to ...rest/layers/
            getVariants().add(new Variant(MediaType.TEXT_XML));
            currentLayer = null;
        } else if (remainingString.indexOf('.') == -1) {
            // no extension provided, default to xml
            getVariants().add(new Variant(MediaType.TEXT_XML));
            myReqLayerName = remainingString;
            currentLayer = myallLayers.get(myReqLayerName);
        } else if (remainingString.indexOf('.') != -1) {
            myReqLayerName = remainingString.substring(0, remainingString
                    .indexOf('.'));
            String format = remainingString.substring(remainingString
                    .indexOf('.') + 1);
            if (format.equals("xml")) {
                getVariants().add(new Variant(MediaType.TEXT_XML));
            } else if (format.equals("json")) {
                getVariants().add(new Variant(MediaType.APPLICATION_JSON));
            } else if (format.equals("text")) {
                getVariants().add(new Variant(MediaType.TEXT_PLAIN));
            }
            currentLayer = myallLayers.get(myReqLayerName);
        }

    }

    /**
     * 
     * @return the respective representation
     */
    public Representation getRepresentation(Variant variant) {
        Representation result = null;
        TileLayer tl = currentLayer;
        if (variant.getMediaType().equals(MediaType.TEXT_PLAIN)) {
            // create a text representation of the current layer
            result = getStringRepresentation(currentLayer);
        } else if (tl == null
                && variant.getMediaType().equals(MediaType.TEXT_XML)) {
            // create a dom representation that will list of all available layers
            result = getDomRepresentationAsListOfLayers();
        } else if (tl != null && variant.getMediaType().equals(MediaType.TEXT_XML)) {
            // create xml representation of the currentLayer
            result = getXMLRepresentation(currentLayer);
        } else if (variant.getMediaType().equals(MediaType.APPLICATION_JSON)) {
            // create JSONRepresentation of the currentLayer
            result = getJsonRepresentation(currentLayer);
        }

        return result;
    }
    /**
     * Returns a DomRepresentation of all available layers
     * @return DomRepresentation
     */
    public DomRepresentation getDomRepresentationAsListOfLayers() {
        XStream xs = RESTDispatcher.getConfig().getConfiguredXStream(new XStream());
        String xmlText = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
        xmlText +="<layers>";
        for (Iterator iter = RESTDispatcher.getAllLayers().entrySet()
                .iterator(); iter.hasNext();) {
            Map.Entry entry = (Map.Entry) iter.next();
            TileLayer layer = (TileLayer) entry.getValue();
            xmlText += xs.toXML(layer);
        }
        xmlText += "</layers>";
        Document doc = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory
                    .newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Reader reader = new CharArrayReader(xmlText.toCharArray());
            doc = builder.parse(new InputSource(reader));

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
        return new DomRepresentation(MediaType.TEXT_XML, doc);
    }

    /**
     * Returns a StringRepresentation of the layer ONLY FOR TESTING PURPOSES
     * 
     * @param layer
     * @return
     */
    public StringRepresentation getStringRepresentation(TileLayer layer) {
        StringBuilder sb = new StringBuilder();
        sb.append("Layer details\n");
        sb.append("_____________\n");
        sb.append("Name: ").append(layer.getName()).append('\n');
        return new StringRepresentation(sb);
    }

    /**
     * Returns a XMLRepresentation of the layer
     * 
     * @param layer
     * @return
     */
    public DomRepresentation getXMLRepresentation(TileLayer layer) {
        XStream xs = RESTDispatcher.getConfig().getConfiguredXStream(new XStream());
        String xmlText = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
        xmlText += xs.toXML(layer);
        System.out.println(xmlText);
        Document doc = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory
                    .newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Reader reader = new CharArrayReader(xmlText.toCharArray());
            doc = builder.parse(new InputSource(reader));

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
        return new DomRepresentation(MediaType.TEXT_XML, doc);
    }

    /**
     * Return s a JsonRepresentation of the layer
     * 
     * @param layer
     * @return
     */
    public JsonRepresentation getJsonRepresentation(TileLayer layer) {
        JsonRepresentation rep = null;
        try {            
            XStream xs = RESTDispatcher.getConfig().getConfiguredXStream(new XStream(new JsonHierarchicalStreamDriver()));
            JSONObject obj = new JSONObject(xs.toXML(layer));
            rep = new JsonRepresentation(obj);
        } catch (JSONException jse) {
            jse.printStackTrace();
        }
        return rep;
    }

    /**
     * handles the POST requests to a tileLayer
     * 
     */

    @Override
    public void post(Representation entity) {
        log.info("Received POST request from  "
                + getRequest().getHostRef().getHostIdentifier());
        try{
            String xmlText = entity.getText();      
            XStream xs = RESTDispatcher.getConfig().getConfiguredXStream(new XStream(new DomDriver()));
            TileLayer tileLayer = null;
            if(entity.getMediaType().equals(MediaType.APPLICATION_XML)){
            	tileLayer = (WMSLayer) xs.fromXML(xmlText);
            }
            
            /**
             * deserializing a json string is more complicated. XStream does not natively
             * support it. Rather it uses a JettisonMappedXmlDriver to convert to intermediate xml
             * and then deserializes that into the desired object. At this time, there is a known issue 
             * with the Jettison driver involving elements that come after an array in the json string. 
             * 
             * http://jira.codehaus.org/browse/JETTISON-48
             * 
             * The code below is a hack: it treats the json string as text, then converts it to the intermediate
             * xml and then deserializes that into the tileLayer object. 
             * 
             * 
             */
            else if(entity.getMediaType().equals(MediaType.APPLICATION_JSON)){
            	HierarchicalStreamDriver driver = new JettisonMappedXmlDriver();
                StringReader reader = new StringReader(xmlText);
                HierarchicalStreamReader hsr = driver.createReader(reader);
                StringWriter writer = new StringWriter();
                new HierarchicalStreamCopier().copy(hsr, new PrettyPrintWriter(writer));
                writer.close(); 
                
                XStream x = new XStream(new DomDriver());
                x.alias("wmslayer", WMSLayer.class);
                x.aliasField("layer-name", TileLayer.class, "name");
                x.alias("grid", Grid.class);
                
                
                tileLayer = (WMSLayer) x.fromXML(writer.toString());
            }
            
            //the layer we are posting to is null, so we need to create a new one
            if(currentLayer == null){
                boolean tryCreate = false;
                try {
                    tryCreate = RESTDispatcher.getConfig().createLayer(tileLayer);
                } catch (GeoWebCacheException gwce) {
                    // Not much we can do
                    log.error(gwce.getMessage());
                }
                if (tryCreate) {
                    log.info("Added layer : " + tileLayer.getName());
                    getResponse().setStatus(Status.SUCCESS_OK);
                } else
                    getResponse().setStatus(
                            Status.CLIENT_ERROR_FAILED_DEPENDENCY);
                
            } else {
                //the layer we are posting to is not null, so we are trying to modify it
                boolean trySave = false;
                try {
                    trySave = RESTDispatcher.getConfig().modifyLayer(currentLayer.getName(), tileLayer);
                } catch (GeoWebCacheException gwce) {
                    // Not much we can do
                    log.error(gwce.getMessage());
                }
                if (trySave) {
                    log.info("Overwrote layer : " + currentLayer.getName() + " with new layer : " + tileLayer.getName());
                    getResponse().setStatus(Status.SUCCESS_OK);
                } else {
                    getResponse().setStatus(Status.CLIENT_ERROR_FAILED_DEPENDENCY);
                }
            }
            
        } catch(IOException ioex){
            ioex.printStackTrace();
        }
    }

    /**
     * handles the DELETE requests to a tileLayer
     * 
     */

    @Override
    public void delete() {

        // if this layer is null -> resource is not available
        if (currentLayer == null) {
            log.info("DELETE Request for non-existing layer from "
                    + getRequest().getHostRef().getHostIdentifier());
            log.trace("DELETE Request for non-existing layer from "
                    + getRequest().getHostRef().getHostIdentifier());
            getResponse().setStatus(Status.CLIENT_ERROR_NOT_FOUND);
            return;
        }

        log.info("Received DELETE request for resource "
                + currentLayer.getName());
        RESTDispatcher.getAllLayers().remove(currentLayer.getName());
        if (RESTDispatcher.getConfig().deleteLayer(currentLayer.getName())) {
            log.info("Deleted layer : " + currentLayer.getName());
            getResponse().setStatus(Status.SUCCESS_OK);
        } else
            getResponse().setStatus(Status.CLIENT_ERROR_FAILED_DEPENDENCY);
    }

    public boolean allowDelete() {
        return true;
    }

    public boolean allowPut() {
        return false;
    }

    public boolean allowPost() {
        return true;
    }
}
