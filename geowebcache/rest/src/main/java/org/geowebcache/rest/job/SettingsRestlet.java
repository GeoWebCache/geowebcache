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
package org.geowebcache.rest.job;

import java.io.IOException;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.rest.GWCRestlet;
import org.geowebcache.rest.RestletException;
import org.geowebcache.storage.JobStore;
import org.geowebcache.storage.SettingsObject;
import org.geowebcache.storage.StorageException;
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

/**
 * This is the settings resource class required by the REST interface
 */
public class SettingsRestlet extends GWCRestlet {
    
    private XMLConfiguration xmlConfig;
    
    private JobStore jobStore;
    
    public void handle(Request request, Response response) {
        Method met = request.getMethod();
        try {
            if(met.equals(Method.GET)) {
                doGet(request, response);
            } else {
                // These modify things, so we reload afterwards
                if (met.equals(Method.POST)) {
                    doPost(request, response);
                } else if (met.equals(Method.PUT)) {
                    throw new RestletException("Method not allowed",
                            Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
                } else if (met.equals(Method.DELETE)) {
                    throw new RestletException("Method not allowed",
                            Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
                } else {
                    throw new RestletException("Method not allowed",
                        Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
                }
            }
        } catch (RestletException re) {
            response.setEntity(re.getRepresentation());
            response.setStatus(re.getStatus());
        } catch (Exception e) {
            // Either GeoWebCacheException or IOException
            response.setEntity(e.getMessage() + " " + e.toString(), MediaType.TEXT_PLAIN);
            response.setStatus(Status.SERVER_ERROR_INTERNAL);
            e.printStackTrace();
        }
    }
    
    protected void doGet(Request req, Response resp) throws RestletException {
        String formatExtension = (String) req.getAttributes().get("extension");
        resp.setEntity(doGetInternal(formatExtension));
    }
    
    /** 
     * We separate out the internal to make unit testing easier
     * 
     * @param formatExtension
     * @return
     * @throws RestletException
     */
    protected Representation doGetInternal(String formatExtension) 
    throws RestletException {
        SettingsObject o = null;
        try {
            o = new SettingsObject();
            o.setClearOldJobs(jobStore.getClearOldJobsSetting());
        } catch (Exception e) {
            throw new RestletException("Couldn't get settings: " + e.getMessage(), 
                    Status.SERVER_ERROR_INTERNAL);
        }
        
        if(formatExtension.equalsIgnoreCase("xml")) {
            return getXMLRepresentation(o);
        } else if(formatExtension.equalsIgnoreCase("json")) {
            return getJsonRepresentation(o);
        } else {
            throw new RestletException("Unknown or missing format extension : " + formatExtension, 
                    Status.CLIENT_ERROR_BAD_REQUEST);
        }
    }

    /**
     * Returns a XMLRepresentation of the settings
     * 
     * @param settings object
     * @return
     */
    public Representation getXMLRepresentation(Object o) {
        XStream xs = xmlConfig.configureXStreamForSettings(new XStream());
        String xmlText = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + xs.toXML(o);
        
        return new StringRepresentation(xmlText, MediaType.TEXT_XML);
    }

    /**
     * Returns a JsonRepresentation of the settings
     * 
     * @param settings object
     * @return
     */
    public JsonRepresentation getJsonRepresentation(Object o) {
        JsonRepresentation rep = null;
        try {
            XStream xs = xmlConfig.configureXStreamForSettings(
                    new XStream(new JsonHierarchicalStreamDriver()));
            JSONObject obj = new JSONObject(xs.toXML(o));
            rep = new JsonRepresentation(obj);
        } catch (JSONException jse) {
            jse.printStackTrace();
        }
        return rep;
    }

    /**
     * POST updates all settings
     */
    private void doPost(Request req, Response resp) throws RestletException, IOException,
            GeoWebCacheException {
        String formatExtension = (String) req.getAttributes().get("extension");
        
        SettingsObject settings = null;
        
        XStream xs = xmlConfig.configureXStreamForSettings(new XStream(new DomDriver()));
        
        if(formatExtension.equalsIgnoreCase("xml")) {
            settings = (SettingsObject) xs.fromXML(req.getEntity().getStream());
        } else if(formatExtension.equalsIgnoreCase("json")){
            settings = (SettingsObject) xs.fromXML(convertJson(req.getEntity().getText()));
        } else {
            throw new RestletException("Format extension unknown or not specified: "
                    + formatExtension,
                    Status.CLIENT_ERROR_BAD_REQUEST);
        }
        
        try {
            jobStore.setClearOldJobsSetting(settings.getClearOldJobs());
        } catch (StorageException e) {
            throw new RestletException(e.getMessage(), Status.SERVER_ERROR_INTERNAL);
        }
    }
    
    public void setJobStore(JobStore jobStore) {
        this.jobStore = jobStore;
    }
    
    public void setXMLConfiguration(XMLConfiguration xmlConfig) {
        this.xmlConfig = xmlConfig;
    }
}
