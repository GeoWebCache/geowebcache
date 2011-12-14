package org.geowebcache.demo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
import org.geowebcache.mime.ImageMime;
import org.geowebcache.mime.MimeType;
import org.geowebcache.mime.XMLMime;
import org.geowebcache.util.ServletUtils;
import org.springframework.util.Assert;

public class Demo {

    public static void makeMap(TileLayerDispatcher tileLayerDispatcher,
            GridSetBroker gridSetBroker, String action, HttpServletRequest request,
            HttpServletResponse response) throws GeoWebCacheException {

        String page = null;

        // Do we have a layer, or should we make a list?
        if (action != null) {
            String layerName = ServletUtils.URLDecode(action, request.getCharacterEncoding());

            TileLayer layer = tileLayerDispatcher.getTileLayer(layerName);

            String rawGridSet = request.getParameter("gridSet");
            String gridSetStr = null;
            if (rawGridSet != null)
                gridSetStr = ServletUtils.URLDecode(rawGridSet, request.getCharacterEncoding());

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

            if (request.getPathInfo().startsWith("/demo")) {
                // Running in GeoServer
                page = generateHTML(layer, gridSetStr, formatStr, true);
            } else {
                page = generateHTML(layer, gridSetStr, formatStr, false);
            }

        } else {
            if (request.getRequestURI().endsWith("/")) {
                try {
                    String reqUri = request.getRequestURI();
                    response.sendRedirect(response.encodeRedirectURL(reqUri.substring(0,
                            reqUri.length() - 1)));
                } catch (IOException e) {
                    e.printStackTrace();
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

    private static String generateHTML(TileLayerDispatcher tileLayerDispatcher,
            GridSetBroker gridSetBroker) throws GeoWebCacheException {
        String reloadPath = "rest/reload";

        String header = "<html>\n" + ServletUtils.gwcHtmlHeader("GWC Demos") + "<body>\n"
                + ServletUtils.gwcHtmlLogoLink("") + "<table>\n"
                + "<table cellspacing=\"10\" border=\"0\">\n"
                + "<tr><td><strong>Layer name:</strong></td>\n"
                + "<td><strong>Enabled:</strong></td>\n"
                + "<td><strong>Grids Sets:</strong></td>\n" + "</tr>\n";

        String rows = tableRows(tileLayerDispatcher, gridSetBroker);

        String footer = "</table>\n"
                + "<br />"
                + "<strong>These are just quick demos. GeoWebCache also supports:</strong><br />\n"
                + "<ul><li>WMTS, TMS, Virtual Earth and Google Maps</li>\n"
                + "<li>Proxying GetFeatureInfo, GetLegend and other WMS requests</li>\n"
                + "<li>Advanced request and parameter filters</li>\n"
                + "<li>Output format adjustments, such as compression level</li>\n"
                + "<li>Adjustable expiration headers and automatic cache expiration</li>\n"
                + "<li>RESTful interface for seeding and configuration (beta)</li>\n"
                + "</ul>\n"
                + "<br />\n"
                + "<strong>Reload Configuration:</strong><br />\n"
                + "<p>You can reload the configuration by pressing the following button. "
                + "The username / password is configured in WEB-INF/user.properties, or the admin "
                + " user in GeoServer if you are using the plugin.</p>\n"
                + "<form form id=\"kill\" action=\""
                + reloadPath
                + "\" method=\"post\">"
                + "<input type=\"hidden\" name=\"reload_configuration\"  value=\"1\" />"
                + "<span><input style=\"padding: 0; margin-bottom: -12px; border: 1;\"type=\"submit\" value=\"Reload Configuration\"></span>"
                + "</form>" + "</body></html>";

        return header + rows + footer;
    }

    private static String tableRows(TileLayerDispatcher tileLayerDispatcher,
            GridSetBroker gridSetBroker) throws GeoWebCacheException {
        StringBuffer buf = new StringBuffer();

        Set<String> layerList = new TreeSet<String>(tileLayerDispatcher.getLayerNames());
        for (String layerName : layerList) {
            TileLayer layer = tileLayerDispatcher.getTileLayer(layerName);
            buf.append("<tr><td style=\"min-width: 100px;\"><strong>" + layer.getName()
                    + "</strong><br />\n");
            buf.append("<a href=\"rest/seed/" + layer.getName() + "\">Seed this layer</a>\n");
            buf.append("</td><td>" + layer.isEnabled() + "</td>");
            buf.append("<td><table width=\"100%\">");

            int count = 0;
            for (String gridSetId : layer.getGridSubsets()) {
                GridSubset gridSubset = layer.getGridSubset(gridSetId);
                String gridSetName = gridSubset.getName();
                if (gridSetName.length() > 20) {
                    gridSetName = gridSetName.substring(0, 20) + "...";
                }
                buf.append("<tr><td style=\"width: 170px;\">").append(gridSetName);

                buf.append("</td><td>OpenLayers: [");
                Iterator<MimeType> mimeIter = layer.getMimeTypes().iterator();
                boolean prependComma = false;
                while (mimeIter.hasNext()) {
                    MimeType mime = mimeIter.next();
                    if (mime instanceof ImageMime) {
                        if (prependComma) {
                            buf.append(", ");
                        } else {
                            prependComma = true;
                        }
                        buf.append(generateDemoUrl(layer.getName(), gridSubset.getName(),
                                (ImageMime) mime));
                    }
                }
                buf.append("]</td><td>\n");

                if (gridSubset.getName().equals(gridSetBroker.WORLD_EPSG4326.getName())) {
                    buf.append(" &nbsp; KML: [");
                    String prefix = "";
                    prependComma = false;
                    Iterator<MimeType> kmlIter = layer.getMimeTypes().iterator();
                    while (kmlIter.hasNext()) {
                        MimeType mime = kmlIter.next();
                        if (mime instanceof ImageMime || mime == XMLMime.kml) {
                            if (prependComma) {
                                buf.append(", ");
                            } else {
                                prependComma = true;
                            }
                            buf.append("<a href=\"" + prefix + "service/kml/" + layer.getName()
                                    + "." + mime.getFileExtension() + ".kml\">"
                                    + mime.getFileExtension() + "</a>");
                        } else if (mime == XMLMime.kmz) {
                            if (prependComma) {
                                buf.append(", ");
                            } else {
                                prependComma = true;
                            }
                            buf.append("<a href=\"" + prefix + "service/kml/" + layer.getName()
                                    + ".kml.kmz\">kmz</a>");
                        }
                    }
                    buf.append("]");
                } else {
                    // No Google Earth support
                }
                buf.append("</td></tr>");
                count++;
            }

            // if(count == 0) {
            // buf.append("<tr><td colspan=\"2\"><i>None</i></td></tr>\n");
            // }

            buf.append("</table></td>\n");
            buf.append("</tr>\n");
        }

        return buf.toString();
    }

    private static String generateDemoUrl(String layerName, String gridSetId, ImageMime imageMime) {
        return "<a href=\"demo/" + layerName + "?gridSet=" + gridSetId + "&format="
                + imageMime.getFormat() + "\">" + imageMime.getFileExtension() + "</a>";
    }

    private static String generateHTML(TileLayer layer, String gridSetStr, String formatStr,
            boolean asPlugin) throws GeoWebCacheException {
        String layerName = layer.getName();

        GridSubset gridSubset = layer.getGridSubset(gridSetStr);

        BoundingBox bbox = gridSubset.getGridSetBounds();
        BoundingBox zoomBounds = gridSubset.getOriginalExtent();

        String res = "resolutions: " + Arrays.toString(gridSubset.getResolutions()) + ",\n";

        String units = "units: \"" + gridSubset.getGridSet().guessMapUnits() + "\",\n";

        String openLayersPath;
        if (asPlugin) {
            openLayersPath = "../../openlayers/OpenLayers.js";
        } else {
            openLayersPath = "../openlayers/OpenLayers.js";
        }

        String page = "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head>\n"
                + "<meta http-equiv=\"imagetoolbar\" content=\"no\">\n" + "<title>"
                + layerName
                + " "
                + gridSubset.getName()
                + " "
                + formatStr
                + "</title>\n"
                + "<style type=\"text/css\">\n"
                + "body { font-family: sans-serif; font-weight: bold; font-size: .8em; }\n"
                + "body { border: 0px; margin: 0px; padding: 0px; }\n"
                + "#map { width: 85%; height: 85%; border: 0px; padding: 0px; }\n"
                + "</style>\n"

                + "<script src=\""
                + openLayersPath
                + "\"></script>    \n"
                + "<script type=\"text/javascript\">               \n"
                + "var map, demolayer;                               \n"
                + "  // sets the chosen modifiable parameter        \n"
                + "  function setParam(name, value){                \n"
                + "   str = \"demolayer.mergeNewParams({\" + name + \": '\" + value + \"'})\" \n"
                + "   // alert(str);                                   \n"
                + "   eval(str);                                    \n"
                + "  }                                              \n"

                + "OpenLayers.DOTS_PER_INCH = "
                + gridSubset.getDotsPerInch()
                + ";\n"
                + "OpenLayers.Util.onImageLoadErrorColor = 'transparent';\n"

                + "function init(){\n"
                + "var mapOptions = { \n"
                + res
                + "projection: new OpenLayers.Projection('"
                + gridSubset.getSRS().toString()
                + "'),\n"
                + "maxExtent: new OpenLayers.Bounds("
                + bbox.toString()
                + "),\n"
                + units
                + "controls: []\n"
                + "};\n"
                + "map = new OpenLayers.Map('map', mapOptions );\n"
                + "map.addControl(new OpenLayers.Control.PanZoomBar({\n"
                + "		position: new OpenLayers.Pixel(2, 15)\n"
                + "}));\n"
                + "map.addControl(new OpenLayers.Control.Navigation());\n"
                + "map.addControl(new OpenLayers.Control.Scale($('scale')));\n"
                + "map.addControl(new OpenLayers.Control.MousePosition({element: $('location')}));\n"
                + "demolayer = new OpenLayers.Layer.WMS(\n"
                + "\""
                + layerName
                + "\",\"../service/wms\",\n"
                + "{layers: '"
                + layerName
                + "', format: '"
                + formatStr
                + "' },\n"
                + "{ tileSize: new OpenLayers.Size("
                + gridSubset.getTileWidth() + "," + gridSubset.getTileHeight() + ")";

        /*
         * If the gridset has a top left tile origin, lets tell that to open layers. Otherwise it'll
         * calculate tile bounds based on the bbox bottom left corner, leading to misaligned
         * requests.
         */
        GridSet gridSet = gridSubset.getGridSet();
        if (gridSet.isTopLeftAligned()) {
            page += ",\n tileOrigin: new OpenLayers.LonLat(" + bbox.getMinX() + ", "
                    + bbox.getMaxY() + ")";
        }

        page += "});\n" + "map.addLayer(demolayer);\n" + "map.zoomToExtent(new OpenLayers.Bounds("
                + zoomBounds.toString()
                + "));\n"
                + "// The following is just for GetFeatureInfo, which is not cached. Most people do not need this \n"
                + "map.events.register('click', map, function (e) {\n"
                + "  document.getElementById('nodelist').innerHTML = \"Loading... please wait...\";\n"
                + "  var params = {\n" + "    REQUEST: \"GetFeatureInfo\",\n"
                + "    EXCEPTIONS: \"application/vnd.ogc.se_xml\",\n"
                + "    BBOX: map.getExtent().toBBOX(),\n" + "    X: e.xy.x,\n" + "    Y: e.xy.y,\n"
                + "    INFO_FORMAT: 'text/html',\n"
                + "    QUERY_LAYERS: map.layers[0].params.LAYERS,\n" + "    FEATURE_COUNT: 50,\n"
                + "    Layers: '" + layerName + "',\n" + "    Styles: '',\n" + "    Srs: '"
                + gridSubset.getSRS().toString() + "',\n" + "    WIDTH: map.size.w,\n"
                + "    HEIGHT: map.size.h,\n" + "    format: \"" + formatStr + "\" };\n"
                + "  OpenLayers.loadURL(\"../service/wms\", params, this, setHTML, setHTML);\n"
                + "  OpenLayers.Event.stop(e);\n" + "  });\n" + "}\n"
                + "function setHTML(response){\n"
                + "    document.getElementById('nodelist').innerHTML = response.responseText;\n"
                + "};\n" + "</script>\n" + "</head>\n" + "<body onload=\"init()\">\n"
                + "<div id=\"params\">" + makeModifiableParameters(layer) + "</div>\n"
                + "<div id=\"map\"></div>\n" + "<div id=\"nodelist\"></div>\n" + "</body>\n"
                + "</html>";
        return page;
    }

    private static String makeModifiableParameters(TileLayer tl) {
        List<ParameterFilter> parameterFilters = tl.getParameterFilters();
        if (parameterFilters == null || parameterFilters.size() == 0) {
            return "";
        }
        parameterFilters = new ArrayList<ParameterFilter>(parameterFilters);
        Collections.sort(parameterFilters, new Comparator<ParameterFilter>() {
            public int compare(ParameterFilter o1, ParameterFilter o2) {
                return o1.getKey().compareTo(o2.getKey());
            }
        });
        StringBuilder doc = new StringBuilder();
        doc.append("Modifiable Parameters:\n");
        doc.append("<table>\n");
        for (ParameterFilter pf : parameterFilters) {
            Assert.notNull(pf);
            String key = pf.getKey();
            String defaultValue = pf.getDefaultValue();
            List<String> legalValues = pf.getLegalValues();
            doc.append("<tr><td>").append(key.toUpperCase()).append(": ").append("</td><td>");
            String parameterId = key;
            if (pf instanceof StringParameterFilter) {
                Map<String, String> keysValues = makeParametersMap(defaultValue, legalValues);
                makePullDown(doc, parameterId, keysValues, defaultValue);
            } else if (pf instanceof RegexParameterFilter) {
                makeTextInput(doc, parameterId, 25);
            } else if (pf instanceof FloatParameterFilter) {
                FloatParameterFilter floatParam = (FloatParameterFilter) pf;
                if (floatParam.getValues().isEmpty()) {
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
            doc.append("</td></tr>\n");
        }
        doc.append("</table>\n");
        return doc.toString();
    }

    private static Map<String, String> makeParametersMap(String defaultValue,
            List<String> legalValues) {
        Map<String, String> map = new TreeMap<String, String>();
        for (String s : legalValues) {
            map.put(s, s);
        }
        map.put(defaultValue, defaultValue);
        return map;
    }

    private static void makePullDown(StringBuilder doc, String id, Map<String, String> keysValues,
            String defaultKey) {
        doc.append("<select name=\"" + id + "\" onchange=\"setParam('" + id + "', value)\">\n");

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

    private static void makeTextInput(StringBuilder doc, String id, int size) {
        doc.append("<input name=\"" + id + "\" type=\"text\" size=\"" + size
                + "\" onblur=\"setParam('" + id + "', value)\" />\n");
    }
}
