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
 * @author Fernando Mino, GeoSolutions, Copyright 2019
 */
package org.geowebcache.azure;

import static org.junit.Assert.assertEquals;

import org.geowebcache.GeoWebCacheEnvironment;
import org.geowebcache.GeoWebCacheExtensions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class AzureBlobStoreDataTest {

    @Before
    @SuppressWarnings("PMD.CloseResource")
    public void setUp() throws Exception {
        System.setProperty("CONTAINER", "MYCONTAINER");
        System.setProperty("ACCOUNT_NAME", "MYNAME");
        System.setProperty("ACCOUNT_KEY", "MYKEY");
        System.setProperty("CONNECTIONS", "30");
        System.setProperty("ALLOW_ENV_PARAMETRIZATION", "true");
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("appContextTestAzure.xml");
        GeoWebCacheExtensions gse = new GeoWebCacheExtensions();
        gse.setApplicationContext(context);
    }

    @After
    public void tearDown() throws Exception {
        System.clearProperty("CONTAINER");
        System.clearProperty("CONNECTIONS");
        System.clearProperty("ACCOUNT_NAME");
        System.clearProperty("ACCOUNT_KEY");
        System.clearProperty("ALLOW_ENV_PARAMETRIZATION");
    }

    @Test
    public void testEnvironmentProperties() {
        GeoWebCacheEnvironment typedEnvironment = GeoWebCacheExtensions.bean(GeoWebCacheEnvironment.class);
        final AzureBlobStoreInfo storeInfo = new AzureBlobStoreInfo("info1");
        storeInfo.setContainer("${CONTAINER}");
        storeInfo.setAccountName("${ACCOUNT_NAME}");
        storeInfo.setAccountKey("${ACCOUNT_KEY}");
        storeInfo.setMaxConnections("${CONNECTIONS}");
        storeInfo.setEnabled(true);
        final AzureBlobStoreData storeData = new AzureBlobStoreData(storeInfo, typedEnvironment);
        assertEquals(Integer.valueOf(30), storeData.getMaxConnections());
        assertEquals("MYCONTAINER", storeData.getContainer());
        assertEquals("MYNAME", storeData.getAccountName());
        assertEquals("MYKEY", storeData.getAccountKey());
    }
}
