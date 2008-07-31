package org.geowebcache.layer;

import java.util.Map;
import java.util.StringTokenizer;
import java.util.Iterator;
import java.io.IOException;
import java.io.Reader;
import java.io.CharArrayReader;

import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.data.Form;
import org.restlet.resource.Representation;
import org.restlet.resource.DomRepresentation;
import org.restlet.resource.Resource;
import org.restlet.resource.StringRepresentation;
import org.restlet.resource.OutputRepresentation;
import org.restlet.resource.Variant;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.thoughtworks.xstream.*;
import com.thoughtworks.xstream.io.json.JsonHierarchicalStreamDriver;

import org.json.JSONObject;
import org.json.JSONException;
import org.restlet.ext.json.JsonRepresentation;

import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.mime.MimeType;
import org.geowebcache.mime.ErrorMime;
import org.geowebcache.mime.MimeException;
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
        XStream xs = new XStream();
        xs.alias("layer", TileLayer.class);
        xs.alias("wmslayer", WMSLayer.class);
        xs.aliasField("layer-name", TileLayer.class, "name");
        xs.alias("grid", Grid.class);
        xs.alias("format", String.class);
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
        XStream xs = new XStream();
        xs.alias("layer", TileLayer.class);
        xs.alias("wmslayer", WMSLayer.class);
        xs.aliasField("layer-name", TileLayer.class, "name");
        xs.alias("grid", Grid.class);
        xs.alias("format", String.class);
        String xmlText = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
        xmlText += xs.toXML(layer);
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
            XStream xs = new XStream(new JsonHierarchicalStreamDriver());
            xs.alias("layer", TileLayer.class);
            xs.alias("wmslayer", WMSLayer.class);
            xs.aliasField("layer-name", TileLayer.class, "name");
            xs.alias("grid", Grid.class);
            xs.alias("format", String.class);

            JSONObject obj = new JSONObject(xs.toXML(layer));
            rep = new JsonRepresentation(obj);
        } catch (JSONException jse) {
            jse.printStackTrace();
        }
        return rep;
    }

    /**
     * handles the PUT requests to a tileLayer
     * 
     */
    @Override
    public void put(Representation entity) {
        if (entity.getMediaType().equals(MediaType.APPLICATION_WWW_FORM)) {
            log.info("Received PUT request from  "
                    + getRequest().getHostRef().getHostIdentifier());
            log.trace("Received PUT request from  "
                    + getRequest().getHostRef().getHostIdentifier());
            if (currentLayer == null) {
                // this layer does not exist and cannot PUT to nonexisting layer
                log.info("PUT request not allowed for non-existing resource. ");
                getResponse().setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
            } else {
                // layer exists, so able to overwrite pre-existing configuration
                Form form = new Form(entity);
                String name = form.getFirstValue("name");
                String type = form.getFirstValue("type");

                TileLayer newLayer = null;
                if (!type.equalsIgnoreCase("wms")) {
                    newLayer = new TileLayer(name, RESTDispatcher.getConfig().getCacheFactory());
                } else {
                	try{
                    newLayer = new WMSLayer(name, RESTDispatcher.getConfig().getCacheFactory());
                	} catch(GeoWebCacheException e){
                		e.printStackTrace();
                	}
                }

                String formats = form.getFirstValue("formats");
                StringTokenizer formatTokenizer = new StringTokenizer(formats,
                        ";");
                while (formatTokenizer.hasMoreTokens()) {
                    newLayer
                            .addFormat(formatTokenizer.nextToken());
                }

                String grids = form.getFirstValue("grids");
                StringTokenizer gridst = new StringTokenizer(grids, ";");
                while (gridst.hasMoreTokens()) {
                    String nextGrid = gridst.nextToken();
                    Grid grid = new Grid();
                    grid
                            .setBounds(nextGrid.substring(0, nextGrid
                                    .indexOf(":")));
                    grid.setGridBounds(nextGrid
                            .substring(nextGrid.indexOf(":") + 1));
                    newLayer.addGrid(grid);

                }

                String projections = form.getFirstValue("projections");
                StringTokenizer projst = new StringTokenizer(projections, ";");
                while (projst.hasMoreTokens()) {
                    int count = 0;
                    try {
                        newLayer.getGrids().get(count).setProjection(
                                new SRS(projst.nextToken()));
                        count++;
                    } catch (GeoWebCacheException gwce) {
                    }
                }

                if (type.equalsIgnoreCase("wms")) {
                    WMSLayer newWMSLayer = (WMSLayer) newLayer;
                    newWMSLayer.setWMSurl(form.getFirstValue("wmsURL"));
                    newWMSLayer.addMetaWidthHeight(Integer.parseInt(form
                            .getFirstValue("mWidth")), Integer.parseInt(form
                            .getFirstValue("mHeight")));
                    
                    newWMSLayer.setErrorMime(form.getFirstValue("errormime"));
                    
                    newWMSLayer.setWidthHeight(Integer.parseInt(form
                            .getFirstValue("width")), Integer.parseInt(form
                            .getFirstValue("height")));
                    newWMSLayer.setVersion(form.getFirstValue("ver"));
                    newWMSLayer.setTiled(form.getFirstValue("tiled")
                            .equalsIgnoreCase("true"));
                    newWMSLayer.setTransparent(form.getFirstValue("transp")
                            .equalsIgnoreCase("true"));
                    newWMSLayer.setDebugHeaders(form.getFirstValue("debughead")
                            .equalsIgnoreCase("true"));

                    if (RESTDispatcher.getConfig().modifyLayer(
                            currentLayer.getName(), newWMSLayer)) {
                        log.info("Added layer : " + newWMSLayer.getName());
                        log.info("Handled successfully");
                        log.trace("Added layer : " + newWMSLayer.getName());
                        log.trace("Handled successfully");
                        getResponse().setStatus(Status.SUCCESS_OK);
                    } else
                        getResponse().setStatus(
                                Status.CLIENT_ERROR_FAILED_DEPENDENCY);

                } else {
                    if (RESTDispatcher.getConfig().modifyLayer(
                            currentLayer.getName(), newLayer)) {
                        log.info("Added layer : " + newLayer.getName());
                        log.info("Handled successfully");
                        log.trace("Addedlayer : " + newLayer.getName());
                        log.trace("Handled successfully");
                        getResponse().setStatus(Status.SUCCESS_OK);
                    } else
                        getResponse().setStatus(
                                Status.CLIENT_ERROR_FAILED_DEPENDENCY);

                }
            }
        }
    }

    /**
     * handles the POST requests to a tileLayer
     * 
     */

    @Override
    public void post(Representation entity) {
        if (entity.getMediaType().equals(MediaType.APPLICATION_WWW_FORM)) {
            log.info("Received POST request from  "
                    + getRequest().getHostRef().getHostIdentifier());
            log.trace("Received POST request from  "
                    + getRequest().getHostRef().getHostIdentifier());
            if (currentLayer == null) {
                // this layer does not exist so create a new one
                Form form = new Form(entity);
                String name = form.getFirstValue("name");
                String type = form.getFirstValue("type");

                TileLayer newLayer = null;
                if (!type.equalsIgnoreCase("wms")) {
                    newLayer = new TileLayer(name, RESTDispatcher.getConfig().getCacheFactory());
                } else {
                	try{
                        newLayer = new WMSLayer(name, RESTDispatcher.getConfig().getCacheFactory());
                    	} catch(GeoWebCacheException e){
                    		e.printStackTrace();
                    	}
                }

                String formats = form.getFirstValue("formats");
                StringTokenizer formatTokenizer = new StringTokenizer(formats,
                        ";");
                while (formatTokenizer.hasMoreTokens()) {
                    newLayer
                            .addFormat(formatTokenizer.nextToken());
                }

                String grids = form.getFirstValue("grids");
                StringTokenizer gridst = new StringTokenizer(grids, ";");
                while (gridst.hasMoreTokens()) {
                    String nextGrid = gridst.nextToken();
                    Grid grid = new Grid();
                    grid
                            .setBounds(nextGrid.substring(0, nextGrid
                                    .indexOf(":")));
                    grid.setGridBounds(nextGrid
                            .substring(nextGrid.indexOf(":") + 1));
                    newLayer.addGrid(grid);

                }

                String projections = form.getFirstValue("projections");
                StringTokenizer projst = new StringTokenizer(projections, ";");
                while (projst.hasMoreTokens()) {
                    int count = 0;
                    try {
                        newLayer.getGrids().get(count).setProjection(
                                new SRS(projst.nextToken()));
                        count++;
                    } catch (GeoWebCacheException gwce) {
                    }
                }

                if (type.equalsIgnoreCase("wms")) {
                    WMSLayer newWMSLayer = (WMSLayer) newLayer;
                    newWMSLayer.setWMSurl(form.getFirstValue("wmsURL"));
                    newWMSLayer.addMetaWidthHeight(Integer.parseInt(form
                            .getFirstValue("mWidth")), Integer.parseInt(form
                            .getFirstValue("mHeight")));
                    newWMSLayer.setErrorMime(form.getFirstValue("errormime"));
                    newWMSLayer.setWidthHeight(Integer.parseInt(form
                            .getFirstValue("width")), Integer.parseInt(form
                            .getFirstValue("height")));
                    newWMSLayer.setVersion(form.getFirstValue("ver"));
                    newWMSLayer.setTiled(form.getFirstValue("tiled")
                            .equalsIgnoreCase("true"));
                    newWMSLayer.setTransparent(form.getFirstValue("transp")
                            .equalsIgnoreCase("true"));
                    newWMSLayer.setDebugHeaders(form.getFirstValue("debughead")
                            .equalsIgnoreCase("true"));

                    if (RESTDispatcher.getConfig().createLayer(newWMSLayer)) {
                        log.info("Added layer : " + newWMSLayer.getName());
                        log.info("Handled successfully");
                        log.trace("Added layer : " + newWMSLayer.getName());
                        log.trace("Handled successfully");
                        getResponse().setStatus(Status.SUCCESS_OK);
                    } else
                        getResponse().setStatus(
                                Status.CLIENT_ERROR_FAILED_DEPENDENCY);

                } else {
                    if (RESTDispatcher.getConfig().createLayer(newLayer)) {
                        log.info("Added layer : " + newLayer.getName());
                        log.info("Handled successfully");
                        log.trace("Addedlayer : " + newLayer.getName());
                        log.trace("Handled successfully");
                        getResponse().setStatus(Status.SUCCESS_OK);
                    } else
                        getResponse().setStatus(
                                Status.CLIENT_ERROR_FAILED_DEPENDENCY);

                }

            } else {
                // layer exists, so cannot overwrite using POST
                log.info("POST request not allowed for resource "
                        + currentLayer.getName());
                getResponse().setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
            }
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
        log.trace("Received DELETE request for resource "
                + currentLayer.getName());
        RESTDispatcher.getAllLayers().remove(currentLayer.getName());
        if (RESTDispatcher.getConfig().deleteLayer(currentLayer.getName())) {
            log.info("Deleted layer : " + currentLayer.getName());
            log.trace("Deleted layer : " + currentLayer.getName());
            log.info("Handled successfully");
            log.trace("Handled successfully");
            getResponse().setStatus(Status.SUCCESS_OK);
        } else
            getResponse().setStatus(Status.CLIENT_ERROR_FAILED_DEPENDENCY);
    }

    public boolean allowDelete() {
        return true;
    }

    public boolean allowPut() {
        return true;
    }

    public boolean allowPost() {
        return true;
    }
}

