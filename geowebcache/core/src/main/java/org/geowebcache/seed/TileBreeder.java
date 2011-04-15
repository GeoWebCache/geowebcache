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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.seed.GWCTask.TYPE;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.TileRange;
import org.geowebcache.storage.TileRangeIterator;
import org.geowebcache.util.GWCVars;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Class in charge of dispatching seed/truncate tasks.
 * <p>
 * As of version 1.2.4a+, it is possible to control how GWC behaves in the event that a backend (WMS
 * for example) request fails during seeding, using the following environment variables:
 * <ul>
 * <li>{@code GWC_SEED_RETRY_COUNT}: specifies how many times to retry a failed request for each
 * tile being seeded. Use {@code 0} for no retries, or any higher number. Defaults to {@code 0}
 * retry meaning no retries are performed. It also means that the defaults to the other two
 * variables do not apply at least you specify a higher value for GWC_SEED_RETRY_COUNT;
 * <li>{@code GWC_SEED_RETRY_WAIT}: specifies how much to wait before each retry upon a failure to
 * seed a tile, in milliseconds. Defaults to {@code 100ms};
 * <li>{@code GWC_SEED_ABORT_LIMIT}: specifies the aggregated number of failures that a group of
 * seeding threads should reach before aborting the seeding operation as a whole. This value is
 * shared by all the threads launched as a single thread group; so if the value is {@code 10} and
 * you launch a seed task with four threads, when {@code 10} failures are reached by all or any of
 * those four threads the four threads will abort the seeding task. The default is {@code 1000}.
 * </ul>
 * These environment variables can be established by any of the following ways, in order of
 * precedence:
 * <ol>
 * <li>As a Java environment variable: for example {@code java -DGWC_SEED_RETRY_COUNT=5 ...};
 * <li>As a Servlet context parameter: for example
 * 
 * <pre>
 * <code>
 *   &lt;context-param&gt;
 *    &lt;!-- milliseconds between each retry upon a backend request failure --&gt;
 *    &lt;param-name&gt;GWC_SEED_RETRY_WAIT&lt;/param-name&gt;
 *    &lt;param-value&gt;500&lt;/param-value&gt;
 *   &lt;/context-param&gt;
 * </code>
 * </pre>
 * 
 * In the web application's {@code WEB-INF/web.xml} configuration file;
 * <li>As a System environment variable:
 * {@code export GWC_SEED_ABORT_LIMIT=2000; <your usual command to run GWC here>}
 * </ol>
 * </p>
 * 
 * @author Gabriel Roldan, based on Marius Suta's and Arne Kepp's SeedRestlet
 */
public class TileBreeder implements ApplicationContextAware {
    private static final String GWC_SEED_ABORT_LIMIT = "GWC_SEED_ABORT_LIMIT";

    private static final String GWC_SEED_RETRY_WAIT = "GWC_SEED_RETRY_WAIT";

    private static final String GWC_SEED_RETRY_COUNT = "GWC_SEED_RETRY_COUNT";

    private static Log log = LogFactory.getLog(TileBreeder.class);

    private SeederThreadPoolExecutor threadPool;

    private TileLayerDispatcher layerDispatcher;

    private StorageBroker storageBroker;

    /**
     * How many retries per failed tile. 0 = don't retry, 1 = retry once if failed, etc
     */
    private int tileFailureRetryCount = 0;

    /**
     * How much (in milliseconds) to wait before trying again a failed tile
     */
    private long tileFailureRetryWaitTime = 100;

    /**
     * How many failures to tolerate before aborting the seed task. Value is shared between all the
     * threads of the same run.
     */
    private long totalFailuresBeforeAborting = 1000;

    /**
     * Initializes the seed task failure control variables either with the provided environment
     * variable values or their defaults.
     * 
     * @see {@link TileBreeder class' javadocs} for more information
     * @see org.springframework.context.ApplicationContextAware#setApplicationContext(org.springframework.context.ApplicationContext)
     */
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        String retryCount = GWCVars.findEnvVar(applicationContext, GWC_SEED_RETRY_COUNT);
        String retryWait = GWCVars.findEnvVar(applicationContext, GWC_SEED_RETRY_WAIT);
        String abortLimit = GWCVars.findEnvVar(applicationContext, GWC_SEED_ABORT_LIMIT);

        tileFailureRetryCount = (int) toLong(GWC_SEED_RETRY_COUNT, retryCount, 0);
        tileFailureRetryWaitTime = toLong(GWC_SEED_RETRY_WAIT, retryWait, 100);
        totalFailuresBeforeAborting = toLong(GWC_SEED_ABORT_LIMIT, abortLimit, 1000);

