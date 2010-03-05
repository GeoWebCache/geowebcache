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
 * @author Marius Suta / The Open Planning Project 2008 (original code from SeedRestlet)
 * @author Arne Kepp / The Open Planning Project 2009 (original code from SeedRestlet)
 * @author Gabriel Roldan / OpenGeo 2010  
 */
package org.geowebcache.seed;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.rest.GWCTask;
import org.geowebcache.rest.GWCTask.TYPE;
import org.geowebcache.rest.seed.MTSeeder;
import org.geowebcache.rest.seed.SeedRequest;
import org.geowebcache.rest.seed.SeedTask;
import org.geowebcache.rest.seed.SeederThreadPoolExecutor;
import org.geowebcache.rest.seed.TileRangeIterator;
import org.geowebcache.rest.seed.TruncateTask;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.TileRange;

/**
 * 
 * @author Gabriel Roldan, based on Marius Suta's and Arne Kepp's SeedRestlet
 */
public class TileBreeder {
    private static Log log = LogFactory.getLog(TileBreeder.class);

    private SeederThreadPoolExecutor threadPool;

    private TileLayerDispatcher layerDispatcher;

    private StorageBroker storageBroker;

    public void seed(final String layerName, final SeedRequest sr) throws GeoWebCacheException {

        TileLayer tl = findTileLayer(layerName);

        TileRange tr = createTileRange(sr, tl);

        GWCTask[] tasks = createTasks(tr, tl, sr.getType(), sr.getThreadCount(), sr
                .getFilterUpdate());

        dispatchTasks(tasks);
    }

    public GWCTask[] createTasks(TileRange tr, TileLayer tl, GWCTask.TYPE type, int threadCount,
            boolean filterUpdate) throws GeoWebCacheException {

        if (type == GWCTask.TYPE.TRUNCATE || threadCount < 1) {
            log.debug("Forcing thread count to 1");
            threadCount = 1;
        }

        if (threadCount > threadPool.getMaximumPoolSize()) {
            throw new GeoWebCacheException("Asked to use " + threadCount + " threads,"
                    + " but maximum is " + threadPool.getMaximumPoolSize());
        }

        TileRangeIterator trIter = new TileRangeIterator(tr, tl.getMetaTilingFactors());

        GWCTask[] tasks = new GWCTask[threadCount];

        for (int i = 0; i < threadCount; i++) {
            tasks[i] = createTask(type, trIter, tl, filterUpdate);
            tasks[i].setThreadInfo(threadCount, i);
        }

        return tasks;
    }

    public void dispatchTasks(GWCTask[] tasks) {
        for (int i = 0; i < tasks.length; i++) {
            threadPool.submit(new MTSeeder(tasks[i]));
        }
    }

    public static TileRange createTileRange(SeedRequest req, TileLayer tl) {
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
            gridSetId = tl.getGridSubsetForSRS(req.getSRS()).getName();
        }
        if (gridSetId == null) {
            gridSetId = tl.getGridSubsets().entrySet().iterator().next().getKey();
        }

        GridSubset gridSubset = tl.getGridSubset(gridSetId);

        long[][] coveredGridLevels;

        BoundingBox bounds = req.getBounds();
        if (bounds == null) {
            coveredGridLevels = gridSubset.getCoverages();
        } else {
            coveredGridLevels = gridSubset.getCoverageIntersections(bounds);
        }

        int[] metaTilingFactors = tl.getMetaTilingFactors();

        coveredGridLevels = gridSubset.expandToMetaFactors(coveredGridLevels, metaTilingFactors);

        // TODO Check the null
        return new TileRange(tl.getName(), gridSetId, zoomStart, zoomStop, coveredGridLevels,
                mimeType, null);
    }

    /**
     * 
     * @param type
     * @param trIter
     * @param tl
     * @param doFilterUpdate
     * @return
     * @throws IllegalArgumentException
     */
    private GWCTask createTask(TYPE type, TileRangeIterator trIter, TileLayer tl,
            boolean doFilterUpdate) throws IllegalArgumentException {

        switch (type) {
        case SEED:
            return new SeedTask(storageBroker, trIter, tl, false, doFilterUpdate);
        case RESEED:
            return new SeedTask(storageBroker, trIter, tl, true, doFilterUpdate);
        case TRUNCATE:
            return new TruncateTask(storageBroker, trIter.getTileRange(), tl, doFilterUpdate);
        default:
            throw new IllegalArgumentException("Unknown request type " + type);
        }
    }

    /**
     * Method returns List of Strings representing the status of the currently running threads
     * 
     * @return
     */
    public long[][] getStatusList() {
        Iterator<Entry<Long, GWCTask>> iter = threadPool.getRunningTasksIterator();

        long[][] ret = new long[threadPool.getMaximumPoolSize()][3];
        int idx = 0;

        while (iter.hasNext()) {
            Entry<Long, GWCTask> entry = iter.next();
            GWCTask task = entry.getValue();

            ret[idx][0] = (int) task.getTilesDone();

            ret[idx][1] = (int) task.getTilesTotal();

            ret[idx][2] = task.getTimeRemaining();

            idx++;
        }

        return ret;
    }

    public void setTileLayerDispatcher(TileLayerDispatcher tileLayerDispatcher) {
        layerDispatcher = tileLayerDispatcher;
    }

    public void setThreadPoolExecutor(SeederThreadPoolExecutor stpe) {
        threadPool = stpe;
        // statusArray = new int[threadPool.getMaximumPoolSize()][3];
    }

    public void setStorageBroker(StorageBroker sb) {
        storageBroker = sb;
    }

    public TileLayer findTileLayer(String layerName) throws GeoWebCacheException {
        TileLayer layer = null;

        layer = layerDispatcher.getTileLayer(layerName);

        if (layer == null) {
            throw new GeoWebCacheException("Uknown layer: " + layerName);
        }

        return layer;
    }

    public Iterator<Entry<Long, GWCTask>> getRunningTasksIterator() {
        return threadPool.getRunningTasksIterator();
    }

    public boolean terminateGWCTask(final long id) {
        return threadPool.terminateGWCTask(id);
    }

    public Map<String, TileLayer> getLayers() {
        return this.layerDispatcher.getLayers();
    }
}
