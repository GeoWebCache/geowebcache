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

import java.util.concurrent.TimeUnit;
import org.geowebcache.diskquota.storage.LayerQuota;
import org.geowebcache.diskquota.storage.Quota;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DiskQuotaConfigTest {

    private DiskQuotaConfig config;

    @Before
    public void setUp() throws Exception {
        config = new DiskQuotaConfig();
        config.setDefaults();
    }

    @Test
    public void testDiskQuotaConfig() {
        Assert.assertEquals(
                DiskQuotaConfig.DEFAULT_CLEANUP_FREQUENCY,
                config.getCacheCleanUpFrequency().intValue());
        Assert.assertEquals(
                DiskQuotaConfig.DEFAULT_MAX_CONCURRENT_CLEANUPS,
                config.getMaxConcurrentCleanUps().intValue());
        Assert.assertEquals(DiskQuotaConfig.DEFAULT_CLEANUP_UNITS, config.getCacheCleanUpUnits());
    }

    @Test
    public void testSetCacheCleanUpFrequency() {
        try {
            config.setCacheCleanUpFrequency(-1);
            Assert.fail("Expected IAE");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(true);
        }

        config.setCacheCleanUpFrequency(10);
        Assert.assertEquals(10, config.getCacheCleanUpFrequency().intValue());
    }

    @Test
    public void testSetCacheCleanUpUnits() {
        try {
            config.setCacheCleanUpUnits(null);
            Assert.fail("Expected IAE");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(true);
        }

        config.setCacheCleanUpUnits(TimeUnit.MILLISECONDS);
        Assert.assertEquals(TimeUnit.MILLISECONDS, config.getCacheCleanUpUnits());
    }

    @Test
    public void testSetLayerQuotas() {
        // config.setLayerQuotas(null);
        Assert.assertNull(config.getLayerQuotas());

        try {
            config.addLayerQuota(new LayerQuota("layer", ExpirationPolicy.LRU));
            Assert.fail("Expected IAE");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(true);
        }
        LayerQuota lq = new LayerQuota("layer", ExpirationPolicy.LFU, new Quota());
        config.addLayerQuota(lq);
        Assert.assertNotNull(config.layerQuota("layer"));
    }

    @Test
    public void testRemove() {
        LayerQuota lq = new LayerQuota("layer", ExpirationPolicy.LFU, new Quota());
        config.addLayerQuota(lq);
        config.remove(lq);
        Assert.assertNull(config.layerQuota("layer"));
    }

    @Test
    public void testSetMaxConcurrentCleanUps() {
        try {
            config.setMaxConcurrentCleanUps(-1);
            Assert.fail("Expected IAE");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(true);
        }
        try {
            config.setMaxConcurrentCleanUps(0);
            Assert.fail("Expected IAE");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(true);
        }
        config.setMaxConcurrentCleanUps(10);
        Assert.assertEquals(10, config.getMaxConcurrentCleanUps().intValue());
    }
}
