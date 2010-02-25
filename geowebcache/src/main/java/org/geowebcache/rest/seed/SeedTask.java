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
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.filter.request.RequestFilter;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.rest.GWCTask;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.TileRange;

public class SeedTask extends GWCTask {
    private static Log log = LogFactory.getLog(org.geowebcache.rest.seed.SeedTask.class);

    private final TileRangeIterator trIter;
    
    private final TileLayer tl;
    
    private boolean reseed; 
    
    private boolean doFilterUpdate;
    
    private StorageBroker storageBroker;
    
    /**
     * Constructs a SeedTask from a SeedRequest
     * @param req - the SeedRequest
     */
    public SeedTask(
            StorageBroker sb, TileRangeIterator trIter, 
            TileLayer tl, boolean reseed,
            boolean doFilterUpdate) {
        this.storageBroker = sb;
        this.trIter = trIter;
        this.tl = tl;
        this.reseed = reseed;
        this.doFilterUpdate = doFilterUpdate;
        
        if(reseed) {
            super.type = GWCTask.TYPE.RESEED;
        } else {
            super.type = GWCTask.TYPE.SEED;
        }
        super.layerName = tl.getName();
        
        super.state = GWCTask.STATE.READY;
    }
    
    /**
     * Method doAction().
     * this is where all the actual work is being done to seed a tile layer. 
     */
    public void doAction() throws GeoWebCacheException {
        super.state = GWCTask.STATE.RUNNING;
        
        // Lower the priority of the thread
        Thread.currentThread().setPriority((java.lang.Thread.NORM_PRIORITY + java.lang.Thread.MIN_PRIORITY) / 2);
        
        // approximate thread creation time
        long START_TIME = System.currentTimeMillis();

        log.info("Thread " + threadOffset + " begins seeding layer : " + tl.getName());

        int arrayIndex = getCurrentThreadArrayIndex();
        
        TileRange tr = trIter.getTileRange();
        
        // TODO move to TileRange object, or distinguish between thread and task
        super.tilesTotal = tileCount(tr.rangeBounds, tr.zoomStart, tr.zoomStop);
        
        final boolean tryCache = !reseed;
        
        long[] gridLoc = trIter.nextMetaGridLocation();
        
        while(gridLoc != null && this.terminate == false) {
            
            ConveyorTile tile = new ConveyorTile(
                    storageBroker, tl.getName(), 
                    tr.gridSetId, gridLoc, tr.mimeType, 
                    null, null, null, null);
            
            // Question is, how resilient should we be ?
            try {
                tl.seedTile(tile, tryCache);
            } catch (IOException ioe) {
                log.error("Seed failed at " + tile.toString() 
                        + ",\n exception: " + ioe.getMessage());
                super.state = GWCTask.STATE.DEAD;
                throw new GeoWebCacheException(ioe.getMessage());
            } catch (GeoWebCacheException gwce) {
                log.error("Seed failed at " + tile.toString()
                        + ",\n exception: " + gwce.getMessage());
                super.state = GWCTask.STATE.DEAD;
                throw gwce;
            }
            
            log.debug("Thread " + threadOffset + " seeded " + Arrays.toString(gridLoc));
            
            long totalTilesCompleted = trIter.getCountRendered() + trIter.getCountRendered();
            
            updateStatusInfo(arrayIndex, tl, totalTilesCompleted, START_TIME);
            
            gridLoc = trIter.nextMetaGridLocation();
        }
        
        if(this.terminate) {
            log.info("Thread " + threadOffset + " was terminated after " + this.tilesDone + " tiles");
        } else {
            log.info("Thread " + threadOffset + " completed (re)seeding layer " 
                    + tl.getName() + " after " + this.tilesDone + " tiles." );
        }
        
        if(threadOffset == 0 && doFilterUpdate) {
            runFilterUpdates(tr.gridSetId);
        }
        
        super.state = GWCTask.STATE.DONE;
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
            long tilesCount, long start_time) {
        
        //working on tile
        this.tilesDone = tilesCount;
        
        //estimated time of completion in seconds, use a moving average over the last 
        timeSpent = (int) (System.currentTimeMillis() - start_time) / 1000;
        
        long timeTotal = Math.round((double) timeSpent * ((double) tilesTotal / (double) tilesCount));
        
        timeRemaining = (int) (timeTotal - timeSpent);
    }
    
    /**
     * Updates any request filters
     */
    private void runFilterUpdates(String gridSetId) {
        // We will assume that all filters that can be updated should be updated
        List<RequestFilter> reqFilters = tl.getRequestFilters();
        if (reqFilters != null && !reqFilters.isEmpty()) {
            Iterator<RequestFilter> iter = reqFilters.iterator();
            while (iter.hasNext()) {
                RequestFilter reqFilter = iter.next();
                if (reqFilter.update(tl, gridSetId)) {
                    log.info("Updated request filter " + reqFilter.getName());
                } else {
                    log.debug("Request filter " + reqFilter.getName() + " returned false on update.");
                }
            }
        }
    }
}