        checkPositive(tileFailureRetryCount, GWC_SEED_RETRY_COUNT);
        checkPositive(tileFailureRetryWaitTime, GWC_SEED_RETRY_WAIT);
        checkPositive(totalFailuresBeforeAborting, GWC_SEED_ABORT_LIMIT);
    }

    @SuppressWarnings("serial")
    private void checkPositive(long value, String variable) {
        if (value < 0) {
            throw new BeanInitializationException(
                    "Invalid configuration value for environment variable " + variable
                            + ". It should be a positive integer.") {
            };
        }
    }

    private long toLong(String varName, String paramVal, long defaultVal) {
        if (paramVal == null) {
            return defaultVal;
        }
        try {
            return Long.valueOf(paramVal);
        } catch (NumberFormatException e) {
            log.warn("Invalid environment parameter for " + varName + ": '" + paramVal
                    + "'. Using default value: " + defaultVal);
        }
        return defaultVal;
    }

    public void seed(final String layerName, final SeedRequest sr) throws GeoWebCacheException {

        TileLayer tl = findTileLayer(layerName);

        TileRange tr = createTileRange(sr, tl);

        GWCTask[] tasks = createTasks(tr, tl, sr.getType(), sr.getThreadCount(),
                sr.getFilterUpdate());

        dispatchTasks(tasks);
    }

    public GWCTask[] createTasks(TileRange tr, GWCTask.TYPE type, int threadCount,
            boolean filterUpdate) throws GeoWebCacheException {

        String layerName = tr.layerName;
        TileLayer tileLayer = layerDispatcher.getTileLayer(layerName);
        return createTasks(tr, tileLayer, type, threadCount, filterUpdate);
    }

    public GWCTask[] createTasks(TileRange tr, TileLayer tl, GWCTask.TYPE type, int threadCount,
            boolean filterUpdate) throws GeoWebCacheException {

        if (type == GWCTask.TYPE.TRUNCATE || threadCount < 1) {
            log.trace("Forcing thread count to 1");
            threadCount = 1;
        }

        if (threadCount > threadPool.getMaximumPoolSize()) {
            throw new GeoWebCacheException("Asked to use " + threadCount + " threads,"
                    + " but maximum is " + threadPool.getMaximumPoolSize());
        }

        TileRangeIterator trIter = new TileRangeIterator(tr, tl.getMetaTilingFactors());

        GWCTask[] tasks = new GWCTask[threadCount];

        AtomicLong failureCounter = new AtomicLong();
        AtomicInteger sharedThreadCount = new AtomicInteger();
        for (int i = 0; i < threadCount; i++) {
            if (type == TYPE.TRUNCATE) {
                tasks[i] = createTruncateTask(trIter, tl, filterUpdate);
            } else {
                SeedTask task = (SeedTask) createSeedTask(type, trIter, tl, filterUpdate);
                task.setFailurePolicy(tileFailureRetryCount, tileFailureRetryWaitTime,
                        totalFailuresBeforeAborting, failureCounter);
                tasks[i] = task;
            }
            tasks[i].setThreadInfo(sharedThreadCount, i);
        }

        return tasks;
    }

    public void dispatchTasks(GWCTask[] tasks) {
        for (int i = 0; i < tasks.length; i++) {
            threadPool.submit(new MTSeeder(tasks[i]));
        }
    }

    public static TileRange createTileRange(SeedRequest req, TileLayer tl)
            throws GeoWebCacheException {
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

        if (gridSubset == null) {
            throw new GeoWebCacheException("Unknown grid set " + gridSetId);
        }

        long[][] coveredGridLevels;

        BoundingBox bounds = req.getBounds();
        if (bounds == null) {
            coveredGridLevels = gridSubset.getCoverages();
        } else {
            coveredGridLevels = gridSubset.getCoverageIntersections(bounds);
        }

        int[] metaTilingFactors = tl.getMetaTilingFactors();

        coveredGridLevels = gridSubset.expandToMetaFactors(coveredGridLevels, metaTilingFactors);

        String layerName = tl.getName();
        Map<String, String> parameters = req.getParameters();
        return new TileRange(layerName, gridSetId, zoomStart, zoomStop, coveredGridLevels,
                mimeType, parameters);
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
    private GWCTask createSeedTask(TYPE type, TileRangeIterator trIter, TileLayer tl,
            boolean doFilterUpdate) throws IllegalArgumentException {

        switch (type) {
        case SEED:
            return new SeedTask(storageBroker, trIter, tl, false, doFilterUpdate);
        case RESEED:
            return new SeedTask(storageBroker, trIter, tl, true, doFilterUpdate);
        default:
            throw new IllegalArgumentException("Unknown request type " + type);
        }
    }

    private GWCTask createTruncateTask(TileRangeIterator trIter, TileLayer tl,
            boolean doFilterUpdate) {

        return new TruncateTask(storageBroker, trIter.getTileRange(), tl, doFilterUpdate);
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

    public Iterable<TileLayer> getLayers() {
        return this.layerDispatcher.getLayerList();
    }

}
