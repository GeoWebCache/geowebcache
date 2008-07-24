package org.geowebcache.demo;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.util.wms.BBOX;

public class OpenLayersDemo {

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

            if (srsStr != null) {
                if (!layer.supportsProjection(new SRS(srsStr))) {
                    throw new GeoWebCacheException("Unsupported SRS " + srsStr);
                }
            } else {
                srsStr = SRS.getEPSG900913().toString();
            }
            page = generateHTML(layer, srsStr, formatStr);

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
            "<html><body><h2>Known layers:</h2><table>";
        
        String rows = tableRows(tileLayerDispatcher);
        
        String footer = "</table>"
            +"<br>ps. this is just a quick and dirty demo. "
            +"You can append &format=image/jpeg to the URLs in the "
            +"table to change the output format."
            +"</body></html>";
        
        return header + rows + footer;
    }
    
    private static String tableRows(TileLayerDispatcher tileLayerDispatcher)  {
        Iterator<Entry<String,TileLayer>> it = 
            tileLayerDispatcher.getLayers().entrySet().iterator();
        
        String rows = "";
        
        while(it.hasNext()) {
            TileLayer layer = it.next().getValue();
            
            rows += "<tr><td>"+layer.getName()+"</td>"
                + "<td><a href=\"demo/"+layer.getName()+"?srs=EPSG:4326\">EPSG:4326</a></td>"
                + "<td><a href=\"demo/"+layer.getName()+"?srs=EPSG:900913\">EPSG:900913</a></td></tr>";
        }
        return rows;
    }
    
    private static String generateHTML(TileLayer layer, String srsStr, String formatStr) 
    throws GeoWebCacheException {
        String layerName = layer.getName();
        String mime = layer.getDefaultMimeType().getFormat();
        int srsIdx = layer.getSRSIndex(new SRS(srsStr));
        
        BBOX bbox = null;
        String res = "";
        if(srsStr.equalsIgnoreCase("EPSG:900913")) {
            //res = "resolutions: [156543.03,78271.52,39135.76,19567.88,9783.94,4891.97],\n";
            res = "resolutions: " + getEPSG900913Resolutions() + ",\n";
            bbox = new BBOX(-20037508.34,-20037508.34,20037508.34,20037508.34);
        } else if(srsStr.equalsIgnoreCase("EPSG:4326")) {
            res = "resolutions: " + getEPSG4326Resolutions() + ",\n";
            bbox = new BBOX(-180.0,-90.0,180.0,90.0);
        }
        
        BBOX layerBounds = layer.getBounds(srsIdx);
        int[] gridBounds = layer.getGridLocForBounds(srsIdx,layerBounds);
        int[] gridLoc = layer.getZoomedOutGridLoc(srsIdx);
        if(gridLoc[2] > -1) {
            bbox = layer.getBboxForGridLoc(srsIdx, gridLoc);
        }
        
        String page =
            "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head>\n"
            +"<meta http-equiv=\"imagetoolbar\" content=\"no\">\n"
            +"<title>"+layerName+" "+srsStr+" "+mime+"</tile>\n"
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
            +"projection: new OpenLayers.Projection('"+srsStr+"'),\n"
            +"maxExtent: new OpenLayers.Bounds("+bbox.toString()+") \n};\n"
            +"map = new OpenLayers.Map('map', mapOptions );\n"
            +"var layerstates = new OpenLayers.Layer.WMS(\n"
            +"\""+layerName+"\",\"../service/wms\",\n"
            +"{layers: '"+layerName+"', format: '"+mime+"'} );\n"
            +"map.addLayer(layerstates);\n"
            +"map.zoomToMaxExtent();\n"
            +"}\n"
            +"</script>\n"
            +"</head>\n"
            +"<body onload=\"init()\">\n"
            +"<div id=\"map\"></div>\n"
            +"</body>\n"
            +"</html>";
        return page;
    }
    
    private static String getEPSG4326Resolutions() {
        String resolutions = "["
            + "0.3515625,0.17578125,0.087890625,0.0439453125,\n"
            + "0.0219726563,0.0109863281,0.0054931641,0.0027465820,\n"
            + "0.0013732910,0.0006866455,0.0003433228,0.0001716614,\n"
            + "0.0000858307,0.0000429153,0.0000214577,0.0000107288,\n"
            + "0.0000053644,0.0000026822"
            + "]";
        
        return resolutions;
    }
    
    private static String getEPSG900913Resolutions() {
        String resolutions = "["
            +"156543.03, 78271.52, 39135.76, 19567.88, 9783.94,\n"
            +"4891.97,   2445.98,  1222.99,  611.50,\n"
            +"305.75,    152.87,   76.44,    38.22,\n"
            +"19.11,     9.55,     4.78,     2.39,\n"
            +"1.19]";
        
        return resolutions;
    }
}
