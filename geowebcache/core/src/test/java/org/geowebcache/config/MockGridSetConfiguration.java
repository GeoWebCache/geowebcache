package org.geowebcache.config;

import java.util.Arrays;
import java.util.List;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.GridSet;

/**
 * Mock Gridset configuration for testing
 *
 */
public class MockGridSetConfiguration extends SimpleGridSetConfiguration {

    public MockGridSetConfiguration(GridSet... mocks) {
        super();
        for(GridSet g: mocks) {
            addInternal(g);
        }
    }

    @Override
    public void reinitialize() throws GeoWebCacheException {
    }

    @Override
    public String getIdentifier() {
        return "MockGridSetConfiguration";
    }

    @Override
    public String getLocation() {
        return "Mock";
    }

    /**
     * List containing a DefaultGridsets and a MockGridSetConfiguration
     */
    public static List<GridSetConfiguration> withDefaults(boolean useEPSG900913, boolean useGWC11xNames, GridSet... mocks) {
        return Arrays.asList(new DefaultGridsets(useEPSG900913, useGWC11xNames), new MockGridSetConfiguration(mocks));
    }
    
    /**
     * List containing a DefaultGridsets and a MockGridSetConfiguration
     */
   public static List<GridSetConfiguration> withDefaults(GridSet... mocks) {
        return Arrays.asList(new DefaultGridsets(true, true), new MockGridSetConfiguration(mocks));
    }

    @Override
    public void afterPropertiesSet() throws Exception {
    }
}
