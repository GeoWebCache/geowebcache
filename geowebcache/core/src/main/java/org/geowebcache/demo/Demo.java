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
 * <p>Copyright 2019
 */
package org.geowebcache.demo;

import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.filter.parameters.FloatParameterFilter;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.filter.parameters.RegexParameterFilter;
import org.geowebcache.filter.parameters.StringParameterFilter;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.ApplicationMime;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.mime.MimeType;
import org.geowebcache.mime.XMLMime;
import org.geowebcache.util.ServletUtils;
import org.springframework.util.Assert;

public class Demo {

    private static Logger LOGGER = Logging.getLogger(Demo.class.getName());

    public static void makeMap(
            TileLayerDispatcher tileLayerDispatcher,
            GridSetBroker gridSetBroker,
            String action,
            HttpServletRequest request,
            HttpServletResponse response)
            throws GeoWebCacheException {

        String page = null;

        // Do we have a layer, or should we make a list?
        if (action != null) {
            String layerName = ServletUtils.URLDecode(action, request.getCharacterEncoding());

            TileLayer layer = tileLayerDispatcher.getTileLayer(layerName);

            String rawGridSet = request.getParameter("gridSet");
            String gridSetStr = null;
            if (rawGridSet != null) gridSetStr = ServletUtils.URLDecode(rawGridSet, request.getCharacterEncoding());

            if (gridSetStr == null) {
                gridSetStr = request.getParameter("srs");

                if (gridSetStr == null) {
                    gridSetStr = layer.getGridSubsets().iterator().next();
                }
            }

            String formatStr = request.getParameter("format");

            if (formatStr != null) {
                if (!layer.supportsFormat(formatStr)) {
                    throw new GeoWebCacheException("Unknow or unsupported format " + formatStr);
                }
            } else {
                formatStr = layer.getDefaultMimeType().getFormat();
            }

            page = generateHTML(layer, gridSetStr, formatStr);

        } else {
            if (request.getRequestURI().endsWith("/")) {
                try {
                    String reqUri = request.getRequestURI();
                    response.sendRedirect(response.encodeRedirectURL(reqUri.substring(0, reqUri.length() - 1)));
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Error sending redirect response", e);
                }
                return;
            } else {
                page = generateHTML(tileLayerDispatcher, gridSetBroker);
            }
        }
        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");

        try {
            response.getOutputStream().write(page.getBytes());
        } catch (IOException ioe) {
            throw new GeoWebCacheException("failed to render HTML");
        }
    }

    private static String generateHTML(TileLayerDispatcher tileLayerDispatcher, GridSetBroker gridSetBroker)
            throws GeoWebCacheException {
        String reloadPath = "rest/reload";
        String truncatePath = "rest/masstruncate";

        StringBuffer buf = new StringBuffer();

        buf.append("<html>\n");
        buf.append(ServletUtils.gwcHtmlHeader("", "GWC Demos"));
        buf.append("<body>\n");
        buf.append(ServletUtils.gwcHtmlLogoLink(""));
        buf.append(
                """
                <table cellspacing="10" border="0">
                <tr><td><strong>Layer name:</strong></td>
                <td><strong>Enabled:</strong></td>
                <td><strong>Grids Sets:</strong></td>
                """);
        buf.append("</tr>\n");

        tableRows(buf, tileLayerDispatcher, gridSetBroker);

        buf.append("</table>\n");
        buf.append("<br />");
        buf.append(
                        """
                        <strong>These are just quick demos. GeoWebCache also supports:</strong><br />
                        <ul><li>WMTS, TMS, Virtual Earth and Google Maps</li>
                        <li>Proxying GetFeatureInfo, GetLegend and other WMS requests</li>
                        <li>Advanced request and parameter filters</li>
                        <li>Output format adjustments, such as compression level</li>
                        <li>Adjustable expiration headers and automatic cache expiration</li>
                        <li>RESTful interface for seeding and configuration (beta)</li>
                        </ul>
                        <br />
                        <strong>Reload TileLayerConfiguration:</strong><br />
                        <p>You can reload the configuration by pressing the following button. \
                        The username / password is configured in WEB-INF/user.properties, or the admin \
                         user in GeoServer if you are using the plugin.</p>
                        <form form id="kill" action="\
                        """)
                .append(reloadPath)
                .append(
                        """
                        " method="post">\
                        <input type="hidden" name="reload_configuration"  value="1" />\
                        <span><input style="padding: 0; margin-bottom: -12px; border: 1;"type="submit" value="Reload TileLayerConfiguration"></span>\
                        </form>\
                        <br /><strong>Truncate All Layers:</strong><br />
                        <p>Truncate all layers\
                        <form form id="truncate" action="\
                        """)
                .append(truncatePath)
                .append("\" method=\"post\"><input type=\"hidden\" name=\"<truncateAll>\" value=\"</truncateAll>\"/>"
                        + "<span><input style=\"padding: 0; margin-bottom: -12px; border: 1;background-color:LightCoral;\"type=\"submit\" value=\"Clear GWC\"></span>"
                        + "</form><br />"
                        + "</body></html>");

        return buf.toString();
    }

