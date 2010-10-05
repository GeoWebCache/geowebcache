package org.geowebcache.diskquota;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContext;

import junit.framework.TestCase;

import org.easymock.classextension.EasyMock;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.diskquota.lfu.ExpirationPolicyLFU;
import org.geowebcache.diskquota.lru.ExpirationPolicyLRU;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.storage.StorageException;
import org.geowebcache.util.ApplicationContextProvider;
import org.geowebcache.util.FileUtils;
import org.springframework.web.context.WebApplicationContext;

public class ConfigLoaderTest extends TestCase {

    private File cacheDir;

    DefaultStorageFinder storageFinder;

    ApplicationContextProvider contextProvider;

    TileLayerDispatcher tld;

    private ConfigLoader loader;

    @Override
    protected void setUp() throws Exception {
        cacheDir = new File("target" + File.separator + getClass().getSimpleName());
        if (!cacheDir.getParentFile().exists()) {
            throw new IllegalStateException(cacheDir.getParentFile().getAbsolutePath()
                    + " does not exist");
        }
        FileUtils.rmFileCacheDir(cacheDir, null);
        cacheDir.mkdirs();

        storageFinder = EasyMock.createMock(DefaultStorageFinder.class);
        EasyMock.expect(storageFinder.getDefaultPath()).andReturn(cacheDir.getAbsolutePath())
                .anyTimes();
        EasyMock.replay(storageFinder);

        ServletContext mockServletCtx = EasyMock.createMock(ServletContext.class);
        String tmpPath = System.getProperty("java.io.tmpdir");
        EasyMock.expect(mockServletCtx.getRealPath(EasyMock.eq(""))).andReturn(tmpPath).anyTimes();
        EasyMock.replay(mockServletCtx);

        WebApplicationContext appContext = EasyMock.createMock(WebApplicationContext.class);
        EasyMock.expect(appContext.getServletContext()).andReturn(mockServletCtx).anyTimes();
        Map mockPolicies = createMockPolicies();
        EasyMock.expect(appContext.getBeansOfType(EasyMock.eq(ExpirationPolicy.class)))
                .andReturn(mockPolicies).anyTimes();
        EasyMock.replay(appContext);

        contextProvider = new ApplicationContextProvider();
        contextProvider.setApplicationContext(appContext);

        tld = EasyMock.createMock(TileLayerDispatcher.class);
        TileLayer toppStates = createMockLayer("topp:states");
        TileLayer raster = createMockLayer("raster test layer");
        EasyMock.expect(tld.getTileLayer(EasyMock.eq("topp:states"))).andReturn(toppStates)
                .anyTimes();
        EasyMock.expect(tld.getTileLayer(EasyMock.eq("raster test layer"))).andReturn(raster)
                .anyTimes();
        EasyMock.replay(tld);

        loader = new ConfigLoader(storageFinder, contextProvider, tld);
    }

    @Override
    protected void tearDown() throws Exception {
        if (cacheDir != null) {
            FileUtils.rmFileCacheDir(cacheDir, null);
        }
    }

    @SuppressWarnings("unchecked")
    private Map createMockPolicies() {
        Map map = new HashMap();
        map.put("lru", new ExpirationPolicyLRU(null, null));
        map.put("lfu", new ExpirationPolicyLFU(null, null));
        return map;
    }

    private TileLayer createMockLayer(String name) {
        TileLayer layer = EasyMock.createMock(TileLayer.class);
        EasyMock.replay(layer);
        return layer;
    }

    public void testLoadConfig() throws ConfigurationException, IOException {
        DiskQuotaConfig config = loader.loadConfig();
        assertNotNull(config);
        assertEquals(4096, config.getDiskBlockSize());
        assertEquals(10, config.getCacheCleanUpFrequency());
        assertEquals(TimeUnit.SECONDS, config.getCacheCleanUpUnits());
        assertEquals(3, config.getMaxConcurrentCleanUps());
        
        assertEquals("LFU", config.getGlobalExpirationPolicyName());
        assertNotNull(config.getGlobalExpirationPolicy());

        assertNotNull(config.getGlobalQuota());
        assertEquals(100, config.getGlobalQuota().getValue().longValue());
        assertEquals(StorageUnit.GiB, config.getGlobalQuota().getUnits());
        
        assertNotNull(config.getLayerQuotas());
        assertEquals(2, config.getLayerQuotas().size());

        LayerQuota states = config.getLayerQuota("topp:states");
        assertNotNull(states);
        assertEquals("LFU", states.getExpirationPolicyName());
        assertEquals(0, states.getUsedQuota().getValue().longValue());
        assertEquals(10, states.getQuota().getValue().longValue());
        assertEquals(StorageUnit.MiB, states.getQuota().getUnits());

        LayerQuota raster = config.getLayerQuota("raster test layer");
        assertNotNull(raster);
        assertEquals(27, raster.getUsedQuota().getValue().longValue());
        assertEquals(StorageUnit.GiB, raster.getUsedQuota().getUnits());
    }

    public void testSaveConfig() throws ConfigurationException, IOException {
        DiskQuotaConfig config = new DiskQuotaConfig();
        List<LayerQuota> quotas = new ArrayList<LayerQuota>();
        LayerQuota lq = new LayerQuota("topp:states", "LRU");
        lq.getQuota().setValue(10);
        lq.getQuota().setUnits(StorageUnit.MiB);
        lq.getUsedQuota().setValue(100);
        lq.getUsedQuota().setUnits(StorageUnit.KiB);
        quotas.add(lq);
        config.setLayerQuotas(quotas);

        File configFile = new File(cacheDir, "geowebcache-diskquota.xml");
        assertFalse(configFile.exists());

        loader.saveConfig(config);
        assertTrue(configFile.exists());

        loader = new ConfigLoader(storageFinder, contextProvider, tld);
        DiskQuotaConfig loadConfig = loader.loadConfig();
        assertNotNull(loadConfig);
    }

    public void testFindExpirationPolicy() {
        assertNotNull(loader.findExpirationPolicy("LRU"));
        assertNotNull(loader.findExpirationPolicy("LFU"));
        try {
            loader.findExpirationPolicy("NonRegistered");
            fail("Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            assertTrue(true);
        }
    }

    public void testGetRootCacheDir() throws StorageException {
        assertEquals(cacheDir.getAbsolutePath(), loader.getRootCacheDir().getAbsolutePath());
    }

}
