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
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.rest.GWCRestlet;
import org.geowebcache.rest.GWCTask;
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
import org.restlet.resource.Representation;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.copy.HierarchicalStreamCopier;
import com.thoughtworks.xstream.io.json.JettisonMappedXmlDriver;
import com.thoughtworks.xstream.io.json.JsonHierarchicalStreamDriver;
import com.thoughtworks.xstream.io.xml.DomDriver;
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;


public class SeedRestlet extends GWCRestlet {
    
    private XMLConfiguration xmlConfig;

    SeederThreadPoolExecutor threadPool;
    
    TileLayerDispatcher layerDispatcher;
    
    //private static int[][] statusArray;
    
    public JSONObject myrequest; 
    
    private static Log log = LogFactory.getLog(org.geowebcache.rest.seed.SeedRestlet.class);
    
    
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

        resp.setEntity(rep);
    }
    
    /**
     * Method responsible for handling incoming POSTs. It will parse the XML
     * document and deserialize it into a SeedRequest, then create a SeedTask
     * and forward it to the thread pool executor.
     */
    public void doPost(Request req, Response resp) 
    throws RestletException, IOException {        
        String formatExtension = (String) req.getAttributes().get("extension");
        
        SeedRequest sr = null;
        
        XStream xs = xmlConfig.getConfiguredXStream(new XStream(new DomDriver()));
        
        if(formatExtension.equalsIgnoreCase("xml")) {
            sr = (SeedRequest) xs.fromXML(req.getEntity().getStream());
        } else if(formatExtension.equalsIgnoreCase("json")){
            sr = (SeedRequest) xs.fromXML(convertJson(req.getEntity().getText()));
        } else {
            throw new RestletException("Format extension unknown or not specified: "
                    + formatExtension,
                    Status.CLIENT_ERROR_BAD_REQUEST);
        }
        
        String layerName = (String) req.getAttributes().get("layer");
        
        TileLayer tl = findTileLayer(layerName, layerDispatcher);
        
        dispatchTasks(sr, tl, threadPool);
        
    }

    /**
     * Deserializing a json string is more complicated. 
     * 
     * XStream does not natively support it. Rather, it uses a 
     * JettisonMappedXmlDriver to convert to intermediate xml and 
     * then deserializes that into the desired object. At this time, 
     * there is a known issue with the Jettison driver involving 
     * elements that come after an array in the json string.
     * 
     * http://jira.codehaus.org/browse/JETTISON-48
     * 
     * The code below is a hack: it treats the json string as text, then
     * converts it to the intermediate xml and then deserializes that
     * into the SeedRequest object.
     */
    private String convertJson(String entityText) throws IOException {
        HierarchicalStreamDriver driver = new JettisonMappedXmlDriver();
        StringReader reader = new StringReader(entityText);
        HierarchicalStreamReader hsr = driver.createReader(reader);
        StringWriter writer = new StringWriter();
        new HierarchicalStreamCopier().copy(hsr, new PrettyPrintWriter(
                writer));
        writer.close();
        return writer.toString();
    }
    
    static void dispatchTasks(SeedRequest sr, TileLayer tl, 
            ThreadPoolExecutor threadPoolExec) throws RestletException {
        String type;
        if(sr.getType() == null || sr.getType().length() == 0) {
           type = "seed";
        } else {
            type = sr.getType();
            if( type.equalsIgnoreCase("seed")
                    || type.equalsIgnoreCase("reseed") 
                    || type.equalsIgnoreCase("truncate")) {
            } else {
                throw new RestletException("Unknown request type " + sr.getType(), 
                        Status.CLIENT_ERROR_BAD_REQUEST);
            }
        }
        
        int threadCount;
        if(null == sr.getThreadCount() 
                || sr.getThreadCount() < 1 
                || type.equalsIgnoreCase("truncate")) {
            threadCount = 1;
        } else {
            threadCount = sr.getThreadCount();
            if(threadCount > threadPoolExec.getMaximumPoolSize()) {
                throw new RestletException("Asked to use " + threadCount + " threads," 
                        +" but maximum is " + threadPoolExec.getMaximumPoolSize(), 
                        Status.SERVER_ERROR_INTERNAL);
            }
        }
        
        if(threadCount > 1) {
            for(int i=0; i<threadCount; i++) {
                GWCTask task = createTask(type,sr,tl);
                task.setThreadInfo(threadCount, i);
                threadPoolExec.submit(new MTSeeder(task));
            }
        } else {
            GWCTask task = createTask(type,sr,tl);
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
            return new TruncateTask(rq, tl);
        }
        
        return null;
    }
    
    /**
     * Method returns List of Strings representing the status of the currently running threads
     * @return
     */
    private int[][] getStatusList() {
        Iterator<Entry<Long, GWCTask>> iter = threadPool.getRunningTasksIterator();
        
        int[][] ret = new int[threadPool.getMaximumPoolSize()][3];
        int idx = 0;
        
        while(iter.hasNext()) {
            Entry<Long, GWCTask> entry = iter.next();
            GWCTask task = entry.getValue();
        
            ret[idx][0] = (int) task.getTilesDone();
            
            ret[idx][1] = (int) task.getTilesTotal();;
            
            ret[idx][2] = task.getTimeRemaining();
        }
        
        return ret;
    }
    
    public void setXMLConfiguration(XMLConfiguration xmlConfig) {
        this.xmlConfig = xmlConfig;
    }
    
    public void setTileLayerDispatcher(TileLayerDispatcher tileLayerDispatcher) {
        layerDispatcher = tileLayerDispatcher;
    }
    
    public void setThreadPoolExecutor(SeederThreadPoolExecutor stpe) {
        threadPool = stpe;
        //statusArray = new int[threadPool.getMaximumPoolSize()][3];
    }
}
