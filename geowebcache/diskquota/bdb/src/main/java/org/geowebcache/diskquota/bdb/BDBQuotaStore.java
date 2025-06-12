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
 * @author Gabriel Roldan Copyright 2011
 */
package org.geowebcache.diskquota.bdb;

import static org.geowebcache.diskquota.DiskQuotaMonitor.GWC_DISKQUOTA_DISABLED;
import static org.geowebcache.util.FileUtils.listFilesNullSafe;

import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.Environment;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.Transaction;
import com.sleepycat.persist.EntityCursor;
import com.sleepycat.persist.EntityStore;
import com.sleepycat.persist.PrimaryIndex;
import com.sleepycat.persist.SecondaryIndex;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
import org.geotools.util.logging.Logging;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.diskquota.QuotaStore;
import org.geowebcache.diskquota.storage.PageStats;
import org.geowebcache.diskquota.storage.PageStatsPayload;
import org.geowebcache.diskquota.storage.PageStoreConfig;
import org.geowebcache.diskquota.storage.Quota;
import org.geowebcache.diskquota.storage.TilePage;
import org.geowebcache.diskquota.storage.TilePageCalculator;
import org.geowebcache.diskquota.storage.TileSet;
import org.geowebcache.diskquota.storage.TileSetVisitor;
import org.geowebcache.storage.DefaultStorageFinder;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.util.Assert;

public class BDBQuotaStore implements QuotaStore {

    private static final Logger log = Logging.getLogger(BDBQuotaStore.class.getName());

    private static final String GLOBAL_QUOTA_NAME = "___GLOBAL_QUOTA___";

    public static final String STORE_VERSION = "1.1";

    private static final String VERSION_FILE = "version.txt";

    private EntityStore entityStore;

    private final String cacheRootDir;

    private final TilePageCalculator tilePageCalculator;

    private static ExecutorService transactionRunner;

    private PrimaryIndex<String, TileSet> tileSetById;

    private PrimaryIndex<Integer, Quota> usedQuotaById;

    private PrimaryIndex<Long, TilePage> pageById;

    private PrimaryIndex<Long, PageStats> pageStatsById;

    private SecondaryIndex<String, String, TileSet> tileSetsByLayer;

    private SecondaryIndex<String, Long, TilePage> pageByKey;

    private SecondaryIndex<String, Long, TilePage> pagesByTileSetId;

    private SecondaryIndex<Long, Long, PageStats> pageStatsByPageId;

    private SecondaryIndex<Float, Long, PageStats> pageStatsByLRU;

    private SecondaryIndex<Float, Long, PageStats> pageStatsByLFU;

    private SecondaryIndex<String, Integer, Quota> usedQuotaByTileSetId;

    private volatile boolean open;

    private boolean diskQuotaEnabled;

    public BDBQuotaStore(final DefaultStorageFinder cacheDirFinder, TilePageCalculator tilePageCalculator)
            throws ConfigurationException {

        Assert.notNull(cacheDirFinder, "cacheDirFinder can't be null");
        Assert.notNull(tilePageCalculator, "tilePageCalculator can't be null");

        this.tilePageCalculator = tilePageCalculator;
        this.cacheRootDir = cacheDirFinder.getDefaultPath();

        boolean disabled = Boolean.parseBoolean(cacheDirFinder.findEnvVar(GWC_DISKQUOTA_DISABLED));
        if (disabled) {
            log.warning(" -- Found environment variable "
                    + GWC_DISKQUOTA_DISABLED
                    + " set to true. DiskQuotaMonitor is disabled.");
        }
        this.diskQuotaEnabled = !disabled;
    }

