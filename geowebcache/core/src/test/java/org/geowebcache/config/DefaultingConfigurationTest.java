/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * <p>Copyright 2018
 */
package org.geowebcache.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Set;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.wms.WMSHttpHelper;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.util.URLs;
import org.junit.Test;

public class DefaultingConfigurationTest {

    DefaultingConfiguration config = getConfig();
    TileLayer tl =
            new WMSLayer(
                    "test",
                    new String[] {"http://example.com/"},
                    null,
                    Integer.toString(1),
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    null);
    GeoWebCacheConfiguration gwcConfig = new GeoWebCacheConfiguration();
    GridSetBroker gridSetBroker =
            new GridSetBroker(Collections.singletonList(new DefaultGridsets(true, true)));

    @Test
    public void testUnsetConfigs() {
        assertNull(tl.isCacheBypassAllowed());
        assertNull(tl.getBackendTimeout());
        assertNull(tl.getFormatModifiers());
    }

    @Test
    public void testSetDefault() {
        config.setDefaultValues(tl);
        boolean cacheBypass = tl.isCacheBypassAllowed();
        int timeout = tl.getBackendTimeout();
        assertFalse(cacheBypass);
        assertEquals(timeout, 120);
        assertNull(tl.getFormatModifiers());
    }

    @Test
    public void initializationTest() {
        initialize(tl);
        Set<String> subsets = tl.getGridSubsets();
        assertEquals(subsets.size(), 2);
        assertEquals(subsets.toArray()[0], "EPSG:4326");
        assertEquals(subsets.toArray()[1], "EPSG:900913");
    }

    GeoWebCacheConfiguration getGwcConfig() {
        return this.gwcConfig;
    }

    DefaultingConfiguration getConfig() {
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
                            proxyUrl = URLs.of(getGwcConfig().getProxyUrl());
                        } else if (wl.getProxyUrl() != null) {
                            proxyUrl = URLs.of(wl.getProxyUrl());
                        }
                    } catch (MalformedURLException e) {
                    }

                    final WMSHttpHelper sourceHelper;

                    if (wl.getHttpUsername() != null) {
                        sourceHelper =
                                new WMSHttpHelper(
                                        wl.getHttpUsername(), wl.getHttpPassword(), proxyUrl);
                    } else if (getGwcConfig().getHttpUsername() != null) {
                        sourceHelper =
                                new WMSHttpHelper(
                                        getGwcConfig().getHttpUsername(),
                                        getGwcConfig().getHttpPassword(),
                                        proxyUrl);
                    } else {
                        sourceHelper = new WMSHttpHelper(null, null, proxyUrl);
                    }

                    wl.setSourceHelper(sourceHelper);
                    wl.setLockProvider(getGwcConfig().getLockProvider());
                }
            }

            @Override
            public void afterPropertiesSet() throws GeoWebCacheException {}

            @Override
            public String getIdentifier() {
                return null;
            }

            @Override
            public String getLocation() {
                return null;
            }

            @Override
            public void deinitialize() throws Exception {}
        };
    }

    private void initialize(final TileLayer layer) {
        config.setDefaultValues(layer);
        layer.initialize(gridSetBroker);
    }
}
