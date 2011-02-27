package org.geowebcache.diskquota.storage;

import java.io.File;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.Future;

import junit.framework.TestCase;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.mutable.MutableInt;
import org.easymock.classextension.EasyMock;
import org.geowebcache.config.Configuration;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.config.XMLConfigurationTest;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.storage.DefaultStorageFinder;

public class BDBQuotaStoreTest extends TestCase {

    private BDBQuotaStore store;

    private TilePageCalculator tilePageCalculator;

    private TileSet testTileSet;

    TileLayerDispatcher layerDispatcher;

    DefaultStorageFinder cacheDirFinder;

    File targetDir;

    @Override
    public void setUp() throws Exception {
        targetDir = new File("target", "mockStore" + Math.random());
        FileUtils.deleteDirectory(targetDir);
        targetDir.mkdirs();

        cacheDirFinder = EasyMock.createMock(DefaultStorageFinder.class);
        EasyMock.expect(cacheDirFinder.getDefaultPath()).andReturn(targetDir.getAbsolutePath())
                .anyTimes();
        EasyMock.replay(cacheDirFinder);

        XMLConfiguration xmlConfig = loadXMLConfig();
        LinkedList<Configuration> configList = new LinkedList<Configuration>();
        configList.add(xmlConfig);

        layerDispatcher = new TileLayerDispatcher(new GridSetBroker(false, false), configList);

        tilePageCalculator = new TilePageCalculator(layerDispatcher);

        store = new BDBQuotaStore(cacheDirFinder, tilePageCalculator);
        store.afterPropertiesSet();
        testTileSet = tilePageCalculator.getTileSetsFor("topp:states2").iterator().next();
    }

    public void tearDown() {
        try {
            store.destroy();
            FileUtils.deleteDirectory(targetDir);
        } catch (Exception e) {
        }
    }

    private XMLConfiguration loadXMLConfig() {
        InputStream is = XMLConfiguration.class
                .getResourceAsStream(XMLConfigurationTest.LATEST_FILENAME);
        XMLConfiguration xmlConfig = null;
        try {
            xmlConfig = new XMLConfiguration(is);
        } catch (Exception e) {
            // Do nothing
        }

        return xmlConfig;
    }

    public void testInitialization() throws Exception {
        Set<TileSet> tileSets = store.getTileSets();
        assertNotNull(tileSets);
        assertEquals(10, tileSets.size());

        TileSet tileSet = new TileSet("topp:states", "GoogleMapsCompatible", "image/png", null);
        assertTrue(tileSets.contains(tileSet));

        tileSet = new TileSet("topp:states", "GoogleMapsCompatible", "image/jpeg", null);
        assertTrue(tileSets.contains(tileSet));

        tileSet = new TileSet("topp:states", "GoogleMapsCompatible", "image/gif", null);
        assertTrue(tileSets.contains(tileSet));

        tileSet = new TileSet("topp:states", "GoogleMapsCompatible",
                "application/vnd.google-earth.kml+xml", null);
        assertTrue(tileSets.contains(tileSet));

        tileSet = new TileSet("topp:states", "GlobalCRS84Geometric", "image/png", null);
        assertTrue(tileSets.contains(tileSet));

        tileSet = new TileSet("topp:states", "GlobalCRS84Geometric", "image/jpeg", null);
        assertTrue(tileSets.contains(tileSet));

        tileSet = new TileSet("topp:states", "GlobalCRS84Geometric", "image/gif", null);
        assertTrue(tileSets.contains(tileSet));

        tileSet = new TileSet("topp:states", "GlobalCRS84Geometric",
                "application/vnd.google-earth.kml+xml", null);
        assertTrue(tileSets.contains(tileSet));

        tileSet = new TileSet("topp:states2", "EPSG:2163", "image/png", null);
        assertTrue(tileSets.contains(tileSet));

        tileSet = new TileSet("topp:states2", "EPSG:2163", "image/jpeg", null);
        assertTrue(tileSets.contains(tileSet));

        // remove one layer from the dispatcher
        layerDispatcher.remove("topp:states");
        // and make sure at the next startup the store catches up (note this behaviour is just a
        // startup consistency check in case the store got out of sync for some reason. On normal
        // situations the store should have been notified through store.deleteLayer(layerName) if
        // the layer was removed programmatically through StorageBroker.deleteLayer
        store.destroy();
        store.startUp();

        tileSets = store.getTileSets();
        assertNotNull(tileSets);
        assertEquals(2, tileSets.size());
        tileSet = new TileSet("topp:states2", "EPSG:2163", "image/png", null);
        assertTrue(tileSets.contains(tileSet));

        tileSet = new TileSet("topp:states2", "EPSG:2163", "image/jpeg", null);
        assertTrue(tileSets.contains(tileSet));
    }