    /** @see {@link #close()} */
    public void startUp() throws InterruptedException, IOException {
        if (!diskQuotaEnabled) {
            log.info(getClass().getName() + " won't start, got env variable " + GWC_DISKQUOTA_DISABLED + "=true");
            return;
        }
        open = true;
        File storeDirectory = new File(cacheRootDir, "diskquota_page_store");
        storeDirectory.mkdirs();
        File version = new File(storeDirectory, VERSION_FILE);
        if (listFilesNullSafe(storeDirectory).length == 0) {
            // Directory is empty
            try {
                FileUtils.write(version, STORE_VERSION, "UTF-8");
            } catch (IOException e) {
                throw new IOException("BDB DiskQuota could not write " + VERSION_FILE + " to new database", e);
            }
        } else {
            // Directory not empty
            try {
                String versionString = FileUtils.readFileToString(version, "UTF-8");
                if (!versionString.equals(STORE_VERSION)) {
                    throw new IOException("BDB DiskQuota does not support database version " + versionString);
                }
            } catch (IOException e) {
                throw new IOException(
                        "BDB DiskQuota could not read " + VERSION_FILE + " to detemine database version", e);
            }
        }

        CustomizableThreadFactory tf = new CustomizableThreadFactory("GWC DiskQuota Store Writer-");
        transactionRunner = Executors.newFixedThreadPool(1, tf);
        try {
            configure(storeDirectory);

            deleteStaleLayersAndCreateMissingTileSets();

            log.config("Berkeley DB JE Disk Quota page store configured at " + storeDirectory.getAbsolutePath());
        } catch (RuntimeException e) {
            transactionRunner.shutdownNow();
            throw e;
        }
        log.config("Quota Store initialized. Global quota: "
                + getGloballyUsedQuota().toNiceString());
    }

