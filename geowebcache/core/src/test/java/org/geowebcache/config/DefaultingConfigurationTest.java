package org.geowebcache.config;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.wms.WMSHttpHelper;
import org.geowebcache.layer.wms.WMSLayer;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNull;
public class DefaultingConfigurationTest {

    DefaultingConfiguration config = getConfig();
    TileLayer tl = new WMSLayer("test", new String[] {"http://example.com/"}, null,
            Integer.toString(1),null, null, null, null,
            null, false, null);
    GeoWebCacheConfiguration gwcConfig = new GeoWebCacheConfiguration();
    GridSetBroker gridSetBroker =  new GridSetBroker(true, true);

    @Test
    public void testUnsetConfigs(){
        assertNull(tl.isCacheBypassAllowed());
        assertNull(tl.getBackendTimeout());
        assertNull(tl.getFormatModifiers());
    }
    @Test
    public void testSetDefault(){
        config.setDefaultValues(tl);
        boolean cacheBypass = tl.isCacheBypassAllowed();
        int timeout = tl.getBackendTimeout();
        assertEquals(cacheBypass, false);
        assertEquals(timeout, 120);
        assertNull(tl.getFormatModifiers());
    }

    @Test
    public void initializationTest(){
        initialize(tl);
        Set<String> subsets = tl.getGridSubsets();
        assertEquals(subsets.size(), 2);
        assertEquals(subsets.toArray()[0], "EPSG:4326");
        assertEquals(subsets.toArray()[1], "EPSG:900913");
    }

    GeoWebCacheConfiguration getGwcConfig() {
        return this.gwcConfig;
    }

    DefaultingConfiguration getConfig(){
        return new DefaultingConfiguration() {
            @Override
            public void setDefaultValues(TileLayer layer) {
                if (layer.isCacheBypassAllowed() == null) {
                    if (getGwcConfig().getCacheBypassAllowed() != null) {
                        layer.setCacheBypassAllowed(getGwcConfig().getCacheBypassAllowed());
                    } else {
                        layer.setCacheBypassAllowed(false);
                    }
                }

                if (layer.getBackendTimeout() == null) {
                    if (getGwcConfig().getBackendTimeout() != null) {
                        layer.setBackendTimeout(getGwcConfig().getBackendTimeout());
                    } else {
                        layer.setBackendTimeout(120);
                    }
                }

                if (layer.getFormatModifiers() == null) {
                    if (getGwcConfig().getFormatModifiers() != null) {
                        layer.setFormatModifiers(getGwcConfig().getFormatModifiers());
                    }
                }

                if (layer instanceof WMSLayer) {
                    WMSLayer wl = (WMSLayer) layer;

                    URL proxyUrl = null;
                    try {
                        if (getGwcConfig().getProxyUrl() != null) {
                            proxyUrl = new URL(getGwcConfig().getProxyUrl());
                        } else if (wl.getProxyUrl() != null) {
                            proxyUrl = new URL(wl.getProxyUrl());
                        }
                    } catch (MalformedURLException e) {
                    }

                    final WMSHttpHelper sourceHelper;

                    if (wl.getHttpUsername() != null) {
                        sourceHelper = new WMSHttpHelper(wl.getHttpUsername(), wl.getHttpPassword(),
                                proxyUrl);
                    } else if (getGwcConfig().getHttpUsername() != null) {
                        sourceHelper = new WMSHttpHelper(getGwcConfig().getHttpUsername(),
                                getGwcConfig().getHttpPassword(), proxyUrl);
                    } else {
                        sourceHelper = new WMSHttpHelper(null, null, proxyUrl);
                    }

                    wl.setSourceHelper(sourceHelper);
                    wl.setLockProvider(getGwcConfig().getLockProvider());
                }
            }

            @Override
            public int initialize(GridSetBroker gridSetBroker) throws GeoWebCacheException {
                return 0;
            }

            @Override
            public String getIdentifier() {
                return null;
            }

            @Override
            public String getLocation() {
                return null;
            }

            @Override
            public void save() throws IOException {

            }
        };
    }

    private void initialize(final TileLayer layer) {
        config.setDefaultValues(layer);
        layer.initialize(gridSetBroker);
    }
}
