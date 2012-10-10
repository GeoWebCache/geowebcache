package org.geowebcache.diskquota.jdbc;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import junit.framework.TestCase;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.easymock.classextension.EasyMock;
import org.geowebcache.config.Configuration;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.config.XMLConfigurationBackwardsCompatibilityTest;
import org.geowebcache.diskquota.DiskQuotaMonitor;
import org.geowebcache.diskquota.QuotaStore;
import org.geowebcache.diskquota.storage.PageStats;
import org.geowebcache.diskquota.storage.PageStatsPayload;
import org.geowebcache.diskquota.storage.Quota;
import org.geowebcache.diskquota.storage.StorageUnit;
import org.geowebcache.diskquota.storage.SystemUtils;
import org.geowebcache.diskquota.storage.TilePage;
import org.geowebcache.diskquota.storage.TilePageCalculator;
import org.geowebcache.diskquota.storage.TileSet;
import org.geowebcache.diskquota.storage.TileSetVisitor;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.storage.DefaultStorageFinder;

public class JDBCQuotaStoreTest extends TestCase {

    JDBCQuotaStore store;

    File targetDir;

    DefaultStorageFinder cacheDirFinder;

    TileLayerDispatcher layerDispatcher;

    TilePageCalculator tilePageCalculator;

    private BasicDataSource dataSource;

    private TileSet testTileSet;

    @Override
    protected void setUp() throws Exception {
        // prepare a mock target directory for tiles
        targetDir = new File("target", "mockStore");
        FileUtils.deleteDirectory(targetDir);
        targetDir.mkdirs();

        cacheDirFinder = EasyMock.createMock(DefaultStorageFinder.class);
        EasyMock.expect(cacheDirFinder.getDefaultPath()).andReturn(targetDir.getAbsolutePath())
                .anyTimes();
        EasyMock.expect(
                cacheDirFinder.findEnvVar(EasyMock.eq(DiskQuotaMonitor.GWC_DISKQUOTA_DISABLED)))
                .andReturn(null).anyTimes();
        EasyMock.replay(cacheDirFinder);

        XMLConfiguration xmlConfig = loadXMLConfig();
        LinkedList<Configuration> configList = new LinkedList<Configuration>();
        configList.add(xmlConfig);

        layerDispatcher = new TileLayerDispatcher(new GridSetBroker(true, true), configList);

        tilePageCalculator = new TilePageCalculator(layerDispatcher);

        // prepare a connection pool for tests against a H2 database
        dataSource = setupDataSource();
        SQLDialect dialect = getDialect();

        // setup the quota store
        store = new JDBCQuotaStore(cacheDirFinder, tilePageCalculator);
        store.setDataSource(dataSource);
        store.setDialect(dialect);

        // finally initialize the store
        store.initialize();
        
        testTileSet = tilePageCalculator.getTileSetsFor("topp:states2").iterator().next();
    }
    
    @Override
    protected void tearDown() throws Exception {
        store.close();
    }

    private SQLDialect getDialect() {
        return new H2Dialect();
    }

    private XMLConfiguration loadXMLConfig() {
        InputStream is = null;
        XMLConfiguration xmlConfig = null;
        try {
            is = XMLConfiguration.class
                    .getResourceAsStream(XMLConfigurationBackwardsCompatibilityTest.LATEST_FILENAME);
            xmlConfig = new XMLConfiguration(is);
        } catch (Exception e) {
            // Do nothing
        } finally {
            IOUtils.closeQuietly(is);
        }

        return xmlConfig;
    }

    private BasicDataSource setupDataSource() throws IOException {
        // cleanup previous eventual db
        File[] files = new File("./target").listFiles(new FilenameFilter() {
            
            public boolean accept(File dir, String name) {
                return name.startsWith("quota-h2");
            }
        });
        for (File file : files) {
           assertTrue(file.delete()); 
        }
        
        BasicDataSource dataSource = new BasicDataSource();

        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:./target/quota-h2");
        dataSource.setUsername("sa");
        dataSource.setPoolPreparedStatements(true);
        dataSource.setAccessToUnderlyingConnectionAllowed(true);
        dataSource.setMinIdle(1);
        dataSource.setMaxActive(4);
        // if we cannot get a connection within 5 seconds give up
        dataSource.setMaxWait(5000);
        return dataSource;
    }
    
