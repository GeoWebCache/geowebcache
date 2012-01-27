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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.geowebcache.GeoWebCacheException;
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
import org.geowebcache.seed.GWCTask.STATE;
import org.geowebcache.seed.GWCTask.TYPE;
import org.geowebcache.seed.SeedRequest;
import org.geowebcache.seed.TileBreeder;
import org.geowebcache.storage.TileRange;
import org.geowebcache.util.ServletUtils;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.springframework.util.Assert;

/**
 *
 */
public class SeedFormRestlet extends GWCRestlet {
    // private static Log log = LogFactory.getLog(org.geowebcache.rest.seed.SeedFormRestlet.class);

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

        handleDoGet(response, tl, false);
    }

    private void handleDoGet(Response response, TileLayer tl, boolean listAllTasks) {
        response.setEntity(makeFormPage(tl, listAllTasks), MediaType.TEXT_HTML);
    }

    public void doPost(Request req, Response resp) throws RestletException, GeoWebCacheException {
        final TileLayer tl;
        {
            String layerName = null;
            if (req.getAttributes().containsKey("layer")) {
                try {
                    layerName = URLDecoder.decode((String) req.getAttributes().get("layer"),
                            "UTF-8");
                } catch (UnsupportedEncodingException uee) {
                    throw new RuntimeException(uee);
                }
                try {
                    tl = seeder.findTileLayer(layerName);
                } catch (GeoWebCacheException e) {
                    throw new RestletException(e.getMessage(), Status.CLIENT_ERROR_BAD_REQUEST);
                }
            } else {
                tl = null;
            }
        }

        Form form = req.getEntityAsForm();

        if (form == null) {
            throw new RestletException("Unable to parse form result.",
                    Status.CLIENT_ERROR_BAD_REQUEST);
        }

        if (form.getFirst("list") != null) {
            if (tl == null) {
                throw new RestletException("No layer specified", Status.CLIENT_ERROR_BAD_REQUEST);
            }
            boolean listAllTasks = "all".equals(form.getFirst("list").getValue());
            handleDoGet(resp, tl, listAllTasks);
        } else if (form.getFirst("kill_thread") != null) {
            handleKillThreadPost(form, tl, resp);
        } else if (form.getFirst("kill_all") != null) {
            handleKillAllThreadsPost(form, tl, resp);
        } else if (form.getFirst("minX") != null) {
            if (tl == null) {
                throw new RestletException("No layer specified", Status.CLIENT_ERROR_BAD_REQUEST);
            }
            handleDoSeedPost(form, tl, resp);
        } else {
            throw new RestletException(
                    "Unknown or malformed request. Please try again, somtimes the form "
                            + "is not properly received. This frequently happens on the first POST "
                            + "after a restart. The POST was to " + req.getResourceRef().getPath(),
                    Status.CLIENT_ERROR_BAD_REQUEST);
        }
    }

    private String makeFormPage(TileLayer tl, boolean listAllTasks) {

        StringBuilder doc = new StringBuilder();

        makeHeader(doc);

        makeTaskList(doc, tl, listAllTasks);

        makeWarningsAndHints(doc, tl);

        makeFormHeader(doc, tl);

        makeThreadCountPullDown(doc);

        makeTypePullDown(doc);

        makeGridSetPulldown(doc, tl);

        makeFormatPullDown(doc, tl);

        makeZoomStartPullDown(doc, tl);

        makeZoomStopPullDown(doc, tl);

        makeModifiableParameters(doc, tl);

        makeBboxFields(doc);

        makeSubmit(doc);

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
                FloatParameterFilter floatFilter = (FloatParameterFilter) pf;
                if (floatFilter.getValues().isEmpty()) {
                    // accepts any value
                    makeTextInput(doc, parameterId, 25);
                } else {
                    Map<String, String> keysValues = makeParametersMap(defaultValue, legalValues);
                    makePullDown(doc, parameterId, keysValues, defaultValue);
                }
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

        makeHeader(doc);

        doc.append("<h3>Task submitted</h3>\n");

        doc.append("<p>Below you can find a list of currently executing tasks, take the numbers with a grain of salt");
        doc.append(" until the task has had a chance to run for a few minutes. ");

        makeTaskList(doc, tl, false);

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
        doc.append("<tr><td>Number of tasks to use:</td><td>\n");
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
        makeTextInput(doc, "minX", 6);
        makeTextInput(doc, "minY", 6);
        makeTextInput(doc, "maxX", 6);
        makeTextInput(doc, "maxY", 6);
        doc.append("</br>These are optional, approximate values are fine.");
        doc.append("</td></tr>\n");
    }

    private void makeBboxHints(StringBuilder doc, TileLayer tl) {

        for (String gridSetId : tl.getGridSubsets()) {
            GridSubset subset = tl.getGridSubset(gridSetId);
            doc.append("<li>" + gridSetId + ":   " + subset.getOriginalExtent().toString()
                    + "</li>\n");
        }

    }

    private void makeTextInput(StringBuilder doc, String id, int size) {
        doc.append("<input name=\"" + id + "\" type=\"text\" size=\"" + size + "\" />\n");
    }

    private void makeSubmit(StringBuilder doc) {
        doc.append("<tr><td></td><td><input type=\"submit\" value=\"Submit\"></td></tr>\n");
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

        int minStart = Integer.MAX_VALUE;
        int maxStop = Integer.MIN_VALUE;

        for (String gridSetId : tl.getGridSubsets()) {
            GridSubset subset = tl.getGridSubset(gridSetId);

            int start = subset.getZoomStart();
            int stop = subset.getZoomStop();

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

    private void makeGridSetPulldown(StringBuilder doc, TileLayer tl) {
        doc.append("<tr><td>Grid Set:</td><td>\n");
        Map<String, String> keysValues = new TreeMap<String, String>();

        String firstGridSetId = null;
        for (String gridSetId : tl.getGridSubsets()) {
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
        doc.append("<select name=\"" + id + "\">\n");

        Iterator<Entry<String, String>> iter = keysValues.entrySet().iterator();

        while (iter.hasNext()) {
            Entry<String, String> entry = iter.next();
            if (entry.getKey().equals(defaultKey)) {
                doc.append("<option value=\"" + entry.getValue() + "\" selected=\"selected\">"
                        + entry.getKey() + "</option>\n");
            } else {
                doc.append("<option value=\"" + entry.getValue() + "\">" + entry.getKey()
                        + "</option>\n");
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

    private void makeHeader(StringBuilder doc) {
        doc.append("<html>\n" + ServletUtils.gwcHtmlHeader("GWC Seed Form") + "<body>\n"
                + ServletUtils.gwcHtmlLogoLink("../../"));
    }

    private void makeWarningsAndHints(StringBuilder doc, TileLayer tl) {
        doc.append("<h4>Please note:</h4><ul>\n"
                + "<li>This minimalistic interface does not check for correctness.</li>\n"
                + "<li>Seeding past zoomlevel 20 is usually not recommended.</li>\n"
                + "<li>Truncating KML will also truncate all KMZ archives.</li>\n"
                + "<li>Please check the logs of the container to look for error messages and progress indicators.</li>\n"
                + "</ul>\n");

        doc.append("Here are the max bounds, if you do not specify bounds these will be used.\n");
        doc.append("<ul>\n");
        makeBboxHints(doc, tl);
        doc.append("</ul>\n");
    }

    private void makeTaskList(StringBuilder doc, TileLayer tl, boolean listAll) {

        doc.append(makeKillallThreadsForm(tl, listAll));

        doc.append("<h4>List of currently executing tasks:</h4>\n");

        Iterator<GWCTask> iter = seeder.getRunningAndPendingTasks();

        boolean tasks = false;
        if (!iter.hasNext()) {
            doc.append("<ul><li><i>none</i></li></ul>\n");
        } else {
            doc.append("<table border=\"0\">");
            doc.append("<tr style=\"font-weight: bold;\"><td style=\"padding-right:20px;\">Id</td><td style=\"padding-right:20px;\">Layer</td><td style=\"padding-right:20px;\">Status</td><td style=\"padding-right:20px;\">Type</td><td>Estimated # of tiles</td>"
                    + "<td style=\"padding-right:20px;\">Tiles completed</td><td style=\"padding-right:20px;\">Time elapsed</td><td>Time remaining</td><td>Tasks</td><td>&nbsp;</td>");
            doc.append("</tr>");
            tasks = true;
        }

        int row = 0;

        final String layerName = tl.getName();
        while (iter.hasNext()) {
            GWCTask task = iter.next();
            if (!listAll && !layerName.equals(task.getLayerName())) {
                continue;
            }
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
            final STATE state = task.getState();

            final String status = STATE.UNSET.equals(state) || STATE.READY.equals(state) ? "PENDING"
                    : state.toString();

            String timeSpent = toTimeString(spent, tilesDone, tilesTotal);
            String timeRemaining = toTimeString(remining, tilesDone, tilesTotal);

            String bgColor = ++row % 2 == 0 ? "#FFFFFF" : "#DDDDDD";
            doc.append("<tr style=\"background-color:" + bgColor + ";\">");
            doc.append("<td style=\"text-align:right\">").append(task.getTaskId()).append("</td>");
            doc.append("<td>");
            if (!layerName.equals(task.getLayerName())) {
                doc.append("<a href=\"./").append(task.getLayerName()).append("\">");
            }
            doc.append(task.getLayerName());
            if (!layerName.equals(task.getLayerName())) {
                doc.append("</a>");
            }
            doc.append("</td>");
            doc.append("<td>").append(status).append("</td>");
            doc.append("<td>").append(task.getType()).append("</td>");
            doc.append("<td>").append(tilesTotalStr).append("</td>");
            doc.append("<td>").append(tilesDoneStr).append("</td>");
            doc.append("<td>").append(timeSpent).append("</td>");
            doc.append("<td>").append(timeRemaining).append("</td>");
            doc.append("<td>(Task ").append(task.getThreadOffset() + 1).append(" of ")
                    .append(task.getThreadCount()).append(") </td>");
            doc.append("<td>").append(makeThreadKillForm(task.getTaskId(), tl)).append("</td>");
            doc.append("<tr>");
        }

        if (tasks) {
            doc.append("</table>");
        }
        doc.append("<p><a href=\"./" + layerName + "\">Refresh list</a></p>\n");
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
                + "<span><input style=\"padding: 0; margin-bottom: -12px; border: 1;\"type=\"submit\" value=\"Kill Task\"></span>"
                + "</form>";

        return ret;
    }

    private String makeKillallThreadsForm(TileLayer tl, boolean listAll) {
        StringBuilder doc = new StringBuilder();

        final String layerName = tl.getName();
        int otherLayersTaskCount = 0;
        if (!listAll) {
            Iterator<GWCTask> tasks = seeder.getRunningAndPendingTasks();
            while (tasks.hasNext()) {
                if (!layerName.equals(tasks.next().getLayerName())) {
                    otherLayersTaskCount++;
                }
            }
        }

        doc.append("<table><tr><td>");
        doc.append("<form form id=\"list\" action=\"./").append(layerName)
                .append("\" method=\"post\">\n");
        doc.append("List ");
        doc.append("<select name=\"list\" onchange=\"this.form.submit();\">\n");
        doc.append("<option value=\"layer\"").append(listAll ? "" : " selected")
                .append(">this Layer tasks</option>\n");
        doc.append("<option value=\"all\"").append(listAll ? " selected" : "")
                .append(">all Layers tasks</option>\n");
        doc.append("</select>\n");
        if (!listAll) {
            doc.append(" (there are ");
            if (otherLayersTaskCount > 0) {
                doc.append(otherLayersTaskCount);
            } else {
                doc.append("no");
            }
            doc.append(" tasks for other Layers)");
        }
        doc.append("</form>\n");

        doc.append("</td></tr><tr><td>");

        doc.append("<form form id=\"kill\" action=\"./").append(layerName)
                .append("\" method=\"post\">\n");
        doc.append("<span>Kill \n");
        doc.append("<select name=\"kill_all\">\n");
        doc.append("<option value=\"all\">all</option>\n");
        doc.append("<option value=\"running\">running</option>\n");
        doc.append("<option value=\"pending\">pending</option>\n");
        doc.append("</select>\n");
        doc.append(" Tasks for Layer '").append(layerName).append("'.");
        doc.append("<input type=\"submit\" value=\" Submit\">");
        doc.append("</span>\n");
        doc.append("</form>\n");

        doc.append("</td></tr></table>");
        return doc.toString();
    }

    private void makeFooter(StringBuilder doc) {
        doc.append("</body></html>\n");
    }

    private void handleKillAllThreadsPost(Form form, TileLayer tl, Response resp)
            throws RestletException {

        final boolean allLayers = tl == null;

        String killCode = form.getFirst("kill_all").getValue();

        final Iterator<GWCTask> tasks;
        if ("1".equals(killCode) || "running".equalsIgnoreCase(killCode)) {
            killCode = "running";
            tasks = seeder.getRunningTasks();
        } else if ("pending".equalsIgnoreCase(killCode)) {
            tasks = seeder.getPendingTasks();
        } else if ("all".equalsIgnoreCase(killCode)) {
            tasks = seeder.getRunningAndPendingTasks();
        } else {
            throw new RestletException("Unknown kill_all code: '" + killCode
                    + "'. One of all|running|pending is expected.", Status.CLIENT_ERROR_BAD_REQUEST);
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
        StringBuilder doc = new StringBuilder();

        makeHeader(doc);
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

        resp.setEntity(doc.toString(), MediaType.TEXT_HTML);
    }

    private void handleKillThreadPost(Form form, TileLayer tl, Response resp) {
        String id = form.getFirstValue("thread_id");

        StringBuilder doc = new StringBuilder();

        makeHeader(doc);

        if (seeder.terminateGWCTask(Long.parseLong(id))) {
            doc.append("<ul><li>Requested to terminate task " + id + ".</li></ul>");
        } else {
            doc.append("<ul><li>Sorry, either task "
                    + id
                    + " has not started yet, or it is a truncate task that cannot be interrutped.</li></ul>");
            ;
        }

        if (tl != null) {
            doc.append("<p><a href=\"./" + tl.getName() + "\">Go back</a></p>\n");
        }

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

        TYPE type = GWCTask.TYPE.valueOf(form.getFirst("type").getValue().toUpperCase());

        final String layerName = tl.getName();
        SeedRequest sr = new SeedRequest(layerName, bounds, gridSetId, threadCount, zoomStart,
                zoomStop, format, type, fullParameters);

        TileRange tr = TileBreeder.createTileRange(sr, tl);

        GWCTask[] tasks;
        try {
            tasks = seeder.createTasks(tr, tl, sr.getType(), sr.getThreadCount(),
                    sr.getFilterUpdate());
        } catch (GeoWebCacheException e) {
            throw new RestletException(e.getMessage(), Status.SERVER_ERROR_INTERNAL);
        }

        seeder.dispatchTasks(tasks);

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

}
