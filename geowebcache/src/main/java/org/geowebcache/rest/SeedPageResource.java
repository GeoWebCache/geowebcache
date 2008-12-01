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

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.demo.Demo;
import org.geowebcache.layer.Grid;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.mime.MimeType;
import org.geowebcache.util.wms.BBOX;
import org.restlet.Context;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Representation;
import org.restlet.resource.Resource;
import org.restlet.resource.StringRepresentation;
import org.restlet.resource.Variant;

public class SeedPageResource extends Resource {
    private static Log log = LogFactory.getLog(org.geowebcache.rest.SeedPageResource.class);
    
    private String layerName = null;
    
    private TileLayer tl = null;
    
    public SeedPageResource(Context context, Request request, Response response) {
        super(context, request, response);
        getVariants().add(new Variant(MediaType.TEXT_HTML));
        //getVariants().add(new Variant(MediaType.ALL));
        this.layerName = (String) request.getAttributes().get("layer");
        
        TileLayerDispatcher tlDispatch = RESTDispatcher.getInstance().getTileLayerDispatcher();
        
        try {
            tl = tlDispatch.getTileLayer(this.layerName);
        } catch (GeoWebCacheException e) {
            log.error(e.getMessage());
        }
        
        if(tl != null) {
            // Need to initialize it first
            tl.isInitialized();
        } else {
            // We'll check later, since we can't write an error from here
        }

        

    }
    
    public Representation getRepresentation(Variant variant) {
        Representation rep;
        
        if(tl != null) {
            rep = new StringRepresentation(makeFormPage(), MediaType.TEXT_HTML); 
        } else {
            rep = new StringRepresentation("Uknown layer: " + this.layerName, MediaType.TEXT_HTML);
        }
        
        return rep;
    }
    
    private String makeFormPage() {
        
        StringBuilder doc = new StringBuilder();
        
        makeHeader(doc);
        
        makeTaskList(doc);
        
        makeWarningsAndHints(doc);
        
        makeFormHeader(doc);
        
        makeThreadCountPullDown(doc);
        
        makeTypePullDown(doc);
        
        makeSRSPulldown(doc);
        
        makeFormatPullDown(doc);
        
        makeZoomStartPullDown(doc);
        
        makeZoomStopPullDown(doc);
        
        makeBboxFields(doc);
        
        makeSubmit(doc);
        
        makeFormFooter(doc);  
        
        makeFooter(doc);
        
        return doc.toString();
    }
    
    private String makeResponsePage() {
        
        StringBuilder doc = new StringBuilder();
        
        makeHeader(doc);
        
        doc.append("<h3>Task submitted</h3>\n");
        
        doc.append("<p>Below you can find a list of currently executing threads, take the numbers with a grain of salt");
        doc.append(" until the thread has had a chance to run for a few minutes. ");
        doc.append("Please note that that you must currently stop or reload the servlet to terminate them prematurely.");
        
        makeTaskList(doc);
        
        makeFooter(doc);
        
        return doc.toString();
    }
    
