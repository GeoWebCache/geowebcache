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
 * @author Gabriel Roldan (OpenGeo) 2010
 */
package org.geowebcache.diskquota;

import static org.geowebcache.diskquota.ExpirationPolicy.LFU;
import static org.geowebcache.diskquota.ExpirationPolicy.LRU;
import static org.geowebcache.diskquota.storage.StorageUnit.B;
import static org.geowebcache.diskquota.storage.StorageUnit.GiB;
import static org.geowebcache.diskquota.storage.StorageUnit.MiB;

import jakarta.servlet.ServletContext;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.easymock.EasyMock;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.diskquota.storage.LayerQuota;
import org.geowebcache.diskquota.storage.Quota;
import org.geowebcache.diskquota.storage.StorageUnit;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.util.ApplicationContextProvider;
import org.geowebcache.util.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.web.context.WebApplicationContext;

public class ConfigLoaderTest {

    private File cacheDir;

    DefaultStorageFinder storageFinder;

    ApplicationContextProvider contextProvider;

    TileLayerDispatcher tld;

    private ConfigLoader loader;

    @Before
    public void setUp() throws Exception {
        cacheDir = new File("target" + File.separator + getClass().getSimpleName());
        if (!cacheDir.getParentFile().exists()) {
            throw new IllegalStateException(cacheDir.getParentFile().getAbsolutePath() + " does not exist");
        }
        FileUtils.rmFileCacheDir(cacheDir, null);
        cacheDir.mkdirs();
        // copy configuration file to cache directory
        try (InputStream in = getClass().getResourceAsStream("/geowebcache-diskquota.xml");
                FileOutputStream out = new FileOutputStream(new File(cacheDir, "geowebcache-diskquota.xml"))) {
            int c;
            while ((c = in.read()) != -1) {
                out.write(c);
            }
        }
        storageFinder = EasyMock.createMock(DefaultStorageFinder.class);
        EasyMock.expect(storageFinder.getDefaultPath())
                .andReturn(cacheDir.getAbsolutePath())
                .anyTimes();
        EasyMock.replay(storageFinder);

        ServletContext mockServletCtx = EasyMock.createMock(ServletContext.class);
        String tmpPath = System.getProperty("java.io.tmpdir");
        EasyMock.expect(mockServletCtx.getRealPath(EasyMock.eq("")))
                .andReturn(tmpPath)
                .anyTimes();
        EasyMock.replay(mockServletCtx);

        WebApplicationContext appContext = EasyMock.createMock(WebApplicationContext.class);
        EasyMock.expect(appContext.getServletContext())
                .andReturn(mockServletCtx)
                .anyTimes();
        EasyMock.replay(appContext);

        contextProvider = new ApplicationContextProvider();
        contextProvider.setApplicationContext(appContext);

        tld = EasyMock.createMock(TileLayerDispatcher.class);
        TileLayer toppStates = createMockLayer("topp:states");
        TileLayer raster = createMockLayer("raster test layer");
        EasyMock.expect(tld.getTileLayer(EasyMock.eq("topp:states")))
                .andReturn(toppStates)
                .anyTimes();
        EasyMock.expect(tld.getTileLayer(EasyMock.eq("raster test layer")))
                .andReturn(raster)
                .anyTimes();

        List<TileLayer> tileLayers = new ArrayList<>();
        tileLayers.add(toppStates);
        tileLayers.add(raster);
        EasyMock.expect(tld.getLayerList()).andReturn(tileLayers).anyTimes();
        EasyMock.replay(tld);

        loader = new ConfigLoader(storageFinder, contextProvider, tld);
    }

    @After
    public void tearDown() throws Exception {
        if (cacheDir != null) {
            FileUtils.rmFileCacheDir(cacheDir, null);
        }
    }

    private TileLayer createMockLayer(String name) {
        TileLayer layer = EasyMock.createMock(TileLayer.class);
        EasyMock.expect(layer.getName()).andReturn(name).anyTimes();
        EasyMock.replay(layer);
        return layer;
    }

    @Test
    public void testLoadConfig() throws ConfigurationException, IOException {
        DiskQuotaConfig config = loader.loadConfig();
        Assert.assertNotNull(config);
        Assert.assertFalse(config.isEnabled());
        Assert.assertEquals(10, config.getCacheCleanUpFrequency().intValue());
        Assert.assertEquals(TimeUnit.SECONDS, config.getCacheCleanUpUnits());
        Assert.assertEquals(3, config.getMaxConcurrentCleanUps().intValue());

        Assert.assertEquals(LFU, config.getGlobalExpirationPolicyName());
        Assert.assertNotNull(config.getGlobalExpirationPolicyName());

        Assert.assertNotNull(config.getGlobalQuota());
        Assert.assertEquals(
                GiB.convertTo(200, B).longValue(),
                config.getGlobalQuota().getBytes().longValue());

        Assert.assertNotNull(config.getLayerQuotas());
        Assert.assertEquals(2, config.getLayerQuotas().size());

        LayerQuota states = config.layerQuota("topp:states");
        Assert.assertNotNull(states);
        Assert.assertEquals(LFU, states.getExpirationPolicyName());
        Assert.assertEquals(
                MiB.convertTo(100, B).longValue(), states.getQuota().getBytes().longValue());

        LayerQuota raster = config.layerQuota("raster test layer");
        Assert.assertNotNull(raster);
    }

    @Test
    public void testSaveConfig() throws ConfigurationException, IOException {
        DiskQuotaConfig config = new DiskQuotaConfig();

        LayerQuota lq = new LayerQuota("topp:states", LRU, new Quota(10, StorageUnit.MiB));
        config.addLayerQuota(lq);

        File configFile = new File(cacheDir, "geowebcache-diskquota.xml");
        if (configFile.exists()) {
            configFile.delete();
        }
        loader.saveConfig(config);
        Assert.assertTrue(configFile.exists());
    }

    @Test
    public void testGetRootCacheDir() throws Exception {
        Assert.assertEquals(cacheDir.getAbsolutePath(), loader.getRootCacheDir().getAbsolutePath());
    }
}
