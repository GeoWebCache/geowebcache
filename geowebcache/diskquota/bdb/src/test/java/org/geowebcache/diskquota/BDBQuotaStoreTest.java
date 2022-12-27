package org.geowebcache.diskquota;

import static org.easymock.EasyMock.newCapture;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import org.geowebcache.diskquota.bdb.BDBQuotaStore;
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
import org.geowebcache.util.FileMatchers;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public class BDBQuotaStoreTest {

    private BDBQuotaStore store;

    private TilePageCalculator tilePageCalculator;

    private TileSet testTileSet;

    TileLayerDispatcher layerDispatcher;

    DefaultStorageFinder cacheDirFinder;

    StorageBroker storageBroker;

    @Rule public TemporaryFolder targetDir = new TemporaryFolder();

    @Rule public ExpectedException exception = ExpectedException.none();
    @Rule public MockWepAppContextRule context = new MockWepAppContextRule();

    Map<String, Set<String>> parameterIdsMap;
    Map<String, Set<Map<String, String>>> parametersMap;

    @Before
    public void setUp() throws Exception {

        cacheDirFinder = EasyMock.createMock(DefaultStorageFinder.class);
        EasyMock.expect(cacheDirFinder.getDefaultPath())
                .andReturn(targetDir.getRoot().getAbsolutePath())
                .anyTimes();
        EasyMock.expect(
                        cacheDirFinder.findEnvVar(
                                EasyMock.eq(DiskQuotaMonitor.GWC_DISKQUOTA_DISABLED)))
                .andReturn(null)
                .anyTimes();
        EasyMock.replay(cacheDirFinder);

        Capture<String> layerNameCap = newCapture();
        storageBroker = EasyMock.createMock(StorageBroker.class);
        EasyMock.expect(storageBroker.getCachedParameterIds(EasyMock.capture(layerNameCap)))
                .andStubAnswer(
                        () ->
                                parameterIdsMap.getOrDefault(
                                        layerNameCap.getValue(), Collections.singleton(null)));
        EasyMock.replay(storageBroker);
        parametersMap = new HashMap<>();
        parametersMap.put(
                "topp:states",
                Stream.of("STYLE=&SOMEPARAMETER=", "STYLE=population&SOMEPARAMETER=2.0")
                        .map(ParametersUtils::getMap)
                        .collect(Collectors.toSet()));
        parameterIdsMap =
                parametersMap.entrySet().stream()
                        .collect(
                                Collectors.toMap(
                                        Map.Entry::getKey,
                                        e ->
                                                e.getValue().stream()
                                                        .map(ParametersUtils::getKvp)
                                                        .collect(Collectors.toSet())));
        XMLConfiguration xmlConfig = loadXMLConfig();
        context.addBean("xmlConfig", xmlConfig, XMLConfiguration.class.getInterfaces());
        LinkedList<TileLayerConfiguration> configList = new LinkedList<>();
        configList.add(xmlConfig);
        context.addBean(
                "DefaultGridsets",
                new DefaultGridsets(true, true),
                DefaultGridsets.class,
                GridSetConfiguration.class,
                BaseConfiguration.class);
        GridSetBroker gridSetBroker = new GridSetBroker();
        gridSetBroker.setApplicationContext(context.getMockContext());
        layerDispatcher = new TileLayerDispatcher(gridSetBroker, null);
        layerDispatcher.setApplicationContext(context.getMockContext());

        tilePageCalculator = new TilePageCalculator(layerDispatcher, storageBroker);

        xmlConfig.setGridSetBroker(gridSetBroker);

        xmlConfig.afterPropertiesSet();
        layerDispatcher.afterPropertiesSet();
        gridSetBroker.afterPropertiesSet();

        store = new BDBQuotaStore(cacheDirFinder, tilePageCalculator);
        store.startUp();
        testTileSet = tilePageCalculator.getTileSetsFor("topp:states2").iterator().next();
    }

    private XMLConfiguration loadXMLConfig() {
        XMLConfiguration xmlConfig = null;
        try {
            xmlConfig =
                    new XMLConfiguration(
                            context.getContextProvider(),
                            new MockConfigurationResourceProvider(
                                    () ->
                                            XMLConfiguration.class.getResourceAsStream(
                                                    XMLConfigurationBackwardsCompatibilityTest
                                                            .LATEST_FILENAME)));
        } catch (Exception e) {
            // Do nothing
        }

        return xmlConfig;
    }

    @Test
    public void testInitialization() throws Exception {
        String[] paramIds = parameterIdsMap.get("topp:states").toArray(new String[2]);
        assertThat(
                store,
                hasProperty(
                        "tileSets",
                        containsInAnyOrder(
                                new TileSet("topp:states", "EPSG:900913", "image/png", paramIds[0]),
                                new TileSet(
                                        "topp:states", "EPSG:900913", "image/jpeg", paramIds[0]),
                                new TileSet("topp:states", "EPSG:900913", "image/gif", paramIds[0]),
                                new TileSet(
                                        "topp:states",
                                        "EPSG:900913",
                                        "application/vnd.google-earth.kml+xml",
                                        paramIds[0]),
                                new TileSet("topp:states", "EPSG:4326", "image/png", paramIds[0]),
                                new TileSet("topp:states", "EPSG:4326", "image/jpeg", paramIds[0]),
                                new TileSet("topp:states", "EPSG:4326", "image/gif", paramIds[0]),
                                new TileSet(
                                        "topp:states",
                                        "EPSG:4326",
                                        "application/vnd.google-earth.kml+xml",
                                        paramIds[0]),
                                new TileSet("topp:states", "EPSG:900913", "image/png", paramIds[1]),
                                new TileSet(
                                        "topp:states", "EPSG:900913", "image/jpeg", paramIds[1]),
                                new TileSet("topp:states", "EPSG:900913", "image/gif", paramIds[1]),
                                new TileSet(
                                        "topp:states",
                                        "EPSG:900913",
                                        "application/vnd.google-earth.kml+xml",
                                        paramIds[1]),
                                new TileSet("topp:states", "EPSG:4326", "image/png", paramIds[1]),
                                new TileSet("topp:states", "EPSG:4326", "image/jpeg", paramIds[1]),
                                new TileSet("topp:states", "EPSG:4326", "image/gif", paramIds[1]),
                                new TileSet(
                                        "topp:states",
                                        "EPSG:4326",
                                        "application/vnd.google-earth.kml+xml",
                                        paramIds[1]),
                                new TileSet("topp:states2", "EPSG:2163", "image/png", null),
                                new TileSet("topp:states2", "EPSG:2163", "image/jpeg", null))));

        // remove one layer from the dispatcher
        layerDispatcher.removeLayer("topp:states");
        // and make sure at the next startup the store catches up (note this behaviour is just a
        // startup consistency check in case the store got out of sync for some reason. On normal
        // situations the store should have been notified through store.deleteLayer(layerName) if
        // the layer was removed programmatically through StorageBroker.deleteLayer
        store.close();
        store.startUp();

        assertThat(
                store,
                hasProperty(
                        "tileSets",
                        containsInAnyOrder(
                                new TileSet("topp:states2", "EPSG:2163", "image/png", null),
                                new TileSet("topp:states2", "EPSG:2163", "image/jpeg", null))));
    }

    /**
     * Combined test for {@link BDBQuotaStore#addToQuotaAndTileCounts(TileSet, Quota, Collection)}
     * and {@link BDBQuotaStore#addHitsAndSetAccesTime(Collection)}
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
        payload.setLastAccessTime(sysUtils.currentTimeMillis() - 1 * 60 * 1000);
        payload.setNumHits(numHits);
        payload.setNumTiles(1);

        store.addToQuotaAndTileCounts(
                tileSet, new Quota(1, StorageUnit.MiB), Collections.singleton(payload));

        Future<List<PageStats>> result =
                store.addHitsAndSetAccesTime(Collections.singleton(payload));
        List<PageStats> allStats = result.get();
        PageStats stats = allStats.get(0);

        assertThat(stats, hasProperty("fillFactor", closeTo(1.0f, 1e-6f)));

        assertThat(
                stats,
                hasProperty("lastAccessTimeMinutes", equalTo(sysUtils.currentTimeMinutes())));

        assertThat(stats, hasProperty("frequencyOfUsePerMinute", closeTo(100f, 1e-6f)));

        // now 1 minute later...
        sysUtils.setCurrentTimeMinutes(sysUtils.currentTimeMinutes() + 2);
        sysUtils.setCurrentTimeMillis(sysUtils.currentTimeMillis() + 2 * 60 * 1000);

        numHits = 10;
        payload.setLastAccessTime(sysUtils.currentTimeMillis() - 1 * 60 * 1000);
        payload.setNumHits(numHits);

        result = store.addHitsAndSetAccesTime(Collections.singleton(payload));
        allStats = result.get();
        stats = allStats.get(0);

        assertThat(stats, hasProperty("lastAccessTimeMinutes", equalTo(11)));

        assertThat(
                stats,
                hasProperty(
                        "frequencyOfUsePerMinute",
                        closeTo(
                                55.0f, // the 100 previous + the 10 added now / the 2 minutes that
                                // elapsed
                                1e-6f)));
    }

    @Test
    public void testGetGloballyUsedQuota() throws InterruptedException {
        store.getGloballyUsedQuota().getBytes();
        assertThat(store, hasProperty("globallyUsedQuota", quotaEmpty()));

        String layerName = tilePageCalculator.getLayerNames().iterator().next();
        TileSet tileSet = tilePageCalculator.getTileSetsFor(layerName).iterator().next();

        Quota quotaDiff = new Quota(BigInteger.valueOf(1000));
        Collection<PageStatsPayload> tileCountDiffs = Collections.emptySet();
        store.addToQuotaAndTileCounts(tileSet, quotaDiff, tileCountDiffs);

        assertThat(store, hasProperty("globallyUsedQuota", bytes(1000)));

        quotaDiff = new Quota(BigInteger.valueOf(-500));
        store.addToQuotaAndTileCounts(tileSet, quotaDiff, tileCountDiffs);

        assertThat(store, hasProperty("globallyUsedQuota", bytes(500)));
    }

    @Test
    public void testDeleteGridset() throws InterruptedException {
        String layerName = "topp:states";
        String gridSetId = "EPSG:4326";

        long quotaToDelete =
                tilePageCalculator.getTileSetsFor(layerName).stream()
                        .filter(ts -> ts.getGridsetId().equals(gridSetId))
                        .map(
                                ts -> {
                                    Quota quotaDiff = new Quota(42, StorageUnit.MiB);
                                    try {
                                        store.addToQuotaAndTileCounts(
                                                ts, quotaDiff, Collections.emptySet());
                                        TilePage page = new TilePage(ts.getId(), 0, 0, (byte) 0);
                                        store.addHitsAndSetAccesTime(
                                                Collections.singleton(new PageStatsPayload(page)));
                                        return 42;
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        throw new AssertionError("Unexpected Exception", e);
                                    }
                                })
                        .collect(Collectors.summingLong(mb -> mb * 1024 * 1024));
        assertThat(quotaToDelete, greaterThan(0L));
        long quotaToKeep =
                tilePageCalculator.getTileSetsFor(layerName).stream()
                        .filter(ts -> !ts.getGridsetId().equals(gridSetId))
                        .map(
                                ts -> {
                                    Quota quotaDiff = new Quota(10, StorageUnit.MiB);
                                    try {
                                        store.addToQuotaAndTileCounts(
                                                ts, quotaDiff, Collections.emptySet());
                                        TilePage page = new TilePage(ts.getId(), 0, 0, (byte) 0);
                                        store.addHitsAndSetAccesTime(
                                                Collections.singleton(new PageStatsPayload(page)));
                                        return 10;
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        throw new AssertionError("Unexpected Exception", e);
                                    }
                                })
                        .collect(Collectors.summingLong(mb -> mb * 1024 * 1024));
        assertThat(quotaToKeep, greaterThan(0L));

        assertThat(store.getUsedQuotaByLayerName(layerName), bytes(quotaToDelete + quotaToKeep));

        store.deleteGridSubset(layerName, gridSetId);

        assertThat(store.getUsedQuotaByLayerName(layerName), bytes(quotaToKeep));
    }

    @Test
    public void testDeleteParameters() throws InterruptedException {
        String layerName = "topp:states";
        String parametersId = parameterIdsMap.get(layerName).iterator().next();

        long quotaToDelete =
                tilePageCalculator.getTileSetsFor(layerName).stream()
                        .filter(ts -> ts.getParametersId().equals(parametersId))
                        .map(
                                ts -> {
                                    Quota quotaDiff = new Quota(42, StorageUnit.MiB);
                                    try {
                                        store.addToQuotaAndTileCounts(
                                                ts, quotaDiff, Collections.emptySet());
                                        TilePage page = new TilePage(ts.getId(), 0, 0, (byte) 0);
                                        store.addHitsAndSetAccesTime(
                                                Collections.singleton(new PageStatsPayload(page)));
                                        return 42;
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        throw new AssertionError("Unexpected Exception", e);
                                    }
                                })
                        .collect(Collectors.summingLong(mb -> mb * 1024 * 1024));
        assertThat(quotaToDelete, greaterThan(0L));
        long quotaToKeep =
                tilePageCalculator.getTileSetsFor(layerName).stream()
                        .filter(ts -> !ts.getParametersId().equals(parametersId))
                        .map(
                                ts -> {
                                    Quota quotaDiff = new Quota(10, StorageUnit.MiB);
                                    try {
                                        store.addToQuotaAndTileCounts(
                                                ts, quotaDiff, Collections.emptySet());
                                        TilePage page = new TilePage(ts.getId(), 0, 0, (byte) 0);
                                        store.addHitsAndSetAccesTime(
                                                Collections.singleton(new PageStatsPayload(page)));
                                        return 10;
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        throw new AssertionError("Unexpected Exception", e);
                                    }
                                })
                        .collect(Collectors.summingLong(mb -> mb * 1024 * 1024));
        assertThat(quotaToKeep, greaterThan(0L));

        assertThat(store.getUsedQuotaByLayerName(layerName), bytes(quotaToDelete + quotaToKeep));

        store.deleteParameters(layerName, parametersId);

        assertThat(store.getUsedQuotaByLayerName(layerName), bytes(quotaToKeep));
    }

    @Test
    public void testRenameLayer() throws InterruptedException {
        final String oldLayerName = tilePageCalculator.getLayerNames().iterator().next();
        final String newLayerName = "renamed_layer";

        BigInteger expectedBytes = BigInteger.valueOf(1024);
        BigInteger emptyBytes = BigInteger.ZERO;

        // make sure the layer is there and has stuff
        assertThat(store.getUsedQuotaByLayerName(oldLayerName), notNullValue());

        TileSet tileSet = tilePageCalculator.getTileSetsFor(oldLayerName).iterator().next();
        TilePage page = new TilePage(tileSet.getId(), 0, 0, (byte) 0);
        store.addHitsAndSetAccesTime(Collections.singleton(new PageStatsPayload(page)));
        store.addToQuotaAndTileCounts(tileSet, new Quota(expectedBytes), Collections.emptyList());

        assertThat(store.getUsedQuotaByLayerName(oldLayerName), bytes(expectedBytes));

        assertThat(store.getTileSetById(tileSet.getId()), notNullValue());

        store.renameLayer(oldLayerName, newLayerName);

        // cascade deleted old layer?
        assertThat(
                store.getLeastRecentlyUsedPage(Collections.singleton(oldLayerName)), nullValue());
        assertThat(store.getUsedQuotaByLayerName(oldLayerName), bytes(emptyBytes));

        // created new layer?
        assertThat(store.getUsedQuotaByLayerName(newLayerName), bytes(expectedBytes));
    }

    @Test
    public void testGetLeastFrequentlyUsedPage() throws Exception {
        final String layerName = testTileSet.getLayerName();
        Set<String> layerNames = Collections.singleton(layerName);

        TilePage lfuPage = store.getLeastFrequentlyUsedPage(layerNames);
        assertThat(lfuPage, nullValue());

        TilePage page1 = new TilePage(testTileSet.getId(), 0, 1, 2);
        TilePage page2 = new TilePage(testTileSet.getId(), 1, 1, 2);

        PageStatsPayload payload1 = new PageStatsPayload(page1);
        PageStatsPayload payload2 = new PageStatsPayload(page2);

        payload1.setNumHits(100);
        payload2.setNumHits(10);
        Collection<PageStatsPayload> statsUpdates = Arrays.asList(payload1, payload2);
        store.addHitsAndSetAccesTime(statsUpdates).get();

        assertThat(store.getLeastFrequentlyUsedPage(layerNames), equalTo(page2));

        payload2.setNumHits(1000);
        store.addHitsAndSetAccesTime(statsUpdates).get();

        assertThat(store.getLeastFrequentlyUsedPage(layerNames), equalTo(page1));
    }

    @Test
    public void testGetLeastRecentlyUsedPage() throws Exception {
        MockSystemUtils mockSystemUtils = new MockSystemUtils();
        mockSystemUtils.setCurrentTimeMinutes(1000);
        mockSystemUtils.setCurrentTimeMillis(mockSystemUtils.currentTimeMinutes() * 60 * 1000);
        SystemUtils.set(mockSystemUtils);

        final String layerName = testTileSet.getLayerName();
        Set<String> layerNames = Collections.singleton(layerName);

        assertThat(store.getLeastRecentlyUsedPage(layerNames), nullValue());

        TilePage page1 = new TilePage(testTileSet.getId(), 0, 1, 2);
        TilePage page2 = new TilePage(testTileSet.getId(), 1, 1, 2);

        PageStatsPayload payload1 = new PageStatsPayload(page1);
        PageStatsPayload payload2 = new PageStatsPayload(page2);

        payload1.setLastAccessTime(mockSystemUtils.currentTimeMillis() + 1 * 60 * 1000);
        payload2.setLastAccessTime(mockSystemUtils.currentTimeMillis() + 2 * 60 * 1000);

        Collection<PageStatsPayload> statsUpdates = Arrays.asList(payload1, payload2);
        store.addHitsAndSetAccesTime(statsUpdates).get();

        assertThat(store.getLeastRecentlyUsedPage(layerNames), equalTo(page1));

        payload1.setLastAccessTime(mockSystemUtils.currentTimeMillis() + 10 * 60 * 1000);
        store.addHitsAndSetAccesTime(statsUpdates).get();

        assertThat(store.getLeastRecentlyUsedPage(layerNames), equalTo(page2));
    }

    @Test
    public void testGetTileSetById() throws Exception {
        assertThat(store.getTileSetById(testTileSet.getId()), equalTo(testTileSet));

        exception.expect(IllegalArgumentException.class);

        store.getTileSetById("NonExistentTileSetId");
    }

    @Test
    public void testGetTilesForPage() throws Exception {
        TilePage page = new TilePage(testTileSet.getId(), 0, 0, 0);

        long[][] expected = tilePageCalculator.toGridCoverage(testTileSet, page);
        long[][] tilesForPage = store.getTilesForPage(page);

        assertThat(tilesForPage[0], equalTo(expected[0]));

        page = new TilePage(testTileSet.getId(), 0, 0, 1);

        expected = tilePageCalculator.toGridCoverage(testTileSet, page);
        tilesForPage = store.getTilesForPage(page);

        assertThat(tilesForPage[1], equalTo(expected[1]));
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

        assertThat(store.getUsedQuotaByLayerName(layerName), bytes(expected.getBytes()));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGetUsedQuotaByTileSetId() throws Exception {
        String layerName = "topp:states2";
        List<TileSet> tileSets = new ArrayList<>(tilePageCalculator.getTileSetsFor(layerName));

        Map<String, Quota> expectedById = new HashMap<>();

        for (TileSet tset : tileSets) {
            Quota quotaDiff = new Quota(10D * Math.random(), StorageUnit.MiB);
            store.addToQuotaAndTileCounts(tset, quotaDiff, Collections.EMPTY_SET);
            store.addToQuotaAndTileCounts(tset, quotaDiff, Collections.EMPTY_SET);
            Quota tsetQuota = new Quota(quotaDiff);
            tsetQuota.add(quotaDiff);
            expectedById.put(tset.getId(), tsetQuota);
        }

        for (Map.Entry<String, Quota> expected : expectedById.entrySet()) {
            BigInteger expectedValue = expected.getValue().getBytes();
            String tsetId = expected.getKey();
            assertThat(store.getUsedQuotaByTileSetId(tsetId), bytes(expectedValue));
        }
    }

    @Test
    public void testSetTruncated() throws Exception {
        String tileSetId = testTileSet.getId();
        TilePage page = new TilePage(tileSetId, 0, 0, 2);

        PageStatsPayload payload = new PageStatsPayload(page);
        int numHits = 100;
        payload.setNumHits(numHits);
        payload.setNumTiles(5);

        store.addToQuotaAndTileCounts(
                testTileSet, new Quota(1, StorageUnit.MiB), Collections.singleton(payload));
        List<PageStats> stats = store.addHitsAndSetAccesTime(Collections.singleton(payload)).get();
        assertThat(stats, contains(hasProperty("fillFactor", greaterThan(0f))));
        PageStats pageStats = store.setTruncated(page);
        assertThat(pageStats, hasProperty("fillFactor", closeTo(0f, 0f)));
    }

    @Test
    public void testCreatesVersion() throws Exception {
        File versionFile = new File(targetDir.getRoot(), "diskquota_page_store/version.txt");
        assertThat(versionFile, FileMatchers.exists());
    }

    static Matcher<Float> closeTo(float f, float epsilon) {
        return new BaseMatcher<Float>() {
            Matcher<Double> doubleMatcher = Matchers.closeTo(f, epsilon);

            @Override
            @SuppressWarnings("SelfAssignment") // this actually changes its fp representation
            public boolean matches(Object item) {
                if (item instanceof Float) {
                    item = (double) (float) item;
                }
                return doubleMatcher.matches(item);
            }

            @Override
            public void describeTo(Description description) {
                doubleMatcher.describeTo(description);
            }
        };
    }

    static Matcher<Quota> bytes(BigInteger bytes) {
        return hasProperty("bytes", equalTo(bytes));
    }

    static Matcher<Quota> bytes(long bytes) {
        return hasProperty("bytes", equalTo(BigInteger.valueOf(bytes)));
    }

    static Matcher<Quota> quotaEmpty() {
        return bytes(BigInteger.ZERO);
    }
}