    private void makeTypePullDown(StringBuilder doc) {
        doc.append("<tr><td>Type of operation:</td><td>\n");
        Map<String,String> keysValues = new TreeMap<String,String>();
        
        keysValues.put("Truncate - remove tiles","truncate");
        keysValues.put("Seed - generate missing tiles","seed");
        keysValues.put("Reseed - regenerate all tiles", "reseed");

        makePullDown(doc, "type", keysValues, "seed");
        doc.append("</td></tr>\n");
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
        doc.append("<tr><td valign=\"top\">Bounding box:</td><td>\n");
        makeTextInput(doc, "minX", 6);
        makeTextInput(doc, "minY", 6);
        makeTextInput(doc, "maxX", 6);
        makeTextInput(doc, "maxY", 6);
        doc.append("</br>These are optional, approximate values are fine.");
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
        doc.append("<input name=\""+id+"\" type=\"text\" size=\""+size+"\" />\n");
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
                makePullDown(doc, "zoomStart", keysValues, "0" + Integer.toString(minStart)); 
            } else {
                makePullDown(doc, "zoomStart", keysValues, Integer.toString(minStart));
            }
            
        } else {
            int midStop = ( minStart + maxStop )/ 2;
            if(midStop < 10) {
                makePullDown(doc, "zoomStop", keysValues, "0" + Integer.toString(midStop));
            } else {
                makePullDown(doc, "zoomStop", keysValues, Integer.toString(midStop));  
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
        doc.append("<select name=\""+id+"\">\n");
        
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
    
    private void makeFormHeader(StringBuilder doc) {
        doc.append("<h4>Create a new task:</h4>\n");
        doc.append("<form id=\"seed\" action=\"./"+this.layerName+"\" method=\"post\">\n");
        doc.append("<table border=\"0\" cellspacing=\"10\">\n");
    }
    
    private void makeFormFooter(StringBuilder doc) {
        doc.append("</table>\n");
        doc.append("</form>\n");
    }
    
    private void makeHeader(StringBuilder doc) {
        doc.append("<html><body>\n" + Demo.GWC_HEADER);    
    }
    
    private void makeWarningsAndHints(StringBuilder doc) {
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
    }
        
    private void makeTaskList(StringBuilder doc) {
        doc.append("<h4>List of currently executing tasks:</h4>\n <ul>\n");
        
        SeederThreadPoolExecutor exec = RESTDispatcher.getInstance().getExecutor();
        
        Iterator<Entry<Long, GWCTask>> iter = exec.getRunningTasksIterator();
        
        if(! iter.hasNext()) {
            doc.append("<li><i>none</i></li>\n");
        }
        
        while(iter.hasNext()) {
            Entry<Long, GWCTask> entry = iter.next();
            GWCTask task = entry.getValue();
            
            String timeRemaining;
            int time = task.timeRemaining;
            
            if(task.tilesDone < 50) {
                timeRemaining= " Estimating...";
            } else if(time > (60*60*24)) {
                timeRemaining= "" + (time / (60*60*24)) + " day(s)";
            } else if(time > 60*60) {
                timeRemaining = "" + (time / (60*60)) + " hour(s)";
            } else if(time > 60) {
                timeRemaining = "" +(time / 60) + " minute(s)";
            } else {
                timeRemaining = "" +(time) + " second(s)";
            }
            
            doc.append("<li>Id: "+ entry.getKey()+", Layer: "+task.layerName
                    + ", Type: " + task.getType() 
                    + ", Tiles total: " + task.tilesTotal
                    + ", Tiles completed: " + task.tilesDone
                    + ", Time remaining: " + timeRemaining
                    + ", (Thread "+(task.threadOffset+1)+" of "+task.threadCount+") </li>\n");
        }
        
        doc.append("</ul>");
        
        doc.append("<p><a href=\"./"+this.layerName+"\">Refresh list</a></p>\n");
    }
    
    private void makeFooter(StringBuilder doc) {
        doc.append("</body></html>\n");
    }
    
    public boolean allowPost() {
        return true;
    }
    
    public void handlePost() {
        Request req = super.getRequest();
        Form form = req.getEntityAsForm();
        
        if(form == null || form.getFirst("minX") == null) {
            log.error("Form object or minX field was null, request was for " + req.getResourceRef().getPath());
            return;
        }
        
        BBOX bounds = null;        
        if( form.getFirst("minX").getValue() != null 
                && form.getFirst("minX").getValue().length() > 0
                && form.getFirst("minY").getValue() != null 
                && form.getFirst("minY").getValue().length() > 0 
                && form.getFirst("maxX").getValue() != null 
                && form.getFirst("maxX").getValue().length() > 0 
                && form.getFirst("maxY").getValue() != null 
                && form.getFirst("maxY").getValue().length() > 0 ) {
            
            bounds = new BBOX(
                    Double.parseDouble(form.getFirst("minX").getValue()), 
                    Double.parseDouble(form.getFirst("minY").getValue()), 
                    Double.parseDouble(form.getFirst("maxX").getValue()), 
                    Double.parseDouble(form.getFirst("maxY").getValue()));
        }
        
        SRS srs = SRS.getSRS(Integer.parseInt(form.getFirst("srs").getValue()));
        
        int threadCount = Integer.parseInt(form.getFirst("threadCount").getValue());
        int zoomStart = Integer.parseInt(form.getFirst("zoomStart").getValue());
        int zoomStop = Integer.parseInt(form.getFirst("zoomStop").getValue());
        
        String format = form.getFirst("format").getValue();
        
        String type = form.getFirst("type").getValue();

        SeedRequest sr = new SeedRequest(this.layerName, bounds, srs,
                threadCount, zoomStart, zoomStop, format, type);

        try {
            SeedResource.dispatchTasks(sr, this.tl, RESTDispatcher
                    .getInstance().getExecutor());
        } catch (GeoWebCacheException gwce) {
            log.error(gwce.getMessage());
        }
        
        // Give the thread executor a chance to run
        try {
            Thread.currentThread().sleep(500);
        } catch (InterruptedException e) {
            // Ok, no worries
        }
        
        this.getResponse().setEntity(this.makeResponsePage(), MediaType.TEXT_HTML);
    }
}
