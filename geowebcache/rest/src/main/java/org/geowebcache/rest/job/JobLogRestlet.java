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

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.job.JobDispatcher;
import org.geowebcache.rest.GWCRestlet;
import org.geowebcache.rest.RestletException;
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

/**
 * This is the job resource class required by the REST interface
 */
public class JobLogRestlet extends GWCRestlet {
    
    private XMLConfiguration xmlConfig;
    
    private JobDispatcher jobDispatcher;
    
    public void handle(Request request, Response response) {
        Method met = request.getMethod();
        try {
            if(met.equals(Method.GET)) {
                doGet(request, response);
            } else {
                // These modify things, so we reload afterwards
                if (met.equals(Method.POST)) {
                    throw new RestletException("Method not allowed",
                            Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
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
    
    /**
     * GET outputs the logs for a job
     * 
     * @param req
     * @param resp
     * @throws RestletException
     * @throws  
     */
    protected void doGet(Request req, Response resp) throws RestletException {
        long jobId = -1;
        try {
            if(req.getAttributes().get("job") == null) {
                jobId = -1;
            } else {
                jobId = Long.parseLong((String)req.getAttributes().get("job"));
            }
        } catch (NumberFormatException nfe) {
            throw new RestletException("'" + jobId + "' is not a valid job ID.", Status.CLIENT_ERROR_BAD_REQUEST);
        }
        
        String formatExtension = (String) req.getAttributes().get("extension");
        resp.setEntity(doGetInternal(jobId, formatExtension));
    }
    
    /** 
     * We separate out the internal to make unit testing easier
     * 
     * @param jobId
     * @param formatExtension
     * @return
     * @throws RestletException
     */
    protected Representation doGetInternal(long jobId, String formatExtension) 
    throws RestletException {
        Object o = null;
        if(jobId == -1) {
            throw new RestletException("Job ID is required.", 
                    Status.CLIENT_ERROR_BAD_REQUEST);
        } else {
            try {
                o = jobDispatcher.getJobLogs(jobId);
            } catch (GeoWebCacheException e) {
                throw new RestletException("Couldn't get logs for job " + jobId + ": " + e.getMessage(), 
                        Status.CLIENT_ERROR_BAD_REQUEST);
            }
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
     * Returns a XMLRepresentation of the job
     * 
     * @param job
     * @return
     */
    public Representation getXMLRepresentation(Object o) {
        XStream xs = xmlConfig.configureXStreamForJobLogs(new XStream());
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
            XStream xs = xmlConfig.configureXStreamForJobLogs(
                    new XStream(new JsonHierarchicalStreamDriver()));
            JSONObject obj = new JSONObject(xs.toXML(o));
            rep = new JsonRepresentation(obj);
        } catch (JSONException jse) {
            jse.printStackTrace();
        }
        return rep;
    }

    public void setJobDispatcher(JobDispatcher jobDispatcher) {
        this.jobDispatcher = jobDispatcher;
    }
    
    public void setXMLConfiguration(XMLConfiguration xmlConfig) {
        this.xmlConfig = xmlConfig;
    }
}
