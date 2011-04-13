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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.rest.GWCRestlet;
import org.geowebcache.rest.RestletException;
import org.geowebcache.seed.GWCTask;
import org.geowebcache.seed.GWCTaskStatus;
import org.geowebcache.seed.SeedRequest;
import org.geowebcache.seed.TileBreeder;
import org.geowebcache.storage.TileRange;
import org.json.JSONObject;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.copy.HierarchicalStreamCopier;
import com.thoughtworks.xstream.io.json.JettisonMappedXmlDriver;
import com.thoughtworks.xstream.io.xml.DomDriver;
import com.thoughtworks.xstream.io.xml.DomReader;
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;


public class SeedRestlet extends GWCRestlet {
    private static Log log = LogFactory.getLog(SeedRestlet.class);

    private TileBreeder seeder;

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
            StringBuilder strBld = new StringBuilder();
            strBld.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
            strBld.append("\n<GeoWebCacheException>");
            strBld.append("\nEncountered IO error " + ioe.getMessage());
            strBld.append("\n</GeoWebCacheException>");
            response.setEntity(strBld.toString(), MediaType.TEXT_XML);
            response.setStatus(Status.SERVER_ERROR_INTERNAL);
        }
    }

    public void doGet(Request req, Response resp) throws RestletException {

        String taskIds = null;
        try {
            taskIds = URLDecoder.decode((String) req.getAttributes().get("taskids"), "UTF-8");
        } catch (UnsupportedEncodingException uee) { 
            log.error("Exception type = " + uee.getClass().getName() + " msg = " + uee.getMessage());
            throw new RestletException(uee.getMessage(), Status.SERVER_ERROR_INTERNAL);
        }

        ArrayList<GWCTaskStatus> taskList = new ArrayList<GWCTaskStatus>(); 
        seeder.getStorageBroker().getTasks(taskIds, taskList);

        List<String> segments = req.getResourceRef().getSegments();
        String reqAction = segments.get(segments.size() -2);
        if (reqAction.equalsIgnoreCase("seedprogress")) {
            handleProgress(req, resp, taskList);
        } else if (reqAction.equalsIgnoreCase("seedcancel")) {
            handleCancel(req, resp, taskList);
        } else {
            String msg = "Unsupported action: " + reqAction;
            log.error(msg);
            throw new RestletException(msg,Status.CLIENT_ERROR_NOT_FOUND);
        }

    }

    /**
     * Returns a StringRepresentation with the status of the running threads
     * in the thread pool. 
     */
    private void handleProgress(Request req, Response resp, ArrayList<GWCTaskStatus> taskList) 
    throws RestletException {

        Object[] tasks = taskList.toArray();

        StringBuilder strBld = new StringBuilder();
        strBld.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        strBld.append("<Tasks>");

        XStream xs = new XStream();
        xs.omitField(GWCTask.class, "parsedType");
        xs.omitField(GWCTask.class, "state");
        xs.omitField(GWCTask.class, "sharedThreadCount");
        xs.omitField(GWCTask.class, "groupStartTime");
        xs.omitField(GWCTask.class, "storageBroker");
        
        xs.alias("GWCTaskStatus", GWCTaskStatus.class);

        for (int i=0; i < tasks.length; i++) {
            GWCTaskStatus taskStatus = (GWCTaskStatus)tasks[i];
            Iterator<Entry<Long, GWCTask>> iter = seeder.getRunningTasksIterator();

            while(iter.hasNext()) {
                Entry<Long, GWCTask> entry = iter.next();
                GWCTask task = entry.getValue();
                if (task.getDbId() == taskStatus.getDbId()) {
                    taskStatus.setTaskId(task.getTaskId());
                    taskStatus.setThreadRunning(true);
                    taskStatus.setTilesDone(task.getTilesDone());
                    taskStatus.setTilesTotal(task.getTilesTotal());
                    taskStatus.setTimeSpent(task.getTimeSpent());
                    taskStatus.setTimeRemaing(task.getTimeRemaining());
                }
            }
            String task_xml= xs.toXML(taskStatus);
            strBld.append(task_xml);
        }

        strBld.append("</Tasks>");
        resp.setEntity(strBld.toString(), MediaType.TEXT_XML);        
    }

    private void handleCancel(Request req, Response resp, ArrayList<GWCTaskStatus> taskList) 
    throws RestletException {

        StringBuilder strBld = new StringBuilder();
        strBld.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        strBld.append("<CancelResults>");

        Object[] tasks = taskList.toArray();
        for (int i = 0; i < tasks.length; i++) {
            GWCTaskStatus taskStatus = (GWCTaskStatus)tasks[i];
            strBld.append("<Task>");
            strBld.append("<dbId>" + taskStatus.getDbId() + "</dbId>");
            strBld.append("<CancelResult>");
            Iterator<Entry<Long, GWCTask>> iter = seeder.getRunningTasksIterator();
            while(iter.hasNext()) {
                Entry<Long, GWCTask> entry = iter.next();
                GWCTask task = entry.getValue();
                if (task.getDbId() == taskStatus.getDbId()) {
                    taskStatus.setThreadRunning(true);
                    taskStatus.setTaskId(task.getTaskId());
                }
            }
            if (!taskStatus.getThreadRunning()) {
                strBld.append("not running");                               
            } else {
                log.debug("terminating task with id = " + taskStatus.getTaskId()); 
                if (seeder.terminateGWCTask(taskStatus.getTaskId())) {
                    strBld.append("true");              
                } else {
                    strBld.append("false");                             
                }
            }
            strBld.append("</CancelResult>");
            strBld.append("</Task>");
        }

        strBld.append("</CancelResults>");
        resp.setEntity(strBld.toString(), MediaType.TEXT_XML);        

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
        try {
            if(formatExtension.equalsIgnoreCase("xml")) {
                String xml_body = req.getEntity().getText();
                log.debug("doPost, xml = " +  xml_body);
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                ByteArrayInputStream xml_bs = new ByteArrayInputStream(xml_body.getBytes());
                Document doc = db.parse(xml_bs);                
                Node rootNode = doc.getFirstChild();
                xs.alias("seedRequest", SeedRequest.class);
                sr = (SeedRequest) xs.unmarshal(new DomReader((Element) rootNode));
                log.debug("doPost, sr = " + sr.getLayerName() );
            } else if(formatExtension.equalsIgnoreCase("json")){
                sr = (SeedRequest) xs.fromXML(convertJson(req.getEntity().getText()));
            } else {
                throw new RestletException("Format extension unknown or not specified: "
                        + formatExtension,
                        Status.CLIENT_ERROR_BAD_REQUEST);
            }
        } catch (Exception  e) {
            log.error("Exception type = " + e.getClass().getName() + " msg = " + e.getMessage());
            e.printStackTrace();
            if (e.getCause() != null){
                log.error("cause = " + e.getCause().getMessage());
            }
        }

        StringBuilder strBld = new StringBuilder();
        strBld.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        String layerName = null;
        try {
            layerName = URLDecoder.decode((String) req.getAttributes().get("layer"), "UTF-8");
        } catch (UnsupportedEncodingException uee) { 
            log.error("Exception type = " + uee.getClass().getName() + " msg = " + uee.getMessage());
            throw new RestletException(uee.getMessage(), Status.SERVER_ERROR_INTERNAL);
        }

        log.debug("layerName = " + layerName + " sr.GridSetId = " + sr.getGridSetId() + " type = " + sr.getType());
        GWCTask[] tasks = null;
        try {
            TileLayer tl = null;
            try {
                tl = seeder.findTileLayer(layerName);
            } catch (GeoWebCacheException e) {
                strBld.append("<GeoWebCacheException>");                
                strBld.append(e.getMessage());              
                strBld.append("</GeoWebCacheException>");               
                resp.setEntity(strBld.toString(), MediaType.TEXT_XML);        
                throw new RestletException(e.getMessage(), Status.SERVER_ERROR_INTERNAL);
            }

            TileRange tr = TileBreeder.createTileRange(sr, tl);

            tasks = seeder.createTasks(tr, tl, sr.getType(), sr.getThreadCount(), sr
                    .getFilterUpdate());

            seeder.dispatchTasks(tasks);
            // Give the thread executor a chance to run
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                // Ok, no worries
            }

        }catch(IllegalArgumentException e){
            log.error("IllegalArgumentException occured: " + e.getMessage());
            throw new RestletException(e.getMessage(), Status.CLIENT_ERROR_BAD_REQUEST);
        } catch (GeoWebCacheException e) {
            log.error("GeoWebCacheException occured: " + e.getMessage());
            throw new RestletException(e.getMessage(), Status.SERVER_ERROR_INTERNAL);
        }

        strBld.append("<Tasks>");
        if( tasks.length == 0) {
            log.debug("No running tasks");
        }
        for (int i = 0; i < tasks.length; i++) {
            if (i > 0) {
                strBld.append(",");
            }
            strBld.append(tasks[i].getDbId());
        }
        strBld.append("</Tasks>\n");
        log.debug("post response = " + strBld.toString());
        resp.setEntity(strBld.toString(), MediaType.TEXT_XML);        
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


    public void setXmlConfig(XMLConfiguration xmlConfig){
        this.xmlConfig = xmlConfig;
    }

    public void setTileBreeder(TileBreeder seeder) {
        this.seeder = seeder;
    }
}
