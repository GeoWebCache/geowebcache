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
package org.geowebcache.rest.seed;

import java.io.IOException;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.rest.GWCRestlet;
import org.geowebcache.rest.RestletException;
import org.geowebcache.seed.SeedEstimate;
import org.geowebcache.seed.SeedEstimator;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.resource.Representation;
import org.restlet.resource.StringRepresentation;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.json.JsonHierarchicalStreamDriver;
import com.thoughtworks.xstream.io.xml.DomDriver;


public class SeedEstimateRestlet extends GWCRestlet {
    public JSONObject myrequest;

    private XMLConfiguration xmlConfig;

    public void handle(Request request, Response response){
        Method met = request.getMethod();
        try {
            if (met.equals(Method.GET)) {
                doGet(request, response);
            } else if(met.equals(Method.POST)) {
                doPost(request, response);
            } else {
                throw new RestletException("Method not allowed", Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
            }
        } catch (RestletException re) {
            response.setEntity(re.getRepresentation());
            response.setStatus(re.getStatus());
        } catch (IOException ioe) {
            response.setEntity("Encountered IO error " + ioe.getMessage(),MediaType.TEXT_PLAIN);
            response.setStatus(Status.SERVER_ERROR_INTERNAL);
        }
    }
    
    /**
     * Returns a StringRepresentation with the status of the running threads
     * in the thread pool. 
     */
    public void doGet(Request req, Response resp) throws RestletException {
        SeedEstimate estimate = new SeedEstimate();
        String formatExtension = (String) req.getAttributes().get("extension");
        
        if(req.getAttributes().get("layerName") == null) {
            throw new RestletException("Couldn't perform estimate, layerName required", Status.CLIENT_ERROR_BAD_REQUEST);
        } else {
            estimate.gridSetId = (String)req.getAttributes().get("gridSetId");
        }

        if(req.getAttributes().get("gridSetId") == null) {
            throw new RestletException("Couldn't perform estimate, gridSetId required", Status.CLIENT_ERROR_BAD_REQUEST);
        } else {
            estimate.gridSetId = (String)req.getAttributes().get("gridSetId");
        }

        if(req.getAttributes().get("bounds") == null) {
            throw new RestletException("Couldn't perform estimate, bounds required", Status.CLIENT_ERROR_BAD_REQUEST);
        } else {
            estimate.bounds = new BoundingBox((String)req.getAttributes().get("bounds"));
        }

        if(req.getAttributes().get("zoomStart") == null) {
            throw new RestletException("Couldn't perform estimate, zoomStart required", Status.CLIENT_ERROR_BAD_REQUEST);
        } else {
            try {
                estimate.zoomStart = Integer.parseInt((String)req.getAttributes().get("zoomStart"));
            } catch (NumberFormatException nfe) {
                throw new RestletException("'" + req.getAttributes().get("zoomStart") + "' is not a valid zoomStart.", Status.CLIENT_ERROR_BAD_REQUEST);
            }
        }

        if(req.getAttributes().get("zoomStop") == null) {
            throw new RestletException("Couldn't perform estimate, zoomStop required", Status.CLIENT_ERROR_BAD_REQUEST);
        } else {
            try {
                estimate.zoomStop = Integer.parseInt((String)req.getAttributes().get("zoomStop"));
            } catch (NumberFormatException nfe) {
                throw new RestletException("'" + req.getAttributes().get("zoomStop") + "' is not a valid zoomStop.", Status.CLIENT_ERROR_BAD_REQUEST);
            }
        }

        if(req.getAttributes().get("threadCount") == null) {
            throw new RestletException("Couldn't perform estimate, threadCount required", Status.CLIENT_ERROR_BAD_REQUEST);
        } else {
            try {
                estimate.threadCount = Integer.parseInt((String)req.getAttributes().get("threadCount"));
            } catch (NumberFormatException nfe) {
                throw new RestletException("'" + req.getAttributes().get("threadCount") + "' is not a valid threadCount.", Status.CLIENT_ERROR_BAD_REQUEST);
            }
        }

        if(req.getAttributes().get("tilesDone") == null || req.getAttributes().get("timeSpent") == null) {
            // assumes 100ms a tile if no known figures exist to work from
            estimate.tilesDone = 5;
            estimate.timeSpent = 1;
        } else {
            try {
                estimate.tilesDone = Long.parseLong((String)req.getAttributes().get("tilesDone"));
            } catch (NumberFormatException nfe) {
                throw new RestletException("'" + req.getAttributes().get("tilesDone") + "' is not a valid tilesDone.", Status.CLIENT_ERROR_BAD_REQUEST);
            }
            try {
                estimate.timeSpent = Long.parseLong((String)req.getAttributes().get("timeSpent"));
            } catch (NumberFormatException nfe) {
                throw new RestletException("'" + req.getAttributes().get("timeSpent") + "' is not a valid timeSpent.", Status.CLIENT_ERROR_BAD_REQUEST);
            }
        }
        
        resp.setEntity(doEstimate(estimate, formatExtension));
    }
    
    /**
     * Method responsible for handling incoming POSTs. It will parse the XML
     * document and deserialize it into a SeedEstimate, perform the estimate 
     * and return the result.
     */
    public void doPost(Request req, Response resp) throws RestletException, IOException {        
        String formatExtension = (String) req.getAttributes().get("extension");
        
        SeedEstimate estimate = null;
        
        XStream xs = xmlConfig.configureXStreamForSeedEstimate(new XStream(new DomDriver()));
        
        if(formatExtension.equalsIgnoreCase("xml")) {
            estimate = (SeedEstimate) xs.fromXML(req.getEntity().getStream());
        } else if(formatExtension.equalsIgnoreCase("json")){
            estimate = (SeedEstimate) xs.fromXML(convertJson(req.getEntity().getText()));
        } else {
            throw new RestletException("Format extension unknown or not specified: " + formatExtension, Status.CLIENT_ERROR_BAD_REQUEST);
        }
        
        try {
            resp.setEntity(doEstimate(estimate, formatExtension));
        }catch(IllegalArgumentException e){
            throw new RestletException("Couldn't perform estimate: " + e.getMessage(), Status.CLIENT_ERROR_BAD_REQUEST);
        }

    }

    private Representation doEstimate(SeedEstimate estimate, String formatExtension) throws RestletException {
        try {
            SeedEstimator.getInstance().performEstimate(estimate);
        } catch (GeoWebCacheException gwce) {
            throw new RestletException("Couldn't perform estimate: " + gwce.getMessage(), Status.SERVER_ERROR_INTERNAL);
        }
        
        if(formatExtension.equalsIgnoreCase("xml")) {
            return getXMLRepresentation(estimate);
        } else if(formatExtension.equalsIgnoreCase("json")) {
            return getJsonRepresentation(estimate);
        } else {
            throw new RestletException("Unknown or missing format extension : " + formatExtension, 
                    Status.CLIENT_ERROR_BAD_REQUEST);
        }
    }

    /**
     * Returns a XMLRepresentation of the job
     * 
     * @param job
     * @return
     */
    public Representation getXMLRepresentation(Object o) {
        XStream xs = xmlConfig.configureXStreamForSeedEstimate(new XStream());
        String xmlText = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + xs.toXML(o);

        return new StringRepresentation(xmlText, MediaType.TEXT_XML);
    }

    /**
     * Returns a JsonRepresentation of the job
     * 
     * @param job
     * @return
     */
    public JsonRepresentation getJsonRepresentation(Object o) {
        JsonRepresentation rep = null;
        try {
            XStream xs = xmlConfig.configureXStreamForSeedEstimate(
                    new XStream(new JsonHierarchicalStreamDriver()));
            JSONObject obj = new JSONObject(xs.toXML(o));
            rep = new JsonRepresentation(obj);
        } catch (JSONException jse) {
            jse.printStackTrace();
        }
        return rep;
    }
    
    public void setXMLConfiguration(XMLConfiguration xmlConfig) {
        this.xmlConfig = xmlConfig;
    }
}