    @Override
    public void close() throws Exception {
        if (!diskQuotaEnabled) {
            return;
        }
        open = false;
        log.config("Requesting to close quota store...");
        transactionRunner.shutdown();
        try {
            transactionRunner.awaitTermination(30 * 1000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            log.log(
                    Level.SEVERE,
                    "Time out shutting down quota store write thread, trying to " + "close the entity store as is.",
                    ie);
            Thread.currentThread().interrupt();
        } finally {
            Environment environment = entityStore.getEnvironment();
            entityStore.close();
            environment.close();
        }
        log.config("Quota store closed.");
    }

    private void configure(final File storeDirectory) throws InterruptedException {
        // todo: make config persistent? or just rely on je.properties (I guess so)
        PageStoreConfig config = new PageStoreConfig();
        EntityStoreBuilder builder = new EntityStoreBuilder(config);
        EntityStore entityStore = builder.buildEntityStore(storeDirectory, null);
        this.entityStore = entityStore;

        tileSetById = entityStore.getPrimaryIndex(String.class, TileSet.class);
        pageById = entityStore.getPrimaryIndex(Long.class, TilePage.class);
        pageStatsById = entityStore.getPrimaryIndex(Long.class, PageStats.class);
        usedQuotaById = entityStore.getPrimaryIndex(Integer.class, Quota.class);

        pageByKey = entityStore.getSecondaryIndex(pageById, String.class, "page_key");
        pagesByTileSetId = entityStore.getSecondaryIndex(pageById, String.class, "tileset_id_fk");
        tileSetsByLayer = entityStore.getSecondaryIndex(tileSetById, String.class, "layer");
        pageStatsByLRU = entityStore.getSecondaryIndex(pageStatsById, Float.class, "LRU");
        pageStatsByLFU = entityStore.getSecondaryIndex(pageStatsById, Float.class, "LFU");
        usedQuotaByTileSetId = entityStore.getSecondaryIndex(usedQuotaById, String.class, "tileset_id");
        pageStatsByPageId = entityStore.getSecondaryIndex(pageStatsById, Long.class, "page_stats_by_page_id");
    }

    private class StartUpInitializer implements Callable<Void> {
        @Override
        public Void call() throws Exception {
            final Transaction transaction = entityStore.getEnvironment().beginTransaction(null, null);
            try {
                if (null == usedQuotaByTileSetId.get(transaction, GLOBAL_QUOTA_NAME, LockMode.DEFAULT)) {
                    log.fine("First time run: creating global quota object");
                    // need a global TileSet cause the Quota->TileSet relationship is enforced
                    TileSet globalTileSet = new TileSet(GLOBAL_QUOTA_NAME);
                    tileSetById.put(transaction, globalTileSet);

                    Quota globalQuota = new Quota();
                    globalQuota.setTileSetId(GLOBAL_QUOTA_NAME);
                    usedQuotaById.put(transaction, globalQuota);
                    log.fine("created Global Quota");
                }

                final Set<String> layerNames = tilePageCalculator.getLayerNames();
                final Set<String> existingLayers = new GetLayerNames().call();

                final Set<String> layersToDelete = new HashSet<>(existingLayers);
                layersToDelete.removeAll(layerNames);

                for (String layerName : layersToDelete) {
                    log.info("Deleting disk quota information for layer '"
                            + layerName
                            + "' as it does not exist anymore...");
                    // do not call issue since we're already running on the transaction thread here
                    try {
                        new Deleter(layerName, ts -> true).call(transaction);
                    } catch (Exception e) {
                        log.log(
                                Level.WARNING,
                                "Error deleting disk quota information for layer '" + layerName + "'",
                                e);
                    }
                }

                // add any missing tileset
                for (String layerName : layerNames) {
                    createLayer(layerName, transaction);
                }
                transaction.commit();
            } catch (RuntimeException e) {
                transaction.abort();
                throw e;
            }
            return null;
        }
    }

    /** @see org.geowebcache.diskquota.QuotaStore#createLayer(java.lang.String) */
    @Override
    public void createLayer(final String layerName) throws InterruptedException {
        issueSync((Callable<Void>) () -> {
            final Transaction transaction = entityStore.getEnvironment().beginTransaction(null, null);
            try {
                createLayer(layerName, transaction);
                transaction.commit();
            } catch (RuntimeException e) {
                transaction.abort();
            }
            return null;
        });
    }

    private void createLayer(String layerName, final Transaction transaction) {
        Set<TileSet> layerTileSets = tilePageCalculator.getTileSetsFor(layerName);
        for (TileSet tset : layerTileSets) {
            getOrCreateTileSet(transaction, tset);
        }
    }

    private TileSet getOrCreateTileSet(final Transaction transaction, final TileSet tset) {
        String id = tset.getId();
        TileSet stored;
        if (null == (stored = tileSetById.get(transaction, id, LockMode.DEFAULT))) {
            log.fine("Creating TileSet for quota tracking: " + tset);
            tileSetById.putNoReturn(transaction, tset);
            stored = tset;
            Quota tileSetUsedQuota = new Quota();
            tileSetUsedQuota.setTileSetId(tset.getId());
            usedQuotaById.putNoReturn(transaction, tileSetUsedQuota);
        }
        return stored;
    }

    /** Asynchronously issues the given {@code command} to the working transactional thread */
    private <E> Future<E> issue(final Callable<E> command) {
        if (!open) {
            throw new IllegalStateException("QuotaStore is closed.");
        }
        Future<E> future = transactionRunner.submit(command);
        return future;
    }

    /**
     * Synchronously issues the given {@code command} to the working transactional thread
     *
     * @throws InterruptedException in case the calling thread was interrupted while waiting for the command to complete
     */
    private <E> E issueSync(final Callable<E> command) throws InterruptedException {
        Future<E> result = issue(command);
        try {
            return result.get();
        } catch (RuntimeException e) {
            throw e;
        } catch (InterruptedException e) {
            log.fine("Caught InterruptedException while waiting for command "
                    + command.getClass().getSimpleName());
            throw e;
        } catch (ExecutionException e) {
            log.log(Level.WARNING, e.getMessage(), e);
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException exception) {
                throw exception;
            }
            throw new RuntimeException(cause);
        }
    }

    private void deleteStaleLayersAndCreateMissingTileSets() throws InterruptedException {
        issueSync(new StartUpInitializer());
    }