    private static void tableRows(
            StringBuffer buf, TileLayerDispatcher tileLayerDispatcher, GridSetBroker gridSetBroker)
            throws GeoWebCacheException {

        Set<String> layerList = new TreeSet<>(tileLayerDispatcher.getLayerNames());
        for (String layerName : layerList) {
            TileLayer layer = tileLayerDispatcher.getTileLayer(layerName);
            if (!layer.isAdvertised()) {
                continue;
            }
            String escapedLayerName = escapeHtml4(layer.getName());
            buf.append("<tr><td style=\"min-width: 100px;\"><strong>")
                    .append(escapedLayerName)
                    .append("</strong><br />\n");
            buf.append("<a href=\"rest/seed/").append(escapedLayerName).append("\">Seed this layer</a>\n");
            buf.append("</td><td>").append(layer.isEnabled()).append("</td>");
            buf.append("<td><table width=\"100%\">");

            for (String gridSetId : layer.getGridSubsets()) {
                GridSubset gridSubset = layer.getGridSubset(gridSetId);
                String gridSetName = gridSubset.getName();
                if (gridSetName.length() > 20) {
                    gridSetName = gridSetName.substring(0, 20) + "...";
                }
                gridSetName = escapeHtml4(gridSetName);
                buf.append("<tr><td style=\"width: 170px;\">").append(gridSetName);

                buf.append("</td><td>OpenLayers: [");
                buf.append(layer.getMimeTypes().stream()
                        .filter(type -> type.supportsTiling() || type.isVector())
                        .map(type -> generateDemoUrl(escapedLayerName, escapeHtml4(gridSubset.getName()), type))
                        .collect(Collectors.joining(", ")));

                buf.append("]</td><td>\n");

                if (gridSubset.getName().equals(gridSetBroker.getWorldEpsg4326().getName())) {
                    outputKMLSupport(buf, layer);
                }
                buf.append("</td></tr>");
            }

            buf.append("</table></td>\n");
            buf.append("</tr>\n");
        }
    }

    private static void outputKMLSupport(StringBuffer buf, TileLayer layer) {
        buf.append(" &nbsp; KML: [");
        String prefix = "";

        buf.append(layer.getMimeTypes().stream()
                .filter(type -> type instanceof ImageMime || XMLMime.kml.equals(type) || XMLMime.kmz.equals(type))
                .map(type -> {
                    if (XMLMime.kmz.equals(type)) {
                        return "<a href=\"%sservice/kml/%s.kml.kmz\">kmz</a>"
                                .formatted(prefix, escapeHtml4(layer.getName()));
                    } else {
                        return "<a href=\"%sservice/kml/%s.%s.kml\">%s</a>"
                                .formatted(
                                        prefix,
                                        escapeHtml4(layer.getName()),
                                        type.getFileExtension(),
                                        type.getFileExtension());
                    }
                })
                .collect(Collectors.joining(", ")));

        buf.append("]");
    }

