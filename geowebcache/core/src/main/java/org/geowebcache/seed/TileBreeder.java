/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Marius Suta / The Open Planning Project 2008 (original code from SeedRestlet)
 * @author Arne Kepp / The Open Planning Project 2009 (original code from SeedRestlet)
 * @author Gabriel Roldan / OpenGeo 2010
 */
package org.geowebcache.seed;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.seed.GWCTask.STATE;
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
 *
 * <p>As of version 1.2.4a+, it is possible to control how GWC behaves in the event that a backend (WMS for example)
 * request fails during seeding, using the following environment variables:
 *
 * <ul>
 *   <li>{@code GWC_SEED_RETRY_COUNT}: specifies how many times to retry a failed request for each tile being seeded.
 *       Use {@code 0} for no retries, or any higher number. Defaults to {@code 0} retry meaning no retries are
 *       performed. It also means that the defaults to the other two variables do not apply at least you specify a
 *       higher value for GWC_SEED_RETRY_COUNT;
 *   <li>{@code GWC_SEED_RETRY_WAIT}: specifies how much to wait before each retry upon a failure to seed a tile, in
 *       milliseconds. Defaults to {@code 100ms};
 *   <li>{@code GWC_SEED_ABORT_LIMIT}: specifies the aggregated number of failures that a group of seeding threads
 *       should reach before aborting the seeding operation as a whole. This value is shared by all the threads launched
 *       as a single thread group; so if the value is {@code 10} and you launch a seed task with four threads, when
 *       {@code 10} failures are reached by all or any of those four threads the four threads will abort the seeding
 *       task. The default is {@code 1000}.
 * </ul>
 *
 * These environment variables can be established by any of the following ways, in order of precedence:
 *
 * <ol>
 *   <li>As a Java environment variable: for example {@code java -DGWC_SEED_RETRY_COUNT=5 ...};
 *   <li>As a Servlet context parameter: for example
 *       <pre>
 * <code>
 *   &lt;context-param&gt;
 *    &lt;!-- milliseconds between each retry upon a backend request failure --&gt;
 *    &lt;param-name&gt;GWC_SEED_RETRY_WAIT&lt;/param-name&gt;
 *    &lt;param-value&gt;500&lt;/param-value&gt;
 *   &lt;/context-param&gt;
 * </code>
 * </pre>
 *       In the web application's {@code WEB-INF/web.xml} configuration file;
 *   <li>As a System environment variable: {@code export GWC_SEED_ABORT_LIMIT=2000; <your usual command to run GWC
 *       here>}
 * </ol>
 *
 * @author Gabriel Roldan, based on Marius Suta's and Arne Kepp's SeedRestlet
 */
public class TileBreeder implements ApplicationContextAware {
    private static final String GWC_SEED_ABORT_LIMIT = "GWC_SEED_ABORT_LIMIT";

    private static final String GWC_SEED_RETRY_WAIT = "GWC_SEED_RETRY_WAIT";

    private static final String GWC_SEED_RETRY_COUNT = "GWC_SEED_RETRY_COUNT";

    private static Logger log = Logging.getLogger(TileBreeder.class.getName());

    private ThreadPoolExecutor threadPool;

    private TileLayerDispatcher layerDispatcher;

    private StorageBroker storageBroker;

    /** How many retries per failed tile. -1: disable checks, 0 = don't retry, 1 = retry once if failed, etc */
    public static int TILE_FAILURE_RETRY_COUNT_DEFAULT = 0;

    /** How much (in milliseconds) to wait before trying again a failed tile */
    public static long TILE_FAILURE_RETRY_WAIT_TIME_DEFAULT = 100;

    /**
     * How many failures to tolerate before aborting the seed task. Value is shared between all the threads of the same
     * run.
     */
    public static long TOTAL_FAILURES_BEFORE_ABORTING_DEFAULT = 1000;

    private Map<Long, SubmittedTask> currentPool = new TreeMap<>();

    private AtomicLong currentId = new AtomicLong();

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    // Number of dispatches without drain() being called.
    private int dispatchesWithoutDrain = 0;
    private static final int MAX_DISPATCHES_WITHOUT_DRAIN = 50;

    private static class SubmittedTask {
        public final GWCTask task;

        public final Future<GWCTask> future;

        public SubmittedTask(final GWCTask task, final Future<GWCTask> future) {
            this.task = task;
            this.future = future;
        }
    }

