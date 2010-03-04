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
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Iterator;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.rest.GWCRestlet;
import org.geowebcache.rest.GWCTask;
import org.geowebcache.rest.RestletException;
import org.geowebcache.rest.GWCTask.TYPE;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.TileRange;
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
    private static Log log = LogFactory.getLog(SeedFormRestlet.class);
    
    SeederThreadPoolExecutor threadPool;
    
    TileLayerDispatcher layerDispatcher;
    
    StorageBroker storageBroker;
    
    public JSONObject myrequest; 
    
    
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
            long[][] list = getStatusList();
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
        
        XStream xs = XMLConfiguration.getConfiguredXStream(new XStream(new DomDriver()));
        
        if(formatExtension.equalsIgnoreCase("xml")) {
            sr = (SeedRequest) xs.fromXML(req.getEntity().getStream());
        } else if(formatExtension.equalsIgnoreCase("json")){
            sr = (SeedRequest) xs.fromXML(convertJson(req.getEntity().getText()));
        } else {
            throw new RestletException("Format extension unknown or not specified: "
                    + formatExtension,
                    Status.CLIENT_ERROR_BAD_REQUEST);
        }
        
        String layerName = null;
        try {
            layerName = URLDecoder.decode((String) req.getAttributes().get("layer"), "UTF-8");
        } catch (UnsupportedEncodingException uee) { }
        
        TileLayer tl = findTileLayer(layerName, layerDispatcher);
        
        TileRange tr = createTileRange(sr, tl);
        
        GWCTask[] tasks = createTasks(tr, tl, sr.getType(), 
                sr.getThreadCount(), sr.getFilterUpdate());
        
        dispatchTasks(tasks);
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
    
    public GWCTask[] createTasks(TileRange tr, TileLayer tl, GWCTask.TYPE type, 
            int threadCount, boolean filterUpdate) throws RestletException {
        
        if(type == GWCTask.TYPE.TRUNCATE || threadCount < 1) {
            log.debug("Forcing thread count to 1");
            threadCount = 1;
        }
        
        if(threadCount > threadPool.getMaximumPoolSize()) {
            throw new RestletException("Asked to use " + threadCount + " threads," 
                    +" but maximum is " + threadPool.getMaximumPoolSize(), 
                    Status.SERVER_ERROR_INTERNAL);
        }
        
        TileRangeIterator trIter = 
            new TileRangeIterator(tr, tl.getMetaTilingFactors());
        
        GWCTask[] tasks = new GWCTask[threadCount];
        
        for(int i=0; i<threadCount; i++) {
            tasks[i] = createTask(type, trIter, tl, filterUpdate);
            tasks[i].setThreadInfo(threadCount, i);
        }
        
        return tasks;
    }
    
    public void dispatchTasks(GWCTask[] tasks) throws RestletException {
        for(int i=0; i<tasks.length; i++) {
            threadPool.submit(new MTSeeder(tasks[i]));
        }
    }
    
    protected static TileRange createTileRange(SeedRequest req, TileLayer tl) {
        int zoomStart = req.getZoomStart().intValue();
        int zoomStop = req.getZoomStop().intValue();
        
        MimeType mimeType = null;
        String format = req.getMimeFormat();
        if (format == null) {
            mimeType = tl.getMimeTypes().get(0);
        } else {
            try {
                mimeType = MimeType.createFromFormat(format);
            } catch (MimeException e4) {
                e4.printStackTrace();
            }
        }
        
        String gridSetId = req.getGridSetId();
        
        if (gridSetId == null) {
            gridSetId = tl.getGridSubsetForSRS(req.getSRS()).getName();
        }
        if(gridSetId == null) {
            gridSetId = tl.getGridSubsets().entrySet().iterator().next().getKey();
        }
        
        GridSubset gridSubset = tl.getGridSubset(gridSetId);

        long[][] coveredGridLevels;
        
        BoundingBox bounds = req.getBounds();
        if (bounds == null) {
            coveredGridLevels = gridSubset.getCoverages();
        } else {
            coveredGridLevels = gridSubset.getCoverageIntersections(bounds);
        }
   
        int[] metaTilingFactors = tl.getMetaTilingFactors();
        
        coveredGridLevels = gridSubset.expandToMetaFactors(coveredGridLevels, metaTilingFactors);
        
        // TODO Check the null
        return new TileRange(
                tl.getName(), gridSetId, 
                zoomStart, zoomStop, 
                coveredGridLevels, mimeType, null);
    }
    
    
    private GWCTask createTask(TYPE type, TileRangeIterator trIter, 
            TileLayer tl, boolean doFilterUpdate) throws RestletException {
       
        switch (type) {
        case SEED:
            return new SeedTask(storageBroker, trIter, tl, false, doFilterUpdate);
        case RESEED:
            return new SeedTask(storageBroker, trIter, tl, true, doFilterUpdate);
        case TRUNCATE:
            return new TruncateTask(storageBroker, trIter.getTileRange(), tl, doFilterUpdate);
        default:
            throw new RestletException("Unknown request type " + type,
                    Status.CLIENT_ERROR_BAD_REQUEST);
        }
    }
    
    /**
     * Method returns List of Strings representing the status of the currently running threads
     * @return
     */
    private long[][] getStatusList() {
        Iterator<Entry<Long, GWCTask>> iter = threadPool.getRunningTasksIterator();
        
        long[][] ret = new long[threadPool.getMaximumPoolSize()][3];
        int idx = 0;
        
        while(iter.hasNext()) {
            Entry<Long, GWCTask> entry = iter.next();
            GWCTask task = entry.getValue();
        
            ret[idx][0] = (int) task.getTilesDone();
            
            ret[idx][1] = (int) task.getTilesTotal();
            
            ret[idx][2] = task.getTimeRemaining();
            
            idx++;
        }
        
        return ret;
    }
    
    public void setTileLayerDispatcher(TileLayerDispatcher tileLayerDispatcher) {
        layerDispatcher = tileLayerDispatcher;
    }
    
    public void setThreadPoolExecutor(SeederThreadPoolExecutor stpe) {
        threadPool = stpe;
        //statusArray = new int[threadPool.getMaximumPoolSize()][3];
    }
    
    public void setStorageBroker(StorageBroker sb) {
        storageBroker = sb;
    }
}