    private static String generateDemoUrl(String layerName, String gridSetId, MimeType type) {
        return "<a href=\"demo/%s?gridSet=%s&format=%s\">%s</a>"
                .formatted(layerName, gridSetId, type.getFormat(), type.getFileExtension());
    }

    private static String generateHTML(TileLayer layer, String gridSetStr, String formatStr)
            throws GeoWebCacheException {
        String layerName = layer.getName();

        GridSubset gridSubset = layer.getGridSubset(gridSetStr);
        GridSet gridSet = gridSubset.getGridSet();

        BoundingBox bbox = gridSubset.getGridSetBounds();
        BoundingBox zoomBounds = gridSubset.getOriginalExtent();

        MimeType formatMime = null;

        for (MimeType mime : layer.getMimeTypes()) {
            if (formatStr.equalsIgnoreCase(mime.getFormat())) {
                formatMime = mime;
            }
        }
        if (formatMime == null) {
            formatMime = layer.getDefaultMimeType();
        }

        StringBuilder buf = new StringBuilder();

        String openLayersPath = "../rest/web/openlayers3/";

        buf.append("<html xmlns=\"http://www.w3.org/1999/xhtml\"><head>\n");
        buf.append("<meta http-equiv=\"imagetoolbar\" content=\"no\">\n" + "<title>")
                .append(escapeHtml4(layerName));
        buf.append(" ").append(escapeHtml4(gridSubset.getName()));
        buf.append(" ").append(escapeHtml4(formatStr));
        buf.append("</title>\n");
        buf.append(
                """
                <style type="text/css">
                body { font-family: sans-serif; font-weight: bold; font-size: .8em; }
                body { border: 0px; margin: 0px; padding: 0px; }
                #map { width: 85%; height: 85%; border: 0px; padding: 0px; }
                #info iframe { width: 100%; height: 250px; border: none; }
                .ol-scale-value {top: 24px; right: 8px; position: absolute; }
                .ol-zoom-value {top: 40px; right: 8px; position: absolute; }
                .tooltip {position: absolute; background-color: white; border: 1px solid black; padding: 5px; border-radius: 3px; white-space: nowrap; max-height: 200px; overflow-y: auto; display: none;}
                .tooltip-header {display: flex; justify-content: space-between; align-items: center; padding: 5px; border-bottom: 1pxsolidblack; }
                .tooltip-content {padding: 5px; max-height: 150px; overflow-y: auto;}
                .close-button {cursor: pointer; background: none; border: none; font-size: 16px; font-weight: bold; }
                </style>
                """);

        buf.append("<script src=\"").append(openLayersPath).append("ol.js\"></script>\n");
        buf.append("<link rel='stylesheet' href='").append(openLayersPath).append("ol.css' type='text/css'>\n");
        buf.append("<script src=\"../rest/web/demo.js\"></script>\n");
        buf.append("</head>\n" + "<body>\n");
        buf.append("<div id=\"params\">")
                .append(makeModifiableParameters(layer))
                .append("</div>\n");

        buf.append("<div id=\"map\"></div>\n" + "<div id=\"info\"></div>\n");
        buf.append(
                """
                <div id="tooltip" class="tooltip">
                      <div class="tooltip-header">
                        <span>Attributes</span>
                        <button id="close-button" class="close-button">&times;</button>
                      </div>
                      <div id="tooltip-content" class="tooltip-content"></div>
                    </div>""");

        // add parameters in hidden inputs
        makeHiddenInput(buf, "dpi", Double.toString(gridSubset.getDotsPerInch()));
        makeHiddenInput(buf, "gridsetName", gridSubset.getGridSet().getName());
        makeHiddenInput(
                buf,
                "gridNames",
                Arrays.stream(gridSubset.getGridNames())
                        .map(s -> "\"%s\"".formatted(s))
                        .collect(Collectors.joining(", ", "[", "]")));
        makeHiddenInput(
                buf,
                "gridNamesNumeric",
                String.valueOf(Arrays.stream(gridSubset.getGridNames()).allMatch(n -> StringUtils.isNumeric(n))));
        makeHiddenInput(buf, "format", formatStr);
        makeHiddenInput(buf, "layerName", layerName);
        makeHiddenInput(buf, "SRS", gridSubset.getSRS().toString());

        String unit = "";
        double mpu = gridSet.getMetersPerUnit();
        if (doubleEquals(mpu, 1)) {
            unit = "m";
        } else if (doubleEquals(mpu, 0.3048)) {
            unit = "ft";
            // Use the average of equatorial and polar radius, and a large margin of error
        } else if (doubleEquals(mpu, Math.PI * (6378137 + 6356752) / 360, Math.PI * (6378137 - 6356752) / 360)) {
            unit = "degrees";
        }
        makeHiddenInput(buf, "unit", unit);

        makeHiddenInput(buf, "resolutions", Arrays.toString(gridSubset.getResolutions()));
        makeHiddenInput(buf, "tileWidth", Integer.toString(gridSubset.getTileWidth()));
        makeHiddenInput(buf, "tileHeight", Integer.toString(gridSubset.getTileHeight()));
        makeHiddenInput(buf, "minX", Double.toString(bbox.getMinX()));
        makeHiddenInput(buf, "maxY", Double.toString(bbox.getMaxY()));
        makeHiddenInput(buf, "isVector", Boolean.toString(formatMime.isVector()));

        if (formatMime.isVector()) {
            // Examine mime type for correct VT format
            String vtName = formatMime.getInternalName();
            if (ApplicationMime.mapboxVector.getInternalName().equals(vtName)) {
                makeHiddenInput(buf, "vtFormatName", "MVT");
            } else if (ApplicationMime.topojson.getInternalName().equals(vtName)) {
                makeHiddenInput(buf, "vtFormatName", "TopoJSON");
            } else if (ApplicationMime.geojson.getInternalName().equals(vtName)) {
                makeHiddenInput(buf, "vtFormatName", "GeoJSON");
            }
        } else {
            makeHiddenInput(buf, "maxX", Double.toString(bbox.getMaxX()));
            makeHiddenInput(buf, "minY", Double.toString(bbox.getMinY()));
            makeHiddenInput(buf, "fullGrid", Boolean.toString(gridSubset.fullGridSetCoverage()));
            if (gridSubset.fullGridSetCoverage()) {
                StringBuilder origin = new StringBuilder().append("[");
                for (int i = 0; i < gridSubset.getResolutions().length; i++) {
                    if (i != 0) {
                        origin.append(",");
                    }
                    BoundingBox subbox = gridSubset.getCoverageBounds(i);
                    origin.append("[")
                            .append(subbox.getMinX())
                            .append(", ")
                            .append(subbox.getMaxY())
                            .append("]");
                }
                makeHiddenInput(buf, "origins", origin.append("]").toString());
            } else {
                makeHiddenInput(buf, "origin", "[" + bbox.getMinX() + ", " + bbox.getMaxY() + "]");
            }
        }

        makeHiddenInput(buf, "bbox", "[" + bbox + "]");
        makeHiddenInput(buf, "zoomBounds", "[" + zoomBounds + "]");
        return buf.append("</body>\n</html>").toString();
    }

