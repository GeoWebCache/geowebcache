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
 * @author Andrea Aime, GeoSolutions, Copyright 2019
 */
package org.geowebcache.azure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.thoughtworks.xstream.XStream;
import org.geowebcache.GeoWebCacheExtensions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class AzureBlobStoreConfigProviderTest {

    @Before
    @SuppressWarnings("PMD.CloseResource")
    public void setUp() throws Exception {
        System.setProperty("CONTAINER", "MYCONTAINER");
        System.setProperty("MYKEY", "99942777gfa+");
        System.setProperty("CONNECTIONS", "30");
        System.setProperty("ENABLED", "true");
        System.setProperty("ALLOW_ENV_PARAMETRIZATION", "true");
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("appContextTestAzure.xml");

        GeoWebCacheExtensions gse = new GeoWebCacheExtensions();
        gse.setApplicationContext(context);
    }

    @After
    public void tearDown() throws Exception {
        System.clearProperty("CONTAINER");
        System.clearProperty("MYKEY");
        System.clearProperty("CONNECTIONS");
        System.clearProperty("ENABLED");
        System.clearProperty("ALLOW_ENV_PARAMETRIZATION");
    }

    @Test
    public void testValuesFromEnvironment() {
        AzureBlobStoreConfigProvider provider = new AzureBlobStoreConfigProvider();
        XStream stream = new XStream();
        stream = provider.getConfiguredXStream(stream);
        Object config = stream.fromXML(getClass().getResourceAsStream("blobstore.xml"));
        assertTrue(config instanceof AzureBlobStoreInfo);
        AzureBlobStoreInfo abConfig = (AzureBlobStoreInfo) config;
        assertEquals("${CONTAINER}", abConfig.getContainer());
        assertEquals("myname", abConfig.getAccountName());
        assertEquals("${MYKEY}", abConfig.getAccountKey());
        assertEquals("30", abConfig.getMaxConnections());
        assertTrue(abConfig.isEnabled());
    }
}
