package org.geowebcache.diskquota.jdbc;

import static org.easymock.EasyMock.newCapture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.io.FileUtils;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.geowebcache.MockWepAppContextRule;
import org.geowebcache.config.BaseConfiguration;
import org.geowebcache.config.DefaultGridsets;
import org.geowebcache.config.GridSetConfiguration;
import org.geowebcache.config.MockConfigurationResourceProvider;
import org.geowebcache.config.TileLayerConfiguration;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.config.XMLConfigurationBackwardsCompatibilityTest;
import org.geowebcache.diskquota.DiskQuotaMonitor;
import org.geowebcache.diskquota.storage.PageStats;
import org.geowebcache.diskquota.storage.PageStatsPayload;
import org.geowebcache.diskquota.storage.Quota;
import org.geowebcache.diskquota.storage.StorageUnit;
import org.geowebcache.diskquota.storage.SystemUtils;
import org.geowebcache.diskquota.storage.TilePage;
import org.geowebcache.diskquota.storage.TilePageCalculator;
import org.geowebcache.diskquota.storage.TileSet;
import org.geowebcache.filter.parameters.ParametersUtils;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.storage.StorageBroker;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public abstract class JDBCQuotaStoreTest {

    protected class JDBCFixtureRule extends OnlineTestRule {
        protected JDBCFixtureRule(String fixtureId) {
            super(fixtureId);
        }

        @Override
        protected void disconnect() throws Exception {
            store.close();
        }

        @Override
        protected boolean isOnline() throws Exception {
            return true;
        }

        @Override
        protected void setUpInternal() throws Exception {
            // prepare a mock target directory for tiles
            targetDir = new File("target", "mockStore");
            FileUtils.deleteDirectory(targetDir);
            targetDir.mkdirs();

            cacheDirFinder = EasyMock.createMock(DefaultStorageFinder.class);
            EasyMock.expect(cacheDirFinder.getDefaultPath())
                    .andReturn(targetDir.getAbsolutePath())
                    .anyTimes();
            EasyMock.expect(cacheDirFinder.findEnvVar(EasyMock.eq(DiskQuotaMonitor.GWC_DISKQUOTA_DISABLED)))
                    .andReturn(null)
                    .anyTimes();
            EasyMock.replay(cacheDirFinder);

            XMLConfiguration xmlConfig = loadXMLConfig();
            extensions.addBean(
                    "xmlConfig",
                    xmlConfig,
                    BaseConfiguration.class,
                    TileLayerConfiguration.class,
                    GridSetConfiguration.class);

            // add extra tests gwc configuration
            XMLConfiguration extraConfig = new XMLConfiguration(
                    extensions.getContextProvider(),
                    new MockConfigurationResourceProvider(
                            () -> this.getClass().getClassLoader().getResourceAsStream("gwc-test-config.xml")));
            extensions.addBean(
                    "extraConfig",
                    extraConfig,
                    BaseConfiguration.class,
                    TileLayerConfiguration.class,
                    GridSetConfiguration.class);

            extensions.addBean(
                    "defaultGridsets",
                    new DefaultGridsets(true, true),
                    GridSetConfiguration.class,
                    DefaultGridsets.class);

            GridSetBroker broker = new GridSetBroker();
            broker.setApplicationContext(extensions.getMockContext());
            extensions.addBean("gridSetBroker", broker, GridSetBroker.class);

            xmlConfig.setGridSetBroker(broker);
            extraConfig.setGridSetBroker(broker);

            layerDispatcher = new TileLayerDispatcher(broker, null);

            layerDispatcher.setApplicationContext(extensions.getMockContext());

            broker.afterPropertiesSet();
            xmlConfig.afterPropertiesSet();
            extraConfig.afterPropertiesSet();
            layerDispatcher.afterPropertiesSet();

            Capture<String> layerNameCap = newCapture();
            storageBroker = EasyMock.createMock(StorageBroker.class);
            EasyMock.expect(storageBroker.getCachedParameterIds(EasyMock.capture(layerNameCap)))
                    .andStubAnswer(
                            () -> parameterIdsMap.getOrDefault(layerNameCap.getValue(), Collections.singleton(null)));
            EasyMock.replay(storageBroker);
            parametersMap = new HashMap<>();
            parametersMap.put(
                    "topp:states",
                    Stream.of("STYLE=&SOMEPARAMETER=", "STYLE=population&SOMEPARAMETER=2.0")
                            .map(ParametersUtils::getMap)
                            .collect(Collectors.toSet()));
            parameterIdsMap = parametersMap.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream()
                            .map(ParametersUtils::getKvp)
                            .collect(Collectors.toSet())));

            tilePageCalculator = new TilePageCalculator(layerDispatcher, storageBroker);

            // prepare a connection pool for tests against a H2 database
            dataSource = getDataSource();
            SQLDialect dialect = getDialect();

            // setup the quota store
            store = new JDBCQuotaStore(cacheDirFinder, tilePageCalculator);
            store.setDataSource(dataSource);
            store.setDialect(dialect);

            // finally initialize the store
            store.initialize();

            testTileSet =
                    tilePageCalculator.getTileSetsFor("topp:states2").iterator().next();

            paramIds = parameterIdsMap.get("topp:states").toArray(new String[2]);

            expectedTileSets = Arrays.asList(
                    new TileSet("topp:states", "EPSG:900913", "image/png", paramIds[0]),
                    new TileSet("topp:states", "EPSG:900913", "image/jpeg", paramIds[0]),
                    new TileSet("topp:states", "EPSG:900913", "image/gif", paramIds[0]),
                    new TileSet("topp:states", "EPSG:900913", "application/vnd.google-earth.kml+xml", paramIds[0]),
                    new TileSet("topp:states", "EPSG:4326", "image/png", paramIds[0]),
                    new TileSet("topp:states", "EPSG:4326", "image/jpeg", paramIds[0]),
                    new TileSet("topp:states", "EPSG:4326", "image/gif", paramIds[0]),
                    new TileSet("topp:states", "EPSG:4326", "application/vnd.google-earth.kml+xml", paramIds[0]),
                    new TileSet("topp:states", "EPSG:900913", "image/png", paramIds[1]),
                    new TileSet("topp:states", "EPSG:900913", "image/jpeg", paramIds[1]),
                    new TileSet("topp:states", "EPSG:900913", "image/gif", paramIds[1]),
                    new TileSet("topp:states", "EPSG:900913", "application/vnd.google-earth.kml+xml", paramIds[1]),
                    new TileSet("topp:states", "EPSG:4326", "image/png", paramIds[1]),
                    new TileSet("topp:states", "EPSG:4326", "image/jpeg", paramIds[1]),
                    new TileSet("topp:states", "EPSG:4326", "image/gif", paramIds[1]),
                    new TileSet("topp:states", "EPSG:4326", "application/vnd.google-earth.kml+xml", paramIds[1]),
                    new TileSet("topp:states2", "EPSG:2163", "image/png", null),
                    new TileSet("topp:states2", "EPSG:2163", "image/jpeg", null),
                    new TileSet("topp:states3", "EPSG:4326", "image/png", null),
                    new TileSet("topp:states3", "EPSG:2163", "image/png", null));
        }

        @Override
        protected void tearDownInternal() throws Exception {
            store.close();
        }
    }

    JDBCQuotaStore store;

    File targetDir;

    DefaultStorageFinder cacheDirFinder;

    TileLayerDispatcher layerDispatcher;

    TilePageCalculator tilePageCalculator;

    private BasicDataSource dataSource;

    private TileSet testTileSet;

    private StorageBroker storageBroker;

    public MockWepAppContextRule extensions = new MockWepAppContextRule();

    public OnlineTestRule fixtureRule = makeFixtureRule();

    protected JDBCFixtureRule makeFixtureRule() {
        return new JDBCFixtureRule(getFixtureId());
    }

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(extensions).around(fixtureRule);

    protected abstract SQLDialect getDialect();

    protected abstract String getFixtureId();

    protected BasicDataSource getDataSource() throws IOException, SQLException {
        BasicDataSource dataSource = new BasicDataSource();

        dataSource.setDriverClassName(fixtureRule.getFixture().getProperty("driver"));
        dataSource.setUrl(fixtureRule.getFixture().getProperty("url"));
        dataSource.setUsername(fixtureRule.getFixture().getProperty("username"));
        dataSource.setPassword(fixtureRule.getFixture().getProperty("password"));
        dataSource.setPoolPreparedStatements(true);
        dataSource.setAccessToUnderlyingConnectionAllowed(true);
        dataSource.setMinIdle(1);
        dataSource.setMaxActive(4);
        // if we cannot get a connection within 5 seconds give up
        dataSource.setMaxWait(5000);

        cleanupDatabase(dataSource);

        return dataSource;
    }

    protected void cleanupDatabase(DataSource dataSource) throws SQLException {
        // cleanup
        try (Connection cx = dataSource.getConnection();
                Statement st = cx.createStatement()) {
            try {
                st.execute("DROP TABLE TILEPAGE CASCADE");
            } catch (Exception e) {
                // fine
            }
            try {
                st.execute("DROP TABLE TILESET CASCADE");
            } catch (Exception e) {
                // fine too
            }
        }
    }

    Map<String, Set<String>> parameterIdsMap;
    Map<String, Set<Map<String, String>>> parametersMap;

    private Collection<TileSet> expectedTileSets;

    private String[] paramIds;

    private XMLConfiguration loadXMLConfig() throws Exception {
        return new XMLConfiguration(
                null,
                new MockConfigurationResourceProvider(() -> XMLConfiguration.class.getResourceAsStream(
                        XMLConfigurationBackwardsCompatibilityTest.LATEST_FILENAME)));
    }

    @Test
    public void testTableSetup() throws Exception {
        // on initialization we should have the tilesets setup properly

        // check the global quota
        Quota global = store.getGloballyUsedQuota();
        assertNotNull(global);
        assertEquals(JDBCQuotaStore.GLOBAL_QUOTA_NAME, global.getTileSetId());
        assertEquals(0, global.getBytes().longValue());

        Set<TileSet> tileSets = store.getTileSets();

        assertNotNull(tileSets);
        assertEquals(expectedTileSets.size(), tileSets.size());

        for (TileSet tileSet : expectedTileSets) {
            assertTrue(tileSets.contains(tileSet));
            assertQuotaZero(tileSet);
        }

        // check the layer wide quotas
        assertQuotaZero("topp:states");
        assertQuotaZero("topp:states2");
        assertQuotaZero("topp:states3");

        // remove one layer from the dispatcher
        layerDispatcher.removeLayer("topp:states");
        // and make sure at the next startup the store catches up (note this behaviour is just a
        // startup consistency check in case the store got out of sync for some reason. On normal
        // situations the store should have been notified through store.deleteLayer(layerName) if
        // the layer was removed programmatically through StorageBroker.deleteLayer
        store.close();
        store.setDataSource(getDataSource());
        store.initialize();

        tileSets = store.getTileSets();
        assertNotNull(tileSets);
        assertEquals(4, tileSets.size());
        TileSet tileSet = new TileSet("topp:states2", "EPSG:2163", "image/png", null);
        assertTrue(tileSets.contains(tileSet));
        assertQuotaZero(tileSet);

        tileSet = new TileSet("topp:states2", "EPSG:2163", "image/jpeg", null);
        assertTrue(tileSets.contains(tileSet));
        assertQuotaZero(tileSet);
    }

    @Test
    public void testRenameLayer() throws InterruptedException {
        assertEquals(16, countTileSetsByLayerName("topp:states"));
        store.renameLayer("topp:states", "states_renamed");
        assertEquals(0, countTileSetsByLayerName("topp:states"));
        assertEquals(16, countTileSetsByLayerName("states_renamed"));
    }

    @Test
    public void testRenameLayer2() throws InterruptedException {
        final String oldLayerName =
                tilePageCalculator.getLayerNames().iterator().next();
        final String newLayerName = "renamed_layer";

        // make sure the layer is there and has stuff
        Quota usedQuota = store.getUsedQuotaByLayerName(oldLayerName);
        assertNotNull(usedQuota);

        TileSet tileSet =
                tilePageCalculator.getTileSetsFor(oldLayerName).iterator().next();
        TilePage page = new TilePage(tileSet.getId(), 0, 0, (byte) 0);
        store.addHitsAndSetAccesTime(Collections.singleton(new PageStatsPayload(page)));
        store.addToQuotaAndTileCounts(tileSet, new Quota(BigInteger.valueOf(1024)), Collections.emptyList());

        Quota expectedQuota = store.getUsedQuotaByLayerName(oldLayerName);
        assertEquals(1024L, expectedQuota.getBytes().longValue());

        assertNotNull(store.getTileSetById(tileSet.getId()));

        store.renameLayer(oldLayerName, newLayerName);

        // cascade deleted old layer?
        assertNull(store.getLeastRecentlyUsedPage(Collections.singleton(oldLayerName)));
        usedQuota = store.getUsedQuotaByLayerName(oldLayerName);
        assertNotNull(usedQuota);
        assertEquals(0L, usedQuota.getBytes().longValue());

        // created new layer?
        Quota newLayerUsedQuota = store.getUsedQuotaByLayerName(newLayerName);
        assertEquals(expectedQuota.getBytes(), newLayerUsedQuota.getBytes());
    }

    @Test
    public void testDeleteGridSet() throws InterruptedException {
        // put some data into four gridsets using two layers
        String layerName1 = "topp:states";
        String layerName2 = "topp:states3";
        TileSet tset1 = new TileSet(layerName1, "EPSG:4326", "image/jpeg", paramIds[0]);
        TileSet tset2 = new TileSet(layerName1, "EPSG:900913", "image/jpeg", paramIds[0]);
        TileSet tset3 = new TileSet(layerName2, "EPSG:4326", "image/png", null);
        TileSet tset4 = new TileSet(layerName1, "EPSG:4326", "image/png", paramIds[0]);
        addToQuotaStore(tset1);
        addToQuotaStore(tset2);
        addToQuotaStore(tset3);
        addToQuotaStore(tset4);
        // get the current quotas
        Quota tset1Quota = store.getUsedQuotaByTileSetId(tset1.getId());
        Quota tset2Quota = store.getUsedQuotaByTileSetId(tset2.getId());
        Quota tset3Quota = store.getUsedQuotaByTileSetId(tset3.getId());
        Quota tset4Quota = store.getUsedQuotaByTileSetId(tset4.getId());
        Quota globalQuota = store.getGloballyUsedQuota();
        // check the current global quota
        Quota sum = new Quota();
        sum.add(tset1Quota);
        sum.add(tset2Quota);
        sum.add(tset3Quota);
        sum.add(tset4Quota);
        assertEquals(globalQuota.getBytes(), sum.getBytes());

        assertThat(
                store.getTileSets(),
                containsInAnyOrder(
                        expectedTileSets.stream().map(Matchers::equalTo).collect(Collectors.toSet())));

        store.deleteGridSubset(layerName1, "EPSG:4326");

        assertThat(
                store.getTileSets(),
                containsInAnyOrder(expectedTileSets.stream()
                        .filter(ts -> !(ts.getGridsetId().equals("EPSG:4326")
                                && ts.getLayerName().equals(layerName1)))
                        .map(Matchers::equalTo)
                        .collect(Collectors.toSet())));

        // verify the quota for tset2 got erased and that now the total is equal to tset1
        Quota newTset1Quota = store.getUsedQuotaByTileSetId(tset1.getId());
        Quota newTset2Quota = store.getUsedQuotaByTileSetId(tset2.getId());
        Quota newTset3Quota = store.getUsedQuotaByTileSetId(tset3.getId());
        Quota newTset4Quota = store.getUsedQuotaByTileSetId(tset4.getId());
        // validate test quota 1
        assertNotNull(newTset1Quota);
        assertEquals(BigInteger.valueOf(0), newTset1Quota.getBytes());
        // validate test quota 2
        assertNotNull(newTset2Quota);
        assertEquals(tset2Quota.getBytes(), newTset2Quota.getBytes());
        // validate test quota 3
        assertNotNull(newTset3Quota);
        assertEquals(tset3Quota.getBytes(), newTset3Quota.getBytes());
        // validate test quota 4
        assertNotNull(newTset4Quota);
        assertEquals(BigInteger.valueOf(0), newTset4Quota.getBytes());
        // test the global quota
        globalQuota = store.getGloballyUsedQuota();
        assertEquals(tset2Quota.getBytes().add(tset3Quota.getBytes()), globalQuota.getBytes());
    }

    @Test
    public void testDeleteParameters() throws InterruptedException {
        // put some data into the two parameterizations
        String layerName = "topp:states";
        TileSet tset1 = new TileSet(layerName, "EPSG:4326", "image/jpeg", paramIds[0]);
        addToQuotaStore(tset1);
        TileSet tset2 = new TileSet(layerName, "EPSG:4326", "image/jpeg", paramIds[1]);
        addToQuotaStore(tset2);
        Quota tset1Quota = store.getUsedQuotaByTileSetId(tset1.getId());
        Quota tset2Quota = store.getUsedQuotaByTileSetId(tset2.getId());
        Quota globalQuota = store.getGloballyUsedQuota();
        Quota sum = new Quota();
        sum.add(tset1Quota);
        sum.add(tset2Quota);
        assertEquals(globalQuota.getBytes(), sum.getBytes());

        assertThat(
                store.getTileSets(),
                containsInAnyOrder(
                        expectedTileSets.stream().map(Matchers::equalTo).collect(Collectors.toSet())));

        store.deleteParameters("topp:states", paramIds[1]);

        assertThat(
                store.getTileSets(),
                containsInAnyOrder(expectedTileSets.stream()
                        .filter(ts -> !(Objects.equals(ts.getParametersId(), paramIds[1])
                                && ts.getLayerName().equals(layerName)))
                        .map(Matchers::equalTo)
                        .collect(Collectors.toSet())));

        // verify the quota for tset2 got erased and that now the total is equal to tset1
        tset1Quota = store.getUsedQuotaByTileSetId(tset1.getId());
        tset2Quota = store.getUsedQuotaByTileSetId(tset2.getId());

        assertNotNull(tset2Quota);
        assertEquals(BigInteger.valueOf(0), tset2Quota.getBytes());
        globalQuota = store.getGloballyUsedQuota();
        assertEquals(tset1Quota.getBytes(), globalQuota.getBytes());
    }

    private void addToQuotaStore(TileSet tset) throws InterruptedException {
        Quota quotaDiff = new Quota(5, StorageUnit.MiB);
        PageStatsPayload stats = new PageStatsPayload(new TilePage(tset.getId(), 0, 0, 3));
        stats.setNumTiles(10);
        store.addToQuotaAndTileCounts(tset, quotaDiff, Collections.singletonList(stats));
    }

    @Test
    public void testDeleteLayer() throws InterruptedException {
        // put some data into the layer
        String layerName = "topp:states2";
        TileSet tset = new TileSet(layerName, "EPSG:2163", "image/jpeg", null);

        addToQuotaStore(tset);

        // make sure the layer is there and has stuff
        Quota oldUsedQuota = store.getUsedQuotaByLayerName(layerName);
        assertNotNull(oldUsedQuota);
        Quota globalQuotaBefore = store.getGloballyUsedQuota();
        assertTrue(oldUsedQuota.getBytes().longValue() > 0);
        assertTrue(globalQuotaBefore.getBytes().longValue() > 0);

        TileSet tileSet =
                tilePageCalculator.getTileSetsFor(layerName).iterator().next();
        TilePage page = new TilePage(tileSet.getId(), 0, 0, (byte) 0);
        store.addHitsAndSetAccesTime(Collections.singleton(new PageStatsPayload(page)));

        assertNotNull(store.getTileSetById(tileSet.getId()));

        // make sure previous steps have released the lock
        Thread.sleep(100);
        store.deleteLayer(layerName);

        // cascade deleted?
        assertNull(store.getLeastRecentlyUsedPage(Collections.singleton(layerName)));
        Quota usedQuota = store.getUsedQuotaByLayerName(layerName);
        assertNotNull(usedQuota);
        assertEquals(0L, usedQuota.getBytes().longValue());

        // make sure the global quota got updated
        Quota globalQuotaAfter = store.getGloballyUsedQuota();
        assertEquals(0, globalQuotaAfter.getBytes().longValue());
    }

    @Test
    public void testVisitor() throws Exception {
        Set<TileSet> tileSets1 = store.getTileSets();
        final Set<TileSet> tileSets2 = new HashSet<>();
        store.accept((tileSet, quotaStore) -> tileSets2.add(tileSet));
        assertEquals(tileSets1, tileSets2);
    }

    @Test
    public void testGetTileSetById() throws Exception {
        TileSet tileSet = store.getTileSetById(testTileSet.getId());
        assertNotNull(tileSet);
        assertEquals(testTileSet, tileSet);

        try {
            store.getTileSetById("NonExistentTileSetId");
            fail("Expected IAE");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGetUsedQuotaByLayerName() throws Exception {
        String layerName = "topp:states2";
        List<TileSet> tileSets = new ArrayList<>(tilePageCalculator.getTileSetsFor(layerName));

        Quota expected = new Quota();
        for (TileSet tset : tileSets) {
            Quota quotaDiff = new Quota(10, StorageUnit.MiB);
            expected.add(quotaDiff);
            store.addToQuotaAndTileCounts(tset, quotaDiff, Collections.EMPTY_SET);
        }

        Quota usedQuotaByLayerName = store.getUsedQuotaByLayerName(layerName);
        assertEquals(expected.getBytes(), usedQuotaByLayerName.getBytes());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGetUsedQuotaByTileSetId() throws Exception {
        String layerName = "topp:states2";
        List<TileSet> tileSets = new ArrayList<>(tilePageCalculator.getTileSetsFor(layerName));

        Map<String, Quota> expectedById = new HashMap<>();

        for (TileSet tset : tileSets) {
            Quota quotaDiff = new Quota(10D * ThreadLocalRandom.current().nextDouble(), StorageUnit.MiB);
            store.addToQuotaAndTileCounts(tset, quotaDiff, Collections.EMPTY_SET);
            store.addToQuotaAndTileCounts(tset, quotaDiff, Collections.EMPTY_SET);
            Quota tsetQuota = new Quota(quotaDiff);
            tsetQuota.add(quotaDiff);
            expectedById.put(tset.getId(), tsetQuota);
        }

        for (Map.Entry<String, Quota> expected : expectedById.entrySet()) {
            BigInteger expectedValaue = expected.getValue().getBytes();
            String tsetId = expected.getKey();
            assertEquals(expectedValaue, store.getUsedQuotaByTileSetId(tsetId).getBytes());
        }
    }

    @Test
    public void testUpdateUsedQuotaWithParameters() throws Exception {
        // prepare a tileset with params
        String paramId = DigestUtils.sha1Hex("&styles=polygon");
        TileSet tset = new TileSet("topp:states2", "EPSG:2163", "image/jpeg", paramId);

        Quota quotaDiff = new Quota(10D * ThreadLocalRandom.current().nextDouble(), StorageUnit.MiB);
        PageStatsPayload stats = new PageStatsPayload(new TilePage(tset.getId(), 0, 0, 3));
        stats.setNumTiles(10);
        store.addToQuotaAndTileCounts(tset, quotaDiff, Collections.singletonList(stats));

        assertEquals(
                quotaDiff.getBytes(),
                store.getUsedQuotaByTileSetId(tset.getId()).getBytes());
    }

    /**
     * Combined test for {@link JDBCQuotaStore#addToQuotaAndTileCounts(TileSet, Quota, Collection)} and
     * {@link JDBCQuotaStore#addHitsAndSetAccesTime(Collection)}
     */
    @Test
    public void testPageStatsGathering() throws Exception {
        final MockSystemUtils sysUtils = new MockSystemUtils();
        sysUtils.setCurrentTimeMinutes(10);
        sysUtils.setCurrentTimeMillis(10 * 60 * 1000);
        SystemUtils.set(sysUtils);

        TileSet tileSet = testTileSet;

        TilePage page = new TilePage(tileSet.getId(), 0, 0, (byte) 0);

        PageStatsPayload payload = new PageStatsPayload(page);
        int numHits = 100;
        payload.setTileSet(tileSet);
        payload.setLastAccessTime(sysUtils.currentTimeMillis() - 1 * 60 * 1000);
        payload.setNumHits(numHits);
        payload.setNumTiles(1);

        store.addToQuotaAndTileCounts(tileSet, new Quota(1, StorageUnit.MiB), Collections.singleton(payload));

        Future<List<PageStats>> result = store.addHitsAndSetAccesTime(Collections.singleton(payload));
        List<PageStats> allStats = result.get();
        PageStats stats = allStats.get(0);
        float fillFactor = stats.getFillFactor();
        assertEquals(1.0f, fillFactor, 1e-6);

        int lastAccessTimeMinutes = stats.getLastAccessTimeMinutes();
        assertEquals(sysUtils.currentTimeMinutes(), lastAccessTimeMinutes);

        float frequencyOfUsePerMinute = stats.getFrequencyOfUsePerMinute();
        assertEquals(100f, frequencyOfUsePerMinute, 1e-5f);

        // now 1 minute later...
        sysUtils.setCurrentTimeMinutes(sysUtils.currentTimeMinutes() + 2);
        sysUtils.setCurrentTimeMillis(sysUtils.currentTimeMillis() + 2 * 60 * 1000);

        numHits = 10;
        payload.setLastAccessTime(sysUtils.currentTimeMillis() - 1 * 60 * 1000);
        payload.setNumHits(numHits);

        result = store.addHitsAndSetAccesTime(Collections.singleton(payload));
        allStats = result.get();
        stats = allStats.get(0);

        lastAccessTimeMinutes = stats.getLastAccessTimeMinutes();
        assertEquals(11, lastAccessTimeMinutes);

        frequencyOfUsePerMinute = stats.getFrequencyOfUsePerMinute();
        float expected = 55.0f; // the 100 previous + the 10 added now / the 2 minutes that elapsed
        assertEquals(expected, frequencyOfUsePerMinute, 1e-6f);
    }

    @Test
    public void testGetGloballyUsedQuota() throws InterruptedException {
        Quota usedQuota = store.getGloballyUsedQuota();
        assertNotNull(usedQuota);
        assertEquals(0, usedQuota.getBytes().intValue());

        String layerName = tilePageCalculator.getLayerNames().iterator().next();
        TileSet tileSet =
                tilePageCalculator.getTileSetsFor(layerName).iterator().next();

        Quota quotaDiff = new Quota(BigInteger.valueOf(1000));
        Collection<PageStatsPayload> tileCountDiffs = Collections.emptySet();
        store.addToQuotaAndTileCounts(tileSet, quotaDiff, tileCountDiffs);

        usedQuota = store.getGloballyUsedQuota();
        assertNotNull(usedQuota);
        assertEquals(1000, usedQuota.getBytes().intValue());

        quotaDiff = new Quota(BigInteger.valueOf(-500));
        store.addToQuotaAndTileCounts(tileSet, quotaDiff, tileCountDiffs);

        usedQuota = store.getGloballyUsedQuota();
        assertNotNull(usedQuota);
        assertEquals(500, usedQuota.getBytes().intValue());
    }

    @Test
    public void testSetTruncated() throws Exception {
        String tileSetId = testTileSet.getId();
        TilePage page = new TilePage(tileSetId, 0, 0, 2);

        PageStatsPayload payload = new PageStatsPayload(page);
        payload.setTileSet(testTileSet);
        int numHits = 100;
        payload.setNumHits(numHits);
        payload.setNumTiles(5);

        store.addToQuotaAndTileCounts(testTileSet, new Quota(1, StorageUnit.MiB), Collections.singleton(payload));
        List<PageStats> stats =
                store.addHitsAndSetAccesTime(Collections.singleton(payload)).get();
        assertTrue(stats.get(0).getFillFactor() > 0f);
        PageStats pageStats = store.setTruncated(page);
        assertEquals(0f, pageStats.getFillFactor(), 1e-6f);
    }

    @Test
    public void testGetLeastFrequentlyUsedPage() throws Exception {
        final String layerName = testTileSet.getLayerName();
        Set<String> layerNames = Collections.singleton(layerName);

        TilePage lfuPage = store.getLeastFrequentlyUsedPage(layerNames);
        assertNull(lfuPage);

        TilePage page1 = new TilePage(testTileSet.getId(), 0, 1, 2);
        TilePage page2 = new TilePage(testTileSet.getId(), 1, 1, 2);

        PageStatsPayload payload1 = new PageStatsPayload(page1, testTileSet);
        PageStatsPayload payload2 = new PageStatsPayload(page2, testTileSet);

        payload1.setNumHits(100);
        payload2.setNumHits(10);
        Collection<PageStatsPayload> statsUpdates = Arrays.asList(payload1, payload2);
        store.addHitsAndSetAccesTime(statsUpdates).get();

        TilePage leastFrequentlyUsedPage = store.getLeastFrequentlyUsedPage(layerNames);
        assertEquals(page2, leastFrequentlyUsedPage);

        payload2.setNumHits(1000);
        store.addHitsAndSetAccesTime(statsUpdates).get();

        leastFrequentlyUsedPage = store.getLeastFrequentlyUsedPage(layerNames);
        assertEquals(page1, leastFrequentlyUsedPage);
    }

    @Test
    public void testGetLeastFrequentlyUsedPageSkipEmpty() throws Exception {
        final String layerName = testTileSet.getLayerName();
        Set<String> layerNames = Collections.singleton(layerName);

        TilePage lfuPage = store.getLeastFrequentlyUsedPage(layerNames);
        assertNull(lfuPage);

        TilePage page1 = new TilePage(testTileSet.getId(), 0, 1, 2);
        TilePage page2 = new TilePage(testTileSet.getId(), 1, 1, 2);

        PageStatsPayload payload1 = new PageStatsPayload(page1, testTileSet);
        PageStatsPayload payload2 = new PageStatsPayload(page2, testTileSet);

        payload1.setNumHits(100);
        payload2.setNumHits(10);
        Collection<PageStatsPayload> statsUpdates = Arrays.asList(payload1, payload2);
        store.addHitsAndSetAccesTime(statsUpdates).get();

        TilePage leastFrequentlyUsedPage = store.getLeastFrequentlyUsedPage(layerNames);
        assertEquals(page2, leastFrequentlyUsedPage);

        store.setTruncated(page2);

        leastFrequentlyUsedPage = store.getLeastFrequentlyUsedPage(layerNames);
        assertEquals(page1, leastFrequentlyUsedPage);
    }

    @Test
    public void testGetLeastRecentlyUsedPage() throws Exception {
        MockSystemUtils mockSystemUtils = new MockSystemUtils();
        mockSystemUtils.setCurrentTimeMinutes(1000);
        mockSystemUtils.setCurrentTimeMillis(mockSystemUtils.currentTimeMinutes() * 60L * 1000L);
        SystemUtils.set(mockSystemUtils);

        final String layerName = testTileSet.getLayerName();
        Set<String> layerNames = Collections.singleton(layerName);

        TilePage leastRecentlyUsedPage = store.getLeastRecentlyUsedPage(layerNames);
        assertNull(leastRecentlyUsedPage);

        TilePage page1 = new TilePage(testTileSet.getId(), 0, 1, 2);
        TilePage page2 = new TilePage(testTileSet.getId(), 1, 1, 2);

        PageStatsPayload payload1 = new PageStatsPayload(page1, testTileSet);
        PageStatsPayload payload2 = new PageStatsPayload(page2, testTileSet);

        payload1.setLastAccessTime(mockSystemUtils.currentTimeMillis() + 1 * 60L * 1000L);
        payload2.setLastAccessTime(mockSystemUtils.currentTimeMillis() + 2 * 60L * 1000L);

        Collection<PageStatsPayload> statsUpdates = Arrays.asList(payload1, payload2);
        store.addHitsAndSetAccesTime(statsUpdates).get();

        leastRecentlyUsedPage = store.getLeastRecentlyUsedPage(layerNames);
        assertEquals(page1, leastRecentlyUsedPage);

        payload1.setLastAccessTime(mockSystemUtils.currentTimeMillis() + 10 * 60L * 1000L);
        store.addHitsAndSetAccesTime(statsUpdates).get();

        leastRecentlyUsedPage = store.getLeastRecentlyUsedPage(layerNames);
        assertEquals(page2, leastRecentlyUsedPage);
    }

    @Test
    public void testGetLeastRecentlyUsedPageSkipEmpty() throws Exception {
        MockSystemUtils mockSystemUtils = new MockSystemUtils();
        mockSystemUtils.setCurrentTimeMinutes(1000);
        mockSystemUtils.setCurrentTimeMillis(mockSystemUtils.currentTimeMinutes() * 60L * 1000L);
        SystemUtils.set(mockSystemUtils);

        final String layerName = testTileSet.getLayerName();
        Set<String> layerNames = Collections.singleton(layerName);

        TilePage leastRecentlyUsedPage = store.getLeastRecentlyUsedPage(layerNames);
        assertNull(leastRecentlyUsedPage);

        TilePage page1 = new TilePage(testTileSet.getId(), 0, 1, 2);
        TilePage page2 = new TilePage(testTileSet.getId(), 1, 1, 2);

        PageStatsPayload payload1 = new PageStatsPayload(page1, testTileSet);
        PageStatsPayload payload2 = new PageStatsPayload(page2, testTileSet);

        payload1.setLastAccessTime(mockSystemUtils.currentTimeMillis() + 1 * 60 * 1000);
        payload2.setLastAccessTime(mockSystemUtils.currentTimeMillis() + 2 * 60 * 1000);

        Collection<PageStatsPayload> statsUpdates = Arrays.asList(payload1, payload2);
        store.addHitsAndSetAccesTime(statsUpdates).get();

        leastRecentlyUsedPage = store.getLeastRecentlyUsedPage(layerNames);
        assertEquals(page1, leastRecentlyUsedPage);

        // truncate the page, setting its fill to 0
        store.setTruncated(page1);

        leastRecentlyUsedPage = store.getLeastRecentlyUsedPage(layerNames);
        assertEquals(page2, leastRecentlyUsedPage);
    }

    @Test
    public void testGetTilesForPage() throws Exception {
        TilePage page = new TilePage(testTileSet.getId(), 0, 0, 0);

        long[][] expected = tilePageCalculator.toGridCoverage(testTileSet, page);
        long[][] tilesForPage = store.getTilesForPage(page);

        assertArrayEquals(expected[0], tilesForPage[0]);

        page = new TilePage(testTileSet.getId(), 0, 0, 1);

        expected = tilePageCalculator.toGridCoverage(testTileSet, page);
        tilesForPage = store.getTilesForPage(page);

        assertArrayEquals(expected[1], tilesForPage[1]);
    }

    private int countTileSetsByLayerName(String layerName) {
        int count = 0;
        for (TileSet ts : store.getTileSets()) {
            if (layerName.equals(ts.getLayerName())) {
                count++;
            }
        }

        return count;
    }

    /** Asserts the quota used by this tile set is null */
    private void assertQuotaZero(TileSet tileSet) {
        Quota quota = store.getUsedQuotaByTileSetId(tileSet.getId());
        assertNotNull(quota);
        assertEquals(0, quota.getBytes().longValue());
    }

    /** Asserts the quota used by this tile set is null */
    private void assertQuotaZero(String layerName) throws InterruptedException {
        Quota quota = store.getUsedQuotaByLayerName(layerName);
        assertNotNull(quota);
        assertEquals(0, quota.getBytes().longValue());
    }
}
