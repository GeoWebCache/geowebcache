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
 * @author David Vick / Boundless 2017
 *
 * Original file
 *
 * SeedRestlet.java
 */
package org.geowebcache.rest.service;

import com.google.common.base.Splitter;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.copy.HierarchicalStreamCopier;
import com.thoughtworks.xstream.io.json.JettisonMappedXmlDriver;
import com.thoughtworks.xstream.io.json.JsonHierarchicalStreamDriver;
import com.thoughtworks.xstream.io.xml.DomDriver;
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.ContextualConfigurationProvider;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.io.GeoWebCacheXStream;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.rest.exception.RestException;
import org.geowebcache.seed.GWCTask;
import org.geowebcache.seed.SeedRequest;
import org.geowebcache.seed.TileBreeder;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Service
public class SeedService {

    static final Log log = LogFactory.getLog(SeedService.class);

    @Autowired
    TileBreeder seeder;

    @Autowired
    protected XMLConfiguration xmlConfig;

    /**
     * GET method for querying running GWC tasks
     * @param request
     * @return
     */
    public ResponseEntity<?> getRunningTasks(HttpServletRequest request) {
        try {
            XStream xs = new GeoWebCacheXStream(new JsonHierarchicalStreamDriver());
            JSONObject obj = null;
            long[][] list;
            list = seeder.getStatusList();
            obj = new JSONObject(xs.toXML(list));
            return new ResponseEntity<>(obj.toString(), HttpStatus.OK);
        } catch (JSONException jse) {
            return new ResponseEntity<Object>("error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * GET method for querying running tasks for the provided layer
     * @param request
     * @param layer
     * @return
     */
    public ResponseEntity<?> getRunningLayerTasks(HttpServletRequest request, String layer) {
        try {
            XStream xs = new GeoWebCacheXStream(new JsonHierarchicalStreamDriver());
            JSONObject obj = null;
            long[][] list;
            if (null == layer) {
                list = seeder.getStatusList();
            } else {
                try {
                    seeder.findTileLayer(layer);
                } catch (GeoWebCacheException e) {
                    return new ResponseEntity<String>(e.getMessage(), HttpStatus.BAD_REQUEST);
                }
                list = seeder.getStatusList(layer);
            }
            obj = new JSONObject(xs.toXML(list));
            return new ResponseEntity<>(obj.toString(), HttpStatus.OK);
        } catch (JSONException jse) {
            log.error(jse);
            return new ResponseEntity<Object>("error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Method to kill running tasks for all of GWC or just the provided layer.
     * @param request
     * @param layer
     * @return
     */
    public String handleKillAllThreads(HttpServletRequest request, String layer) {
        final TileLayer tl;
        if (layer != null) {
            try {
                tl = seeder.findTileLayer(layer);
            } catch (GeoWebCacheException e) {
                throw new RestException(e.getMessage(), HttpStatus.BAD_REQUEST);
            }
        } else {
            tl = null;
        }
        try {
            StringBuilder doc = new StringBuilder();
            StringWriter writer = new StringWriter();
            IOUtils.copy(request.getInputStream(), writer, "UTF-8");
            String body = writer.toString();
            Map<String, String> commandMap = splitToMap(body);

            final boolean allLayers = tl == null;

            if (commandMap.containsKey("kill_all")) {
                String killCode = commandMap.get("kill_all");
                final Iterator<GWCTask> tasks;
                if ("1".equals(killCode) || "running".equalsIgnoreCase(killCode)) {
                    killCode = "running";
                    tasks = seeder.getRunningTasks();
                } else if ("pending".equalsIgnoreCase(killCode)) {
                    tasks = seeder.getPendingTasks();
                } else if ("all".equalsIgnoreCase(killCode)) {
                    tasks = seeder.getRunningAndPendingTasks();
                } else {
                    throw new RestException("Unknown kill_all code: '" + killCode
                            + "'. One of all|running|pending is expected.", HttpStatus.BAD_REQUEST);
                }
                List<GWCTask> terminatedTasks = new LinkedList<GWCTask>();
                List<GWCTask> nonTerminatedTasks = new LinkedList<GWCTask>();
                while (tasks.hasNext()) {
                    GWCTask task = tasks.next();
                    String layerName = task.getLayerName();
                    if (!allLayers && !tl.getName().equals(layerName)) {
                        continue;
                    }
                    long taskId = task.getTaskId();
                    boolean terminated = seeder.terminateGWCTask(taskId);
                    if (terminated) {
                        terminatedTasks.add(task);
                    } else {
                        nonTerminatedTasks.add(task);
                    }
                }

                doc.append("<p>Requested to terminate ").append(killCode).append(" tasks.");
                doc.append("Terminated tasks: <ul>");
                for (GWCTask t : terminatedTasks) {
                    doc.append("<li>").append(t).append("</li>");
                }
                doc.append("</ul>Tasks already finished: <ul>");
                for (GWCTask t : nonTerminatedTasks) {
                    doc.append("<li>").append(t).append("</li>");
                }
                if (tl != null) {
                    doc.append("</ul><p><a href=\"./" + tl.getName() + "\">Go back</a></p>\n");
                }
            }
            return doc.toString();
        } catch (IOException e) {
            return "error";
        }
    }

    /**
     * Method to do the seeding and truncating.
     * @param request
     * @param extension
     * @param layer
     * @return
     */
    public ResponseEntity<?> doSeeding(HttpServletRequest request, String layer, String extension, String body) {
        XStream xs = configXStream(new GeoWebCacheXStream(new DomDriver()));

        Object obj = null;

        try {
            if (extension==null || extension.equalsIgnoreCase("xml")) {
                obj = xs.fromXML(body);
            } else if (extension.equalsIgnoreCase("json")) {
                obj = xs.fromXML(convertJson(body));
            } else {
                throw new RestException("Format extension unknown or not specified: "
                        + extension, HttpStatus.BAD_REQUEST);
            }
            handleRequest(layer, obj);
            return new ResponseEntity<Object>(HttpStatus.OK);
        } catch (IOException e) {
            return new ResponseEntity<Object>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * METHOD that handles the seeding/truncating task from the POST method.
     * @param layerName
     * @param obj
     */
    protected void handleRequest(String layerName, Object obj) {
        final SeedRequest sr = (SeedRequest) obj;
        try {
            seeder.seed(layerName, sr);
        } catch (IllegalArgumentException e) {
            throw new RestException(e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (GeoWebCacheException e) {
            throw new RestException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    protected XStream configXStream(XStream xs) {
        return xmlConfig.getConfiguredXStreamWithContext(xs, ContextualConfigurationProvider.Context.REST);
    }

    private Map<String, String> splitToMap(String data) {
        if (data.contains("&")) {
            return Splitter.on("&").withKeyValueSeparator("=").split(data);
        }else {
            return Splitter.on(" ").withKeyValueSeparator("=").split(data);
        }
    }

    /**
     * Deserializing a json string is more complicated.
     *
     * XStream does not natively support it. Rather, it uses a JettisonMappedXmlDriver to convert to
     * intermediate xml and then deserializes that into the desired object. At this time, there is a
     * known issue with the Jettison driver involving elements that come after an array in the json
     * string.
     *
     * http://jira.codehaus.org/browse/JETTISON-48
     *
     * The code below is a hack: it treats the json string as text, then converts it to the
     * intermediate xml and then deserializes that into the SeedRequest object.
     */
    protected String convertJson(String entityText) throws IOException {
        HierarchicalStreamDriver driver = new JettisonMappedXmlDriver();
        StringReader reader = new StringReader(entityText);
        HierarchicalStreamReader hsr = driver.createReader(reader);
        StringWriter writer = new StringWriter();
        new HierarchicalStreamCopier().copy(hsr, new PrettyPrintWriter(writer));
        writer.close();
        return writer.toString();
    }


}
