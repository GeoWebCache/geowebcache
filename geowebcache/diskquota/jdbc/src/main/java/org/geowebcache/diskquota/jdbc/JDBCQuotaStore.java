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
 * @author Andrea Aime - GeoSolutions Copyright 2012
 */
package org.geowebcache.diskquota.jdbc;

import java.io.Closeable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.apache.commons.dbcp.BasicDataSource;
import org.geotools.util.logging.Logging;
import org.geowebcache.diskquota.QuotaStore;
import org.geowebcache.diskquota.storage.PageStats;
import org.geowebcache.diskquota.storage.PageStatsPayload;
import org.geowebcache.diskquota.storage.Quota;
import org.geowebcache.diskquota.storage.TilePage;
import org.geowebcache.diskquota.storage.TilePageCalculator;
import org.geowebcache.diskquota.storage.TileSet;
import org.geowebcache.diskquota.storage.TileSetVisitor;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.util.SuppressFBWarnings;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * An abstract quota store based on a JDBC reachable database, and configurable via a dialect class
 *
 * @author Andrea Aime - GeoSolutions
 */
public class JDBCQuotaStore implements QuotaStore {

    private static final Logger log = Logging.getLogger(JDBCQuotaStore.class.getName());

    /** The constant identifying the global quota tile set key */
    public static final String GLOBAL_QUOTA_NAME = "___GLOBAL_QUOTA___";

    /** The dialect accounting for database specific differences */
    SQLDialect dialect;

    /** The template used to execute commands */
    SimpleJdbcTemplate jt;

    /** The template used to run transactions */
    TransactionTemplate tt;

    /** The database schema (optional) */
    String schema;

    /** The storage finder, used to locate the data directory */
    DefaultStorageFinder finder;

    /** The tile page calculator, serving as the source or layers, tile sets, tile pages */
    TilePageCalculator calculator;

    /** Max number of attempts we do to insert/update page stats in race-free mode */
    int maxLoops = 100;

    /** The executor used for asynch requests */
    ExecutorService executor;

    private DataSource dataSource;

    public JDBCQuotaStore(DefaultStorageFinder finder, TilePageCalculator tilePageCalculator) {
        this.finder = finder;
        this.calculator = tilePageCalculator;
        this.executor = Executors.newFixedThreadPool(1);
    }

    /** Gets the SQL dialect used by this quota store */
    public SQLDialect getDialect() {
        return dialect;
    }

    /** Returns the SQL dialect used by this quota store */
    public void setDialect(SQLDialect dialect) {
        this.dialect = dialect;
    }

    /** Returns he database schema used by this store */
    public String getSchema() {
        return schema;
    }

    /** Sets the database schema used by this store */
    public void setSchema(String schema) {
        this.schema = schema;
    }

