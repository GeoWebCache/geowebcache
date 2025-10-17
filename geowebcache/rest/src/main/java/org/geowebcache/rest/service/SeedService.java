/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Marius Suta / The Open Planning Project 2008
 * @author Arne Kepp / The Open Planning Project 2009
 * @author David Vick / Boundless 2017
 *     <p>Original file
 *     <p>SeedRestlet.java
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
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.IOUtils;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.ContextualConfigurationProvider;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.io.GeoWebCacheXStream;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.rest.exception.RestException;
import org.geowebcache.seed.GWCTask;
import org.geowebcache.seed.SeedRequest;
import org.geowebcache.seed.TileBreeder;
import org.geowebcache.util.ApplicationContextProvider;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.context.WebApplicationContext;

@Service
public class SeedService {

    static final Logger LOG = Logging.getLogger(SeedService.class.getName());

    @Autowired
    TileBreeder seeder;

    private final WebApplicationContext context;

    @Autowired
    public SeedService(ApplicationContextProvider appCtx) {
        context = appCtx.getApplicationContext();
    }

    /** GET method for querying running GWC tasks */
    public ResponseEntity<?> getRunningTasks(HttpServletRequest request) {
        try {
            XStream xs = new GeoWebCacheXStream(new JsonHierarchicalStreamDriver());
            long[][] list = seeder.getStatusList();
            JSONObject obj = new JSONObject(xs.toXML(list));
            return constructResponse(MediaType.APPLICATION_JSON, obj.toString(), HttpStatus.OK);
        } catch (JSONException jse) {
            return constructResponse(MediaType.TEXT_PLAIN, "error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** GET method for querying running tasks for the provided layer */
    public ResponseEntity<?> getRunningLayerTasks(String layer) {
        try {
            XStream xs = new GeoWebCacheXStream(new JsonHierarchicalStreamDriver());
            long[][] list;
            try {
                list = findTileLayer(layer);
            } catch (GeoWebCacheException e) {
                return constructResponse(MediaType.TEXT_PLAIN, e.getMessage(), HttpStatus.BAD_REQUEST);
            }
            JSONObject obj = new JSONObject(xs.toXML(list));
            return constructResponse(MediaType.APPLICATION_JSON, obj.toString(), HttpStatus.OK);
        } catch (JSONException jse) {
            LOG.log(Level.SEVERE, jse.getMessage(), jse);
            return constructResponse(MediaType.TEXT_PLAIN, "error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public ResponseEntity<?> getRunningLayerTasksXml(String layer) {
        XStream xs = new GeoWebCacheXStream();
        long[][] list;
        try {
            list = findTileLayer(layer);
        } catch (GeoWebCacheException e) {
            return constructResponse(MediaType.TEXT_PLAIN, e.getMessage(), HttpStatus.BAD_REQUEST);
        }
        return constructResponse(MediaType.APPLICATION_XML, xs.toXML(list), HttpStatus.OK);
    }

    private ResponseEntity<?> constructResponse(MediaType mediaType, String message, HttpStatus status) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        return new ResponseEntity<>(message, headers, status);
    }

    private long[][] findTileLayer(String layer) throws GeoWebCacheException {
        if (null == layer) {
            return seeder.getStatusList();
        } else {
            seeder.findTileLayer(layer);
            return seeder.getStatusList(layer);
        }
    }

    /** Method to kill running tasks for all of GWC or just the provided layer. */
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
                    throw new RestException(
                            "Unknown kill_all code: '" + killCode + "'. One of all|running|pending is expected.",
                            HttpStatus.BAD_REQUEST);
                }
                List<GWCTask> terminatedTasks = new LinkedList<>();
                List<GWCTask> nonTerminatedTasks = new LinkedList<>();
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

    /** Method to do the seeding and truncating. */
    public ResponseEntity<?> doSeeding(HttpServletRequest request, String layer, String extension, String body) {
        XStream xs = configXStream(new GeoWebCacheXStream(new DomDriver()));

        Object obj = null;

        try {
            if (extension == null || extension.equalsIgnoreCase("xml")) {
                obj = xs.fromXML(body);
            } else if (extension.equalsIgnoreCase("json")) {
                obj = xs.fromXML(convertJson(body));
            } else {
                throw new RestException(
                        "Format extension unknown or not specified: " + extension, HttpStatus.BAD_REQUEST);
            }
            handleRequest(layer, obj);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            return new ResponseEntity<>(headers, HttpStatus.OK);
        } catch (IOException e) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            return new ResponseEntity<>(headers, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** METHOD that handles the seeding/truncating task from the POST method. */
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
        return XMLConfiguration.getConfiguredXStreamWithContext(
                xs, context, ContextualConfigurationProvider.Context.REST);
    }

    private Map<String, String> splitToMap(String data) {
        if (data.contains("&")) {
            return Splitter.on("&").withKeyValueSeparator("=").split(data);
        } else {
            return Splitter.on(" ").withKeyValueSeparator("=").split(data);
        }
    }

    /**
     * Deserializing a json string is more complicated.
     *
     * <p>XStream does not natively support it. Rather, it uses a JettisonMappedXmlDriver to convert to intermediate xml
     * and then deserializes that into the desired object. At this time, there is a known issue with the Jettison driver
     * involving elements that come after an array in the json string.
     *
     * <p>http://jira.codehaus.org/browse/JETTISON-48
     *
     * <p>The code below is a hack: it treats the json string as text, then converts it to the intermediate xml and then
     * deserializes that into the SeedRequest object.
     */
    protected String convertJson(String entityText) throws IOException {
        HierarchicalStreamDriver driver = new JettisonMappedXmlDriver();
        try (StringReader reader = new StringReader(entityText);
                StringWriter writer = new StringWriter()) {
            HierarchicalStreamReader hsr = driver.createReader(reader);
            new HierarchicalStreamCopier().copy(hsr, new PrettyPrintWriter(writer));
            return writer.toString();
        }
    }
}
