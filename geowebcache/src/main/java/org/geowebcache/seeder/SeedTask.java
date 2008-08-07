package org.geowebcache.seeder;

import java.io.IOException;

import org.restlet.data.MediaType;
import org.restlet.data.Response;
import org.restlet.resource.StringRepresentation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.RESTDispatcher;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.tile.Tile;
import org.geowebcache.util.wms.BBOX;

public class SeedTask {
    private static Log log = LogFactory.getLog(org.geowebcache.seeder.SeedTask.class);
    private SeedRequest req = null;
    
    public SeedTask(SeedRequest req){
        this.req = req;
    }
    
    public void doSeed(){
       try{
        log.info("begun seeding");
        TileLayer layer = RESTDispatcher.getAllLayers().get(req.getName()); 
        int zoomStart = req.getZoomStart(); 
        int zoomStop = req.getZoomStop();  
        MimeType mimeType =  null; 
        try {
            mimeType = MimeType.createFromFormat(req.getMimeFormat());
        } catch (MimeException e4) {
            e4.printStackTrace();
        }
        SRS srs  = req.getProjection(); 
        BBOX bounds = req. getBounds();
        
        
        StringBuilder sb = new StringBuilder();
        StringRepresentation entity = null; 
        
        int srsIdx = layer.getSRSIndex(srs);
        int[][] coveredGridLevels = layer.getCoveredGridLevels(srsIdx, bounds);
        int[] metaTilingFactors = layer.getMetaTilingFactors();
        
        try {
            infoStart(sb, layer, zoomStart, zoomStop, mimeType, bounds);
        } catch (IOException e3) {
            e3.printStackTrace();
        }
        
        for (int level = zoomStart; level <= zoomStop; level++) {
            int[] gridBounds = coveredGridLevels[level];
            
            try {
                infoLevelStart(sb, layer, level, gridBounds);
            } catch (IOException e2) {
                e2.printStackTrace();
            }
            
            int count = 0;
            
            for (int gridy = gridBounds[1]; gridy <= gridBounds[3];  ) {
                
                for (int gridx = gridBounds[0]; gridx <= gridBounds[2]; ) {
                    
                    try {
                        infoTile(sb, count++);
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    int[] gridLoc = { gridx , gridy , level };
                    
                    Tile tile = new Tile(layer.getName(), srs, gridLoc, mimeType, null, null);
                   
                    try {
                        layer.getResponse(tile);
                    } catch (GeoWebCacheException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    
                    // Next column
                    gridx += metaTilingFactors[0];
                }
                // Next row
                gridy += metaTilingFactors[1];
            }

            log.info("Completed seeding level " + level + " for layer " + layer.getName());
            try {
                infoLevelStop(sb);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            infoEnd(sb);
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        entity = new StringRepresentation(sb);
        entity.setMediaType(MediaType.TEXT_HTML);
       // response.setEntity(entity);
        
        System.out.println(sb); 
      }
       catch(Exception e){
           e.printStackTrace();
           
       }
    }
    private void infoStart(StringBuilder sb, TileLayer layer, int zoomStart, int zoomStop,
            MimeType mimeType, BBOX bounds) throws IOException {
        if (sb == null) {
            return;
        }
        sb.append("<html><body><table><tr><td>Seeding " + layer.getName() 
                + " from level "+ zoomStart + " to level " + zoomStop 
                + " for format " + mimeType.getFormat() 
                + " and bounds " + bounds.getReadableString() 
                + "</td></tr>");
        
    }

    private void infoEnd(StringBuilder sb) throws IOException {
        if (sb == null) {
            return;
        }

        sb.append("</table></body></html>");
    }

    private void infoLevelStart(StringBuilder sb, TileLayer layer, int level, int[] gridBounds) throws IOException {
        if (sb == null) {
            return;
        }

        int tileCountX = (gridBounds[2] - gridBounds[0] + 1);
        int tileCountY = (gridBounds[3] - gridBounds[1] + 1);
        double metaCountX = ((double) tileCountX )/ layer.getMetaTilingFactors()[0];
        double metaCountY = ((double) tileCountY ) / layer.getMetaTilingFactors()[1];
        int metaTileCountX = (int) Math.ceil(metaCountX);
        int metaTileCountY = (int) Math.ceil(metaCountY);
        
        //tileCount / (layer.profile.metaHeight * layer.profile.metaWidth)
        sb.append("<tr><td>Level "
                + level
                + ", ~"
                + metaTileCountX*metaTileCountY
                + " metatile(s) [" + tileCountX*tileCountY
                + " tile(s)]</td></tr><tr><td>");
    }

    private void infoLevelStop(StringBuilder sb) throws IOException {
        if (sb == null) {
            return;
        }

        sb.append("</td></tr>");
    }

    private void infoTile(StringBuilder sb, int count) throws IOException {
        if (sb == null) {
            return;
        }

        // System.out.println("Count: " + count);
        sb.append("" + count + ", ");
    }
    
}
