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
 * @author Marius Suta / The Open Planning Project 2008 
 * @author Arne Kepp / The Open Planning Project 2009 
 */
package org.geowebcache.rest.layers;

import java.io.CharArrayReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.rest.GWCRestlet;
import org.geowebcache.rest.RestletException;
import org.geowebcache.util.XMLConfiguration;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.resource.DomRepresentation;
import org.restlet.resource.Representation;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.copy.HierarchicalStreamCopier;
import com.thoughtworks.xstream.io.json.JettisonMappedXmlDriver;
import com.thoughtworks.xstream.io.json.JsonHierarchicalStreamDriver;
import com.thoughtworks.xstream.io.xml.DomDriver;
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;

/**
 * This is the TileLayer resource class required by the REST interface
 */
public class TileLayerRestlet extends GWCRestlet {
    private static Log log = 
        LogFactory.getLog(org.geowebcache.rest.layers.TileLayerRestlet.class);
    
    private XMLConfiguration xmlConfig;
    
    private TileLayerDispatcher layerDispatcher;
    
    public void handle(Request request, Response response) {
        Method met = request.getMethod();
        try {
            if(met.equals(Method.GET)) {
                doGet(request, response);
            } else {
                // These modify layers, so we reload afterwards
                if (met.equals(Method.POST)) {
                    doPost(request, response);
                } else if (met.equals(Method.PUT)) {
                    doPut(request, response);
                } else if (met.equals(Method.DELETE)) {
                    doDelete(request, response);
                } else {
                    throw new RestletException("Method not allowed",
                        Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
                }
                
                layerDispatcher.reInit();
            }
        } catch (RestletException re) {
            response.setEntity(re.getRepresentation());
            response.setStatus(re.getStatus());
        } catch (Exception e) {
            // Either GeoWebCacheException or IOException
            response.setEntity(e.getMessage(), MediaType.TEXT_PLAIN);
            response.setStatus(Status.SERVER_ERROR_INTERNAL);
        }
    }
    
    /**
     * GET outputs an existing layer
     * 
     * @param req
     * @param resp
     * @throws RestletException
     */
    protected void doGet(Request req, Response resp) throws RestletException {
        String layerName = (String) req.getAttributes().get("layer");
        String formatExtension = (String) req.getAttributes().get("extension");
        resp.setEntity(doGetInternal(layerName, formatExtension));
    }
    
    /** 
     * We separate out the internal to make unit testing easier
     * 
     * @param layerName
     * @param formatExtension
     * @return
     * @throws RestletException
     */
    protected Representation doGetInternal(String layerName, String formatExtension) 
    throws RestletException {
        TileLayer tl = findTileLayer(layerName, layerDispatcher);
        
        if(formatExtension.equalsIgnoreCase("xml")) {
            return getXMLRepresentation(tl);
        } else if(formatExtension.equalsIgnoreCase("json")) {
            return getJsonRepresentation(tl);
        } else {
            throw new RestletException("Unknown or missing format extension : " + formatExtension, 
                    Status.CLIENT_ERROR_BAD_REQUEST);
        }
    }
    
    /**
     * POST overwrites an existing layer
     * 
     * @param req
     * @param resp
     * @throws RestletException
     */
    private void doPost(Request req, Response resp) throws RestletException, IOException {
        TileLayer tl = deserializeAndCheckLayer(req, resp);
        xmlConfig.modifyLayer(tl);
    }
    
    /**
     * PUT creates a new layer
     * 
     * @param req
     * @param resp
     * @throws RestletException
     */
    private void doPut(Request req, Response resp) throws RestletException, IOException {
        TileLayer tl = deserializeAndCheckLayer(req, resp);
        
        TileLayer testtl = null;
        try {
            testtl = findTileLayer(tl.getName(), layerDispatcher);  
        } catch (RestletException re) {
            // This is the expected behavior, it should not exist
        }
        
        if(testtl == null) {
            xmlConfig.addLayer(tl); 
        } else {
            throw new RestletException(
            "Layer with name " + tl.getName() + " already exists, "
            +"use POST if you want to replace it.", Status.CLIENT_ERROR_BAD_REQUEST );
        }
    }
    
    /**
     * DELETE removes an existing layer
     * 
     * @param req
     * @param resp
     * @throws RestletException
     */
    private void doDelete(Request req, Response resp) throws RestletException {
        String layerName = (String) req.getAttributes().get("layer");
        TileLayer tl = findTileLayer(layerName, layerDispatcher);
        xmlConfig.deleteLayer(tl);
    }
    
    
    protected TileLayer deserializeAndCheckLayer(Request req, Response resp) 
    throws RestletException, IOException {
        String layerName = (String) req.getAttributes().get("layer");
        String formatExtension = (String) req.getAttributes().get("extension");
        InputStream is = req.getEntity().getStream();
        
        return deserializeAndCheckLayerInternal(layerName, formatExtension, is);
    }
    
    /**
     * We separate out the internal to make unit testing easier
     * 
     * @param layerName
     * @param formatExtension
     * @param is
     * @return
     * @throws RestletException
     * @throws IOException
     */
    protected TileLayer deserializeAndCheckLayerInternal(
            String layerName, String formatExtension, InputStream is) 
    throws RestletException, IOException {
        
        TileLayer tl = findTileLayer(layerName, layerDispatcher);

        XStream xs = XMLConfiguration.getConfiguredXStream(
                new XStream(new DomDriver()));

        WMSLayer newLayer = null;

        if (formatExtension.equalsIgnoreCase("xml")) {
            newLayer = (WMSLayer) xs.fromXML(is);
        } else if (formatExtension.equalsIgnoreCase("json")) {
            HierarchicalStreamDriver driver = new JettisonMappedXmlDriver();
            HierarchicalStreamReader hsr = driver.createReader(is);
            // See http://jira.codehaus.org/browse/JETTISON-48
            StringWriter writer = new StringWriter();
            new HierarchicalStreamCopier().copy(
                    hsr, new PrettyPrintWriter(writer));
            writer.close();
            newLayer = (WMSLayer) xs.fromXML(writer.toString());
        } else {
            throw new RestletException("Unknown or missing format extension: "
                    + formatExtension, Status.CLIENT_ERROR_BAD_REQUEST);
        }

        if (!newLayer.getName().equals(layerName)) {
            throw new RestletException(
                    "There is a mismatch between the name of the "
                    + " layer in the submission and the URL you specified.",
                    Status.CLIENT_ERROR_BAD_REQUEST);
        }

        return newLayer;
    }
    
   
    /**
     * Returns a XMLRepresentation of the layer
     * 
     * @param layer
     * @return
     */
    public DomRepresentation getXMLRepresentation(TileLayer layer) {
        XStream xs = XMLConfiguration.getConfiguredXStream(new XStream());
        String xmlText = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + xs.toXML(layer);
        
        Document doc = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Reader reader = new CharArrayReader(xmlText.toCharArray());
            doc = builder.parse(new InputSource(reader));
        } catch (ParserConfigurationException pce) {
            log.error(pce.getMessage());
            pce.printStackTrace();
        } catch (IOException ei) {
            log.error("Exception occured while creating documet from file");
            ei.printStackTrace(System.err);
        } catch (SAXException saxe) {
            log.error(saxe.getMessage());
            saxe.printStackTrace();
        }
        
        return new DomRepresentation(MediaType.TEXT_XML, doc);
    }

    /**
     * Returns a JsonRepresentation of the layer
     * 
     * @param layer
     * @return
     */
    public JsonRepresentation getJsonRepresentation(TileLayer layer) {
        JsonRepresentation rep = null;
        try {
            XStream xs = XMLConfiguration.getConfiguredXStream(
                    new XStream(new JsonHierarchicalStreamDriver()));
            JSONObject obj = new JSONObject(xs.toXML(layer));
            rep = new JsonRepresentation(obj);
        } catch (JSONException jse) {
            jse.printStackTrace();
        }
        return rep;
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
    
    public void setTileLayerDispatcher(TileLayerDispatcher tileLayerDispatcher) {
        layerDispatcher = tileLayerDispatcher;
    }
    
    public void setXMLConfiguration(XMLConfiguration xmlConfig) {
        this.xmlConfig = xmlConfig;
    }
}