    private class GetLayerNames implements Callable<Set<String>> {

        @Override
        public Set<String> call() throws Exception {
            EntityCursor<String> layerNameCursor = tileSetsByLayer.keys(null, CursorConfig.DEFAULT);
            Set<String> names = new HashSet<>();
            try {
                String name;
                while ((name = layerNameCursor.nextNoDup()) != null) {
                    if (!GLOBAL_QUOTA_NAME.equals(name)) {
                        names.add(name);
                    }
                }
            } finally {
                layerNameCursor.close();
            }
            return names;
        }
    }

    /** @see org.geowebcache.diskquota.QuotaStore#getGloballyUsedQuota() */
    @Override
    public Quota getGloballyUsedQuota() throws InterruptedException {
        return getUsedQuotaByTileSetId(GLOBAL_QUOTA_NAME);
    }

    /** @see org.geowebcache.diskquota.QuotaStore#getUsedQuotaByTileSetId(java.lang.String) */
    @Override
    public Quota getUsedQuotaByTileSetId(final String tileSetId) throws InterruptedException {
        Quota usedQuota = issueSync(new UsedQuotaByTileSetId(tileSetId));
        return usedQuota;
    }

    private final class UsedQuotaByTileSetId implements Callable<Quota> {
        private final String tileSetId;

        private UsedQuotaByTileSetId(String tileSetId) {
            this.tileSetId = tileSetId;
        }

        @Override
        public Quota call() throws Exception {
            Quota quota = usedQuotaByTileSetId.get(null, tileSetId, LockMode.READ_COMMITTED);
            if (quota == null) {
                quota = new Quota();
            }
            return quota;
        }
    }

    /** @see org.geowebcache.diskquota.QuotaStore#deleteLayer(java.lang.String) */
    @Override
    public void deleteLayer(final String layerName) {
        Assert.notNull(layerName, "LayerName must be non null");
        issue(new Deleter(layerName, ts -> true));
    }

    @Override
    public void deleteGridSubset(String layerName, String gridSetId) {
        issue(new Deleter(layerName, ts -> Objects.equals(ts.getGridsetId(), gridSetId)));
    }

    @Override
    public void deleteParameters(String layerName, String parametersId) {
        issue(new Deleter(layerName, ts -> Objects.equals(ts.getParametersId(), parametersId)));
    }

    private class Deleter implements Callable<Void> {

        private final String layerName;
        Predicate<TileSet> shouldDelete;

        public Deleter(String layerName, Predicate<TileSet> shouldDelete) {
            this.layerName = layerName;
            this.shouldDelete = shouldDelete;
        }

        @Override
        public Void call() throws Exception {
            Transaction transaction = entityStore.getEnvironment().beginTransaction(null, null);
            try {
                call(transaction);
                transaction.commit();
            } catch (RuntimeException e) {
                transaction.abort();
                throw e;
            }
            return null;
        }

        public void call(Transaction transaction) {
            EntityCursor<TileSet> tileSets =
                    tileSetsByLayer.entities(transaction, layerName, true, layerName, true, null);
            TileSet tileSet;
            Quota freed;
            Quota global;
            try {
                while (null != (tileSet = tileSets.next())) {
                    if (shouldDelete.test(tileSet)) {
                        freed = usedQuotaByTileSetId.get(transaction, tileSet.getId(), LockMode.DEFAULT);
                        global = usedQuotaByTileSetId.get(transaction, GLOBAL_QUOTA_NAME, LockMode.DEFAULT);

                        tileSets.delete();
                        global.subtract(freed.getBytes());
                        usedQuotaById.put(transaction, global);
                    }
                }
            } finally {
                tileSets.close();
            }
        }
    }

