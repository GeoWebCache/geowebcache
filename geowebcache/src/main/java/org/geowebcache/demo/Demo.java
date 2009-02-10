package org.geowebcache.demo;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.Grid;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.util.wms.BBOX;

public class Demo {

    public static final String GWC_HEADER = "<a id=\"logo\" href=\"http://geowebcache.org\">" 
        +"<img src=\"http://geowebcache.org/trac/chrome/site/geowebcache_logo.png\""
        +"height=\"100\" width=\"353\" border=\"0\"/>"
        +"</a>\n";
    
    public static void makeMap(TileLayerDispatcher tileLayerDispatcher,
            String action, HttpServletRequest request,
            HttpServletResponse response) throws GeoWebCacheException {

        String page = null;

        // Do we have a layer, or should we make a list?
        if (action != null) {
            TileLayer layer = tileLayerDispatcher.getTileLayer(action);

            String srsStr = request.getParameter("srs");
            String formatStr = request.getParameter("format");

            if (formatStr != null) {
                if (!layer.supportsFormat(formatStr)) {
                    throw new GeoWebCacheException(
                            "Unknow or unsupported format " + formatStr);
                }
            } else {
                formatStr = layer.getDefaultMimeType().getFormat();
            }

            SRS srs = null;
            if (srsStr != null) {
                srs = SRS.getSRS(srsStr);
            } else {
                srs = SRS.getEPSG900913();
            }
            page = generateHTML(layer, srs, formatStr);

        } else {
            if(request.getRequestURI().endsWith("demo/")) {
                page = generateHTML(tileLayerDispatcher, true);
            } else {
                page = generateHTML(tileLayerDispatcher, false);
            }
            
        }
        response.setContentType("text/html");

        try {
            response.getOutputStream().write(page.getBytes());
        } catch (IOException ioe) {
            throw new GeoWebCacheException("failed to render HTML");
        }
    }
    
    private static String generateHTML(TileLayerDispatcher tileLayerDispatcher, boolean trailingSlash) 
    throws GeoWebCacheException {
        String reloadPath = "rest/reload";
        if(trailingSlash) {
            reloadPath = "../rest/reload";
        }
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
        
        String rows = tableRows(tileLayerDispatcher, trailingSlash);
        
        String footer = "</table>\n</body></html>";
        
        return header + rows + footer;
    }
    
    private static String tableRows(TileLayerDispatcher tileLayerDispatcher, boolean trailingSlash)
    throws GeoWebCacheException {
        Iterator<Entry<String,TileLayer>> it = 
            tileLayerDispatcher.getLayers().entrySet().iterator();
        
        StringBuffer buf = new StringBuffer();
        
        while(it.hasNext()) {
            TileLayer layer = it.next().getValue();     
            buf.append("<tr><td>"+layer.getName()+"</td>");
            if(layer.supportsSRS(SRS.getEPSG4326())) {
                buf.append("<td>"+generateDemoUrl(layer.getName(), 4326,"EPSG:4326", trailingSlash)+"</td>");
            } else {
                buf.append("<td>EPSG:4326 not supported</td>");
            }
            
            if(layer.supportsSRS(SRS.getEPSG900913())) {
                buf.append("<td>"+generateDemoUrl(layer.getName(), 900913,"EPSG:900913", trailingSlash)+"</td>");
            } else {
                buf.append("<td>EPSG:900913 not supported</td>");
            }
            
            if(layer.supportsSRS(SRS.getEPSG4326())) {
                String prefix = "";
                if(trailingSlash) {
                    prefix = "../";
                }
                buf.append("<td><a href=\""+prefix+"service/kml/"+layer.getName()+".png.kmz\">KML (PNG)</a></td>"
                + "<td><a href=\""+prefix+"service/kml/"+layer.getName()+".kml.kmz\">KML (vector)</a></td>");
            } else {
                buf.append("<td colspan=\"2\"> Google Earth requires EPSG:4326 support</td>");
            }
            
            // Any custom projections?
            buf.append("<td>");
            int count = 0;
            Iterator<SRS> iter = layer.getGrids().keySet().iterator();
            while(iter.hasNext()) {
                SRS curSRS = iter.next();
                if(curSRS.getNumber() != 4326 && curSRS.getNumber() != 900913) { 
                    buf.append(generateDemoUrl(layer.getName(), curSRS.getNumber(),curSRS.toString(), trailingSlash)+"<br />");
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
    
    private static String generateDemoUrl(String layerName, int epsgNumber, String text, boolean trailingSlash) {
        if(trailingSlash) {
            return "<a href=\"./"+layerName+"?srs=EPSG:"+epsgNumber+"\">"+text+"</a>";
        } else {
            return "<a href=\"demo/"+layerName+"?srs=EPSG:"+epsgNumber+"\">"+text+"</a>";
        }
    }
    
    private static String generateHTML(TileLayer layer, SRS srs, String formatStr) 
    throws GeoWebCacheException {
        String layerName = layer.getName();
        //String mime = MimeType.createFromFormat(formatStr).g;
        //int srsIdx = layer.getSRSIndex(SRS.getSRS(srsStr));
        
        Grid grid = layer.getGrid(srs);
        BBOX bbox = grid.getGridBounds();
        BBOX zoomBounds = grid.getBounds();
        //String res = "resolutions: "+ Arrays.toString(grid.getResolutions()) + ",\n";
        String res;
        
        if(grid.hasStaticResolutions()) {
            res = "resolutions: " + Arrays.toString(grid.getResolutions()) + ",\n";
        } else {
            res = "maxResolution: " + Double.toString(grid.getResolutions()[grid.getZoomStart()]) +",\n"
                        +"numZoomLevels: "+(grid.getZoomStop() - grid.getZoomStart() + 1)+",\n";
        }
        
        String page =
            "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head>\n"
            +"<meta http-equiv=\"imagetoolbar\" content=\"no\">\n"
            +"<title>"+layerName+" "+srs.toString()+" "+formatStr+"</title>\n"
            +"<style type=\"text/css\">\n"
            +"body { font-family: sans-serif; font-weight: bold; font-size: .8em; }\n"
            +"body { border: 0px; margin: 0px; padding: 0px; }\n"
            +"#map { width: 85%; height: 85%; border: 0px; padding: 0px; }\n"
            +"</style>\n"

            +"<script src=\"http://openlayers.org/api/OpenLayers.js\"></script>\n"
            +"<script type=\"text/javascript\">\n"
            +"OpenLayers.Util.onImageLoadErrorColor = 'transparent';\n"
            +"var map, layer;\n"
        		
            +"function init(){\n"
            +"var mapOptions = { \n"
            + res
            +"projection: new OpenLayers.Projection('"+srs.toString()+"'),\n"
            +"maxExtent: new OpenLayers.Bounds("+bbox.toString()+") \n};\n"
            +"map = new OpenLayers.Map('map', mapOptions );\n"
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