    private static String makeModifiableParameters(TileLayer tl) {
        List<ParameterFilter> parameterFilters = tl.getParameterFilters();
        if (parameterFilters == null || parameterFilters.isEmpty()) {
            return "";
        }
        parameterFilters = new ArrayList<>(parameterFilters);
        Collections.sort(parameterFilters, (o1, o2) -> o1.getKey().compareTo(o2.getKey()));
        StringBuilder doc = new StringBuilder();
        doc.append("Modifiable Parameters:\n");
        doc.append("<table>\n");
        for (ParameterFilter pf : parameterFilters) {
            Assert.notNull(pf, "The parameter filter must be non null");
            String key = pf.getKey();
            String defaultValue = pf.getDefaultValue();
            List<String> legalValues = pf.getLegalValues();
            doc.append("<tr><td>")
                    .append(escapeHtml4(key.toUpperCase()))
                    .append(": ")
                    .append("</td><td>");
            String parameterId = key;
            if (pf instanceof StringParameterFilter) {
                Map<String, String> keysValues = makeParametersMap(defaultValue, legalValues);
                makePullDown(doc, parameterId, keysValues, defaultValue);
            } else if (pf instanceof RegexParameterFilter) {
                makeTextInput(doc, parameterId, 25);
            } else if (pf instanceof FloatParameterFilter floatParam) {
                if (floatParam.getValues().isEmpty()) {
                    // accepts any value
                    makeTextInput(doc, parameterId, 25);
                } else {
                    Map<String, String> keysValues = makeParametersMap(defaultValue, legalValues);
                    makePullDown(doc, parameterId, keysValues, defaultValue);
                }
            } else if ("org.geowebcache.filter.parameters.NaiveWMSDimensionFilter"
                    .equals(pf.getClass().getName())) {
                makeTextInput(doc, parameterId, 25);
            } else {
                // Unknown filter type
                if (legalValues == null) {
                    // Doesn't have a defined set of values, just provide a text field
                    makeTextInput(doc, parameterId, 25);
                } else {
                    // Does have a defined set of values, so provide a drop down
                    Map<String, String> keysValues = makeParametersMap(defaultValue, legalValues);
                    makePullDown(doc, parameterId, keysValues, defaultValue);
                }
            }
            doc.append("</td></tr>\n");
        }
        doc.append("</table>\n");
        return doc.toString();
    }