    /** @see org.geowebcache.diskquota.QuotaStore#renameLayer(java.lang.String, java.lang.String) */
    @Override
    public void renameLayer(String oldLayerName, String newLayerName) throws InterruptedException {
        Assert.notNull(oldLayerName, "Old layer name must be non null");
        Assert.notNull(newLayerName, "New layer name must be non null");
        issueSync(new RenameLayer(oldLayerName, newLayerName));
    }

    private class RenameLayer implements Callable<Void> {

        private final String oldLayerName;

        private final String newLayerName;

        public RenameLayer(final String oldLayerName, final String newLayerName) {
            this.oldLayerName = oldLayerName;
            this.newLayerName = newLayerName;
        }

        /**
         * Copy over old {@link TileSet}s, used {@link Quota}s and {@link TilePage}s from oldLayerName to newLayerName
         * and delete the old ones
         *
         * @see java.util.concurrent.Callable#call()
         */
        @Override
        public Void call() throws Exception {
            Transaction transaction = entityStore.getEnvironment().beginTransaction(null, null);
            try {
                copyTileSets(transaction);
                Deleter deleteCommand = new Deleter(oldLayerName, ts -> true);
                deleteCommand.call(transaction);
                transaction.commit();
            } catch (RuntimeException e) {
                transaction.abort();
                throw e;
            }
            return null;
        }

        private void copyTileSets(Transaction transaction) {
            EntityCursor<TileSet> tileSets =
                    tileSetsByLayer.entities(transaction, oldLayerName, true, oldLayerName, true, null);
            try {
                TileSet oldTileSet;
                TileSet newTileSet;
                Quota oldQuota;
                Quota newQuota;
                TilePage oldPage;
                TilePage newPage;
                while (null != (oldTileSet = tileSets.next())) {
                    final String gridsetId = oldTileSet.getGridsetId();
                    final String blobFormat = oldTileSet.getBlobFormat();
                    final String parametersId = oldTileSet.getParametersId();
                    newTileSet = new TileSet(newLayerName, gridsetId, blobFormat, parametersId);
                    // this creates the tileset's empty used Quota too
                    newTileSet = getOrCreateTileSet(transaction, newTileSet);

                    final String oldTileSetId = oldTileSet.getId();
                    final String newTileSetId = newTileSet.getId();

                    oldQuota = usedQuotaByTileSetId.get(transaction, oldTileSetId, LockMode.DEFAULT);
                    newQuota = usedQuotaByTileSetId.get(transaction, newTileSetId, LockMode.DEFAULT);
                    newQuota.setBytes(oldQuota.getBytes());
                    usedQuotaById.putNoReturn(transaction, newQuota);

                    EntityCursor<TilePage> oldPages = pagesByTileSetId.entities(
                            transaction, oldTileSetId, true, oldTileSetId, true, CursorConfig.DEFAULT);
                    try {
                        while (null != (oldPage = oldPages.next())) {
                            long oldPageId = oldPage.getId();
                            newPage = new TilePage(
                                    newTileSetId, oldPage.getPageX(), oldPage.getPageY(), oldPage.getZoomLevel());
                            pageById.put(transaction, newPage);
                            PageStats pageStats = pageStatsByPageId.get(oldPageId);
                            if (pageStats != null) {
                                pageStats.setPageId(newPage.getId());
                                pageStatsById.putNoReturn(transaction, pageStats);
                            }
                        }
                    } finally {
                        oldPages.close();
                    }
                }
            } finally {
                tileSets.close();
            }
        }
    }

    /** @see org.geowebcache.diskquota.QuotaStore#getUsedQuotaByLayerName(java.lang.String) */
    @Override
    public Quota getUsedQuotaByLayerName(final String layerName) throws InterruptedException {
        return issueSync(new UsedQuotaByLayerName(layerName));
    }

    private final class UsedQuotaByLayerName implements Callable<Quota> {
        private final String layerName;

        public UsedQuotaByLayerName(final String layerName) {
            this.layerName = layerName;
        }

