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
import java.io.InputStream;
import java.io.StringWriter;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.job.JobDispatcher;
import org.geowebcache.job.JobScheduler;
import org.geowebcache.rest.GWCRestlet;
import org.geowebcache.rest.RestletException;
import org.geowebcache.seed.TileBreeder;
import org.geowebcache.storage.JobObject;
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
import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.copy.HierarchicalStreamCopier;
import com.thoughtworks.xstream.io.json.JettisonMappedXmlDriver;
import com.thoughtworks.xstream.io.json.JsonHierarchicalStreamDriver;
import com.thoughtworks.xstream.io.xml.DomDriver;
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;

/**
 * This is the job resource class required by the REST interface
 */
public class JobRestlet extends GWCRestlet {
    
    private XMLConfiguration xmlConfig;
    
    private TileBreeder seeder;

    private JobDispatcher jobDispatcher;
    
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
                    doPut(request, response);
                } else if (met.equals(Method.DELETE)) {
                    doDelete(request, response);
                } else {
                    throw new RestletException("Method not allowed",
                        Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
                }
                
                jobDispatcher.reInit();
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
     * GET outputs an existing job
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
            try {
                o = jobDispatcher.getJobList();
            } catch (GeoWebCacheException gwce) {
                throw new RestletException("Encountered error: " + gwce.getMessage(), 
                        Status.SERVER_ERROR_INTERNAL, gwce);
            }
        } else {
            o = findJob(jobId);
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
     * POST overwrites an existing job
     * 
     * @param req
     * @param resp
     * @throws RestletException
     */
    private void doPost(Request req, Response resp) throws RestletException, IOException,
            GeoWebCacheException {
        throw new RestletException("Method not allowed", Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
    }
    
    /**
     * PUT creates a new job
     * 
     * @param req
     * @param resp
     * @throws RestletException
     */
    private void doPut(Request req, Response resp) throws RestletException, IOException,
            GeoWebCacheException {
        String formatExtension = (String) req.getAttributes().get("extension");
        
        JobObject job = null;
        
        XStream xs = xmlConfig.configureXStreamForJobs(new XStream(new DomDriver()));
        
        if(formatExtension.equalsIgnoreCase("xml")) {
            job = (JobObject) xs.fromXML(req.getEntity().getStream());
        } else if(formatExtension.equalsIgnoreCase("json")){
            job = (JobObject) xs.fromXML(convertJson(req.getEntity().getText()));
        } else {
            throw new RestletException("Format extension unknown or not specified: "
                    + formatExtension,
                    Status.CLIENT_ERROR_BAD_REQUEST);
        }
        
        try {
            if(job.isScheduled()) {
                JobScheduler.scheduleJob(job, seeder, jobDispatcher.getJobStore());
            } else {
                seeder.executeJob(job);
            }
        }catch(IllegalArgumentException e){
            throw new RestletException(e.getMessage(), Status.CLIENT_ERROR_BAD_REQUEST);
        } catch (GeoWebCacheException e) {
            throw new RestletException(e.getMessage(), Status.SERVER_ERROR_INTERNAL);
        }
    }
    
    /**
     * DELETE removes (stops and deletes) an existing job
     * 
     * @param req
     * @param resp
     * @throws RestletException
     */
    private void doDelete(Request req, Response resp) 
    throws RestletException, GeoWebCacheException {
        long jobId = -1;
        
        try {
            jobId = Long.parseLong((String)req.getAttributes().get("job"));
        } catch (NumberFormatException nfe) {
            throw new RestletException("'" + jobId + "' is not a valid job ID.", Status.CLIENT_ERROR_BAD_REQUEST);
        }
        
        if(jobId == -1) {
            throw new RestletException("No valid job ID provided.", Status.CLIENT_ERROR_BAD_REQUEST);
        }
        
        // JobObject job = findJob(jobId);
        
        jobDispatcher.remove(jobId);
    }
    
    protected JobObject deserializeAndCheckJob(Request req, Response resp, boolean isPut) 
    throws RestletException, IOException {

        long jobId = -1;
        
        try {
            jobId = Long.parseLong((String)req.getAttributes().get("job"));
        } catch (NumberFormatException nfe) {
            throw new RestletException("'" + jobId + "' is not a valid job ID.", Status.CLIENT_ERROR_BAD_REQUEST);
        }
        
        if(jobId == -1) {
            throw new RestletException("No valid job ID provided.", Status.CLIENT_ERROR_BAD_REQUEST);
        }
        
        // TODO UTF-8 may not always be right here
        String formatExtension = (String) req.getAttributes().get("extension");
        InputStream is = req.getEntity().getStream();
        
        // If appropriate, check whether this job exists
        if(!isPut) {
            findJob(jobId);
        }
        
        return deserializeAndCheckJobInternal(jobId, formatExtension, is);
    }
    
    /**
     * We separate out the internal to make unit testing easier
     * 
     * @param jobId
     * @param formatExtension
     * @param is
     * @return
     * @throws RestletException
     * @throws IOException
     */
    protected JobObject deserializeAndCheckJobInternal(
            long jobId, String formatExtension, InputStream is) 
    throws RestletException, IOException {
        
        XStream xs = xmlConfig.configureXStreamForJobs(new XStream(new DomDriver()));

        JobObject newJob = null;

        if (formatExtension.equalsIgnoreCase("xml")) {
            newJob = (JobObject) xs.fromXML(is);
        } else if (formatExtension.equalsIgnoreCase("json")) {
            HierarchicalStreamDriver driver = new JettisonMappedXmlDriver();
            HierarchicalStreamReader hsr = driver.createReader(is);
            // See http://jira.codehaus.org/browse/JETTISON-48
            StringWriter writer = new StringWriter();
            new HierarchicalStreamCopier().copy(
                    hsr, new PrettyPrintWriter(writer));
            writer.close();
            newJob = (JobObject) xs.fromXML(writer.toString());
        } else {
            throw new RestletException("Unknown or missing format extension: "
                    + formatExtension, Status.CLIENT_ERROR_BAD_REQUEST);
        }

        if (newJob.getJobId() != jobId) {
            throw new RestletException(
                    "There is a mismatch between the ID of the "
                    + " job in the submission and the URL you specified.",
                    Status.CLIENT_ERROR_BAD_REQUEST);
        }

        return newJob;
    }
    
   
    /**
     * Returns a XMLRepresentation of the job
     * 
     * @param job
     * @return
     */
    public Representation getXMLRepresentation(Object o) {
        XStream xs = xmlConfig.configureXStreamForJobs(new XStream());
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
            XStream xs = xmlConfig.configureXStreamForJobs(
                    new XStream(new JsonHierarchicalStreamDriver()));
            JSONObject obj = new JSONObject(xs.toXML(o));
            rep = new JsonRepresentation(obj);
        } catch (JSONException jse) {
            jse.printStackTrace();
        }
        return rep;
    }
    
    protected JobObject findJob(long jobId) {
        if(jobId == -1) {
            throw new RestletException("Job not specified",
                    Status.CLIENT_ERROR_BAD_REQUEST);
        }
        
        JobObject job = null;
        try {
            job = jobDispatcher.getJob(jobId);
        } catch (GeoWebCacheException gwce) {
            throw new RestletException("Encountered error: " + gwce.getMessage(), 
                    Status.SERVER_ERROR_INTERNAL, gwce);
        }
        
        if(job == null) {
            throw new RestletException("Uknown job: " + jobId, 
                    Status.CLIENT_ERROR_NOT_FOUND);
        }
        
        return job;
    }

    public void setJobDispatcher(JobDispatcher jobDispatcher) {
        this.jobDispatcher = jobDispatcher;
    }
    
    public void setTileBreeder(TileBreeder seeder) {
        this.seeder = seeder;
    }

    public void setXMLConfiguration(XMLConfiguration xmlConfig) {
        this.xmlConfig = xmlConfig;
    }
}
