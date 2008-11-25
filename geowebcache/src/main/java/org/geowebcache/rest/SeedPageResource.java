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
package org.geowebcache.rest;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.Grid;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.mime.MimeType;
import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Representation;
import org.restlet.resource.Resource;
import org.restlet.resource.StringRepresentation;
import org.restlet.resource.Variant;

public class SeedPageResource extends Resource {
    
    private String layerName = null;
    
    private TileLayer tl = null;
    
    public SeedPageResource(Context context, Request request, Response response) {
        super(context, request, response);
        getVariants().add(new Variant(MediaType.TEXT_HTML));
        getVariants().add(new Variant(MediaType.ALL));
        this.layerName = (String) request.getAttributes().get("layer");
    }
    
    public Representation getRepresentation(Variant variant) {
        Representation rep = new StringRepresentation(makePage(), MediaType.TEXT_HTML);
        return rep;
    }
    
    private String makePage() {
        TileLayerDispatcher tlDispatch = RESTDispatcher.getInstance().getTileLayerDispatcher();
        
        try {
            tl = tlDispatch.getTileLayer(this.layerName);
        } catch (GeoWebCacheException e) {
            e.printStackTrace();
        }
        
        if(tl == null) {
            return "Uknown layer: " + this.layerName;
        }
        
        // Need to initialize it first
        tl.isInitialized();
        
        StringBuilder doc = new StringBuilder();
        
        makeHeader(doc);
        
        makeThreadCountPullDown(doc);
        
        makeSRSPulldown(doc);
        
        makeFormatPullDown(doc);
        
        makeZoomStartPullDown(doc);
        
        makeZoomStopPullDown(doc);
        
        makeBboxFields(doc);
        
        makeSubmit(doc);
        
        makeFooter(doc);
        
        return doc.toString();
    }
    
    private void makeThreadCountPullDown(StringBuilder doc) {
        doc.append("<tr><td>Number of threads to use:</td><td>\n");
        Map<String,String> keysValues = new TreeMap<String,String>();
        
        for(int i=1; i<17; i++) {
            if(i < 10) {
                keysValues.put("0"+Integer.toString(i), "0"+Integer.toString(i));
            } else {
                keysValues.put(Integer.toString(i), Integer.toString(i));
            }
        }
        makePullDown(doc, "threadCount", keysValues, Integer.toString(2));
        doc.append("</td></tr>\n");
    }

    private void makeBboxFields(StringBuilder doc) {
        doc.append("<tr><td>Bounding box (optional):</td><td>\n");
        makeTextInput(doc, "minX", 6);
        makeTextInput(doc, "minY", 6);
        makeTextInput(doc, "maxX", 6);
        makeTextInput(doc, "maxY", 6);
        doc.append("</td></tr>\n");
    }
    
    private void makeBboxHints(StringBuilder doc) {
        Iterator<Entry<SRS, Grid>> iter = tl.getGrids().entrySet().iterator();
        
        int minStart = Integer.MAX_VALUE;
        int maxStop = Integer.MIN_VALUE;
        
        while(iter.hasNext()) {
            Entry<SRS, Grid> entry = iter.next();
            doc.append("<li>"+entry.getKey().toString()
                    +":   "+entry.getValue().getBounds().toString()+"</li>\n");
        }
        
    }

    private void makeTextInput(StringBuilder doc, String id, int size) {
        doc.append("<input id=\""+id+"\" type=\"text\" size=\""+size+"\" />\n");
    }

    private void makeSubmit(StringBuilder doc) {
        doc.append("<tr><td></td><td><input type=\"submit\" value=\"Submit\"></td></tr>\n");
    }

    private void makeZoomStopPullDown(StringBuilder doc) {
        doc.append("<tr><td>Zoom stop:</td><td>\n");
        makeZoomPullDown(doc, false);
        doc.append("</td></tr>\n");
    }

    private void makeZoomStartPullDown(StringBuilder doc) {
        doc.append("<tr><td>Zoom start:</td><td>\n");
        makeZoomPullDown(doc, true);
        doc.append("</td></tr>\n");
    }
    
