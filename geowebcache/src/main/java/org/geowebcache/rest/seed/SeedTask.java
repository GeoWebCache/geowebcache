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
 * @author Arne Kepp / The Open Planning Project 2009 
 */
package org.geowebcache.rest.seed;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubSet;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.rest.GWCTask;
import org.geowebcache.storage.StorageBroker;

public class SeedTask extends GWCTask {
    private static Log log = LogFactory.getLog(org.geowebcache.rest.seed.SeedTask.class);

    private final SeedRequest req; 
    
    private final TileLayer tl;
    
    private boolean reseed = false; 
    
    private StorageBroker storageBroker;
    
    /**
     * Constructs a SeedTask from a SeedRequest
     * @param req - the SeedRequest
     */
    public SeedTask(StorageBroker sb, SeedRequest req, TileLayer tl, boolean reseed) {
        this.storageBroker = sb;
        this.req = req;
        this.tl = tl;
        this.reseed = reseed;
        
        if(reseed) {
            super.type = GWCTask.TYPE_RESEED;
        } else {
            super.type = GWCTask.TYPE_SEED;
        }
        super.layerName = tl.getName();
    }

    /**
     * Method doAction().
     * this is where all the actual work is being done to seed a tile layer. 
     */
    public void doAction() throws GeoWebCacheException {        
        // Lower the priority of the thread
        Thread.currentThread().setPriority((java.lang.Thread.NORM_PRIORITY + java.lang.Thread.MIN_PRIORITY) / 2);

        // approximate thread creation time
        long START_TIME = System.currentTimeMillis();

        log.info("Thread " + threadOffset + " begins seeding layer : " + tl.getName());
        int zoomStart = req.getZoomStart().intValue();
        int zoomStop = req.getZoomStop().intValue();
        
        MimeType mimeType = null;
        String format = req.getMimeFormat();
        if (format == null) {
            mimeType = tl.getMimeTypes().get(0);
        } else {
            try {
                mimeType = MimeType.createFromFormat(format);
            } catch (MimeException e4) {
                e4.printStackTrace();
            }
        }
        
        String gridSetId = req.getGridSetId();
        if (gridSetId == null) {
            gridSetId = tl.getGridSubSets().entrySet().iterator().next().getKey();
        }
        
        GridSubSet gridSubSet = tl.getGridSubSet(gridSetId);

        long[][] coveredGridLevels;
        
        BoundingBox bounds = req.getBounds();
        if (bounds == null) {
            coveredGridLevels = gridSubSet.getCoverages();
        } else {
            coveredGridLevels = gridSubSet.getCoverageIntersections(bounds);
        }
   
        int[] metaTilingFactors = tl.getMetaTilingFactors();

        int arrayIndex = getCurrentThreadArrayIndex();
        long TOTAL_TILES = -1;
        super.tilesTotal = -1;
        TOTAL_TILES = tileCount(coveredGridLevels, zoomStart, zoomStop);
        if(TOTAL_TILES > 0) {
            super.tilesTotal = TOTAL_TILES / (long) threadCount;
	}
        
        int count = 0;
        boolean tryCache = !reseed;

        for (int level = zoomStart; level <= zoomStop && this.terminate == false; level++) {
            long[] levelGrid = coveredGridLevels[level];
            
            // Round down to the closes metatile boundary before starting
            long starty = levelGrid[1] - (levelGrid[1] % metaTilingFactors[1]);
            for (long gridy = starty; gridy <= levelGrid[3];) {
            	
            	// Round down to the closest metatile boundary before starting
            	long startx = levelGrid[0] - (levelGrid[0] % metaTilingFactors[0]);
                for (long gridx = startx + (threadOffset * metaTilingFactors[0]); gridx <= levelGrid[2] && this.terminate == false; ) {

                    long[] gridLoc = { gridx, gridy, level };

                    ConveyorTile tile = new ConveyorTile(storageBroker, tl.getName(), gridSetId, gridLoc, mimeType, null, null, null, null);
                    
                    // Question is, how resilient should we be ?
                    try {
                        tl.seedTile(tile, tryCache);
                    //} catch (GeoWebCacheException e) {
                    //    e.printStackTrace();
                    } catch (IOException ioe) {
                        //e.printStackTrace();
                        throw new GeoWebCacheException(ioe.getMessage());
                    }

                    long countX;
                    if (gridx + metaTilingFactors[0] - 1 > levelGrid[2]) {
                        countX = (gridx + metaTilingFactors[0] - 1) - levelGrid[2];
                    } else {
                        countX = metaTilingFactors[0];
                    }

                    long countY;
                    if (gridy + metaTilingFactors[1] - 1 > levelGrid[3]) {
                        countY = (gridy + metaTilingFactors[1] - 1) - levelGrid[3];
                    } else {
                        countY = metaTilingFactors[1];
                    }

                    count += countX * countY;

                    updateStatusInfo(arrayIndex, tl, count, START_TIME);

                    // Next column
                    gridx += (metaTilingFactors[0] * threadCount);
                }
                // Next row
                gridy += metaTilingFactors[1];
            }

            double percCompl = (100.0 * count)
                    / (double) super.tilesTotal;
            int intPercCompl = (int) Math.floor(percCompl);
            int decPercCompl = (int) Math.round((percCompl - intPercCompl) * 100);

            if (intPercCompl < 0) {
                intPercCompl = 0;
                decPercCompl = 0;
            }

            log.info("Thread " + threadOffset + " completed (re)seeding level "
                    + level + " for layer " + tl.getName() + " (ca. "
                    + intPercCompl + "." + decPercCompl + "%)");
        }
        
        if(this.terminate) {
            log.info("Thread " + threadOffset + " was terminated after " + this.tilesDone + " tiles");
        } else {
            log.info("Thread " + threadOffset + " completed (re)seeding layer "
                    + tl.getName() + " after " + super.tilesDone
                    + " tiles, of an estimated " + super.tilesTotal);
        }
    }

    /**
     * helper for counting the number of tiles
     * @param layer
     * @param level
     * @param gridBounds
     * @return -1 if too many
     */
    private long tileCount(long[][] coveredGridLevels, int startZoom, int stopZoom) {
        long count = 0;
        
        for(int i=startZoom; i<=stopZoom; i++) {
            long[] gridBounds = coveredGridLevels[i];
            
            long thisLevel = (1 + gridBounds[2] - gridBounds[0]) * (1 + gridBounds[3] - gridBounds[1]);
            
            if(thisLevel > (Long.MAX_VALUE / 4) && i != stopZoom) {
                return -1;
            } else {
                count += thisLevel;
            }
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
        return Integer.parseInt(tmp) - 1;
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
    private void updateStatusInfo(int arrayIndex, TileLayer layer,
            int tilesCount, long start_time) {
        
        //working on tile
        this.tilesDone = tilesCount;
        
        //estimated time of completion in seconds, use a moving average over the last 
        timeSpent = (int) (System.currentTimeMillis() - start_time) / 1000;
        
        long timeTotal = Math.round((double) timeSpent * ((double) tilesTotal / (double) tilesCount));
        
        timeRemaining = (int) (timeTotal - timeSpent);
    }
}
