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
 * @author Marius Suta / The Open Planning Project 2008 
 */
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
    /**
     * Constructs a SeedTask from a SeedRequest
     * @param req - the SeedRequest
     */
    public SeedTask(SeedRequest req) {
        this.req = req;
    }
    /**
     * Method doSeed().
     * this is where all the actual work is being done to seed a tile layer. 
     */
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
            int arrayIndex = getCurrentThreadArrayIndex();
            
            int count = 1; 
            
            ////total number of tiles is 555
            int TOTAL_TILES = 555; 
            
            for (int level = zoomStart; level <= zoomStop; level++) {
                int[] gridBounds = coveredGridLevels[level];
                StringBuilder sb = getStatusInfo(arrayIndex, layer, count, TOTAL_TILES);
                for (int gridy = gridBounds[1]; gridy <= gridBounds[3];) {

                    for (int gridx = gridBounds[0]; gridx <= gridBounds[2];) {
                        int[] gridLoc = { gridx, gridy, level };

                        Tile tile = new Tile(layer, srs, gridLoc, mimeType,
                                null, null);
                        count++;
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

    /**
     * helper for reporting progress of seed task
     * @param layer
     * @param level
     * @param gridBounds
     * @return
     */
    private String infoLevelStart( int start, int stop, int[] gridBounds) {
        
        int tileCountX = (gridBounds[2] - gridBounds[0] + 1);
        int tileCountY = (gridBounds[3] - gridBounds[1] + 1);
        int levels = stop - start + 1; 
    
        /*
        double metaCountX = ((double) tileCountX )/ layer.getMetaTilingFactors()[0];
        double metaCountY = ((double) tileCountY ) / layer.getMetaTilingFactors()[1];
        int metaTileCountX = (int) Math.ceil(metaCountX);
        int metaTileCountY = (int) Math.ceil(metaCountY);
        */
        return levels * tileCountX*tileCountY + " tiles ";


    }
    /**
     * Helper method to get an index into the status array for the current thread.
     * Assumes the default name for the threads in the threadpool, i.e. "pool-#-thread-#"
     * where # is an integer. The index in the array will be the number of the thread, 
     * i.e. # in thread-# minus 1, since arrays are zero indexed an thread counting begins at 1.
     * @return
     */
    private int getCurrentThreadArrayIndex() {
        String tn = Thread.currentThread().getName();
        int indexOfnumber = tn.indexOf('d')+2;
        String tmp = tn.substring(indexOfnumber);
        int arrayIndex = Integer.parseInt(tmp);        
        arrayIndex--;
        return arrayIndex;
        
    }
    /**
     * Helper method to report status of thread progress.
     * @param arrayIndex
     * @param layer
     * @param zoomStart
     * @param zoomStop
     * @param level
     * @param gridBounds
     * @return
     */
    private StringBuilder getStatusInfo(int arrayIndex, TileLayer layer,
            int tilecount, int total_tiles) {
        StringBuilder sb = new StringBuilder();
        sb.append("Thread " + arrayIndex + " : \n");
        sb.append("\tseeding tile layer " + layer.getName() + " ");
        sb.append("\n working on tile " + tilecount);
        sb.append(" of " + total_tiles);
        return sb; 
    }
}
