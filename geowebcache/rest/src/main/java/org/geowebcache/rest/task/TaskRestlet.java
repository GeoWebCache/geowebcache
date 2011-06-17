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
package org.geowebcache.rest.task;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.rest.GWCRestlet;
import org.geowebcache.rest.RestletException;
import org.geowebcache.seed.GWCTask;
import org.geowebcache.task.TaskDispatcher;
import org.geowebcache.util.ServletUtils;
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
 * This is the task resource class required by the REST interface
 */
public class TaskRestlet extends GWCRestlet {
    
    private XMLConfiguration xmlConfig;
    
    private TaskDispatcher taskDispatcher;
    
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
                
                taskDispatcher.reInit();
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
     * GET outputs an existing task
     * 
     * @param req
     * @param resp
     * @throws RestletException
     * @throws  
     */
    protected void doGet(Request req, Response resp) throws RestletException {
        String taskIdent = null;
        try {
            if(req.getAttributes().get("task") == null) {
                taskIdent = null;
            } else {
                taskIdent = URLDecoder.decode((String) req.getAttributes().get("task"), "UTF-8");
            }
        } catch (UnsupportedEncodingException uee) { }
        
        String formatExtension = (String) req.getAttributes().get("extension");
        resp.setEntity(doGetInternal(taskIdent, formatExtension));
    }
    
    /** 
     * We separate out the internal to make unit testing easier
     * 
     * @param taskIdent
     * @param formatExtension
     * @return
     * @throws RestletException
     */
    protected Representation doGetInternal(String taskIdent, String formatExtension) 
    throws RestletException {
        Object o = null;
        if(taskIdent == null) {
            o = taskDispatcher.getTaskList();
        } else {
            o = findTask(taskIdent);
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
     * POST overwrites an existing task
     * 
     * @param req
     * @param resp
     * @throws RestletException
     */
    private void doPost(Request req, Response resp) 
    throws RestletException, IOException, GeoWebCacheException {
        throw new RestletException("Method not allowed",
                Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
    }
    
    /**
     * PUT creates a new task
     * 
     * @param req
     * @param resp
     * @throws RestletException
     */
    private void doPut(Request req, Response resp) 
    throws RestletException, IOException , GeoWebCacheException {
        throw new RestletException("Method not allowed",
                Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
    }
    
    /**
     * DELETE removes (stops and deletes) an existing task
     * 
     * @param req
     * @param resp
     * @throws RestletException
     */
    private void doDelete(Request req, Response resp) 
    throws RestletException, GeoWebCacheException {
        String taskIdent = (String) req.getAttributes().get("task");
        GWCTask tl = findTask(taskIdent);
        //TODO JIMG delete the task
        taskDispatcher.reInit();
    }
    
    
    protected GWCTask deserializeAndCheckTask(Request req, Response resp, boolean isPut) 
    throws RestletException, IOException {
        // TODO UTF-8 may not always be right here
        String taskIdent = ServletUtils.URLDecode((String) req.getAttributes().get("task"), "UTF-8");
        String formatExtension = (String) req.getAttributes().get("extension");
        InputStream is = req.getEntity().getStream();
        
        // If appropriate, check whether this layer exists
        if(!isPut) {
            findTask(taskIdent);
        }
        
        return deserializeAndCheckTaskInternal(taskIdent, formatExtension, is);
    }
    
    /**
     * We separate out the internal to make unit testing easier
     * 
     * @param taskIdent
     * @param formatExtension
     * @param is
     * @return
     * @throws RestletException
     * @throws IOException
     */
    protected GWCTask deserializeAndCheckTaskInternal(
            String taskIdent, String formatExtension, InputStream is) 
    throws RestletException, IOException {
        
        XStream xs = xmlConfig.configureXStreamForTasks(new XStream(new DomDriver()));

        GWCTask newTask = null;

        if (formatExtension.equalsIgnoreCase("xml")) {
            newTask = (GWCTask) xs.fromXML(is);
        } else if (formatExtension.equalsIgnoreCase("json")) {
            HierarchicalStreamDriver driver = new JettisonMappedXmlDriver();
            HierarchicalStreamReader hsr = driver.createReader(is);
            // See http://jira.codehaus.org/browse/JETTISON-48
            StringWriter writer = new StringWriter();
            new HierarchicalStreamCopier().copy(
                    hsr, new PrettyPrintWriter(writer));
            writer.close();
            newTask = (GWCTask) xs.fromXML(writer.toString());
        } else {
            throw new RestletException("Unknown or missing format extension: "
                    + formatExtension, Status.CLIENT_ERROR_BAD_REQUEST);
        }

        if (newTask.getTaskId() != Long.parseLong(taskIdent)) {
            throw new RestletException(
                    "There is a mismatch between the ID of the "
                    + " task in the submission and the URL you specified.",
                    Status.CLIENT_ERROR_BAD_REQUEST);
        }

        return newTask;
    }
    
   
    /**
     * Returns a XMLRepresentation of the layer
     * 
     * @param layer
     * @return
     */
    public Representation getXMLRepresentation(Object o) {
        XStream xs = xmlConfig.configureXStreamForTasks(new XStream());
        String xmlText = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + xs.toXML(o);
        
        return new StringRepresentation(xmlText, MediaType.TEXT_XML);
    }

    /**
     * Returns a JsonRepresentation of the layer
     * 
     * @param layer
     * @return
     */
    public JsonRepresentation getJsonRepresentation(Object o) {
        JsonRepresentation rep = null;
        try {
            XStream xs = xmlConfig.configureXStreamForTasks(
                    new XStream(new JsonHierarchicalStreamDriver()));
            JSONObject obj = new JSONObject(xs.toXML(o));
            rep = new JsonRepresentation(obj);
        } catch (JSONException jse) {
            jse.printStackTrace();
        }
        return rep;
    }
    
    protected GWCTask findTask(String taskIdent) {
        if(taskIdent == null || taskIdent.length() == 0) {
            throw new RestletException("Task not specified",
                    Status.CLIENT_ERROR_BAD_REQUEST);
        }
        
        GWCTask task = null;
        try {
            task = taskDispatcher.getTask(taskIdent);
        } catch (GeoWebCacheException gwce) {
            throw new RestletException("Encountered error: " + gwce.getMessage(), 
                    Status.SERVER_ERROR_INTERNAL);
        }
        
        if(task == null) {
            throw new RestletException("Uknown task: " + taskIdent, 
                    Status.CLIENT_ERROR_NOT_FOUND);
        }
        
        return task;
    }

    public void setTaskDispatcher(TaskDispatcher taskDispatcher) {
        this.taskDispatcher = taskDispatcher;
    }
    
    public void setXMLConfiguration(XMLConfiguration xmlConfig) {
        this.xmlConfig = xmlConfig;
    }
}
