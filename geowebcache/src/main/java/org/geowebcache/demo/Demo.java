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
            page = generateHTML(tileLayerDispatcher);
        }
        response.setContentType("text/html");

        try {
            response.getOutputStream().write(page.getBytes());
        } catch (IOException ioe) {
            throw new GeoWebCacheException("failed to render HTML");
        }
    }
    
    private static String generateHTML(TileLayerDispatcher tileLayerDispatcher) 
    throws GeoWebCacheException {
        String header = 
            "<html><body>\n"
            +"<a id=\"logo\" href=\"http://geowebcache.org\">" 
            +"<img src=\"http://geowebcache.org/trac/chrome/site/geowebcache_text.png?page=demos\""
            +"height=\"63\" width=\"306\" border=\"0\"/>"
            +"</a>\n"
            +"<h3>Known layers:</h3><table>\n"
            +"<ul><li>This is just a quick demo, the bounds are likely to be less than perfect.</li>\n"
            +"<li>You can append &format=image/jpeg to the URLs in the "
            +"table to change the output format.</li>\n"
            +"<li>If the layers are loaded from a WMS getcapabilities"
            +" document you will probably see duplicates without the namespace prefix.</li>\n"
            +"<li>OpenLayers does not support bounds per zoomlevel, and GWC tightens the bounds as you zoom in."
            +" Some tile requests will therefore be rejected. </li>\n"
            +"</ul>\n"
            +"<hr>\n"
            +"<table cellspacing=\"10\" border=\"0\">\n"
            +"<tr><td><strong>Layer name:</strong></td>" 
            +"<td colspan=\"2\"><strong>OpenLayers:</strong></td>"
            +"<td colspan=\"2\"><strong>Google Earth:</strong></td>" 
            +"<td><strong>Custom:</strong></td>" 
            +"</tr>\n";
        
        String rows = tableRows(tileLayerDispatcher);
        
        String footer = "</table>\n</body></html>";
        
        return header + rows + footer;
    }
    
    private static String tableRows(TileLayerDispatcher tileLayerDispatcher)
    throws GeoWebCacheException {
        Iterator<Entry<String,TileLayer>> it = 
            tileLayerDispatcher.getLayers().entrySet().iterator();
        
        StringBuffer buf = new StringBuffer();
        
        while(it.hasNext()) {
            TileLayer layer = it.next().getValue();     
            buf.append("<tr><td>"+layer.getName()+"</td>");
            if(layer.supportsSRS(SRS.getEPSG4326())) {
                buf.append("<td><a href=\"demo/"+layer.getName()+"?srs=EPSG:4326\">EPSG:4326</a></td>");
            } else {
                buf.append("<td>EPSG:4326 not supported</td>");
            }
            
            if(layer.supportsSRS(SRS.getEPSG900913())) {
                buf.append("<td><a href=\"demo/"+layer.getName()+"?srs=EPSG:900913\">EPSG:900913</a></td>");
            } else {
                buf.append("<td>EPSG:900913 not supported</td>");
            }
            
            if(layer.supportsSRS(SRS.getEPSG4326())) {
                buf.append("<td><a href=\"service/kml/"+layer.getName()+".png.kmz\">KML (PNG)</a></td>"
                + "<td><a href=\"service/kml/"+layer.getName()+".kml.kmz\">KML (vector)</a></td>");
            } else {
                buf.append("<td colspan=\"2\"> Google Earth requires EPSG:4326 support</td>");
            }
            
            // Any custom projections?
            buf.append("<td>");
            int count = 0;
            Iterator<SRS> iter = layer.getGrids().keySet().iterator();
            while(iter.hasNext()) {
                SRS curSRS = iter.next();
                if(curSRS != SRS.getEPSG4326() && curSRS != SRS.getEPSG900913()) { 
                    buf.append("<a href=\"demo/"+layer.getName()+"?srs="+curSRS.toString()+"\">"
                        + curSRS.toString()+"</a><br />");
                    count++;
                }
            }
            
            if(count == 0){
                buf.append("<i>none</i>");
            }
            buf.append("</td></tr>\n");
        }
        return buf.toString();
    }
    
    private static String generateHTML(TileLayer layer, SRS srs, String formatStr) 
    throws GeoWebCacheException {
        String layerName = layer.getName();
        String mime = layer.getDefaultMimeType().getFormat();
        //int srsIdx = layer.getSRSIndex(SRS.getSRS(srsStr));
        
        Grid grid = layer.getGrid(srs);
        BBOX bbox = grid.getGridBounds();
        BBOX zoomBounds = grid.getBounds();
        //String res = "resolutions: "+ Arrays.toString(grid.getResolutions()) + ",\n";
        String res = "maxResolution: " + Double.toString(grid.getResolutions()[0]) +",\n"; 
        String page =
            "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head>\n"
            +"<meta http-equiv=\"imagetoolbar\" content=\"no\">\n"
            +"<title>"+layerName+" "+srs.toString()+" "+mime+"</tile>\n"
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
            +"{layers: '"+layerName+"', format: '"+mime+"'} );\n"
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