        @Override
        public Quota call() throws Exception {
            Quota aggregated = null;

            EntityCursor<TileSet> layerTileSetsIds =
                    tileSetsByLayer.entities(null, layerName, true, layerName, true, CursorConfig.DEFAULT);
            TileSet tileSet;
            try {
                Quota tileSetUsedQuota;
                while (null != (tileSet = layerTileSetsIds.next())) {
                    if (aggregated == null) {
                        aggregated = new Quota();
                    }
                    tileSetUsedQuota = new UsedQuotaByTileSetId(tileSet.getId()).call();
                    aggregated.add(tileSetUsedQuota);
                }
            } finally {
                layerTileSetsIds.close();
            }
            if (aggregated == null) {
                aggregated = new Quota();
            }

            return aggregated;
        }
    }

    /** @see org.geowebcache.diskquota.QuotaStore#getTilesForPage(org.geowebcache.diskquota.storage.TilePage) */
    @Override
    public long[][] getTilesForPage(TilePage page) throws InterruptedException {
        TileSet tileSet = getTileSetById(page.getTileSetId());
        long[][] gridCoverage = tilePageCalculator.toGridCoverage(tileSet, page);
        return gridCoverage;
    }

    /** @see org.geowebcache.diskquota.QuotaStore#getTileSets() */
    @Override
    public Set<TileSet> getTileSets() {
        Map<String, TileSet> map = new HashMap<>(tileSetById.map());
        map.remove(GLOBAL_QUOTA_NAME);
        HashSet<TileSet> hashSet = new HashSet<>(map.values());
        return hashSet;
    }

    /** @see org.geowebcache.diskquota.QuotaStore#getTileSetById(java.lang.String) */
    @Override
    public TileSet getTileSetById(final String tileSetId) throws InterruptedException {
        return issueSync(() -> {
            TileSet tileSet = tileSetById.get(tileSetId);
            if (tileSet == null) {
                throw new IllegalArgumentException("TileSet does not exist: " + tileSetId);
            }
            return tileSet;
        });
    }

    /** @see org.geowebcache.diskquota.QuotaStore#accept(org.geowebcache.diskquota.storage.TileSetVisitor) */
    @Override
    public void accept(TileSetVisitor visitor) {
        EntityCursor<TileSet> cursor = this.tileSetById.entities();
        try {
            TileSet tileSet;
            while ((tileSet = cursor.next()) != null) {
                visitor.visit(tileSet, this);
            }
        } finally {
            cursor.close();
        }
    }

    /** @see org.geowebcache.diskquota.QuotaStore#getTilePageCalculator() */
    @Override
    public TilePageCalculator getTilePageCalculator() {
        return tilePageCalculator;
    }

    /**
     * @see org.geowebcache.diskquota.QuotaStore#addToQuotaAndTileCounts(org.geowebcache.diskquota.storage.TileSet,
     *     org.geowebcache.diskquota.storage.Quota, java.util.Collection)
     */
    @Override
    public void addToQuotaAndTileCounts(
            final TileSet tileSet, final Quota quotaDiff, final Collection<PageStatsPayload> tileCountDiffs)
            throws InterruptedException {
        issueSync(new AddToQuotaAndTileCounts(tileSet, quotaDiff, tileCountDiffs));
    }

    private class AddToQuotaAndTileCounts implements Callable<Void> {

        private final TileSet tileSet;

        private final Collection<PageStatsPayload> tileCountDiffs;

        private final Quota quotaDiff;

        public AddToQuotaAndTileCounts(
                final TileSet tileSet, Quota quotaDiff, final Collection<PageStatsPayload> tileCountDiffs) {
            this.tileSet = tileSet;
            this.quotaDiff = quotaDiff;
            this.tileCountDiffs = tileCountDiffs;
        }