    private void makeZoomPullDown(StringBuilder doc, boolean isStart) {
        Map<String,String> keysValues = new TreeMap<String,String>();
        
        Iterator<Entry<SRS, Grid>> iter = tl.getGrids().entrySet().iterator();
        
        int minStart = Integer.MAX_VALUE;
        int maxStop = Integer.MIN_VALUE;
        
        while(iter.hasNext()) {
            Entry<SRS, Grid> entry = iter.next();
            
            // This is a bit of an issue...
            // TODO shouldn't have to initialize grid calc here
            try {
                entry.getValue().getGridCalculator();
            } catch (GeoWebCacheException e) {
                e.printStackTrace();
            }
            
            int start = entry.getValue().getZoomStart();
            int stop = entry.getValue().getZoomStop();
            
            if(start < minStart) {
                minStart = start;
            }
            if(stop > maxStop) {
                maxStop = stop;
            }
        }
        
        for(int i=minStart; i<=maxStop; i++) {
            if(i < 10) {
                keysValues.put("0" + Integer.toString(i), "0" + Integer.toString(i));
            } else {
                keysValues.put(Integer.toString(i), Integer.toString(i));
            }
        }
        
        if(isStart) {
            if(minStart < 10) {
                makePullDown(doc, "minZoom", keysValues, "0" + Integer.toString(minStart)); 
            } else {
                makePullDown(doc, "minZoom", keysValues, Integer.toString(minStart));
            }
            
        } else {
            if(maxStop < 10) {
                makePullDown(doc, "maxZoom", keysValues, "0" + Integer.toString(maxStop));
            } else {
                makePullDown(doc, "maxZoom", keysValues, Integer.toString(maxStop));  
            }
        }
    }

    private void makeFormatPullDown(StringBuilder doc) {
        doc.append("<tr><td>Format:</td><td>\n");
        Map<String,String> keysValues = new TreeMap<String,String>();
        
        Iterator<MimeType> iter = this.tl.getMimeTypes().iterator();
        
        while(iter.hasNext()) {
            MimeType mime = iter.next();
            keysValues.put(mime.getFormat(), mime.getFormat());
        }
        
        makePullDown(doc, "format", keysValues, ImageMime.png.getFormat());
        doc.append("</td></tr>\n");
    }

    private void makeSRSPulldown(StringBuilder doc) {
        doc.append("<tr><td>SRS:</td><td>\n");
        Map<String,String> keysValues = new TreeMap<String,String>();
        
        Iterator<Entry<SRS, Grid>> iter = tl.getGrids().entrySet().iterator();
        
        while(iter.hasNext()) {
            Entry<SRS, Grid> entry = iter.next();
            keysValues.put(entry.getKey().toString(), Integer.toString(entry.getKey().getNumber()));
        }
        
        makePullDown(doc, "srs", keysValues, SRS.getEPSG4326().toString());
        doc.append("</td></tr>\n");
    }

    private void makePullDown(StringBuilder doc, String id, Map<String,String> keysValues, String defaultKey) {
        doc.append("<select id=\""+id+"\">\n");
        
        Iterator<Entry<String,String>> iter = keysValues.entrySet().iterator();
        
        while(iter.hasNext()) {
            Entry<String,String> entry = iter.next();
            if(entry.getKey().equals(defaultKey)) {
                doc.append("<option value=\""+entry.getValue()+"\" selected=\"selected\">"+entry.getKey()+"</option>\n");
            } else {
                doc.append("<option value=\""+entry.getValue()+"\">"+entry.getKey()+"</option>\n");
            }
        }
        
        doc.append("</select>\n");
    }
    
    
    private void makeHeader(StringBuilder doc) {
        doc.append("<html><body>\n");
        doc.append("<h4>Please note:</h4><ul>\n"
                + "<li>This minimalistic interface does not check for correctness.</li>\n"
        	+ "<li>The only way to currently stop seeding threads is to stop the servlet.</li>\n"
                + "<li>Seeding past zoomlevel 20 is usually not recommended.</li>\n"
        	+ "<li>Please check the logs of the container to look for error messages and progress indicators.</li>\n"
        	+ "</ul>\n");
        doc.append("Here are the max bounds, if you do not specify bounds these will be used.\n");
        doc.append("<ul>\n");
        makeBboxHints(doc);
        doc.append("</ul>\n");
        
        doc.append("<table border=\"0\" cellspacing=\"10\">\n");
        doc.append("<form name=\"seed\" action=\"./"+this.layerName+"\" method=\"post\">\n");
    }
    
    private void makeFooter(StringBuilder doc) {
        doc.append("</form>\n");
        doc.append("</table>\n");
        doc.append("</body></html>\n");
    }
    
    public boolean allowPost() {
        return true;
    }
    
    public void post(Representation entity) {
        System.out.println("Hello...");
    }
}