    /**
     * Initializes the seed task failure control variables either with the provided environment variable values or their
     * defaults.
     *
     * @see {@link TileBreeder class' javadocs} for more information
     * @see
     *     org.springframework.context.ApplicationContextAware#setApplicationContext(org.springframework.context.ApplicationContext)
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        String retryCount = GWCVars.findEnvVar(applicationContext, GWC_SEED_RETRY_COUNT);
        String retryWait = GWCVars.findEnvVar(applicationContext, GWC_SEED_RETRY_WAIT);
        String abortLimit = GWCVars.findEnvVar(applicationContext, GWC_SEED_ABORT_LIMIT);

        TILE_FAILURE_RETRY_COUNT_DEFAULT = (int) toLong(GWC_SEED_RETRY_COUNT, retryCount, -1);
        TILE_FAILURE_RETRY_WAIT_TIME_DEFAULT = toLong(GWC_SEED_RETRY_WAIT, retryWait, 100);
        TOTAL_FAILURES_BEFORE_ABORTING_DEFAULT = toLong(GWC_SEED_ABORT_LIMIT, abortLimit, 1000);

        checkPositive(TILE_FAILURE_RETRY_WAIT_TIME_DEFAULT, GWC_SEED_RETRY_WAIT);
        checkPositive(TOTAL_FAILURES_BEFORE_ABORTING_DEFAULT, GWC_SEED_ABORT_LIMIT);
    }

    @SuppressWarnings("serial")
    private void checkPositive(long value, String variable) {
        if (value < 0) {
            throw new BeanInitializationException("Invalid configuration value for environment variable "
                    + variable
                    + ". It should be a positive integer.") {};
        }
    }

    private long toLong(String varName, String paramVal, long defaultVal) {
        if (paramVal == null) {
            return defaultVal;
        }
        try {
            return Long.valueOf(paramVal);
        } catch (NumberFormatException e) {
            log.warning("Invalid environment parameter for "
                    + varName
                    + ": '"
                    + paramVal
                    + "'. Using default value: "
                    + defaultVal);
        }
        return defaultVal;
    }

    /** Create and dispatch tasks to fulfil a seed request */
    // TODO: The SeedRequest specifies a layer name. Would it make sense to use that instead of
    // including one as a separate parameter?
    public void seed(final String layerName, final SeedRequest sr) throws GeoWebCacheException {

        TileLayer tl = findTileLayer(layerName);

        TileRange tr = createTileRange(sr, tl);

        GWCTask[] tasks = createTasks(tr, tl, sr.getType(), sr.getThreadCount(), sr.getFilterUpdate());

        dispatchTasks(tasks);
    }

    /**
     * Create tasks to manipulate the cache (Seed, truncate, etc) They will still need to be dispatched.
     *
     * @param tr The range of tiles to work on.
     * @param type The type of task(s) to create
     * @param threadCount The number of threads to use, forced to 1 if type is TRUNCATE
     * @param filterUpdate // TODO: What does this do?
     * @return Array of tasks. Will have length threadCount or 1.
     */
    public GWCTask[] createTasks(TileRange tr, GWCTask.TYPE type, int threadCount, boolean filterUpdate)
            throws GeoWebCacheException {

        String layerName = tr.getLayerName();
        TileLayer tileLayer = layerDispatcher.getTileLayer(layerName);
        return createTasks(tr, tileLayer, type, threadCount, filterUpdate);
    }

    public GWCTask[] createTasks(TileRange tr, TileLayer tl, GWCTask.TYPE type, int threadCount, boolean filterUpdate)
            throws GeoWebCacheException {
        return createTasks(
                tr,
                tl,
                type,
                threadCount,
                filterUpdate,
                TILE_FAILURE_RETRY_COUNT_DEFAULT,
                TILE_FAILURE_RETRY_WAIT_TIME_DEFAULT,
                TOTAL_FAILURES_BEFORE_ABORTING_DEFAULT);
    }