        @Override
        public Void call() throws Exception {
            final Transaction tx = entityStore.getEnvironment().beginTransaction(null, null);
            try {
                TileSet storedTileset = getOrCreateTileSet(tx, tileSet);
                // increase the tileset used quota
                addToUsedQuota(tx, storedTileset, quotaDiff);

                // and each page's fillFactor for lru/lfu expiration
                if (!tileCountDiffs.isEmpty()) {
                    TilePage page;
                    String pageKey;
                    for (PageStatsPayload payload : tileCountDiffs) {
                        page = payload.getPage();
                        pageKey = page.getKey();
                        PageStats pageStats;

                        TilePage storedPage = pageByKey.get(tx, pageKey, LockMode.DEFAULT);
                        if (null == storedPage) {
                            pageById.put(tx, page);
                            storedPage = page;
                            pageStats = new PageStats(storedPage.getId());
                            // pageStatsById.put(tx, pageStats);
                        } else {
                            pageStats = pageStatsByPageId.get(tx, storedPage.getId(), null);
                        }

                        final byte level = page.getZoomLevel();
                        final BigInteger tilesPerPage = tilePageCalculator.getTilesPerPage(tileSet, level);
                        final int tilesAdded = payload.getNumTiles();

                        pageStats.addTiles(tilesAdded, tilesPerPage);
                        pageStatsById.putNoReturn(tx, pageStats);
                    }
                }
                tx.commit();
                return null;
            } catch (RuntimeException e) {
                tx.abort();
                throw e;
            }
        }

        private void addToUsedQuota(final Transaction tx, final TileSet tileSet, final Quota quotaDiff) {
            Quota usedQuota = usedQuotaByTileSetId.get(tx, tileSet.getId(), LockMode.DEFAULT);
            Quota globalQuota = usedQuotaByTileSetId.get(tx, GLOBAL_QUOTA_NAME, LockMode.DEFAULT);

            usedQuota.add(quotaDiff);
            globalQuota.add(quotaDiff);

            usedQuotaById.putNoReturn(tx, usedQuota);
            usedQuotaById.putNoReturn(tx, globalQuota);
        }
    }

    /** @see org.geowebcache.diskquota.QuotaStore#addHitsAndSetAccesTime(java.util.Collection) */
    @Override
    public Future<List<PageStats>> addHitsAndSetAccesTime(final Collection<PageStatsPayload> statsUpdates) {

        Assert.notNull(statsUpdates, "Stats update must be non null");

        return issue(new AddHitsAndSetAccesTime(statsUpdates));
    }

    /** */
    private class AddHitsAndSetAccesTime implements Callable<List<PageStats>> {

        private final Collection<PageStatsPayload> statsUpdates;

        public AddHitsAndSetAccesTime(Collection<PageStatsPayload> statsUpdates) {
            this.statsUpdates = statsUpdates;
        }

        @Override
        public List<PageStats> call() throws Exception {
            List<PageStats> allStats = new ArrayList<>(statsUpdates.size());
            PageStats pageStats = null;
            final Transaction tx = entityStore.getEnvironment().beginTransaction(null, null);
            try {
                for (PageStatsPayload payload : statsUpdates) {
                    TilePage page = payload.getPage();
                    TileSet storedTileset = tileSetById.get(tx, page.getTileSetId(), LockMode.DEFAULT);
                    if (null == storedTileset) {
                        log.info("Can't add usage stats. TileSet does not exist. Was it deleted? "
                                + page.getTileSetId());
                        continue;
                    }

                    TilePage storedPage = pageByKey.get(tx, page.getKey(), null);

                    if (storedPage == null) {
                        pageById.put(tx, page);
                        storedPage = page;
                        pageStats = new PageStats(storedPage.getId());
                    } else {
                        pageStats = pageStatsByPageId.get(tx, storedPage.getId(), null);
                    }

                    final int addedHits = payload.getNumHits();
                    final int lastAccessTimeMinutes = (int) (payload.getLastAccessTime() / 1000 / 60);
                    final int creationTimeMinutes = storedPage.getCreationTimeMinutes();
                    pageStats.addHitsAndAccessTime(addedHits, lastAccessTimeMinutes, creationTimeMinutes);
                    pageStatsById.putNoReturn(tx, pageStats);
                    allStats.add(pageStats);
                }
                tx.commit();
                return allStats;
            } catch (RuntimeException e) {
                tx.abort();
                throw e;
            }
        }
    }