    public void testTableSetup() throws Exception {
        // on initialization we should have the tilesets setup properly
        
        // check the global quota
        Quota global = store.getGloballyUsedQuota();
        assertNotNull(global);
        assertEquals(JDBCQuotaStore.GLOBAL_QUOTA_NAME, global.getTileSetId());
        assertEquals(0, global.getBytes().longValue());
        
        Set<TileSet> tileSets = store.getTileSets();
        // two formats for topp:states2, four formats and two tilesets for topp:states
        assertNotNull(tileSets);
        assertEquals(10, tileSets.size());

        // check every possibility
        TileSet tileSet = new TileSet("topp:states", "EPSG:900913", "image/png", null);
        assertTrue(tileSets.contains(tileSet));
        assertQuotaZero(tileSet);

        tileSet = new TileSet("topp:states", "EPSG:900913", "image/jpeg", null);
        assertTrue(tileSets.contains(tileSet));
        assertQuotaZero(tileSet);

        tileSet = new TileSet("topp:states", "EPSG:900913", "image/gif", null);
        assertTrue(tileSets.contains(tileSet));
        assertQuotaZero(tileSet);

        tileSet = new TileSet("topp:states", "EPSG:900913", "application/vnd.google-earth.kml+xml",
                null);
        assertTrue(tileSets.contains(tileSet));
        assertQuotaZero(tileSet);

        tileSet = new TileSet("topp:states", "EPSG:4326", "image/png", null);
        assertTrue(tileSets.contains(tileSet));
        assertQuotaZero(tileSet);

        tileSet = new TileSet("topp:states", "EPSG:4326", "image/jpeg", null);
        assertTrue(tileSets.contains(tileSet));
        assertQuotaZero(tileSet);

        tileSet = new TileSet("topp:states", "EPSG:4326", "image/gif", null);
        assertTrue(tileSets.contains(tileSet));
        assertQuotaZero(tileSet);

        tileSet = new TileSet("topp:states", "EPSG:4326", "application/vnd.google-earth.kml+xml",
                null);
        assertTrue(tileSets.contains(tileSet));
        assertQuotaZero(tileSet);

        tileSet = new TileSet("topp:states2", "EPSG:2163", "image/png", null);
        assertTrue(tileSets.contains(tileSet));
        assertQuotaZero(tileSet);

        tileSet = new TileSet("topp:states2", "EPSG:2163", "image/jpeg", null);
        assertTrue(tileSets.contains(tileSet));
        assertQuotaZero(tileSet);
        
        // check the layer wide quotas
        assertQuotaZero("topp:states");
        assertQuotaZero("topp:states2");

        // remove one layer from the dispatcher
        Configuration configuration = layerDispatcher.removeLayer("topp:states");
        configuration.save();
        // and make sure at the next startup the store catches up (note this behaviour is just a
        // startup consistency check in case the store got out of sync for some reason. On normal
        // situations the store should have been notified through store.deleteLayer(layerName) if
        // the layer was removed programmatically through StorageBroker.deleteLayer
        store.close();
        store.setDataSource(setupDataSource());
        store.initialize();

        tileSets = store.getTileSets();
        assertNotNull(tileSets);
        assertEquals(2, tileSets.size());
        tileSet = new TileSet("topp:states2", "EPSG:2163", "image/png", null);
        assertTrue(tileSets.contains(tileSet));
        assertQuotaZero(tileSet);

        tileSet = new TileSet("topp:states2", "EPSG:2163", "image/jpeg", null);
        assertTrue(tileSets.contains(tileSet));
        assertQuotaZero(tileSet);
    }
    
    public void testRenameLayer() throws InterruptedException {
        assertEquals(8, countTileSetsByLayerName("topp:states"));
        store.renameLayer("topp:states", "states_renamed");
        assertEquals(0, countTileSetsByLayerName("topp:states"));
        assertEquals(8, countTileSetsByLayerName("states_renamed"));
    }
    
