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
import java.util.stream.Collectors;

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
        
        StringBuffer buf = new StringBuffer();
        
        buf.append("<html>\n");
        buf.append(ServletUtils.gwcHtmlHeader("","GWC Demos"));
        buf.append("<body>\n");
        buf.append(ServletUtils.gwcHtmlLogoLink(""));
        buf.append("<table cellspacing=\"10\" border=\"0\">\n"
                +"<tr><td><strong>Layer name:</strong></td>\n"
                +"<td><strong>Enabled:</strong></td>\n"
                +"<td><strong>Grids Sets:</strong></td>\n");
        buf.append("</tr>\n");

        tableRows(buf, tileLayerDispatcher, gridSetBroker);

        buf.append("</table>\n");
        buf.append("<br />");
        buf.append("<strong>These are just quick demos. GeoWebCache also supports:</strong><br />\n"
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
                + "<form form id=\"kill\" action=\"")
            .append(reloadPath)
            .append("\" method=\"post\">"
                + "<input type=\"hidden\" name=\"reload_configuration\"  value=\"1\" />"
                + "<span><input style=\"padding: 0; margin-bottom: -12px; border: 1;\"type=\"submit\" value=\"Reload Configuration\"></span>"
                + "</form>" + "</body></html>");

        return buf.toString();
    }
    
    private static void tableRows(StringBuffer buf, TileLayerDispatcher tileLayerDispatcher,
            GridSetBroker gridSetBroker) throws GeoWebCacheException {

        Set<String> layerList = new TreeSet<String>(tileLayerDispatcher.getLayerNames());
        for (String layerName : layerList) {
            TileLayer layer = tileLayerDispatcher.getTileLayer(layerName);
            if(!layer.isAdvertised()){
                continue;
            }
            buf.append("<tr><td style=\"min-width: 100px;\"><strong>")
                .append(layer.getName())
                .append("</strong><br />\n");
            buf.append("<a href=\"rest/seed/")
                .append(layer.getName())
                .append("\">Seed this layer</a>\n");
            buf.append("</td><td>")
                .append(layer.isEnabled())
                .append("</td>");
            buf.append("<td><table width=\"100%\">");

            for (String gridSetId : layer.getGridSubsets()) {
                GridSubset gridSubset = layer.getGridSubset(gridSetId);
                String gridSetName = gridSubset.getName();
                if (gridSetName.length() > 20) {
                    gridSetName = gridSetName.substring(0, 20) + "...";
                }
                buf.append("<tr><td style=\"width: 170px;\">").append(gridSetName);

                buf.append("</td><td>OpenLayers: [");
                buf.append(layer.getMimeTypes().stream()
                    .filter(MimeType::supportsTiling)
                    .map(type -> generateDemoUrl(layer.getName(), gridSubset.getName(),type))
                    .collect(Collectors.joining(", ")));
                    
                buf.append("]</td><td>\n");
                
                if (gridSubset.getName().equals(gridSetBroker.WORLD_EPSG4326.getName())) {
                    buf.append(" &nbsp; KML: [");
                    String prefix = "";
                    
                    buf.append(layer.getMimeTypes().stream()
                        .filter(type-> type instanceof ImageMime || type == XMLMime.kml || type == XMLMime.kmz)
                        .map(type -> {
                            if (type == XMLMime.kmz) {
                                return String.format("<a href=\"%sservice/kml/%s.kml.kmz\">kmz</a>", 
                                    prefix, 
                                    layer.getName());
                            } else {
                                return String.format("<a href=\"%sservice/kml/%s.%s.kml\">%s</a>", 
                                        prefix, 
                                        layer.getName(), 
                                        type.getFileExtension(), 
                                        type.getFileExtension());
                            }
                        })
                        .collect(Collectors.joining(", ")));

                    buf.append("]");
                } else {
                    // No Google Earth support
                }
                buf.append("</td></tr>");
            }
            
            buf.append("</table></td>\n");
            buf.append("</tr>\n");
        }
    }

    private static String generateDemoUrl(String layerName, String gridSetId, MimeType type) {
        return String.format("<a href=\"demo/%s?gridSet=%s&format=%s\">%s</a>", 
                layerName, 
                gridSetId,
                type.getFormat(), 
                type.getFileExtension());
    }

    private static String generateHTML(TileLayer layer, String gridSetStr, String formatStr,
            boolean asPlugin) throws GeoWebCacheException {
        String layerName = layer.getName();

        GridSubset gridSubset = layer.getGridSubset(gridSetStr);

        BoundingBox bbox = gridSubset.getGridSetBounds();
        BoundingBox zoomBounds = gridSubset.getOriginalExtent();
        
        StringBuffer buf = new StringBuffer();

        String res = "resolutions: " + Arrays.toString(gridSubset.getResolutions()) + ",\n";

        String units = "units: \"" + gridSubset.getGridSet().guessMapUnits() + "\",\n";

        String openLayersPath;
        if (asPlugin) {
            openLayersPath = "../../openlayers/OpenLayers.js";
        } else {
            openLayersPath = "../openlayers/OpenLayers.js";
        }
        
        buf.append("<html xmlns=\"http://www.w3.org/1999/xhtml\"><head>\n");
        
        buf.append("<meta http-equiv=\"imagetoolbar\" content=\"no\">\n");
        buf.append("<title>")
            .append(layerName)
            .append(" ")
            .append(gridSubset.getName())
            .append(" ")
            .append(formatStr)
            .append("</title>\n");
        

        buf.append("<style type=\"text/css\">\n"
                + "body { font-family: sans-serif; font-weight: bold; font-size: .8em; }\n"
                + "body { border: 0px; margin: 0px; padding: 0px; }\n"
                + "#map { width: 85%; height: 85%; border: 0px; padding: 0px; }\n"
                + "</style>\n");

        buf.append("<script src=\"")
            .append(openLayersPath)
            .append("\"></script>    \n"
                + "<script type=\"text/javascript\">               \n"
                + "var map, demolayer, params;                     \n"
                + "filteredParams = {};                                    \n"
                + "  // sets the chosen modifiable parameter        \n"
                + "  function setParam(name, value){                \n"
                + "   var newParams = {};                           \n"
                + "   newParams[name] = value;                      \n"
                + "   demolayer.mergeNewParams(newParams);          \n"
                + "   filteredParams[name]=value;                   \n"
                + "  }                                              \n"
                + "OpenLayers.DOTS_PER_INCH = ")
            .append(gridSubset.getDotsPerInch())
            .append(";\n"
                + "OpenLayers.Util.onImageLoadErrorColor = 'transparent';\n"
                + "function init(){\n"
                + "var mapOptions = { \n")
            .append(res)
            .append("projection: new OpenLayers.Projection('")
            .append(gridSubset.getSRS().toString())
            .append("'),\n"
                + "maxExtent: new OpenLayers.Bounds(")
            .append(bbox.toString())
            .append("),\n")
            .append(units)
            .append("controls: []\n"
                + "};\n"
                + "map = new OpenLayers.Map('map', mapOptions );\n"
                + "map.addControl(new OpenLayers.Control.PanZoomBar({\n"
                + "		position: new OpenLayers.Pixel(2, 15)\n"
                + "}));\n"
                + "map.addControl(new OpenLayers.Control.Navigation());\n"
                + "map.addControl(new OpenLayers.Control.Scale($('scale')));\n"
                + "map.addControl(new OpenLayers.Control.MousePosition({element: $('location')}));\n"
                + "demolayer = new OpenLayers.Layer.WMS(\n"
                + "\"")
            .append(layerName)
            .append("\",\"../service/wms\",\n"
                + "{layers: '")
            .append(layerName)
            .append("', format: '")
            .append(formatStr)
            .append("' },\n"
                + "{ tileSize: new OpenLayers.Size(")
            .append(gridSubset.getTileWidth()).append(",").append(gridSubset.getTileHeight()).append(")");

        /*
         * If the gridset has a top left tile origin, lets tell that to open layers. Otherwise it'll
         * calculate tile bounds based on the bbox bottom left corner, leading to misaligned
         * requests.
         */
        GridSet gridSet = gridSubset.getGridSet();
        if (gridSet.isTopLeftAligned()) {
            buf
                .append(",\n tileOrigin: new OpenLayers.LonLat(")
                .append(bbox.getMinX())
                .append(", ")
                .append(bbox.getMaxY())
                .append(")");
        }

        buf
            .append("});\n" + "map.addLayer(demolayer);\n" + "map.zoomToExtent(new OpenLayers.Bounds(")
            .append(zoomBounds.toString())
            .append("));\n"
                + "// The following is just for GetFeatureInfo, which is not cached. Most people do not need this \n"
                + "map.events.register('click', map, function (e) {\n"
                + "  document.getElementById('nodelist').innerHTML = \"Loading... please wait...\";\n"
                + "  var params = {\n" + "    REQUEST: \"GetFeatureInfo\",\n"
                + "    EXCEPTIONS: \"application/vnd.ogc.se_xml\",\n"
                + "    BBOX: map.getExtent().toBBOX(),\n" + "    X: e.xy.x,\n" + "    Y: e.xy.y,\n"
                + "    INFO_FORMAT: 'text/html',\n"
                + "    QUERY_LAYERS: map.layers[0].params.LAYERS,\n" + "    FEATURE_COUNT: 50,\n"
                + "    Layers: '").append( layerName).append("',\n")
            .append("    Srs: '").append(gridSubset.getSRS().toString()).append("',\n" 
                + "    WIDTH: map.size.w,\n"
                + "    HEIGHT: map.size.h,\n" 
                + "    format: \"").append(formatStr).append("\" };\n"
                + "  // Merge in filtered params\n"
                + "  for(var p in filteredParams) {\n"
                + "    params[p]=filteredParams[p];\n"
                + "  }\n"
                + "  OpenLayers.loadURL(\"../service/wms\", params, this, setHTML, setHTML);\n"
                + "  OpenLayers.Event.stop(e);\n" + "  });\n" + "}\n"
                + "function setHTML(response){\n"
                + "    document.getElementById('nodelist').innerHTML = response.responseText;\n"
                + "};\n" + "</script>\n" + "</head>\n" + "<body onload=\"init()\">\n"
                + "<div id=\"params\">").append(makeModifiableParameters(layer)).append("</div>\n"
                + "<div id=\"map\"></div>\n" + "<div id=\"nodelist\"></div>\n" + "</body>\n"
                + "</html>");
        return buf.toString();
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
            final String key = entry.getKey();
            // equal, including both null
            if ((key==null && defaultKey==null) || (key!=null && key.equals(defaultKey))) {
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