    /** Sets the connection pool provider and initializes the tables in the dbms if missing */
    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
        DataSourceTransactionManager dsTransactionManager = new DataSourceTransactionManager(dataSource);
        this.tt = new TransactionTemplate(dsTransactionManager);
        this.tt.setIsolationLevel(TransactionTemplate.ISOLATION_SERIALIZABLE);
        this.jt = new SimpleJdbcTemplate(dsTransactionManager.getDataSource());
    }

    /** Called to initialize the database structure and the layers */
    public void initialize() {
        if (dialect == null || jt == null || tt == null) {
            throw new IllegalStateException(
                    "Please provide both the sql dialect and the data " + "source before calling inizialize");
        }
        tt.execute(new TransactionCallbackWithoutResult() {

            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                // setup the tables if necessary
                dialect.initializeTables(schema, jt);

                // get the existing table names
                List<String> existingLayers =
                        jt.query(dialect.getAllLayersQuery(schema), (rs, rowNum) -> rs.getString(1));

                // compare with the ones available in the config
                final Set<String> layerNames = calculator.getLayerNames();
                final Set<String> layersToDelete = new HashSet<>(existingLayers);
                layersToDelete.removeAll(layerNames);

                // remove all the layers we don't need
                for (String layerName : layersToDelete) {
                    deleteLayer(layerName);
                }

                // add any missing tileset
                for (String layerName : layerNames) {
                    createLayerInternal(layerName);
                }

                // create the global quota if necessary
                Quota global = getUsedQuotaByTileSetIdInternal(GLOBAL_QUOTA_NAME);
                if (global == null) {
                    createLayerInternal(GLOBAL_QUOTA_NAME);
                }
            }
        });
    }

    @Override
    public void createLayer(String layerName) throws InterruptedException {
        createLayerInternal(layerName);
    }

    private void createLayerInternal(final String layerName) {
        tt.execute(new TransactionCallbackWithoutResult() {

            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                Set<TileSet> layerTileSets;
                if (!GLOBAL_QUOTA_NAME.equals(layerName)) {
                    layerTileSets = calculator.getTileSetsFor(layerName);
                } else {
                    layerTileSets = Collections.singleton(new TileSet(GLOBAL_QUOTA_NAME));
                }
                for (TileSet tset : layerTileSets) {
                    // other nodes in the cluster might be trying to create the same layer,
                    // so use getOrCreate
                    getOrCreateTileSet(tset);
                }
            }
        });
    }

    @Override
    public Quota getGloballyUsedQuota() throws InterruptedException {
        return nonNullQuota(getUsedQuotaByTileSetIdInternal(GLOBAL_QUOTA_NAME));
    }

    @Override
    public Quota getUsedQuotaByTileSetId(String tileSetId) {
        return nonNullQuota(getUsedQuotaByTileSetIdInternal(tileSetId));
    }

    @Override
    public Quota getUsedQuotaByLayerName(String layerName) {
        String sql = dialect.getUsedQuotaByLayerName(schema, "layerName");
        return nonNullQuota(jt.queryForOptionalObject(
                sql, new DiskQuotaMapper(), Collections.singletonMap("layerName", layerName)));
    }

    public Quota getUsedQuotaByGridsetid(String gridsetId) {
        String sql = dialect.getUsedQuotaByGridSetId(schema, "gridSetId");
        return nonNullQuota(jt.queryForOptionalObject(
                sql, new DiskQuotaMapper(), Collections.singletonMap("gridSetId", gridsetId)));
    }

    /** Utility method that retrieves the disk quota used by a layer gridset. */
    public Quota getUsedQuotaByLayerGridset(String layerName, String gridsetId) {
        // getting the sql query for the current database
        String sql = dialect.getUsedQuotaByLayerGridset(schema, "layerName", "gridSetId");
        // setup the parameters for the sql query
        Map<String, String> parameters = new HashMap<>();
        parameters.put("layerName", layerName);
        parameters.put("gridSetId", gridsetId);
        // execute the sql query
        return nonNullQuota(jt.queryForOptionalObject(sql, new DiskQuotaMapper(), parameters));
    }

    public Quota getUsedQuotaByParametersId(String parametersId) {
        String sql = dialect.getUsedQuotaByParametersId(schema, "parametersId");
        return nonNullQuota(jt.queryForOptionalObject(
                sql, new DiskQuotaMapper(), Collections.singletonMap("parametersId", parametersId)));
    }

    protected Quota getUsedQuotaByTileSetIdInternal(final String tileSetId) {
        String sql = dialect.getUsedQuotaByTileSetId(schema, "key");
        return jt.queryForOptionalObject(
                sql,
                (rs, rowNum) -> {
                    BigDecimal bytes = rs.getBigDecimal(1);
                    Quota quota = new Quota(bytes.toBigInteger());
                    quota.setTileSetId(tileSetId);
                    return quota;
                },
                Collections.singletonMap("key", tileSetId));
    }

    /** Return a empty quota object in case a null value is passed, otherwise return the passed value */
    private Quota nonNullQuota(Quota optionalQuota) {
        if (optionalQuota == null) {
            return new Quota();
        } else {
            return optionalQuota;
        }
    }

    @Override
    public void deleteLayer(final String layerName) {
        tt.execute(new TransactionCallbackWithoutResult() {

            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                deleteLayerInternal(layerName);
            }
        });
    }

    @Override
    public void deleteGridSubset(final String layerName, final String gridSetId) {
        tt.execute(new TransactionCallbackWithoutResult() {

            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                // get the disk quota used by the layer gridset
                Quota quota = getUsedQuotaByLayerGridset(layerName, gridSetId);
                // we will subtracting the current disk quota value
                quota.setBytes(quota.getBytes().negate());
                // update the global disk quota by subtracting the value above
                String updateQuota = dialect.getUpdateQuotaStatement(schema, "tileSetId", "bytes");
                Map<String, Object> params = new HashMap<>();
                params.put("tileSetId", GLOBAL_QUOTA_NAME);
                params.put("bytes", new BigDecimal(quota.getBytes()));
                jt.update(updateQuota, params);
                // delete layer gridset
                String statement = dialect.getLayerGridDeletionStatement(schema, "layerName", "gridSetId");
                params = new HashMap<>();
                params.put("layerName", layerName);
                params.put("gridSetId", gridSetId);
                jt.update(statement, params);
            }
        });
    }

    public void deleteLayerInternal(final String layerName) {
        getUsedQuotaByLayerName(layerName);
        tt.execute(new TransactionCallbackWithoutResult() {

            @Override
            protected void doInTransactionWithoutResult(TransactionStatus arg0) {
                // update the global quota
                Quota quota = getUsedQuotaByLayerName(layerName);
                quota.setBytes(quota.getBytes().negate());
                String updateQuota = dialect.getUpdateQuotaStatement(schema, "tileSetId", "bytes");
                Map<String, Object> params = new HashMap<>();
                params.put("tileSetId", GLOBAL_QUOTA_NAME);
                params.put("bytes", new BigDecimal(quota.getBytes()));
                jt.update(updateQuota, params);

                // delete the layer
                log.info("Deleting disk quota information for layer '" + layerName + "'");
                String statement = dialect.getLayerDeletionStatement(schema, "layerName");
                jt.update(statement, Collections.singletonMap("layerName", layerName));
            }
        });
    }

    @Override
    public void renameLayer(final String oldLayerName, final String newLayerName) throws InterruptedException {
        tt.execute(new TransactionCallbackWithoutResult() {

            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                String sql = dialect.getRenameLayerStatement(schema, "oldName", "newName");
                Map<String, Object> params = new HashMap<>();
                params.put("oldName", oldLayerName);
                params.put("newName", newLayerName);
                int updated = jt.update(sql, params);
                log.info("Updated " + updated + " tile sets after layer rename");
            }
        });
    }

    @Override
    public Set<TileSet> getTileSets() {
        String getTileSet = dialect.getTileSetsQuery(schema);
        List<TileSet> tilesets = jt.query(getTileSet, new TileSetRowMapper());

        // collect in a set and remove the global quota one
        Set<TileSet> result = new HashSet<>();
        for (TileSet ts : tilesets) {
            if (!GLOBAL_QUOTA_NAME.equals(ts.getId())) {
                result.add(ts);
            }
        }
        return result;
    }

    @Override
    public void accept(final TileSetVisitor visitor) {
        String getTileSet = dialect.getTileSetsQuery(schema);
        final TileSetRowMapper tileSetMapper = new TileSetRowMapper();
        jt.query(getTileSet, rs -> {
            TileSet tileSet = tileSetMapper.mapRow(rs, 0);
            if (tileSet != null && !GLOBAL_QUOTA_NAME.equals(tileSet.getId())) {
                visitor.visit(tileSet, this);
            }
        });
    }

    @Override
    public TileSet getTileSetById(String tileSetId) throws InterruptedException {
        // locate the tileset
        TileSet result = getTileSetByIdInternal(tileSetId);

        // make it compatible with BDB quota store behavior
        if (result == null) {
            throw new IllegalArgumentException("Could not find a tile set with id: " + tileSetId);
        }
        return result;
    }

    private TileSet getTileSetByIdInternal(String tileSetId) {
        String getTileSet = dialect.getTileSetQuery(schema, "key");
        final TileSetRowMapper tileSetMapper = new TileSetRowMapper();
        return jt.queryForOptionalObject(getTileSet, tileSetMapper, Collections.singletonMap("key", tileSetId));
    }

    private boolean createTileSet(TileSet tset) {

        if (log.isLoggable(Level.FINE)) {
            log.fine("Creating tileset " + tset);
        }

        String createTileSet =
                dialect.getCreateTileSetQuery(schema, "key", "layerName", "gridSetId", "blobFormat", "parametersId");
        Map<String, Object> params = new HashMap<>();
        params.put("key", tset.getId());
        params.put("layerName", tset.getLayerName());
        params.put("gridSetId", tset.getGridsetId());
        params.put("blobFormat", tset.getBlobFormat());
        params.put("parametersId", tset.getParametersId());

        // run the insert, if that creates a record then also create the quota
        return jt.update(createTileSet, params) > 0;
    }

    protected TileSet getOrCreateTileSet(TileSet tileSet) {
        Exception lastException = null;
        for (int i = 0; i < maxLoops; i++) {
            TileSet tset = getTileSetByIdInternal(tileSet.getId());
            if (tset != null) {
                return tset;
            }

            try {
                if (createTileSet(tileSet)) {
                    return tileSet;
                }
            } catch (DataAccessException e) {
                // fine, it might be another node created this table
                lastException = e;
            }
        }

        throw new ConcurrencyFailureException(
                "Failed to create or locate tileset " + tileSet + " after " + maxLoops + " attempts", lastException);
    }

    @Override
    public TilePageCalculator getTilePageCalculator() {
        return calculator;
    }

    @Override
    public void addToQuotaAndTileCounts(
            final TileSet tileSet, final Quota quotaDiff, final Collection<PageStatsPayload> tileCountDiffs)
            throws InterruptedException {
        tt.execute(new TransactionCallbackWithoutResult() {

            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                getOrCreateTileSet(tileSet);
                updateQuotas(tileSet, quotaDiff);

                if (tileCountDiffs != null) {
                    // sort the payloads by page id as a deadlock avoidance measure, out
                    // of order updates may result in deadlock with the
                    // addHitsAndSetAccessTime method
                    List<PageStatsPayload> sorted = sortPayloads(tileCountDiffs);
                    for (PageStatsPayload payload : sorted) {
                        upsertTilePageFillFactor(payload);
                    }
                }
            }

            private void updateQuotas(final TileSet tileSet, final Quota quotaDiff) {
                if (log.isLoggable(Level.FINE)) {
                    log.info("Applying quota diff " + quotaDiff.getBytes() + " on tileset " + tileSet);
                }

                String updateQuota = dialect.getUpdateQuotaStatement(schema, "tileSetId", "bytes");
                Map<String, Object> params = new HashMap<>();
                params.put("tileSetId", tileSet.getId());
                params.put("bytes", new BigDecimal(quotaDiff.getBytes()));
                jt.update(updateQuota, params);
                params.put("tileSetId", GLOBAL_QUOTA_NAME);
                jt.update(updateQuota, params);
            }

            private void upsertTilePageFillFactor(PageStatsPayload payload) {
                if (log.isLoggable(Level.FINE)) {
                    log.info("Applying page stats payload " + payload);
                }

                // see http://en.wikipedia.org/wiki/Merge_(SQL)
                // Even the Merge command that some databases support is prone to race
                // conditions
                // under concurrent load, but we don't want to lose data and it's difficult
                // to
                // tell apart the race conditions from other failures, so we use tolerant
                // commands
                // and loop over them.
                // Loop conditions: we find the page stats, but they are deleted before we
                // can
                // update
                // them, we don't find the page stats, but they are inserted before we can
                // do so, in
                // both cases we re-start from zero
                TilePage page = payload.getPage();
                final byte level = page.getZoomLevel();
                final BigInteger tilesPerPage = calculator.getTilesPerPage(tileSet, level);

                int modified = 0;
                int count = 0;
                while (modified == 0 && count < maxLoops) {
                    try {
                        count++;
                        PageStats stats = getPageStats(page.getKey());
                        if (stats != null) {
                            float oldFillFactor = stats.getFillFactor();
                            stats.addTiles(payload.getNumTiles(), tilesPerPage);
                            // if no change, bail out early
                            if (oldFillFactor == stats.getFillFactor()) {
                                return;
                            }

                            // update the record in the db
                            modified = updatePageFillFactor(page, stats, oldFillFactor);
                        } else {
                            // create the stats and update the fill factor
                            stats = new PageStats(0);
                            stats.addTiles(payload.getNumTiles(), tilesPerPage);

                            modified = createNewPageStats(stats, page);
                        }
                    } catch (PessimisticLockingFailureException e) {
                        if (log.isLoggable(Level.FINE)) {
                            log.log(Level.FINE, "Deadlock while updating page stats, will retry", e);
                        }
                    }
                }

                if (modified == 0) {
                    throw new ConcurrencyFailureException("Failed to create or update page stats for page "
                            + payload.getPage()
                            + " after "
                            + count
                            + " attempts");
                }
            }
        });
    }

    /** Sorts the payloads by page key */
    protected List<PageStatsPayload> sortPayloads(Collection<PageStatsPayload> tileCountDiffs) {
        List<PageStatsPayload> result = new ArrayList<>(tileCountDiffs);
        Collections.sort(result, (pl1, pl2) -> {
            TilePage p1 = pl1.getPage();
            TilePage p2 = pl2.getPage();
            return p1.getKey().compareTo(p2.getKey());
        });
        return result;
    }

    private int updatePageFillFactor(TilePage page, PageStats stats, float oldFillFactor) {
        if (log.isLoggable(Level.FINE)) {
            log.info("Updating page " + page + " fill factor from  " + oldFillFactor + " to " + stats.getFillFactor());
        }

        String update = dialect.conditionalUpdatePageStatsFillFactor(schema, "key", "fillFactor", "oldFillFactor");
        Map<String, Object> params = new HashMap<>();
        params.put("key", page.getKey());
        params.put("fillFactor", stats.getFillFactor());
        params.put("oldFillFactor", oldFillFactor);
        return jt.update(update, params);
    }

    private int setPageFillFactor(TilePage page, PageStats stats) {
        if (log.isLoggable(Level.FINE)) {
            log.info("Setting page " + page + " fill factor to " + stats.getFillFactor());
        }

        String update = dialect.updatePageStatsFillFactor(schema, "key", "fillFactor");
        Map<String, Object> params = new HashMap<>();
        params.put("key", page.getKey());
        params.put("fillFactor", stats.getFillFactor());
        return jt.update(update, params);
    }

    private int createNewPageStats(PageStats stats, TilePage page) {
        if (log.isLoggable(Level.FINE)) {
            log.info("Creating new page stats: " + stats);
        }

        // for the moment we don't have the page in the db, we have to create it
        String insert = dialect.contionalTilePageInsertStatement(
                schema,
                "key",
                "tileSetId",
                "pageZ",
                "pageX",
                "pageY",
                "creationTime",
                "frequencyOfUse",
                "lastAccessTime",
                "fillFactor",
                "numHits");
        Map<String, Object> params = new HashMap<>();
        params.put("key", page.getKey());
        params.put("tileSetId", page.getTileSetId());
        params.put("pageZ", page.getZoomLevel());
        params.put("pageX", page.getPageX());
        params.put("pageY", page.getPageY());
        params.put("creationTime", page.getCreationTimeMinutes());
        params.put("frequencyOfUse", stats.getFrequencyOfUsePerMinute());
        params.put("lastAccessTime", stats.getLastAccessTimeMinutes());
        params.put("fillFactor", stats.getFillFactor());
        params.put("numHits", new BigDecimal(stats.getNumHits()));

        // try the insert, mind, someone else might have done it as well, in such
        // case the insert will fail and return 0 record modified
        return jt.update(insert, params);
    }

    private PageStats getPageStats(String pageStatsKey) {
        String getPageStats = dialect.getPageStats(schema, "key");
        return jt.queryForOptionalObject(
                getPageStats,
                (rs, rowNum) -> {
                    PageStats ps = new PageStats(0);
                    // FREQUENCY_OF_USE, LAST_ACCESS_TIME, FILL_FACTOR, NUM_HITS FROM
                    ps.setFrequencyOfUsePerMinute(rs.getFloat(1));
                    ps.setLastAccessMinutes(rs.getInt(2));
                    ps.setFillFactor(rs.getFloat(3));
                    ps.setNumHits(rs.getBigDecimal(4).toBigInteger());

                    return ps;
                },
                Collections.singletonMap("key", pageStatsKey));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Future<List<PageStats>> addHitsAndSetAccesTime(final Collection<PageStatsPayload> statsUpdates) {
        return executor.submit(() -> (List<PageStats>) tt.execute(new QuotaStoreCallback(statsUpdates)));
    }

    @Override
    public long[][] getTilesForPage(TilePage page) throws InterruptedException {
        TileSet tileSet = getTileSetById(page.getTileSetId());
        long[][] gridCoverage = calculator.toGridCoverage(tileSet, page);
        return gridCoverage;
    }

    @Override
    public TilePage getLeastFrequentlyUsedPage(Set<String> layerNames) throws InterruptedException {
        return getSinglePage(layerNames, true);
    }

    @Override
    public TilePage getLeastRecentlyUsedPage(Set<String> layerNames) throws InterruptedException {
        return getSinglePage(layerNames, false);
    }

    private TilePage getSinglePage(Set<String> layerNames, boolean leastFrequentlyUsed) {
        Map<String, Object> params = new HashMap<>();
        List<String> layerParamNames = new ArrayList<>();
        int i = 0;
        for (String layer : layerNames) {
            i++;
            String param = "Layer" + i;
            params.put(param, layer);
            layerParamNames.add(param);
        }
        String select;
        if (leastFrequentlyUsed) {
            select = dialect.getLeastFrequentlyUsedPage(schema, layerParamNames);
        } else {
            select = dialect.getLeastRecentlyUsedPage(schema, layerParamNames);
        }
        TilePageRowMapper mapper = new TilePageRowMapper();
        return jt.queryForOptionalObject(select, mapper, params);
    }

    @Override
    public PageStats setTruncated(final TilePage page) throws InterruptedException {
        return (PageStats) tt.execute((TransactionCallback<Object>) status -> {
            if (log.isLoggable(Level.FINE)) {
                log.info("Truncating page " + page);
            }

            PageStats stats = getPageStats(page.getKey());
            if (stats != null) {
                stats.setFillFactor(0);

                // update the record in the db
                int modified = setPageFillFactor(page, stats);
                // if no record updated the page has been deleted by another
                // instance
                if (modified == 0) {
                    return null;
                }
            }

            return stats;
        });
    }

    @Override
    @SuppressWarnings("PMD.CloseResource") // we're closing dataSource, not sure why PMD complains
    public void close() throws Exception {
        log.info("Closing up the JDBC quota store ");

        // try to close the data source if possible
        if (dataSource instanceof BasicDataSource source) {
            source.close();
        } else if (dataSource instanceof Closeable closeable) {
            closeable.close();
        }

        // release the templates
        tt = null;
        jt = null;
    }

    /**
     * Maps a BigDecimal column into a Quota object
     *
     * @author Andrea Aime - GeoSolutions
     */
    static class DiskQuotaMapper implements RowMapper<Quota> {
        @Override
        public Quota mapRow(ResultSet rs, int rowNum) throws SQLException {
            BigDecimal bytes = rs.getBigDecimal(1);
            if (bytes == null) {
                bytes = BigDecimal.ZERO;
            }
            return new Quota(bytes.toBigInteger());
        }
    }

    /**
     * Maps a result set into {@link TileSet} objects
     *
     * @author Andrea Aime - GeoSolutions
     */
    static class TileSetRowMapper implements RowMapper<TileSet> {

        @Override
        public TileSet mapRow(ResultSet rs, int rowNum) throws SQLException {
            String key = rs.getString(1);
            String layerName = rs.getString(2);
            String gridSetId = rs.getString(3);
            String blobFormat = rs.getString(4);
            String parametersId = rs.getString(5);
            if (GLOBAL_QUOTA_NAME.equals(key)) {
                return new TileSet(key);
            } else {
                return new TileSet(layerName, gridSetId, blobFormat, parametersId);
            }
        }
    }

    /**
     * Maps a result set into {@link TilePage} objects
     *
     * @author Andrea Aime - GeoSolutions
     */
    static class TilePageRowMapper implements RowMapper<TilePage> {

        @Override
        public TilePage mapRow(ResultSet rs, int rowNum) throws SQLException {
            String tileSetId = rs.getString(1);
            int pageX = rs.getInt(2);
            int pageY = rs.getInt(3);
            int pageZ = rs.getInt(4);
            int creationTimeMinutes = rs.getInt(5);

            return new TilePage(tileSetId, pageX, pageY, pageZ, creationTimeMinutes);
        }
    }

    @Override
    public void deleteParameters(final String layerName, final String parametersId) {
        tt.execute(new TransactionCallbackWithoutResult() {

            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                // first gather the disk quota used by the gridset, and update the global
                // quota
                Quota quota = getUsedQuotaByParametersId(parametersId);
                quota.setBytes(quota.getBytes().negate());
                String updateQuota = dialect.getUpdateQuotaStatement(schema, "tileSetId", "bytes");
                Map<String, Object> params = new HashMap<>();
                params.put("tileSetId", GLOBAL_QUOTA_NAME);
                params.put("bytes", new BigDecimal(quota.getBytes()));
                jt.update(updateQuota, params);

                // then delete all the gridsets with the specified id
                String statement = dialect.getLayerParametersDeletionStatement(schema, "layerName", "parametersId");
                params = new HashMap<>();
                params.put("layerName", layerName);
                params.put("parametersId", parametersId);
                jt.update(statement, params);
            }
        });
    }

    private class QuotaStoreCallback implements TransactionCallback<Object> {

        private final Collection<PageStatsPayload> statsUpdates;

        public QuotaStoreCallback(Collection<PageStatsPayload> statsUpdates) {
            this.statsUpdates = statsUpdates;
        }

        @Override
        public Object doInTransaction(TransactionStatus status) {
            List<PageStats> result = new ArrayList<>();
            if (statsUpdates != null) {
                // sort the payloads by page id as a deadlock
                // avoidance measure, out
                // of order updates may result in deadlock with
                // the addHitsAndSetAccessTime method
                List<PageStatsPayload> sorted = sortPayloads(statsUpdates);
                for (PageStatsPayload payload : sorted) {
                    // verify the stats are referring to an
                    // existing tile set id
                    TileSet tset = payload.getTileSet();
                    if (tset == null) {
                        String tileSetId = payload.getPage().getTileSetId();
                        tset = getTileSetByIdInternal(tileSetId);
                        if (tset == null) {
                            log.warning("Could not locate tileset with id "
                                    + tileSetId
                                    + ", skipping page stats update: "
                                    + payload);
                        }
                    } else {
                        getOrCreateTileSet(tset);
                    }

                    // update the stats
                    PageStats stats = upsertTilePageHitAccessTime(payload);
                    result.add(stats);
                }
            }

            return result;
        }

        private PageStats upsertTilePageHitAccessTime(PageStatsPayload payload) {
            TilePage page = payload.getPage();

            if (log.isLoggable(Level.FINE)) {
                log.info("Updating page " + page + " with payload " + payload);
            }

            int modified = 0;
            int count = 0;
            PageStats stats = null;
            while (modified == 0 && count < maxLoops) {
                try {
                    count++;
                    stats = getPageStats(page.getKey());
                    if (stats != null) {
                        // gather the old values, we'll use them
                        // for the optimistic locking
                        final BigInteger oldHits = stats.getNumHits();
                        final float oldFrequency = stats.getFrequencyOfUsePerMinute();
                        final int oldAccessTime = stats.getLastAccessTimeMinutes();
                        // update the page so that it computes
                        // the new stats
                        updatePageStats(payload, page, stats);

                        // update the record in the db
                        String update = dialect.updatePageStats(
                                schema,
                                "key",
                                "newHits",
                                "oldHits",
                                "newFrequency",
                                "oldFrequency",
                                "newAccessTime",
                                "oldAccessTime");
                        Map<String, Object> params = new HashMap<>();
                        params.put("key", page.getKey());
                        params.put("newHits", new BigDecimal(stats.getNumHits()));
                        params.put("oldHits", new BigDecimal(oldHits));
                        params.put("newFrequency", stats.getFrequencyOfUsePerMinute());
                        params.put("oldFrequency", oldFrequency);
                        params.put("newAccessTime", stats.getLastAccessTimeMinutes());
                        params.put("oldAccessTime", oldAccessTime);
                        modified = jt.update(update, params);
                    } else {
                        // create the new stats and insert it
                        stats = new PageStats(0);
                        updatePageStats(payload, page, stats);
                        modified = createNewPageStats(stats, page);
                    }
                } catch (PessimisticLockingFailureException e) {
                    if (log.isLoggable(Level.FINE)) {
                        log.log(Level.FINE, "Deadlock while updating page stats, will retry", e);
                    }
                }
            }

            if (modified == 0) {
                throw new ConcurrencyFailureException("Failed to create or update page stats for page "
                        + payload.getPage()
                        + " after "
                        + count
                        + " attempts");
            }

            return stats;
        }

        private void updatePageStats(PageStatsPayload payload, TilePage page, PageStats stats) {
            final int addedHits = payload.getNumHits();
            final int lastAccessTimeMinutes = (int) (payload.getLastAccessTime() / 1000 / 60);
            final int creationTimeMinutes = page.getCreationTimeMinutes();
            stats.addHitsAndAccessTime(addedHits, lastAccessTimeMinutes, creationTimeMinutes);
        }
    }
}