    /**
     * Combined test for {@link BDBQuotaStore#addToQuotaAndTileCounts(TileSet, Quota, Collection)}
     * and {@link BDBQuotaStore#addHitsAndSetAccesTime(Collection)}
     * 
     * @throws Exception
     */
    public void testPageStatsGathering() throws Exception {
        final MutableInt baseTimeMinutes = new MutableInt(10000);

        SystemUtils.set(new SystemUtils() {
            @Override
            public int currentTimeMinutes() {
                return baseTimeMinutes.intValue();
            }

        });

        TileSet tileSet = testTileSet;

        TilePage page = new TilePage(tileSet.getId(), 0, 0, (byte) 0);

        PageStatsPayload payload = new PageStatsPayload(page);
        int numHits = 100;
        payload.setLastAccessTime(baseTimeMinutes.intValue() - 100);
        payload.setNumHits(numHits);
        payload.setNumTiles(1);

        store.addToQuotaAndTileCounts(tileSet, new Quota(1, StorageUnit.MiB),
                Collections.singleton(payload));

        Future<PageStats> result = store.addHitsAndSetAccesTime(Collections.singleton(payload));
        PageStats stats = result.get();
        float fillFactor = stats.getFillFactor();
        assertEquals(1.0f, fillFactor, 1e-6);

        int lastAccessTimeMinutes = stats.getLastAccessTimeMinutes();
        assertEquals(baseTimeMinutes.intValue(), lastAccessTimeMinutes);

        float frequencyOfUsePerMinute = stats.getFrequencyOfUsePerMinute();
        assertEquals(100f, frequencyOfUsePerMinute);

        // now 1 minute later...
        baseTimeMinutes.setValue(baseTimeMinutes.intValue() + 2);

        numHits = 10;
        payload.setLastAccessTime(baseTimeMinutes.intValue() - 100);
        payload.setNumHits(numHits);

        result = store.addHitsAndSetAccesTime(Collections.singleton(payload));
        stats = result.get();

        lastAccessTimeMinutes = stats.getLastAccessTimeMinutes();
        assertEquals(baseTimeMinutes.intValue(), lastAccessTimeMinutes);

        frequencyOfUsePerMinute = stats.getFrequencyOfUsePerMinute();
        float expected = 52.5f;// the 100 previous + the 10 added now / the 2 minutes that elapsed,
                               // all divided by 2
        assertEquals(expected, frequencyOfUsePerMinute, 1e-6f);
    }

    public void testGetGloballyUsedQuota() throws InterruptedException {
        Quota usedQuota = store.getGloballyUsedQuota();
        assertNotNull(usedQuota);
        assertEquals(0, usedQuota.getBytes().intValue());

        String layerName = tilePageCalculator.getLayerNames().iterator().next();
        TileSet tileSet = tilePageCalculator.getTileSetsFor(layerName).iterator().next();

        final String tileSetId = tileSet.getId();

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

    public void testDeleteLayer() throws InterruptedException {
        String layerName = tilePageCalculator.getLayerNames().iterator().next();
        // make sure the layer is there and has stuff
        Quota usedQuota = store.getUsedQuotaByLayerName(layerName);
        assertNotNull(usedQuota);

        TileSet tileSet = tilePageCalculator.getTileSetsFor(layerName).iterator().next();
        TilePage page = new TilePage(tileSet.getId(), 0, 0, (byte) 0);
        store.addHitsAndSetAccesTime(Collections.singleton(new PageStatsPayload(page)));
        // page.setNumHits(10);
        // page.setLastAccessTime(System.currentTimeMillis());
        // store.addHitsAndSetAccesTime(page);

        assertNotNull(store.getTileSetById(tileSet.getId()));

        store.deleteLayer(layerName);

        // cascade deleted?
        assertNull(store.getLeastRecentlyUsedPage(Collections.singleton(layerName)));
        try {
            store.getUsedQuotaByLayerName(layerName);
            fail("Expected IAE");
        } catch (RuntimeException e) {
            assertTrue(true);
        }
    }

    public void testGetLeastFrequentlyUsedPage() {
        System.err.println(getName() + " Not yet implemented");
    }

    public void getLeastRecentlyUsedPage() {
        System.err.println(getName() + " Not yet implemented");
    }

    public void testGetTileSetById() {
        System.err.println(getName() + " Not yet implemented");
    }

    public void testGetTilesForPage() {
        System.err.println(getName() + " Not yet implemented");
    }

    public void testGetUsedQuotaByLayerName() {
        System.err.println(getName() + " Not yet implemented");
    }

    public void testGetUsedQuotaByTileSetId() {
        System.err.println(getName() + " Not yet implemented");
    }

    public void testSetTruncated() {
        System.err.println(getName() + " Not yet implemented");
    }

}
