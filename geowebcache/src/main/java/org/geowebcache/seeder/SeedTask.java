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
            //approximate thread creation time
            long START_TIME = System.currentTimeMillis();
            
            
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
            int TOTAL_TILES = tileCount(coveredGridLevels, zoomStart, zoomStop); 
            int count = 1;
            for (int level = zoomStart; level <= zoomStop; level++) {
                int[] gridBounds = coveredGridLevels[level];
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
                        
                        int[][] list = SeedResource.getStatusList();
                        synchronized(list) { 
                            list[arrayIndex]= getStatusInfo(arrayIndex, layer, count, TOTAL_TILES, START_TIME);
                        }
                        count++;
                    }
                    // Next row
                    gridy += metaTilingFactors[1];
                }

                log.info("Completed seeding level " + level + " for layer "
                        + layer.getName());
            }
            log.info("Completed seeding layer " + layer.getName());
            int[][] list = SeedResource.getStatusList();
            synchronized(list) {                
                    list[arrayIndex] = new int[3];
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            e.printStackTrace();

        }
    }

    /**
     * helper for counting the number of tiles
     * @param layer
     * @param level
     * @param gridBounds
     * @return
     */
    private int tileCount(int[][] coveredGridLevels, int startZoom, int stopZoom) {
        int count = 0;
        
        for(int i=startZoom; i<=stopZoom; i++) {
            int[] gridBounds = coveredGridLevels[i];
            count += (gridBounds[2] - gridBounds[0] + 1) * (gridBounds[3] - gridBounds[1] + 1);
        }
        
        return count;
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
    private int[] getStatusInfo(int arrayIndex, TileLayer layer,
            int tilecount, int total_tiles, long start_time) {
        int[] temp = new int[3];
        //working on tile
        temp[0] = tilecount;
        //out of
        temp[1] = total_tiles;
        //estimated time of completion in seconds
        int etc = (int) ((System.currentTimeMillis() - start_time)/tilecount)*(total_tiles - tilecount +1)/1000;
        temp[2] = etc;
        
        return temp; 
    }
}
