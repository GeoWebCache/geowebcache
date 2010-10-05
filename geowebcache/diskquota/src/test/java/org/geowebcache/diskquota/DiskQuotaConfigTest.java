/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Gabriel Roldan (OpenGeo) 2010
 *  
 */
package org.geowebcache.diskquota;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

public class DiskQuotaConfigTest extends TestCase {

    private DiskQuotaConfig config;

    @Override
    protected void setUp() throws Exception {
        config = new DiskQuotaConfig();
    }

    public void testDiskQuotaConfig() {
        assertEquals(DiskQuotaConfig.DEFAULT_CLEANUP_FREQUENCY, config.getCacheCleanUpFrequency());
        assertEquals(DiskQuotaConfig.DEFAULT_DISK_BLOCK_SIZE, config.getDiskBlockSize());
        assertEquals(DiskQuotaConfig.DEFAULT_MAX_CONCURRENT_CLEANUPS,
                config.getMaxConcurrentCleanUps());
        assertEquals(DiskQuotaConfig.DEFAULT_CLEANUP_UNITS, config.getCacheCleanUpUnits());
    }

    public void testSetDiskBlockSize() {
        try {
            config.setDiskBlockSize(-1);
            fail("Expected IAE");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }

        config.setDiskBlockSize(4096);
        assertEquals(4096, config.getDiskBlockSize());
    }

    public void testSetCacheCleanUpFrequency() {
        try {
            config.setCacheCleanUpFrequency(-1);
            fail("Expected IAE");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }

        config.setCacheCleanUpFrequency(10);
        assertEquals(10, config.getCacheCleanUpFrequency());
    }

    public void testSetCacheCleanUpUnits() {
        try {
            config.setCacheCleanUpUnits(null);
            fail("Expected IAE");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }

        config.setCacheCleanUpUnits(TimeUnit.MILLISECONDS);
        assertEquals(TimeUnit.MILLISECONDS, config.getCacheCleanUpUnits());
    }

    public void testSetLayerQuotas() {
        config.setLayerQuotas(null);
        assertNotNull(config.getLayerQuotas());
        assertEquals(0, config.getLayerQuotas().size());

        config.setLayerQuotas(Collections.singletonList(new LayerQuota("layer", "LRU")));
        assertEquals(1, config.getLayerQuotas().size());
    }

    public void testRemove() {
        LayerQuota lq = new LayerQuota("layer", "LFU");
        config.setLayerQuotas(new ArrayList<LayerQuota>(Collections.singletonList(lq)));
        config.remove(lq);
        assertNull(config.getLayerQuota("layer"));
    }

    public void testSetMaxConcurrentCleanUps() {
        try {
            config.setMaxConcurrentCleanUps(-1);
            fail("Expected IAE");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
        try {
            config.setMaxConcurrentCleanUps(0);
            fail("Expected IAE");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
        config.setMaxConcurrentCleanUps(10);
        assertEquals(10, config.getMaxConcurrentCleanUps());
    }

}
