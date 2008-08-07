package org.geowebcache.seeder;

import java.io.IOException;
import java.util.List;

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
    private static Log log = LogFactory
            .getLog(org.geowebcache.seeder.SeedTask.class);

    private SeedRequest req = null;

    public SeedTask(SeedRequest req) {
        this.req = req;
    }

    public void doSeed() {
        try {
            TileLayer layer = RESTDispatcher.getAllLayers().get(req.getName());
            log.info("Begin seeding layer : " + layer.getName());
            int zoomStart = req.getZoomStart();
            int zoomStop = req.getZoomStop();
            MimeType mimeType = null;
            try {
                mimeType = MimeType.createFromFormat(req.getMimeFormat());
            } catch (MimeException e4) {
                e4.printStackTrace();
            }
            SRS srs = req.getProjection();
            BBOX bounds = req.getBounds();

            int srsIdx = layer.getSRSIndex(srs);
            int[][] coveredGridLevels = layer.getCoveredGridLevels(srsIdx,
                    bounds);
            int[] metaTilingFactors = layer.getMetaTilingFactors();
            
            String tn = Thread.currentThread().getName();
            int indexOfnumber = tn.indexOf('d')+2;
            String tmp = tn.substring(indexOfnumber);
            int arrayIndex = Integer.parseInt(tmp);        
            arrayIndex--;
            for (int level = zoomStart; level <= zoomStop; level++) {
                StringBuilder sb = new StringBuilder();
                sb.append("Thread " + arrayIndex +" : \n");
                sb.append("\tseeding tile layer " + layer.getName() + " ");
                sb.append("from zoom level : " + zoomStart + " ");
                sb.append(" to zoom level : " + zoomStop + " ");
                int[] gridBounds = coveredGridLevels[level];            
                sb.append("\n\t working on level : " + infoLevelStart(layer, level, gridBounds) );
                for (int gridy = gridBounds[1]; gridy <= gridBounds[3];) {

                    for (int gridx = gridBounds[0]; gridx <= gridBounds[2];) {
                        int[] gridLoc = { gridx, gridy, level };

                        Tile tile = new Tile(layer, srs, gridLoc, mimeType,
                                null, null);

                        try {
                            layer.getResponse(tile);
                        } catch (GeoWebCacheException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        // Next column
                        gridx += metaTilingFactors[0];
                        
                        List list = SeedResource.getStatusList();
                        synchronized(list) { 
                            if(!list.isEmpty() && list.get(arrayIndex) != null)
                                list.remove(arrayIndex);
                            list.add(arrayIndex, sb.toString());
                        }
                    }
                    // Next row
                    gridy += metaTilingFactors[1];
                }

                log.info("Completed seeding level " + level + " for layer "
                        + layer.getName());
            }
            log.info("Completed seeding layer " + layer.getName());
        } catch (Exception e) {
            log.error(e.getMessage());
            e.printStackTrace();

        }
    }

    private String infoLevelStart( TileLayer layer, int level, int[] gridBounds) {

        int tileCountX = (gridBounds[2] - gridBounds[0] + 1);
        int tileCountY = (gridBounds[3] - gridBounds[1] + 1);
        double metaCountX = ((double) tileCountX )/ layer.getMetaTilingFactors()[0];
        double metaCountY = ((double) tileCountY ) / layer.getMetaTilingFactors()[1];
        int metaTileCountX = (int) Math.ceil(metaCountX);
        int metaTileCountY = (int) Math.ceil(metaCountY);
        
        return level + " with a total of " + tileCountX*tileCountY + " tiles ";


    }

}
