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
    
    private SeedEstimator estimator; 

    public void handle(Request request, Response response){
        Method met = request.getMethod();
        try {
            if(met.equals(Method.POST)) {
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
            estimator.performEstimate(estimate);
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

    public void setEstimator(SeedEstimator estimator) {
        this.estimator = estimator;
    }
}
