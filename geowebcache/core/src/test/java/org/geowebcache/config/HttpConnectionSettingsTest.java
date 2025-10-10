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
package org.geowebcache.config;

import static org.junit.Assert.*;

import org.geowebcache.util.HttpClientConnectionManagerFactory;
import org.junit.Test;

/** Test for HTTP connection settings configuration */
public class HttpConnectionSettingsTest {

    @Test
    public void testDefaultSettings() {
        HttpConnectionSettings settings = new HttpConnectionSettings();

        assertEquals(HttpConnectionSettings.DEFAULT_MAX_CONNECTIONS_TOTAL, settings.getMaxConnectionsTotal());
        assertEquals(HttpConnectionSettings.DEFAULT_MAX_CONNECTIONS_PER_ROUTE, settings.getMaxConnectionsPerRoute());
        assertEquals(HttpConnectionSettings.DEFAULT_CONNECTION_TIMEOUT, settings.getConnectionTimeout());
        assertEquals(HttpConnectionSettings.DEFAULT_SOCKET_TIMEOUT, settings.getSocketTimeout());
    }

    @Test
    public void testCustomSettings() {
        HttpConnectionSettings settings = new HttpConnectionSettings(100, 20, 60, 120);

        assertEquals(100, settings.getMaxConnectionsTotal());
        assertEquals(20, settings.getMaxConnectionsPerRoute());
        assertEquals(60, settings.getConnectionTimeout());
        assertEquals(120, settings.getSocketTimeout());
    }

    @Test
    public void testPartialSettings() {
        HttpConnectionSettings settings = new HttpConnectionSettings();
        settings.setMaxConnectionsTotal(50);
        settings.setMaxConnectionsPerRoute(10);

        assertEquals(50, settings.getMaxConnectionsTotal());
        assertEquals(10, settings.getMaxConnectionsPerRoute());
        assertEquals(HttpConnectionSettings.DEFAULT_CONNECTION_TIMEOUT, settings.getConnectionTimeout());
        assertEquals(HttpConnectionSettings.DEFAULT_SOCKET_TIMEOUT, settings.getSocketTimeout());
    }

    @Test
    public void testConnectionManagerFactory() {
        HttpConnectionSettings settings = new HttpConnectionSettings(30, 5, 45, 90);

        HttpClientConnectionManagerFactory factory = HttpClientConnectionManagerFactory.getInstance();
        factory.initialize(settings);

        HttpConnectionSettings retrievedSettings = factory.getSettings();
        assertEquals(settings.getMaxConnectionsTotal(), retrievedSettings.getMaxConnectionsTotal());
        assertEquals(settings.getMaxConnectionsPerRoute(), retrievedSettings.getMaxConnectionsPerRoute());
        assertEquals(settings.getConnectionTimeout(), retrievedSettings.getConnectionTimeout());
        assertEquals(settings.getSocketTimeout(), retrievedSettings.getSocketTimeout());
    }

    @Test
    public void testEqualsAndHashCode() {
        HttpConnectionSettings settings1 = new HttpConnectionSettings(20, 4, 30, 60);
        HttpConnectionSettings settings2 = new HttpConnectionSettings(20, 4, 30, 60);
        HttpConnectionSettings settings3 = new HttpConnectionSettings(30, 4, 30, 60);

        assertEquals(settings1, settings2);
        assertNotEquals(settings1, settings3);
        assertEquals(settings1.hashCode(), settings2.hashCode());
        assertNotEquals(settings1.hashCode(), settings3.hashCode());
    }
}
