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
 * @author Arne Kepp / The Open Planning Project 2008 
 */
package org.geowebcache.rest.seed;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.filter.parameters.FloatParameterFilter;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.filter.parameters.RegexParameterFilter;
import org.geowebcache.filter.parameters.StringParameterFilter;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.mime.MimeType;
import org.geowebcache.rest.GWCRestlet;
import org.geowebcache.rest.RestletException;
import org.geowebcache.seed.GWCTask;
import org.geowebcache.seed.SeedRequest;
import org.geowebcache.seed.TileBreeder;
import org.geowebcache.seed.GWCTask.PRIORITY;
import org.geowebcache.seed.GWCTask.TYPE;
import org.geowebcache.storage.TileRange;
import org.geowebcache.util.ServletUtils;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.springframework.util.Assert;

public class SeedFormRestlet extends GWCRestlet {
    // private static Log log = LogFactory.getLog(org.geowebcache.rest.seed.SeedFormRestlet.class);

    private XMLConfiguration xmlConfig;

    private TileBreeder seeder;

    public void handle(Request request, Response response) {
        
        Method met = request.getMethod();
        try {
            if (met.equals(Method.GET)) {
                doGet(request, response);
            } else if (met.equals(Method.POST)) {
                try {
                    doPost(request, response);
                } catch (GeoWebCacheException e) {
                    throw new RestletException(e.getMessage(), Status.CLIENT_ERROR_BAD_REQUEST);
                }
            } else {
                throw new RestletException("Method not allowed",
                        Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
            }
        } catch (RestletException re) {
            response.setEntity(re.getRepresentation());
            response.setStatus(re.getStatus());
        }
    }

    public void doGet(Request request, Response response) throws RestletException {
        // String layerName = (String) request.getAttributes().get("layer");
        String layerName = null;
        try {
            layerName = URLDecoder.decode((String) request.getAttributes().get("layer"), "UTF-8");
        } catch (UnsupportedEncodingException uee) {
        }

        TileLayer tl;
        try {
            tl = seeder.findTileLayer(layerName);
        } catch (GeoWebCacheException e) {
            throw new RestletException(e.getMessage(), Status.CLIENT_ERROR_BAD_REQUEST);
        }

        response.setEntity(makeFormPage(tl), MediaType.TEXT_HTML);
    }

    public void doPost(Request req, Response resp) throws RestletException, GeoWebCacheException {
        String layerName = null;
        try {
            layerName = URLDecoder.decode((String) req.getAttributes().get("layer"), "UTF-8");
        } catch (UnsupportedEncodingException uee) {
        }

        Form form = req.getEntityAsForm();

        if (form == null) {
            throw new RestletException("Unable to parse form result.",
                    Status.CLIENT_ERROR_BAD_REQUEST);
        }

        // String layerName = form.getFirst("layerName").getValue();

        TileLayer tl = null;
        try {
            tl = seeder.findTileLayer(layerName);
        } catch (GeoWebCacheException e) {
            throw new RestletException(e.getMessage(), Status.CLIENT_ERROR_BAD_REQUEST);
        }

        if (form.getFirst("kill_thread") != null) {
            handleKillThreadPost(form, tl, resp);
        } else if (form.getFirst("kill_all") != null) {
            handleKillAllThreadsPost(form, tl, resp);
        } else if (form.getFirst("minX") != null) {
            handleDoSeedPost(form, tl, resp);
        } else {
            throw new RestletException(
                    "Unknown or malformed request. Please try again, somtimes the form "
                            + "is not properly received. This frequently happens on the first POST "
                            + "after a restart. The POST was to " + req.getResourceRef().getPath(),
                    Status.CLIENT_ERROR_BAD_REQUEST);
        }
    }

    private String makeFormPage(TileLayer tl) {

        StringBuilder doc = new StringBuilder();

        makeHeader(doc, tl, "map = initOpenLayers()");

        makeTaskList(doc, tl);

        makeWarningsAndHints(doc, tl);

        makeFormHeader(doc, tl);

        makeThreadCountPullDown(doc);

        makeTypePullDown(doc);
        
        makePriorityPullDown(doc);

        makeThroughputPullDown(doc);

        makeScheduleFields(doc);

        makeGridSetPulldown(doc, tl);

        makeFormatPullDown(doc, tl);

        makeZoomStartPullDown(doc, tl);

        makeZoomStopPullDown(doc, tl);

        makeModifiableParameters(doc, tl);

        makeBboxFields(doc);

        makeSubmit(doc);

        makeOpenLayersBBoxInput(doc);
        
        makeFormFooter(doc);

        makeFooter(doc);

        return doc.toString();
    }

    private void makeModifiableParameters(StringBuilder doc, TileLayer tl) {
        List<ParameterFilter> parameterFilters = tl.getParameterFilters();
        if (parameterFilters == null || parameterFilters.size() == 0) {
            return;
        }
        doc.append("<tr><td>Modifiable Parameters:</td><td>\n");
        doc.append("<table>");
        for (ParameterFilter pf : parameterFilters) {
            Assert.notNull(pf);
            String key = pf.getKey();
            String defaultValue = pf.getDefaultValue();
            List<String> legalValues = pf.getLegalValues();
            doc.append("<tr><td>").append(key.toUpperCase()).append(": ").append("</td><td>");
            String parameterId = "parameter_" + key;
            if (pf instanceof StringParameterFilter) {
                Map<String, String> keysValues = makeParametersMap(defaultValue, legalValues);
                makePullDown(doc, parameterId, keysValues, defaultValue);
            } else if (pf instanceof RegexParameterFilter) {
                makeTextInput(doc, parameterId, 25);
            } else if (pf instanceof FloatParameterFilter) {
                Map<String, String> keysValues = makeParametersMap(defaultValue, legalValues);
                makePullDown(doc, parameterId, keysValues, defaultValue);
            } else if ("org.geowebcache.filter.parameters.NaiveWMSDimensionFilter".equals(pf
                    .getClass().getName())) {
                makeTextInput(doc, parameterId, 25);
            } else {
                throw new IllegalStateException("Unknown parameter filter type for layer '"
                        + tl.getName() + "': " + pf.getClass().getName());
            }
            doc.append("</td></tr>");
        }
        doc.append("</table>");
        doc.append("</td></tr>\n");
    }

    private Map<String, String> makeParametersMap(String defaultValue, List<String> legalValues) {
        Map<String, String> map = new TreeMap<String, String>();
        for (String s : legalValues) {
            map.put(s, s);
        }
        map.put(defaultValue, defaultValue);
        return map;
    }

    private String makeResponsePage(TileLayer tl) {

        StringBuilder doc = new StringBuilder();

        makeHeader(doc, tl);

        doc.append("<h3>Task submitted</h3>\n");

        doc.append("<p>Below you can find a list of currently executing threads, take the numbers with a grain of salt");
        doc.append(" until the thread has had a chance to run for a few minutes. ");

        makeTaskList(doc, tl);

        makeFooter(doc);

        return doc.toString();
    }

    private void makeTypePullDown(StringBuilder doc) {
        doc.append("<tr><td>Type of operation:</td><td>\n");
        Map<String, String> keysValues = new TreeMap<String, String>();

        keysValues.put("Truncate - remove tiles", "truncate");
        keysValues.put("Seed - generate missing tiles", "seed");
        keysValues.put("Reseed - regenerate all tiles", "reseed");

        makePullDown(doc, "type", keysValues, "Seed - generate missing tiles");
        doc.append("</td></tr>\n");
    }

    private void makeThreadCountPullDown(StringBuilder doc) {
        doc.append("<tr><td>Number of threads to use:</td><td>\n");
        Map<String, String> keysValues = new TreeMap<String, String>();

        for (int i = 1; i < 17; i++) {
            if (i < 10) {
                keysValues.put("0" + Integer.toString(i), "0" + Integer.toString(i));
            } else {
                keysValues.put(Integer.toString(i), Integer.toString(i));
            }
        }
        makePullDown(doc, "threadCount", keysValues, Integer.toString(2));
        doc.append("</td></tr>\n");
    }

    private void makeBboxFields(StringBuilder doc) {
        doc.append("<tr><td valign=\"top\">Bounding box:</td><td>\n");
        makeTextInput(doc, "minX", 12);
        makeTextInput(doc, "minY", 12);
        makeTextInput(doc, "maxX", 12);
        makeTextInput(doc, "maxY", 12);
        makeButton(doc, "updateExtent", "Update", "onclick=\"updateFeature();\"");
        makeButton(doc, "resetExtent", "Reset", "onclick=\"resetFeature()\"");
        doc.append("</br>Approximate values are fine.");
        doc.append("</td></tr>\n");
    }
    
    private void makeOpenLayersBBoxInput(StringBuilder doc) {
        doc.append("<tr><td colspan=\"2\" valign=\"top\">\n");

        doc.append("<div id=\"controls\" style=\"float:left; width: 512px\">\n");
        doc.append("  <div id=\"map\" class=\"bboxinputmap\"></div>\n");
        doc.append("  <div id=\"wrapper\" style=\"float:left\">\n");
        doc.append("    <div id=\"mapLocation\"></div>\n");
        doc.append("    <div id=\"mapScale\"></div>\n");
        doc.append("  </div>\n");
        doc.append("</div>\n");
        
        doc.append("</td></tr>\n");
    }

    private void makeBboxHints(StringBuilder doc, TileLayer tl) {
        Iterator<Entry<String, GridSubset>> iter = tl.getGridSubsets().entrySet().iterator();

        // int minStart = Integer.MAX_VALUE;
        // int maxStop = Integer.MIN_VALUE;

        while (iter.hasNext()) {
            Entry<String, GridSubset> entry = iter.next();
            doc.append("<li>" + entry.getKey().toString() + ":   "
                    + entry.getValue().getOriginalExtent().toString() + "</li>\n");
        }

    }

    private void makeTextInput(StringBuilder doc, String id, int size) {
        doc.append("<input name=\"" + id + "\" id=\"" + id + "\" type=\"text\" size=\"" + size + "\" />\n");
    }

    private void makeSubmit(StringBuilder doc) {
        doc.append("<tr><td></td><td><input type=\"submit\" value=\"Submit\" /></td></tr>\n");
    }

    private void makeButton(StringBuilder doc, String id, String text, String extra) {
        doc.append("<input type=\"button\" name=\"" + id + "\" id=\"" + id + "\" value=\"" + text + "\" " + extra + "/>\n");
    }

    private void makeZoomStopPullDown(StringBuilder doc, TileLayer tl) {
        doc.append("<tr><td>Zoom stop:</td><td>\n");
        makeZoomPullDown(doc, false, tl);
        doc.append("</td></tr>\n");
    }

    private void makeZoomStartPullDown(StringBuilder doc, TileLayer tl) {
        doc.append("<tr><td>Zoom start:</td><td>\n");
        makeZoomPullDown(doc, true, tl);
        doc.append("</td></tr>\n");
    }

    private void makeZoomPullDown(StringBuilder doc, boolean isStart, TileLayer tl) {
        Map<String, String> keysValues = new TreeMap<String, String>();

        Iterator<Entry<String, GridSubset>> iter = tl.getGridSubsets().entrySet().iterator();

        int minStart = Integer.MAX_VALUE;
        int maxStop = Integer.MIN_VALUE;

        while (iter.hasNext()) {
            Entry<String, GridSubset> entry = iter.next();

            int start = entry.getValue().getZoomStart();
            int stop = entry.getValue().getZoomStop();

            if (start < minStart) {
                minStart = start;
            }
            if (stop > maxStop) {
                maxStop = stop;
            }
        }

        for (int i = minStart; i <= maxStop; i++) {
            if (i < 10) {
                keysValues.put("0" + Integer.toString(i), "0" + Integer.toString(i));
            } else {
                keysValues.put(Integer.toString(i), Integer.toString(i));
            }
        }

        if (isStart) {
            if (minStart < 10) {
                makePullDown(doc, "zoomStart", keysValues, "0" + Integer.toString(minStart));
            } else {
                makePullDown(doc, "zoomStart", keysValues, Integer.toString(minStart));
            }

        } else {
            int midStop = (minStart + maxStop) / 2;
            if (midStop < 10) {
                makePullDown(doc, "zoomStop", keysValues, "0" + Integer.toString(midStop));
            } else {
                makePullDown(doc, "zoomStop", keysValues, Integer.toString(midStop));
            }
        }
    }

    private void makeFormatPullDown(StringBuilder doc, TileLayer tl) {
        doc.append("<tr><td>Format:</td><td>\n");
        Map<String, String> keysValues = new TreeMap<String, String>();

        Iterator<MimeType> iter = tl.getMimeTypes().iterator();

        while (iter.hasNext()) {
            MimeType mime = iter.next();
            keysValues.put(mime.getFormat(), mime.getFormat());
        }

        makePullDown(doc, "format", keysValues, ImageMime.png.getFormat());
        doc.append("</td></tr>\n");
    }

    private void makePriorityPullDown(StringBuilder doc) {
        doc.append("<tr><td>Priority:</td><td>\n");
        Map<String, String> keysValues = new TreeMap<String, String>();

        Iterator<PRIORITY> iter = Arrays.asList(PRIORITY.values()).iterator();

        while (iter.hasNext()) {
            PRIORITY p = iter.next();
            keysValues.put(p.toString(), p.name());
        }

        makePullDown(doc, "priority", keysValues, GWCTask.PRIORITY.LOW.toString());
        doc.append("</td></tr>\n");
    }

    private void makeThroughputPullDown(StringBuilder doc) {
        doc.append("<tr><td>Throughput:</td><td>\n");
        Map<String, String> keysValues = new TreeMap<String, String>();

        for(int i = 1; i < 4; i++) {
            for(int j = 1; j <= 100; j*=10) {
                if(i == 3) {
                    i = 5;
                }
                int tps = i * j;
                keysValues.put(String.format("%03d", tps), String.format("%d requests / second", tps));
            }
        }

        keysValues.put("-1", "No Restrictions");
        
        makePullDown(doc, "maxThroughput", keysValues, "No Restrictions", true);
        doc.append("</td></tr>\n");
    }

    private void makeScheduleFields(StringBuilder doc) {
        doc.append("<tr><td>Schedule (<a target=\"_blank\" href=\"http://en.wikipedia.org/wiki/Cron\">CRON</a>):</td><td>\n");

        doc.append("<input type=\"radio\" name=\"is_scheduled\" value=\"false\" checked=\"checked\"/>Now ");
        doc.append("<input type=\"radio\" name=\"is_scheduled\" value=\"true\"/>Repeat\n");
        doc.append("<tr><td>&nbsp;</td><td>\n");
        doc.append("<input type=\"text\" name=\"schedule\"/>\n");
        
        doc.append("</td></tr>\n");
    }

    private void makeGridSetPulldown(StringBuilder doc, TileLayer tl) {
        doc.append("<tr><td>Grid Set:</td><td>\n");
        Map<String, String> keysValues = new TreeMap<String, String>();

        Iterator<String> iter = tl.getGridSubsets().keySet().iterator();

        String firstGridSetId = null;
        while (iter.hasNext()) {
            String gridSetId = iter.next();
            if (firstGridSetId == null) {
                firstGridSetId = gridSetId;
            }
            keysValues.put(gridSetId, gridSetId);
        }

        makePullDown(doc, "gridSetId", keysValues, firstGridSetId);
        doc.append("</td></tr>\n");
    }

    private void makePullDown(StringBuilder doc, String id, Map<String, String> keysValues,
            String defaultKey) {
        makePullDown(doc, id, keysValues, defaultKey, false);
    }
    private void makePullDown(StringBuilder doc, String id, Map<String, String> keysValues,
            String defaultKey, boolean orderByValue) {
    
        doc.append("<select name=\"" + id + "\">\n");

        Iterator<Entry<String, String>> iter = keysValues.entrySet().iterator();

        while (iter.hasNext()) {
            Entry<String, String> entry = iter.next();
            
            String key;
            String value;
            
            if(orderByValue) {
                key = entry.getValue();
                value = entry.getKey();
            } else {
                key = entry.getKey();
                value = entry.getValue();
            }
            if (key.equals(defaultKey)) {
                doc.append("<option value=\"" + value + "\" selected=\"selected\">" + key + "</option>\n");
            } else {
                doc.append("<option value=\"" + value + "\">" + key + "</option>\n");
            }
        }

        doc.append("</select>\n");
    }

    private void makeFormHeader(StringBuilder doc, TileLayer tl) {
        doc.append("<h4>Create a new task:</h4>\n");
        doc.append("<form id=\"seed\" action=\"./" + tl.getName() + "\" method=\"post\">\n");
        doc.append("<table border=\"0\" cellspacing=\"10\">\n");
    }

    private void makeFormFooter(StringBuilder doc) {
        doc.append("</table>\n");
        doc.append("</form>\n");
    }

    private void makeHeader(StringBuilder doc, TileLayer tl) {
        makeHeader(doc, tl, null);
    }
    private void makeHeader(StringBuilder doc, TileLayer tl, String bodyOnLoad) {
        String basemapConfig = xmlConfig.getBasemapConfig();
        
        if(basemapConfig == null) {
            basemapConfig = "    return null;\n";
        }
        
        String extras = "<script type=\"text/javascript\" src=\"../../openlayers/OpenLayers.js\"></script>\n";
        
        extras += "<script type=\"text/javascript\">\n" + 
                  "  var layerName = '" + tl.getName() + "';\n" + 
                  "  var layerFormat = '" + tl.getDefaultMimeType().getMimeType() + "';\n" + 
                  "  var layerTileSize = new OpenLayers.Size(" + tl.getDefaultGridSubset().getTileWidth() + "," + tl.getDefaultGridSubset().getTileHeight() + ");\n" + 
                  "  var layerProjection = new OpenLayers.Projection('" + tl.getDefaultGridSubset().getSRS() + "');\n" + 
                  "  var layerResolutions = " + Arrays.toString(tl.getDefaultGridSubset().getResolutions()) + ";\n" +
                  "  var layerExtents = new OpenLayers.Bounds(" + tl.getDefaultGridSubset().getOriginalExtent().toString() + ");\n" + 
                  "  var maxExtents = new OpenLayers.Bounds(" + tl.getDefaultGridSubset().getGridSetBounds().toString() + ");\n" + 
                  "  var layerUnits = '" + tl.getDefaultGridSubset().getGridSet().guessMapUnits() + "';\n" +
                  "  var layerDotsPerInch = " + tl.getDefaultGridSubset().getDotsPerInch() + "\n;" +
                  "  function getBasemapLayer() {\n" + 
                  basemapConfig +
                  "  }\n" +
                  "</script>\n";

        extras += "<script type=\"text/javascript\" src=\"../../js/seedform.js\"></script>\n";
        
        extras += "<link rel=\"stylesheet\" href=\"../../openlayers/theme/default/style.css\" type=\"text/css\">\n" + 
                  "  <style type=\"text/css\">\n" + 
                  "  .bboxinputmap { width: 768px; height: 512px; border: 1px solid #ccc; }\n" + 
                  "  .olControlEditingToolbar .olControlSizeItemActive {\n" +  
                  "    background-position: 0px -23px;\n" + 
                  "  }\n" + 
                  "  .olControlEditingToolbar .olControlSizeItemInactive {\n" +  
                  "    background-position: 0px -0px;\n" + 
                  "  }\n" + 
                  "</style>\n";

        String bodyTag = "<body>";
        if(bodyOnLoad != null) {
            bodyTag = String.format("<body onload=\"%s\">", bodyOnLoad);
        }
        
        doc.append("<html>\n" + ServletUtils.gwcHtmlHeader("GWC Seed Form", extras) + bodyTag + "\n"
                + ServletUtils.gwcHtmlLogoLink("../../"));
    }

    private void makeWarningsAndHints(StringBuilder doc, TileLayer tl) {
        doc.append("<h4>Please note:</h4><ul>\n"
                + "<li>This minimalistic interface does not check for correctness.</li>\n"
                + "<li>Seeding past zoomlevel 20 is usually not recommended.</li>\n"
                + "<li>Truncating KML will also truncate all KMZ archives.</li>\n"
                + "<li>Please check the logs of the container to look for error messages and progress indicators.</li>\n"
                + "<li>Responding to user requests for tiles is likely at Normal priority. Usually, seed tasks should be below this.</li>\n"
                + "<li>Truncating at normal priority is common to ensure user based seeding of the latest tiles.</li>\n"
                + "<li>Use high priority sparingly, if at all.</li>\n"
                + "</ul>\n");

        doc.append("Here are the max bounds, if you do not specify bounds these will be used.\n");
        doc.append("<ul>\n");
        makeBboxHints(doc, tl);
        doc.append("</ul>\n");
    }

    private void makeTaskList(StringBuilder doc, TileLayer tl) {
        doc.append("<h4>List of currently executing tasks:</h4>\n");

        Iterator<Entry<Long, GWCTask>> iter = seeder.getRunningTasksIterator();

        boolean tasks = false;
        if (!iter.hasNext()) {
            doc.append("<ul><li><i>none</i></li></ul>\n");
        } else {
            doc.append("<table border=\"0\" cellspacing=\"10\">");
            doc.append("<tr style=\"font-weight: bold;\"><td>Id</td><td>Layer</td><td>Type</td><td>Estimated # of tiles</td>"
                    + "<td>Tiles completed</td><td>Time elapsed</td><td>Time remaining</td><td>Threads</td><td>&nbsp;</td>");
            doc.append("<td>").append(makeKillallThreadsForm(tl)).append("</td>");
            doc.append("</tr>");
            tasks = true;
        }

        while (iter.hasNext()) {
            Entry<Long, GWCTask> entry = iter.next();
            GWCTask task = entry.getValue();

            final long spent = task.getTimeSpent();
            final long remining = task.getTimeRemaining();
            final long tilesDone = task.getTilesDone();
            final long tilesTotal = task.getTilesTotal();

            NumberFormat nf = NumberFormat.getInstance(Locale.ENGLISH);
            nf.setGroupingUsed(true);
            final String tilesTotalStr;
            if (tilesTotal < 0) {
                tilesTotalStr = "Too many to count";
            } else {
                tilesTotalStr = nf.format(tilesTotal);
            }
            final String tilesDoneStr = nf.format(task.getTilesDone());
            String timeSpent = toTimeString(spent, tilesDone, tilesTotal);
            String timeRemaining = toTimeString(remining, tilesDone, tilesTotal);

            doc.append("<tr>");
            doc.append("<td>").append(entry.getKey()).append("</td>");
            doc.append("<td>").append(task.getLayerName()).append("</td>");
            doc.append("<td>").append(task.getType()).append("</td>");
            doc.append("<td>").append(tilesTotalStr).append("</td>");
            doc.append("<td>").append(tilesDoneStr).append("</td>");
            doc.append("<td>").append(timeSpent).append("</td>");
            doc.append("<td>").append(timeRemaining).append("</td>");
            doc.append("<td>(Thread ").append(task.getThreadOffset() + 1).append(" of ")
                    .append(task.getThreadCount()).append(") </td>");
            doc.append("<td>").append(makeThreadKillForm(entry.getKey(), tl)).append("</td>");
            doc.append("<tr>");
        }

        if (tasks) {
            doc.append("</table>");
        }
        doc.append("<p><a href=\"./" + tl.getName() + "\">Refresh list</a></p>\n");
    }

    private String toTimeString(long timeSeconds, final long tilesDone, final long tilesTotal) {
        String timeString;
        if (tilesDone < 50) {
            timeString = " Estimating...";
        } else {
            final int MINUTE_SECONDS = 60;
            final int HOUR_SECONDS = MINUTE_SECONDS * 60;
            final int DAY_SECONDS = HOUR_SECONDS * 24;

            if (timeSeconds == -2 && tilesDone < tilesTotal) {
                timeString = " A decade or three.";
            } else {
                if (timeSeconds > DAY_SECONDS) {
                    timeString = (timeSeconds / DAY_SECONDS) + " day(s) ";
                    timeString += ((timeSeconds % DAY_SECONDS) / HOUR_SECONDS) + "h)";
                } else if (timeSeconds > HOUR_SECONDS) {
                    long hours = timeSeconds / HOUR_SECONDS;
                    long minutes = (timeSeconds % HOUR_SECONDS) / MINUTE_SECONDS;
                    timeString = hours + " hour" + (hours > 1 ? "s " : " ");
                    timeString += minutes == 0 ? "" : (minutes + " m");
                } else if (timeSeconds > MINUTE_SECONDS) {
                    long minutes = timeSeconds / MINUTE_SECONDS;
                    long seconds = timeSeconds % MINUTE_SECONDS;
                    timeString = minutes + " minute" + (minutes > 1 ? "s " : " ");
                    timeString += seconds == 0 ? "" : seconds + " s";
                } else {
                    timeString = timeSeconds + " second" + (timeSeconds == 1 ? "" : "s");
                }
            }
        }
        return timeString;
    }

    private String makeThreadKillForm(Long key, TileLayer tl) {
        String ret = "<form form id=\"kill\" action=\"./"
                + tl.getName()
                + "\" method=\"post\">"
                + "<input type=\"hidden\" name=\"kill_thread\"  value=\"1\" />"
                + "<input type=\"hidden\" name=\"thread_id\"  value=\""
                + key
                + "\" />"
                + "<span><input style=\"padding: 0; margin-bottom: -12px; border: 1;\"type=\"submit\" value=\"Kill Thread\"></span>"
                + "</form>";

        return ret;
    }

    private String makeKillallThreadsForm(TileLayer tl) {
        String ret = "<form form id=\"kill\" action=\"./"
                + tl.getName()
                + "\" method=\"post\">"
                + "<input type=\"hidden\" name=\"kill_all\"  value=\"1\" />"
                + "<span><input style=\"padding: 0; margin-bottom: -12px; border: 1;\"type=\"submit\" value=\"Kill All Threads\"></span>"
                + "</form>";

        return ret;
    }

    private void makeFooter(StringBuilder doc) {
        doc.append("</body></html>\n");
    }

    private void handleKillAllThreadsPost(Form form, TileLayer tl, Response resp) {
        Iterator<Entry<Long, GWCTask>> runningTasks = seeder.getRunningTasksIterator();
        while (runningTasks.hasNext()) {
            Entry<Long, GWCTask> next = runningTasks.next();
            GWCTask task = next.getValue();
            long taskId = task.getTaskId();
            seeder.terminateGWCTask(taskId);
        }
        StringBuilder doc = new StringBuilder();

        makeHeader(doc, tl);
        doc.append("<ul><li>Requested to terminate all tasks.</li></ul>");
        doc.append("<p><a href=\"./" + tl.getName() + "\">Go back</a></p>\n");

        resp.setEntity(doc.toString(), MediaType.TEXT_HTML);
    }

    private void handleKillThreadPost(Form form, TileLayer tl, Response resp) {
        String id = form.getFirstValue("thread_id");

        StringBuilder doc = new StringBuilder();

        makeHeader(doc, tl);

        if (seeder.terminateGWCTask(Long.parseLong(id))) {
            doc.append("<ul><li>Requested to terminate task " + id + ".</li></ul>");
        } else {
            doc.append("<ul><li>Sorry, either task "
                    + id
                    + " has not started yet, or it is a truncate task that cannot be interrutped.</li></ul>");
            ;
        }

        doc.append("<p><a href=\"./" + tl.getName() + "\">Go back</a></p>\n");

        resp.setEntity(doc.toString(), MediaType.TEXT_HTML);
    }

    private void handleDoSeedPost(Form form, TileLayer tl, Response resp) throws RestletException,
            GeoWebCacheException {
        BoundingBox bounds = null;

        if (form.getFirst("minX").getValue() != null) {
            bounds = new BoundingBox(parseDouble(form, "minX"), parseDouble(form, "minY"),
                    parseDouble(form, "maxX"), parseDouble(form, "maxY"));
        }

        String gridSetId = form.getFirst("gridSetId").getValue();

        int threadCount = Integer.parseInt(form.getFirst("threadCount").getValue());
        int zoomStart = Integer.parseInt(form.getFirst("zoomStart").getValue());
        int zoomStop = Integer.parseInt(form.getFirst("zoomStop").getValue());
        String format = form.getFirst("format").getValue();
        Map<String, String> fullParameters;
        {
            Map<String, String> parameters = new HashMap<String, String>();
            Set<String> paramNames = form.getNames();
            String prefix = "parameter_";
            for (String name : paramNames) {
                if (name.startsWith(prefix)) {
                    String paramName = name.substring(prefix.length());
                    String value = form.getFirstValue(name);
                    parameters.put(paramName, value);
                }
            }
            fullParameters = tl.getModifiableParameters(parameters, "UTF-8");
        }

        TYPE type = TYPE.valueOf(form.getFirst("type").getValue().toUpperCase());
        
        // new attributes can't be expected to be set or older clients won't work.
        PRIORITY priority = PRIORITY.LOW;
        if(form.getFirst("priority") != null) {
            priority = PRIORITY.valueOf(form.getFirst("priority").getValue());
        } else {
            if(type == TYPE.TRUNCATE) {
                priority = PRIORITY.NORMAL;
            }
        }
        
        String schedule = GWCTask.NO_SCHEDULE;
        if(form.getFirst("is_scheduled") != null) {
            if(form.getFirst("is_scheduled").getValue().equals("true")) {
                if(form.getFirst("schedule") != null) {
                    schedule = form.getFirst("schedule").getValue();
                }
            }
        }
        
        int maxThroughput = GWCTask.NO_THROUGHOUT_RESTRICTIONS;
        if(form.getFirst("maxThroughput") != null) {
            maxThroughput = Integer.parseInt(form.getFirst("maxThroughput").getValue());
        }

        final String layerName = tl.getName();
        SeedRequest sr = new SeedRequest(layerName, bounds, gridSetId, threadCount, zoomStart,
                zoomStop, format, type, priority, schedule, maxThroughput, fullParameters);

        try {
            seeder.seed(sr);
        } catch(GeoWebCacheException gwce) {
            throw new RestletException(gwce.getMessage(), Status.SERVER_ERROR_INTERNAL);
        }

        // Give the thread executor a chance to run
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            // Ok, no worries
        }

        resp.setEntity(this.makeResponsePage(tl), MediaType.TEXT_HTML);
    }

    private static double parseDouble(Form form, String key) throws RestletException {
        String value = form.getFirst(key).getValue();
        if (value == null || value.length() == 0)
            throw new RestletException("Missing value for " + key, Status.CLIENT_ERROR_BAD_REQUEST);
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException nfe) {
            throw new RestletException("Value for " + key + " is not a double",
                    Status.CLIENT_ERROR_BAD_REQUEST);
        }
    }

    public void setTileBreeder(TileBreeder seeder) {
        this.seeder = seeder;
    }

    public void setXMLConfiguration(XMLConfiguration xmlConfig) {
        this.xmlConfig = xmlConfig;
    }
}