    /**
     * Create tasks to manipulate the cache (Seed, truncate, etc). They will still need to be dispatched.
     *
     * @param tr The range of tiles to work on.
     * @param tl The layer to work on. Overrides any layer specified on tr.
     * @param type The type of task(s) to create
     * @param threadCount The number of threads to use, forced to 1 if type is TRUNCATE
     * @param filterUpdate // TODO: What does this do?
     * @param tileFailureRetryCount Number of retries for a single tile
     * @param tileFailureRetryWaitTime Time to wait between retries
     * @param totalFailuresBeforeAborting Total number of failures, across all threads, before aborting seeding
     * @return Array of tasks. Will have length threadCount or 1.
     */
    public GWCTask[] createTasks(
            TileRange tr,
            TileLayer tl,
            GWCTask.TYPE type,
            int threadCount,
            boolean filterUpdate,
            int tileFailureRetryCount,
            long tileFailureRetryWaitTime,
            long totalFailuresBeforeAborting)
            throws GeoWebCacheException {

        if (threadCount < 1) {
            log.finer("Forcing thread count to 1");
            threadCount = 1;
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
                task.setFailurePolicy(
                        tileFailureRetryCount, tileFailureRetryWaitTime, totalFailuresBeforeAborting, failureCounter);
                tasks[i] = task;
            }
            tasks[i].setThreadInfo(sharedThreadCount, i);
        }

