package org.geowebcache.demo;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubSet;
import org.geowebcache.grid.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.util.ServletUtils;

public class Demo {

    public static final String GWC_HEADER = "<a id=\"logo\" href=\"http://geowebcache.org\">" 
        +"<img src=\"http://geowebcache.org/trac/chrome/site/geowebcache_logo.png\""
        +"height=\"100\" width=\"353\" border=\"0\"/>"
        +"</a>\n";
    
    public static void makeMap(TileLayerDispatcher tileLayerDispatcher, GridSetBroker gridSetBroker, 
            String action, HttpServletRequest request,
            HttpServletResponse response) throws GeoWebCacheException {

        String page = null;

        // Do we have a layer, or should we make a list?
        if (action != null) {
            String layerName = ServletUtils.URLDecode(action, request.getCharacterEncoding());
            
            TileLayer layer = tileLayerDispatcher.getTileLayer(layerName);

            String gridSetStr = ServletUtils.URLDecode(request.getParameter("gridSet"), request.getCharacterEncoding());
            
            if(gridSetStr == null) {
                gridSetStr = request.getParameter("srs");
                
                if(gridSetStr == null) {
                    gridSetStr = SRS.getEPSG4326().toString();
                }
            }
            
            String formatStr = request.getParameter("format");

            if (formatStr != null) {
                if (!layer.supportsFormat(formatStr)) {
                    throw new GeoWebCacheException(
                            "Unknow or unsupported format " + formatStr);
                }
            } else {
                formatStr = layer.getDefaultMimeType().getFormat();
            }
            
            if(request.getPathInfo().startsWith("/demo")) {
                // Running in GeoServer
                page = generateHTML(layer, gridSetStr, formatStr, true);
            } else {
                page = generateHTML(layer, gridSetStr, formatStr, false);
            }
            

        } else {
            if(request.getRequestURI().endsWith("/")) {
                try {
                    String reqUri = request.getRequestURI();
                    response.sendRedirect(response.encodeRedirectURL(reqUri.substring(0, reqUri.length() - 1)));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return;
            } else {
                page = generateHTML(tileLayerDispatcher, gridSetBroker);
            }
            
        }
        response.setContentType("text/html");

        try {
            response.getOutputStream().write(page.getBytes());
        } catch (IOException ioe) {
            throw new GeoWebCacheException("failed to render HTML");
        }
    }
    
    private static String generateHTML(TileLayerDispatcher tileLayerDispatcher, GridSetBroker gridSetBroker) 
    throws GeoWebCacheException {
        String reloadPath = "rest/reload";

        String header = 
            "<html><body>\n"
            + GWC_HEADER
            +"<h3>Reload Configuration:</h3>\n"
            +"<p>You can reload the configuration by pressing the following button. " 
            +"The username / password is configured in WEB-INF/user.properties, or the admin " 
            +" user in GeoServer if you are using the plugin.</p>\n"
            +"<form form id=\"kill\" action=\""+reloadPath+"\" method=\"post\">"
            +"<input type=\"hidden\" name=\"reload_configuration\"  value=\"1\" />"
            +"<span><input style=\"padding: 0; margin-bottom: -12px; border: 1;\"type=\"submit\" value=\"Reload Configuration\"></span>"
            +"</form>"
            +"<hr>\n"
            +"<h3>Known Layers:</h3><table>\n"
            +"<ul><li>This is just a quick demo, the bounds are likely to be less than perfect.</li>\n"
            +"<li>You can append &format=image/jpeg to the URLs in the "
            +"table to change the output format.</li>\n"
            +"</ul>\n"
            +"<hr>\n"
            +"<table cellspacing=\"10\" border=\"0\">\n"
            +"<tr><td><strong>Layer name:</strong></td>" 
            +"<td colspan=\"2\"><strong>OpenLayers:</strong></td>"
            +"<td colspan=\"2\"><strong>Google Earth:</strong></td>" 
            +"<td><strong>Custom:</strong></td>" 
            +"</tr>\n";
        
        String rows = tableRows(tileLayerDispatcher, gridSetBroker);
        
        String footer = "</table>\n</body></html>";
        
        return header + rows + footer;
    }
    
    private static String tableRows(TileLayerDispatcher tileLayerDispatcher, GridSetBroker gridSetBroker)
    throws GeoWebCacheException {
        Iterator<Entry<String,TileLayer>> it = tileLayerDispatcher.getLayers().entrySet().iterator();
        
        StringBuffer buf = new StringBuffer();
        
        while(it.hasNext()) {
            TileLayer layer = it.next().getValue();     
            buf.append("<tr><td>"+layer.getName()+"</td>");
            
            GridSubSet epsg4326GridSubSet = layer.getGridSubSetForSRS(SRS.getEPSG4326());
            if(null != epsg4326GridSubSet) {
                buf.append("<td>"+generateDemoUrl(
                        layer.getName(),
                        epsg4326GridSubSet.getName())
                        +"</td>");
            } else {
                buf.append("<td>EPSG:4326 not supported</td>");
            }
            
            // We get the SRS from the GridSet because it may use EPSG:900913 under the covers
            GridSubSet epsg3857GridSubSet = layer.getGridSubSetForSRS(gridSetBroker.WORLD_EPSG3857.getSRS());
            if(null != epsg3857GridSubSet) {
                buf.append("<td>"+generateDemoUrl(
                        layer.getName(),
                        epsg3857GridSubSet.getName())
                        +"</td>");
            } else {
                buf.append("<td>EPSG:3857 not supported</td>");
            }
            
            if(null != epsg4326GridSubSet && epsg4326GridSubSet.getGridSet().equals(gridSetBroker.WORLD_EPSG4326)) {
                String prefix = "";
                buf.append("<td><a href=\""+prefix+"service/kml/"+layer.getName()+".png.kml\">KML (PNG)</a></td>"
                + "<td><a href=\""+prefix+"service/kml/"+layer.getName()+".kml.kmz\">KMZ (vector)</a></td>");
            } else {
                buf.append("<td colspan=\"2\"> Google Earth requires "+gridSetBroker.WORLD_EPSG4326.getName()+"</td>");
            }
            
            // Any custom projections?
            buf.append("<td>");
            int count = 0;
            Iterator<GridSubSet> iter = layer.getGridSubSets().values().iterator();
            while(iter.hasNext()) {
                GridSubSet gridSubSet = iter.next();
                if(! gridSubSet.getGridSet().equals(gridSetBroker.WORLD_EPSG4326) 
                        && ! gridSubSet.getGridSet().equals(gridSetBroker.WORLD_EPSG3857)) { 
                    buf.append(generateDemoUrl(layer.getName(), gridSubSet.getName())+"<br />");
                    count++;
                }
            }
            
            if(count == 0){
                buf.append("<i>none</i>");
            }
            buf.append("</td>\n");
            buf.append("<td><a href=\"rest/seed/"+layer.getName()+"\">Seed this layer</a></td>\n");
            buf.append("</tr>\n");
        }
        return buf.toString();
    }
    
    private static String generateDemoUrl(String layerName, String gridSetId) {
        return "<a href=\"demo/"+layerName+"?gridSet="+gridSetId+"\">"+gridSetId+"</a>";
    }
    
    private static String generateHTML(TileLayer layer, String gridSetStr, String formatStr, boolean asPlugin) 
    throws GeoWebCacheException {
        String layerName = layer.getName();
        
        GridSubSet gridSubSet = layer.getGridSubSet(gridSetStr);
        
        BoundingBox bbox = gridSubSet.getGridSetBounds();
        BoundingBox zoomBounds = gridSubSet.getCoverageBestFitBounds();
        
        String res = "resolutions: " + Arrays.toString(gridSubSet.getResolutions()) + ",\n";
        
        String openLayersPath;
        if(asPlugin) {
            openLayersPath = "../../openlayers/OpenLayers.js";
        } else {
            openLayersPath = "../openlayers/OpenLayers.js";
        }
        
        
        String page =
            "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head>\n"
            +"<meta http-equiv=\"imagetoolbar\" content=\"no\">\n"
            +"<title>"+layerName+" "+gridSubSet.getName()+" "+formatStr+"</title>\n"
            +"<style type=\"text/css\">\n"
            +"body { font-family: sans-serif; font-weight: bold; font-size: .8em; }\n"
            +"body { border: 0px; margin: 0px; padding: 0px; }\n"
            +"#map { width: 85%; height: 85%; border: 0px; padding: 0px; }\n"
            +"</style>\n"

            +"<script src=\""+openLayersPath+"\"></script>\n"
            +"<script type=\"text/javascript\">\n"
            +"OpenLayers.Util.onImageLoadErrorColor = 'transparent';\n"
            +"var map, layer;\n"
        		
            +"function init(){\n"
            +"var mapOptions = { \n"
            + res
            +"projection: new OpenLayers.Projection('"+gridSubSet.getSRS().toString()+"'),\n"
            +"maxExtent: new OpenLayers.Bounds("+bbox.toString()+"),\n"
	    +"controls: [] "
	    +"};\n"
            +"map = new OpenLayers.Map('map', mapOptions );\n"
	    +"map.addControl(new OpenLayers.Control.PanZoomBar({\n"
	    +"		position: new OpenLayers.Pixel(2, 15)\n"
	    +"}));\n"
	    +"map.addControl(new OpenLayers.Control.Navigation());\n"
	    +"map.addControl(new OpenLayers.Control.Scale($('scale')));\n"
	    +"map.addControl(new OpenLayers.Control.MousePosition({element: $('location')}));\n"
            +"var demolayer = new OpenLayers.Layer.WMS(\n"
            +"\""+layerName+"\",\"../service/wms\",\n"
            +"{layers: '"+layerName+"', format: '"+formatStr+"'} );\n"
            +"map.addLayer(demolayer);\n"
            +"map.zoomToExtent(new OpenLayers.Bounds("+zoomBounds.toString()+"));\n"
            +"}\n"
            +"</script>\n"
            +"</head>\n"
            +"<body onload=\"init()\">\n"
            +"<div id=\"map\"></div>\n"
            +"</body>\n"
            +"</html>";
        return page;
    }
}