    public void testRenameLayer2() throws InterruptedException {
        final String oldLayerName = tilePageCalculator.getLayerNames().iterator().next();
        final String newLayerName = "renamed_layer";

        // make sure the layer is there and has stuff
        Quota usedQuota = store.getUsedQuotaByLayerName(oldLayerName);
        assertNotNull(usedQuota);

        TileSet tileSet = tilePageCalculator.getTileSetsFor(oldLayerName).iterator().next();
        TilePage page = new TilePage(tileSet.getId(), 0, 0, (byte) 0);
        store.addHitsAndSetAccesTime(Collections.singleton(new PageStatsPayload(page)));
        store.addToQuotaAndTileCounts(tileSet, new Quota(BigInteger.valueOf(1024)),
                Collections.EMPTY_LIST);

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
    
    public void testDeleteGridSet() throws InterruptedException {
        assertEquals(8, countTileSetsByLayerName("topp:states"));
        store.deleteGridSubset("topp:states", "EPSG:900913");
        assertEquals(4, countTileSetsByLayerName("topp:states"));
    }
    
    public void testDeleteLayer() throws InterruptedException {
        String layerName = tilePageCalculator.getLayerNames().iterator().next();
        // make sure the layer is there and has stuff
        Quota usedQuota = store.getUsedQuotaByLayerName(layerName);
        assertNotNull(usedQuota);

        TileSet tileSet = tilePageCalculator.getTileSetsFor(layerName).iterator().next();
        TilePage page = new TilePage(tileSet.getId(), 0, 0, (byte) 0);
        store.addHitsAndSetAccesTime(Collections.singleton(new PageStatsPayload(page)));

        assertNotNull(store.getTileSetById(tileSet.getId()));

        store.deleteLayer(layerName);

        // cascade deleted?
        assertNull(store.getLeastRecentlyUsedPage(Collections.singleton(layerName)));
        usedQuota = store.getUsedQuotaByLayerName(layerName);
        assertNotNull(usedQuota);
        assertEquals(0L, usedQuota.getBytes().longValue());
    }

    
    public void testVisitor() throws Exception {
        Set<TileSet> tileSets1 = store.getTileSets();
        final Set<TileSet> tileSets2 = new HashSet<TileSet>();
        store.accept(new TileSetVisitor() {
            
            public void visit(TileSet tileSet, QuotaStore quotaStore) {
                tileSets2.add(tileSet);                
            }
        });
        assertEquals(tileSets1, tileSets2);
    }
    
    
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
    public void testGetUsedQuotaByLayerName() throws Exception {
        String layerName = "topp:states2";
        List<TileSet> tileSets;
        tileSets = new ArrayList<TileSet>(tilePageCalculator.getTileSetsFor(layerName));

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
    public void testGetUsedQuotaByTileSetId() throws Exception {
        String layerName = "topp:states2";
        List<TileSet> tileSets;
        tileSets = new ArrayList<TileSet>(tilePageCalculator.getTileSetsFor(layerName));

        Map<String, Quota> expectedById = new HashMap<String, Quota>();

        for (TileSet tset : tileSets) {
            Quota quotaDiff = new Quota(10D * Math.random(), StorageUnit.MiB);
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
    
    /**
     * Combined test for {@link BDBQuotaStore#addToQuotaAndTileCounts(TileSet, Quota, Collection)}
     * and {@link BDBQuotaStore#addHitsAndSetAccesTime(Collection)}
     * 
     * @throws Exception
     */
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

        store.addToQuotaAndTileCounts(tileSet, new Quota(1, StorageUnit.MiB),
                Collections.singleton(payload));

        Future<List<PageStats>> result = store.addHitsAndSetAccesTime(Collections
                .singleton(payload));
        List<PageStats> allStats = result.get();
        PageStats stats = allStats.get(0);
        float fillFactor = stats.getFillFactor();
        assertEquals(1.0f, fillFactor, 1e-6);

        int lastAccessTimeMinutes = stats.getLastAccessTimeMinutes();
        assertEquals(sysUtils.currentTimeMinutes(), lastAccessTimeMinutes);

        float frequencyOfUsePerMinute = stats.getFrequencyOfUsePerMinute();
        assertEquals(100f, frequencyOfUsePerMinute);

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
        float expected = 55.0f;// the 100 previous + the 10 added now / the 2 minutes that elapsed
        assertEquals(expected, frequencyOfUsePerMinute, 1e-6f);
    }
    
    public void testGetGloballyUsedQuota() throws InterruptedException {
        Quota usedQuota = store.getGloballyUsedQuota();
        assertNotNull(usedQuota);
        assertEquals(0, usedQuota.getBytes().intValue());

        String layerName = tilePageCalculator.getLayerNames().iterator().next();
        TileSet tileSet = tilePageCalculator.getTileSetsFor(layerName).iterator().next();

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
    
    public void testSetTruncated() throws Exception {
        String tileSetId = testTileSet.getId();
        TilePage page = new TilePage(tileSetId, 0, 0, 2);

        PageStatsPayload payload = new PageStatsPayload(page);
        int numHits = 100;
        payload.setNumHits(numHits);
        payload.setNumTiles(5);

        store.addToQuotaAndTileCounts(testTileSet, new Quota(1, StorageUnit.MiB),
                Collections.singleton(payload));
        List<PageStats> stats = store.addHitsAndSetAccesTime(Collections.singleton(payload)).get();
        assertTrue(stats.get(0).getFillFactor() > 0f);
        PageStats pageStats = store.setTruncated(page);
        assertEquals(0f, pageStats.getFillFactor());
    }
    
    public void testGetLeastFrequentlyUsedPage() throws Exception {
        final String layerName = testTileSet.getLayerName();
        Set<String> layerNames = Collections.singleton(layerName);

        TilePage lfuPage;
        lfuPage = store.getLeastFrequentlyUsedPage(layerNames);
        assertNull(lfuPage);

        TilePage page1 = new TilePage(testTileSet.getId(), 0, 1, 2);
        TilePage page2 = new TilePage(testTileSet.getId(), 1, 1, 2);

        PageStatsPayload payload1 = new PageStatsPayload(page1);
        PageStatsPayload payload2 = new PageStatsPayload(page2);

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

    public void testGetLeastRecentlyUsedPage() throws Exception {
        MockSystemUtils mockSystemUtils = new MockSystemUtils();
        mockSystemUtils.setCurrentTimeMinutes(1000);
        mockSystemUtils.setCurrentTimeMillis(mockSystemUtils.currentTimeMinutes() * 60 * 1000);
        SystemUtils.set(mockSystemUtils);

        final String layerName = testTileSet.getLayerName();
        Set<String> layerNames = Collections.singleton(layerName);

        TilePage leastRecentlyUsedPage;
        leastRecentlyUsedPage = store.getLeastRecentlyUsedPage(layerNames);
        assertNull(leastRecentlyUsedPage);

        TilePage page1 = new TilePage(testTileSet.getId(), 0, 1, 2);
        TilePage page2 = new TilePage(testTileSet.getId(), 1, 1, 2);

        PageStatsPayload payload1 = new PageStatsPayload(page1);
        PageStatsPayload payload2 = new PageStatsPayload(page2);

        payload1.setLastAccessTime(mockSystemUtils.currentTimeMillis() + 1 * 60 * 1000);
        payload2.setLastAccessTime(mockSystemUtils.currentTimeMillis() + 2 * 60 * 1000);

        Collection<PageStatsPayload> statsUpdates = Arrays.asList(payload1, payload2);
        store.addHitsAndSetAccesTime(statsUpdates).get();

        leastRecentlyUsedPage = store.getLeastRecentlyUsedPage(layerNames);
        assertEquals(page1, leastRecentlyUsedPage);

        payload1.setLastAccessTime(mockSystemUtils.currentTimeMillis() + 10 * 60 * 1000);
        store.addHitsAndSetAccesTime(statsUpdates).get();

        leastRecentlyUsedPage = store.getLeastRecentlyUsedPage(layerNames);
        assertEquals(page2, leastRecentlyUsedPage);
    }
    
    public void testGetTilesForPage() throws Exception {
        TilePage page = new TilePage(testTileSet.getId(), 0, 0, 0);

        long[][] expected = tilePageCalculator.toGridCoverage(testTileSet, page);
        long[][] tilesForPage = store.getTilesForPage(page);

        assertTrue(Arrays.equals(expected[0], tilesForPage[0]));

        page = new TilePage(testTileSet.getId(), 0, 0, 1);

        expected = tilePageCalculator.toGridCoverage(testTileSet, page);
        tilesForPage = store.getTilesForPage(page);

        assertTrue(Arrays.equals(expected[1], tilesForPage[1]));
    }

    private int countTileSetsByLayerName(String layerName) {
        int count = 0;
        for(TileSet ts : store.getTileSets()) {
            if(layerName.equals(ts.getLayerName())) {
                count++;
            }
        }
        
        return count;
    }

    /**
     * Asserts the quota used by this tile set is null
     * @param tileSet
     */
    private void assertQuotaZero(TileSet tileSet) {
        Quota quota = store.getUsedQuotaByTileSetId(tileSet.getId());
        assertNotNull(quota);
        assertEquals(0, quota.getBytes().longValue());
    }
    
    /**
     * Asserts the quota used by this tile set is null
     * @param tileSet
     * @throws InterruptedException 
     */
    private void assertQuotaZero(String layerName) throws InterruptedException {
        Quota quota = store.getUsedQuotaByLayerName(layerName);
        assertNotNull(quota);
        assertEquals(0, quota.getBytes().longValue());
    }
}