        return tasks;
    }

    /** Dispatches tasks */
    public void dispatchTasks(GWCTask[] tasks) {
        lock.writeLock().lock();
        try {
            for (GWCTask gwcTask : tasks) {
                final Long taskId = this.currentId.incrementAndGet();
                final GWCTask task = gwcTask;
                task.setTaskId(taskId);
                Future<GWCTask> future = threadPool.submit(new MTSeeder(task));
                this.currentPool.put(taskId, new SubmittedTask(task, future));
            }
            dispatchesWithoutDrain++;
            if (dispatchesWithoutDrain > MAX_DISPATCHES_WITHOUT_DRAIN) {
                // There are probably a lot of completed tasks that need to be drained
                drain();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Find the tile range for a Seed Request. */
    public static TileRange createTileRange(SeedRequest req, TileLayer tl) throws GeoWebCacheException {
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
                log.log(Level.FINE, e4.getMessage(), e4);
            }
        }

        String gridSetId = req.getGridSetId();

        if (gridSetId == null) {
            SRS srs = req.getSRS();
            List<GridSubset> crsMatches = tl.getGridSubsetsForSRS(srs);
            if (!crsMatches.isEmpty()) {
                if (crsMatches.size() == 1) {
                    gridSetId = crsMatches.get(0).getName();
                } else {
                    throw new IllegalArgumentException("More than one GridSubet matches the requested SRS "
                            + srs
                            + ". gridSetId must be specified");
                }
            }
        }
        if (gridSetId == null) {
            gridSetId = tl.getGridSubsets().iterator().next();
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
        return new TileRange(layerName, gridSetId, zoomStart, zoomStop, coveredGridLevels, mimeType, parameters);
    }

    /**
     * Create a Seed/Reseed task.
     *
     * @param type the type, SEED or RESEED
     * @param trIter a collection of tile ranges
     * @param tl the layer
     */
    private GWCTask createSeedTask(TYPE type, TileRangeIterator trIter, TileLayer tl, boolean doFilterUpdate)
            throws IllegalArgumentException {

        switch (type) {
            case SEED:
                return new SeedTask(storageBroker, trIter, tl, false, doFilterUpdate);
            case RESEED:
                return new SeedTask(storageBroker, trIter, tl, true, doFilterUpdate);
            default:
                throw new IllegalArgumentException("Unknown request type " + type);
        }
    }

    private GWCTask createTruncateTask(TileRangeIterator trIter, TileLayer tl, boolean doFilterUpdate) {

        return new TruncateTask(storageBroker, trIter.getTileRange(), tl, doFilterUpdate);
    }

    /**
     * Method returns List of Strings representing the status of the currently running and scheduled threads
     *
     * @return array of {@code [[tilesDone, tilesTotal, timeRemaining, taskID, taskStatus],...]} where
     *     {@code taskStatus} is one of: {@code 0 = PENDING, 1 = RUNNING, 2 = DONE, -1 = ABORTED}
     */
    public long[][] getStatusList() {
        return getStatusList(null);
    }

    /**
     * Method returns List of Strings representing the status of the currently running and scheduled threads for a
     * specific layer.
     *
     * @return array of {@code [[tilesDone, tilesTotal, timeRemaining, taskID, taskStatus],...]} where
     *     {@code taskStatus} is one of: {@code 0 = PENDING, 1 = RUNNING, 2 = DONE, -1 = ABORTED}
     * @param layerName the name of the layer. null for all layers.
     */
    public long[][] getStatusList(final String layerName) {
        List<long[]> list = new ArrayList<>(currentPool.size());

        lock.readLock().lock();
        try {
            Iterator<Entry<Long, SubmittedTask>> iter = currentPool.entrySet().iterator();
            while (iter.hasNext()) {
                Entry<Long, SubmittedTask> entry = iter.next();
                GWCTask task = entry.getValue().task;
                if (layerName != null && !layerName.equals(task.getLayerName())) {
                    continue;
                }
                long[] ret = new long[5];
                ret[0] = task.getTilesDone();
                ret[1] = task.getTilesTotal();
                ret[2] = task.getTimeRemaining();
                ret[3] = task.getTaskId();
                ret[4] = stateCode(task.getState());
                list.add(ret);
            }
        } finally {
            lock.readLock().unlock();
            this.drain();
        }

        long[][] ret = list.toArray(new long[list.size()][]);
        return ret;
    }

    private long stateCode(STATE state) {
        switch (state) {
            case UNSET:
            case READY:
                return 0;
            case RUNNING:
                return 1;
            case DONE:
                return 2;
            case DEAD:
                return -1;
            default:
                throw new IllegalArgumentException("Unknown state: " + state);
        }
    }

    /** Remove all inactive tasks from the current pool */
    private void drain() {
        lock.writeLock().lock();
        try {
            dispatchesWithoutDrain = 0;
            threadPool.purge();
            for (Iterator<Entry<Long, SubmittedTask>> it =
                            this.currentPool.entrySet().iterator();
                    it.hasNext(); ) {
                if (it.next().getValue().future.isDone()) {
                    it.remove();
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void setTileLayerDispatcher(TileLayerDispatcher tileLayerDispatcher) {
        layerDispatcher = tileLayerDispatcher;
    }

    public void setThreadPoolExecutor(SeederThreadPoolExecutor stpe) {
        threadPool = stpe;
    }

    public void setStorageBroker(StorageBroker sb) {
        storageBroker = sb;
    }

    public StorageBroker getStorageBroker() {
        return storageBroker;
    }

    /**
     * Find a layer by name.
     *
     * @throws GeoWebCacheException if the layer is not found
     */
    public TileLayer findTileLayer(String layerName) throws GeoWebCacheException {

        TileLayer layer = layerDispatcher.getTileLayer(layerName);

        if (layer == null) {
            throw new GeoWebCacheException("Unknown layer: " + layerName);
        }

        return layer;
    }

    /** Get all tasks that are running */
    public Iterator<GWCTask> getRunningTasks() {
        drain();
        return filterTasks(STATE.RUNNING);
    }

    /** Get all tasks that are running or waiting to run. */
    public Iterator<GWCTask> getRunningAndPendingTasks() {
        drain();
        return filterTasks(STATE.READY, STATE.UNSET, STATE.RUNNING);
    }

    /** Get all tasks that are waiting to run. */
    public Iterator<GWCTask> getPendingTasks() {
        drain();
        return filterTasks(STATE.READY, STATE.UNSET);
    }

    /**
     * Return all current tasks that are in the specified states
     *
     * @param filter the states to filter for
     */
    private Iterator<GWCTask> filterTasks(STATE... filter) {
        Set<STATE> states = new HashSet<>(Arrays.asList(filter));
        List<GWCTask> runningTasks = new ArrayList<>(this.currentPool.size());
        lock.readLock().lock();
        try {
            Collection<SubmittedTask> values = this.currentPool.values();
            for (SubmittedTask t : values) {
                GWCTask task = t.task;
                if (states.contains(task.getState())) {
                    runningTasks.add(task);
                }
            }
        } finally {
            lock.readLock().unlock();
        }
        return runningTasks.iterator();
    }

    /** Terminate a running or pending task */
    public boolean terminateGWCTask(final long id) {
        SubmittedTask submittedTask = this.currentPool.remove(Long.valueOf(id));
        if (submittedTask == null) {
            return false;
        }
        submittedTask.task.terminateNicely();
        // submittedTask.future.cancel(true);
        return true;
    }

    /** Get an iterator over the layers. */
    public Iterable<TileLayer> getLayers() {
        return this.layerDispatcher.getLayerList();
    }
}
