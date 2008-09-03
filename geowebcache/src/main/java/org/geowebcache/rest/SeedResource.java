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
 */
package org.geowebcache.rest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.util.wms.BBOX;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.resource.Representation;
import org.restlet.resource.Resource;
import org.restlet.resource.Variant;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.copy.HierarchicalStreamCopier;
import com.thoughtworks.xstream.io.json.JsonHierarchicalStreamDriver;
import com.thoughtworks.xstream.io.json.JettisonMappedXmlDriver;
import com.thoughtworks.xstream.io.xml.DomDriver;
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.concurrent.*;


public class SeedResource extends GWCResource {
    private static int[][] statusArray = new int[RESTDispatcher.getInstance().getExecutor().getCorePoolSize()][3]; 
    public JSONObject myrequest; 
    private static Log log = LogFactory.getLog(org.geowebcache.rest.SeedResource.class);
    
    /**
     * Constructor
     * @param context
     * @param request
     * @param response
     */
    public SeedResource(Context context, Request request, Response response) {
        super(context, request, response);
        getVariants().add(new Variant(MediaType.APPLICATION_JSON));
        getVariants().add(new Variant(MediaType.APPLICATION_XML));
    }
    
    /**
     * Method returns a StringRepresentation with the status of the running threads
     * in the thread pool. 
     */
    public Representation getRepresentation(Variant variant) {
        Representation rep = null;

        try {
            XStream xs = new XStream(new JsonHierarchicalStreamDriver());
            JSONObject obj = null;
            int[][] list = getStatusList();
            synchronized (list) {
                obj = new JSONObject(xs.toXML(list));
            }

            rep = new JsonRepresentation(obj);
        } catch (JSONException jse) {
            jse.printStackTrace();
        }

        return rep;
    }

    /**
     * Method responsible for handling incoming POSTs. It will parse the xml
     * document and deserialize it into a SeedRequest, then create a SeedTask
     * and forward it to the thread pool executor.
     */
    @Override
    public void post(Representation entity) {
        try {
            checkPosMediaType(entity);
            
            String text = entity.getText();

            XStream xs = new XStream(new DomDriver());
            xs.alias("seedRequest", SeedRequest.class);
            xs.alias("format", String.class);
            xs.alias("bounds", BBOX.class);
            xs.alias("projection", SRS.class);
            xs.alias("zoomstart", Integer.class);
            xs.alias("zoomstop", Integer.class);

            SeedRequest rq = null;

            if (entity.getMediaType().equals(MediaType.APPLICATION_XML)) {
                rq = (SeedRequest) xs.fromXML(text);
            }

            /**
             * deserializing a json string is more complicated. XStream does not
             * natively support it. Rather it uses a JettisonMappedXmlDriver to
             * convert to intermediate xml and then deserializes that into the
             * desired object. At this time, there is a known issue with the
             * Jettison driver involving elements that come after an array in
             * the json string.
             * 
             * http://jira.codehaus.org/browse/JETTISON-48
             * 
             * The code below is a hack: it treats the json string as text, then
             * converts it to the intermediate xml and then deserializes that
             * into the SeedRequest object.
             * 
             * 
             */

            else if (entity.getMediaType().equals(MediaType.APPLICATION_JSON)) {
                HierarchicalStreamDriver driver = new JettisonMappedXmlDriver();
                StringReader reader = new StringReader(text);
                HierarchicalStreamReader hsr = driver.createReader(reader);
                StringWriter writer = new StringWriter();
                new HierarchicalStreamCopier().copy(hsr, new PrettyPrintWriter(
                        writer));
                writer.close();
                String test = writer.toString();

                rq = (SeedRequest) xs.fromXML(test);
            }
            
            TileLayerDispatcher tlDispatch = RESTDispatcher.getInstance().getTileLayerDispatcher();
            TileLayer tl = tlDispatch.getTileLayer(rq.getLayerName());
            
            if(tl != null) {
                
                ThreadPoolExecutor threadPoolExec = RESTDispatcher.getInstance().getExecutor();
                

                
            } else {
                writeError(Status.CLIENT_ERROR_BAD_REQUEST, "Unknown layer " + rq.getLayerName());
            }
            
        } catch (IOException ioex) {
            writeError(Status.SERVER_ERROR_INTERNAL, ioex.getMessage());
        } catch(GeoWebCacheException gwce) {
            writeError(Status.CLIENT_ERROR_BAD_REQUEST, gwce.getMessage());
        }
    }
    
    private static void dispatchTasks(SeedRequest rq, TileLayer tl, 
            ThreadPoolExecutor threadPoolExec) throws GeoWebCacheException {
        String type;
        if(rq.getType() == null || rq.getType().length() == 0) {
           type = "seed";
        } else {
            type = rq.getType();
            if( type.equalsIgnoreCase("ssed")
                    || type.equalsIgnoreCase("reseed") 
                    || type.equalsIgnoreCase("truncate")) {
                // ok
            } else {
                throw new GeoWebCacheException("Unknown request type " + rq.getType());
            }
        }
        
        int threadCount;
        if(null == rq.getThreadCount() 
                || rq.getThreadCount() < 1 
                || type.equalsIgnoreCase("seed")) {
            threadCount = 1;
        } else {
            threadCount = rq.getThreadCount();
            if(threadCount > threadPoolExec.getMaximumPoolSize()) {
                throw new GeoWebCacheException("Asked to use " + threadCount + " threads," 
                        +" but maximum is " + threadPoolExec.getMaximumPoolSize());
            }
        }
        
        if(threadCount > 1) {
            for(int i=0; i<threadCount; i++) {
                GWCTask task = createTask(type,rq,tl);
                task.setThreadInfo(threadCount, i);
                threadPoolExec.submit(new MTSeeder(task));
            }
        } else {
            GWCTask task = createTask(type,rq,tl);
            threadPoolExec.submit(new MTSeeder(task));
        }
    }
    
    private static GWCTask createTask(String type, SeedRequest rq, TileLayer tl) {
        if(type.equalsIgnoreCase("seed")) {
            return new SeedTask(rq,tl,false);
        }
        if(type.equalsIgnoreCase("reseed")) {
            return new SeedTask(rq,tl,true);
        }
        if(type.equalsIgnoreCase("truncate")) {
            return new TruncateTask();
        }
        
        return null;
    }

    /**
     * Method returns the thread pool executor that handles seeds
     * @return
     */
    //protected static ThreadPoolExecutor getExecutor() {
    //    return RESTDispatcher.getExecutor();
    //}
    
    /**
     * Method returns List of Strings representing the status of the currently running threads
     * @return
     */
    static int[][] getStatusList() {
        return statusArray;
    }

    public boolean allowPost() {
        return true;
    }
}
