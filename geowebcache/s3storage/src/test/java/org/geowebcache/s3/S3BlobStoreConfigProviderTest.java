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
 * @author Mauro Bartolomeoli, GeoSolutions Sas, Copyright 2017
 */
package org.geowebcache.s3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.geowebcache.GeoWebCacheExtensions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.thoughtworks.xstream.XStream;

public class S3BlobStoreConfigProviderTest {

    @Before
    public void setUp() throws Exception {
        System.setProperty("BUCKET", "MYBUCKET");
        System.setProperty("CONNECTIONS", "30");
        System.setProperty("ENABLED", "true");
        System.setProperty("ALLOW_ENV_PARAMETRIZATION", "true");
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
                "appContextTestS3.xml");

        GeoWebCacheExtensions gse = new GeoWebCacheExtensions();
        gse.setApplicationContext(context);
    }

    @After
    public void tearDown() throws Exception {
        System.clearProperty("BUCKET");
        System.clearProperty("CONNECTIONS");
        System.clearProperty("ENABLED");
        System.clearProperty("ALLOW_ENV_PARAMETRIZATION");
    }

    @Test
    public void testValuesFromEnvironment() {
        S3BlobStoreConfigProvider provider = new S3BlobStoreConfigProvider();
        XStream stream = new XStream();
        stream = provider.getConfiguredXStream(stream);
        Object config = stream.fromXML(getClass().getResourceAsStream("blobstore.xml"));
        assertTrue(config instanceof S3BlobStoreInfo);
        S3BlobStoreInfo s3Config = (S3BlobStoreInfo) config;
        assertEquals("MYBUCKET", s3Config.getBucket());
        assertEquals(30, s3Config.getMaxConnections().intValue());
        assertEquals(true, s3Config.isEnabled());
    }
}