    /** @see org.geowebcache.diskquota.QuotaStore#getLeastFrequentlyUsedPage(java.util.Set) */
    @Override
    public TilePage getLeastFrequentlyUsedPage(final Set<String> layerNames) throws InterruptedException {

        SecondaryIndex<Float, Long, PageStats> expirationPolicyIndex = pageStatsByLFU;
        TilePage nextToExpire = issueSync(new FindPageToExpireByLayer(expirationPolicyIndex, layerNames));

        return nextToExpire;
    }

    /** @see org.geowebcache.diskquota.QuotaStore#getLeastRecentlyUsedPage(java.util.Set) */
    @Override
    public TilePage getLeastRecentlyUsedPage(final Set<String> layerNames) throws InterruptedException {
        SecondaryIndex<Float, Long, PageStats> expirationPolicyIndex = pageStatsByLRU;
        TilePage nextToExpire = issueSync(new FindPageToExpireByLayer(expirationPolicyIndex, layerNames));

        return nextToExpire;
    }

    private class FindPageToExpireByLayer implements Callable<TilePage> {
        private final SecondaryIndex<Float, Long, PageStats> expirationPolicyIndex;

        private final Set<String> layerNames;

        public FindPageToExpireByLayer(
                SecondaryIndex<Float, Long, PageStats> expirationPolicyIndex, Set<String> layerNames) {
            this.expirationPolicyIndex = expirationPolicyIndex;
            this.layerNames = layerNames;
        }

        @Override
        public TilePage call() throws Exception {

            // find out the tilesets for the requested layers
            final Set<String> tileSetIds = new HashSet<>();
            for (String layerName : layerNames) {
                EntityCursor<TileSet> keys = tileSetsByLayer.entities(layerName, true, layerName, true);
                try {
                    TileSet tileSet;
                    while ((tileSet = keys.next()) != null) {
                        tileSetIds.add(tileSet.getId());
                    }
                } finally {
                    keys.close();
                }
            }

            TilePage nextToExpire = null;
            // find out the LRU page that matches a requested tileset
            final EntityCursor<PageStats> pageStatsCursor = expirationPolicyIndex.entities();

            try {
                String tileSetId;
                long pageId;
                PageStats pageStats;
                while ((pageStats = pageStatsCursor.next()) != null) {
                    if (pageStats.getFillFactor() > 0) {
                        pageId = pageStats.getPageId();
                        TilePage tilePage = pageById.get(pageId);
                        tileSetId = tilePage.getTileSetId();
                        if (tileSetIds.contains(tileSetId)) {
                            nextToExpire = tilePage;
                            break;
                        }
                    }
                }
            } finally {
                pageStatsCursor.close();
            }

            return nextToExpire;
        }
    }

    /** @see org.geowebcache.diskquota.QuotaStore#setTruncated(org.geowebcache.diskquota.storage.TilePage) */
    @Override
    public PageStats setTruncated(final TilePage tilePage) throws InterruptedException {
        return issueSync(new TruncatePage(tilePage));
    }

    private class TruncatePage implements Callable<PageStats> {
        private final TilePage tilePage;

        public TruncatePage(TilePage tilePage) {
            this.tilePage = tilePage;
        }

        @Override
        public PageStats call() throws Exception {
            Transaction tx = entityStore.getEnvironment().beginTransaction(null, null);
            try {
                PageStats pageStats = pageStatsByPageId.get(tx, tilePage.getId(), null);
                if (pageStats != null) {
                    pageStats.setFillFactor(0f);
                    pageStatsById.putNoReturn(tx, pageStats);
                }
                tx.commit();
                return pageStats;
            } catch (Exception e) {
                tx.abort();
                throw e;
            }
        }
    }
}
