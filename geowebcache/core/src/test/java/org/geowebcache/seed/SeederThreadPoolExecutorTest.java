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
 * <p>Copyright 2026
 */
package org.geowebcache.seed;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.geowebcache.config.ServerConfiguration;
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
        corePoolSizeProp.setValue(null);
        maxPoolSizeProp.setValue(null);

        ServerConfiguration config = mock(ServerConfiguration.class);
        when(config.getSeederCorePoolSize()).thenReturn(null);
        when(config.getSeederMaxPoolSize()).thenReturn(null);

        SeederThreadPoolExecutor executor = new SeederThreadPoolExecutor(config);
        try {
            assertEquals(16, executor.getCorePoolSize());
            assertEquals(32, executor.getMaximumPoolSize());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testConfiguredValues() {
        corePoolSizeProp.setValue(null);
        maxPoolSizeProp.setValue(null);

        ServerConfiguration config = mock(ServerConfiguration.class);
        when(config.getSeederCorePoolSize()).thenReturn(24);
        when(config.getSeederMaxPoolSize()).thenReturn(48);

        SeederThreadPoolExecutor executor = new SeederThreadPoolExecutor(config);
        try {
            assertEquals(24, executor.getCorePoolSize());
            assertEquals(48, executor.getMaximumPoolSize());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testSystemPropertyOverridesConfig() {
        corePoolSizeProp.setValue("100");
        maxPoolSizeProp.setValue("200");

        ServerConfiguration config = mock(ServerConfiguration.class);
        when(config.getSeederCorePoolSize()).thenReturn(24);
        when(config.getSeederMaxPoolSize()).thenReturn(48);

        SeederThreadPoolExecutor executor = new SeederThreadPoolExecutor(config);
        try {
            assertEquals(100, executor.getCorePoolSize());
            assertEquals(200, executor.getMaximumPoolSize());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testInvalidValueFallsBackToConfig() {
        corePoolSizeProp.setValue("invalid");
        maxPoolSizeProp.setValue("notanumber");

        ServerConfiguration config = mock(ServerConfiguration.class);
        when(config.getSeederCorePoolSize()).thenReturn(24);
        when(config.getSeederMaxPoolSize()).thenReturn(48);

        SeederThreadPoolExecutor executor = new SeederThreadPoolExecutor(config);
        try {
            assertEquals(24, executor.getCorePoolSize());
            assertEquals(48, executor.getMaximumPoolSize());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testNonPositiveValueFallsBackToConfig() {
        corePoolSizeProp.setValue("-5");
        maxPoolSizeProp.setValue("0");

        ServerConfiguration config = mock(ServerConfiguration.class);
        when(config.getSeederCorePoolSize()).thenReturn(24);
        when(config.getSeederMaxPoolSize()).thenReturn(48);

        SeederThreadPoolExecutor executor = new SeederThreadPoolExecutor(config);
        try {
            assertEquals(24, executor.getCorePoolSize());
            assertEquals(48, executor.getMaximumPoolSize());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testPartialOverride() {
        corePoolSizeProp.setValue("20");
        maxPoolSizeProp.setValue(null);

        ServerConfiguration config = mock(ServerConfiguration.class);
        when(config.getSeederCorePoolSize()).thenReturn(null);
        when(config.getSeederMaxPoolSize()).thenReturn(48);

        SeederThreadPoolExecutor executor = new SeederThreadPoolExecutor(config);
        try {
            assertEquals(20, executor.getCorePoolSize());
            assertEquals(48, executor.getMaximumPoolSize());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testWhitespaceValueFallsBackToConfig() {
        corePoolSizeProp.setValue("  ");
        maxPoolSizeProp.setValue("");

        ServerConfiguration config = mock(ServerConfiguration.class);
        when(config.getSeederCorePoolSize()).thenReturn(24);
        when(config.getSeederMaxPoolSize()).thenReturn(48);

        SeederThreadPoolExecutor executor = new SeederThreadPoolExecutor(config);
        try {
            assertEquals(24, executor.getCorePoolSize());
            assertEquals(48, executor.getMaximumPoolSize());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testValueWithWhitespaceIsTrimmed() {
        corePoolSizeProp.setValue(" 24 ");
        maxPoolSizeProp.setValue(" 48 ");

        ServerConfiguration config = mock(ServerConfiguration.class);
        when(config.getSeederCorePoolSize()).thenReturn(null);
        when(config.getSeederMaxPoolSize()).thenReturn(null);

        SeederThreadPoolExecutor executor = new SeederThreadPoolExecutor(config);
        try {
            assertEquals(24, executor.getCorePoolSize());
            assertEquals(48, executor.getMaximumPoolSize());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testCorePoolSizeExceedsMaxPoolSizeAdjustsMax() {
        corePoolSizeProp.setValue("64");
        maxPoolSizeProp.setValue("32");

        ServerConfiguration config = mock(ServerConfiguration.class);
        when(config.getSeederCorePoolSize()).thenReturn(null);
        when(config.getSeederMaxPoolSize()).thenReturn(null);

        SeederThreadPoolExecutor executor = new SeederThreadPoolExecutor(config);
        try {
            assertEquals(64, executor.getCorePoolSize());
            assertEquals(64, executor.getMaximumPoolSize());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testConfigCoreExceedsConfigMaxAdjustsMax() {
        corePoolSizeProp.setValue(null);
        maxPoolSizeProp.setValue(null);

        ServerConfiguration config = mock(ServerConfiguration.class);
        when(config.getSeederCorePoolSize()).thenReturn(64);
        when(config.getSeederMaxPoolSize()).thenReturn(32);

        SeederThreadPoolExecutor executor = new SeederThreadPoolExecutor(config);
        try {
            assertEquals(64, executor.getCorePoolSize());
            assertEquals(64, executor.getMaximumPoolSize());
        } finally {
            executor.shutdownNow();
        }
    }
}
