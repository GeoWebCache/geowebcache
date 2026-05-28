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
 */
package org.geowebcache.seed;

import static org.junit.Assert.assertEquals;

import org.geowebcache.util.PropertyRule;
import org.junit.Rule;
import org.junit.Test;

public class SeederThreadPoolExecutorTest {

    @Rule
    public PropertyRule corePoolSizeProp = PropertyRule.system(SeederThreadPoolExecutor.GWC_SEEDER_CORE_POOL_SIZE);

    @Rule
    public PropertyRule maxPoolSizeProp = PropertyRule.system(SeederThreadPoolExecutor.GWC_SEEDER_MAX_POOL_SIZE);

    @Test
    public void testDefaultValues() {
        // No system property set, should use the constructor-provided defaults
        corePoolSizeProp.setValue(null);
        maxPoolSizeProp.setValue(null);

        SeederThreadPoolExecutor executor = new SeederThreadPoolExecutor(16, 32);
        try {
            assertEquals(16, executor.getCorePoolSize());
            assertEquals(32, executor.getMaximumPoolSize());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testSystemPropertyOverride() {
        corePoolSizeProp.setValue("24");
        maxPoolSizeProp.setValue("64");

        SeederThreadPoolExecutor executor = new SeederThreadPoolExecutor(16, 32);
        try {
            assertEquals(24, executor.getCorePoolSize());
            assertEquals(64, executor.getMaximumPoolSize());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testInvalidValueFallsBackToDefault() {
        corePoolSizeProp.setValue("invalid");
        maxPoolSizeProp.setValue("notanumber");

        SeederThreadPoolExecutor executor = new SeederThreadPoolExecutor(16, 32);
        try {
            assertEquals(16, executor.getCorePoolSize());
            assertEquals(32, executor.getMaximumPoolSize());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testNegativeValueFallsBackToDefault() {
        corePoolSizeProp.setValue("-5");
        maxPoolSizeProp.setValue("0");

        SeederThreadPoolExecutor executor = new SeederThreadPoolExecutor(16, 32);
        try {
            assertEquals(16, executor.getCorePoolSize());
            assertEquals(32, executor.getMaximumPoolSize());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testPartialOverride() {
        // Only override core pool size, leave max at default
        corePoolSizeProp.setValue("20");
        maxPoolSizeProp.setValue(null);

        SeederThreadPoolExecutor executor = new SeederThreadPoolExecutor(16, 32);
        try {
            assertEquals(20, executor.getCorePoolSize());
            assertEquals(32, executor.getMaximumPoolSize());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testWhitespaceValueFallsBackToDefault() {
        corePoolSizeProp.setValue("  ");
        maxPoolSizeProp.setValue("");

        SeederThreadPoolExecutor executor = new SeederThreadPoolExecutor(16, 32);
        try {
            assertEquals(16, executor.getCorePoolSize());
            assertEquals(32, executor.getMaximumPoolSize());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testValueWithWhitespaceIsTrimmed() {
        corePoolSizeProp.setValue(" 24 ");
        maxPoolSizeProp.setValue(" 48 ");

        SeederThreadPoolExecutor executor = new SeederThreadPoolExecutor(16, 32);
        try {
            assertEquals(24, executor.getCorePoolSize());
            assertEquals(48, executor.getMaximumPoolSize());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testCorePoolSizeExceedsMaxPoolSizeAdjustsMax() {
        // Set core higher than max — max should be adjusted upward to match core
        corePoolSizeProp.setValue("64");
        maxPoolSizeProp.setValue("32");

        SeederThreadPoolExecutor executor = new SeederThreadPoolExecutor(16, 32);
        try {
            assertEquals(64, executor.getCorePoolSize());
            assertEquals(64, executor.getMaximumPoolSize());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testCorePoolSizeOverrideExceedsDefaultMax() {
        // Only override core to be higher than the default max — max should adjust
        corePoolSizeProp.setValue("48");
        maxPoolSizeProp.setValue(null);

        SeederThreadPoolExecutor executor = new SeederThreadPoolExecutor(16, 32);
        try {
            assertEquals(48, executor.getCorePoolSize());
            assertEquals(48, executor.getMaximumPoolSize());
        } finally {
            executor.shutdownNow();
        }
    }
}