    private static Map<String, String> makeParametersMap(String defaultValue, List<String> legalValues) {
        Map<String, String> map = new TreeMap<>();
        for (String s : legalValues) {
            map.put(s, s);
        }
        map.put(defaultValue, defaultValue);
        return map;
    }

    private static void makeHiddenInput(StringBuilder doc, String id, String value) {
        doc.append("<input type=\"hidden\" id=\"hidden_")
                .append(escapeHtml4(id))
                .append("\" value=\"")
                .append(escapeHtml4(value))
                .append("\" />\n");
    }

    private static void makePullDown(StringBuilder doc, String id, Map<String, String> keysValues, String defaultKey) {
        doc.append("<select name=\"" + escapeHtml4(id) + "\">\n");

        Iterator<Entry<String, String>> iter = keysValues.entrySet().iterator();

        while (iter.hasNext()) {
            Entry<String, String> entry = iter.next();
            final String key = entry.getKey();
            // equal, including both null
            if ((key == null && defaultKey == null) || (key != null && key.equals(defaultKey))) {
                doc.append("<option value=\""
                        + escapeHtml4(entry.getValue())
                        + "\" selected=\"selected\">"
                        + escapeHtml4(entry.getKey())
                        + "</option>\n");
            } else {
                doc.append("<option value=\""
                        + escapeHtml4(entry.getValue())
                        + "\">"
                        + escapeHtml4(entry.getKey())
                        + "</option>\n");
            }
        }

        doc.append("</select>\n");
    }

    private static void makeTextInput(StringBuilder doc, String id, int size) {
        doc.append("<input name=\"" + escapeHtml4(id) + "\" type=\"text\" size=\"" + size + "\" />\n");
    }

    private static boolean doubleEquals(double d1, double d2) {
        return doubleEquals(d1, d2, 0);
    }

    private static boolean doubleEquals(double d1, double d2, double buffer) {
        double diff = Math.abs(d1 - d2);
        return diff < (Math.ulp(d1) + Math.ulp(d2) + buffer);
    }
}
