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
 * @author Gabriel Roldan, Camptocamp, Copyright 2025
 */
package org.geowebcache.storage.blobstore.gcs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.thoughtworks.xstream.XStream;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import org.geowebcache.GeoWebCacheEnvironment;
import org.geowebcache.config.FileBlobStoreInfo;
import org.geowebcache.storage.StorageException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class GoogleCloudStorageConfigProviderTest {

    private ClassPathXmlApplicationContext context;

    private Map<String, String> properties;

    @Before
    public void setUp() throws Exception {
        properties = new HashMap<>();
        setProperty("ALLOW_ENV_PARAMETRIZATION", "true");
        context = new ClassPathXmlApplicationContext("appContextTestGcs.xml");
    }

    private void setProperty(String name, String value) {
        System.setProperty(name, value);
        properties.put(name, value);
    }

    @After
    public void tearDown() throws Exception {
        properties.keySet().forEach(System::clearProperty);
    }

    @Test
    public void testCanSave() {
        GoogleCloudStorageConfigProvider provider = context.getBean(GoogleCloudStorageConfigProvider.class);
        assertFalse(provider.canSave(null));
        assertFalse(provider.canSave(new FileBlobStoreInfo()));
        assertTrue(provider.canSave(new GoogleCloudStorageBlobStoreInfo()));
    }

    @Test
    public void testValuesFromEnvironment() throws StorageException {
        GoogleCloudStorageConfigProvider provider = new GoogleCloudStorageConfigProvider();
        XStream stream = new XStream();
        stream = provider.getConfiguredXStream(stream);
        String xml =
                """
                <GoogleCloudStorageBlobStore>
                    <id>myGcsBlobStore</id>
                    <enabled>true</enabled>
                    <projectId>${GCS_PROJECT_ID}</projectId>
                    <quotaProjectId>${GCS_QUOTA_PROJECT_ID}</quotaProjectId>
                    <bucket>${GCS_BUCKET}</bucket>
                    <prefix>${GCS_PREFIX}</prefix>
                    <endpointUrl>${GCS_ENDPOINT}</endpointUrl>
                    <apiKey>${GCS_APIKEY}</apiKey>
                    <useDefaultCredentialsChain>${GCS_DEFAULT_CREDENTIALS}</useDefaultCredentialsChain>
                </GoogleCloudStorageBlobStore>
                """;

        Object parsed = stream.fromXML(new StringReader(xml));
        assertTrue(parsed instanceof GoogleCloudStorageBlobStoreInfo);

        GoogleCloudStorageBlobStoreInfo config = (GoogleCloudStorageBlobStoreInfo) parsed;
        assertEquals("myGcsBlobStore", config.getId());
        assertEquals("myGcsBlobStore", config.getName());
        assertTrue(config.isEnabled());

        assertEquals("${GCS_PROJECT_ID}", config.getProjectId());
        assertEquals("${GCS_QUOTA_PROJECT_ID}", config.getQuotaProjectId());
        assertEquals("${GCS_BUCKET}", config.getBucket());
        assertEquals("${GCS_PREFIX}", config.getPrefix());
        assertEquals("${GCS_ENDPOINT}", config.getEndpointUrl());
        assertEquals("${GCS_APIKEY}", config.getApiKey());

        setProperty("GCS_PROJECT_ID", "my-project");
        setProperty("GCS_QUOTA_PROJECT_ID", "bills-go-here");
        setProperty("GCS_BUCKET", "test-bucket");
        setProperty("GCS_PREFIX", "gwc");
        setProperty("GCS_ENDPOINT", "https://goog.compatible.storage/api");
        setProperty("GCS_APIKEY", "goog-fake-api-key");
        setProperty("GCS_DEFAULT_CREDENTIALS", "true");

        GeoWebCacheEnvironment environment = context.getBean(GeoWebCacheEnvironment.class);
        GoogleCloudStorageClient.Builder builder = GoogleCloudStorageClient.builder(config, environment);
        assertEquals("my-project", builder.projectId);
        assertEquals("bills-go-here", builder.quotaProjectId);
        assertEquals("test-bucket", builder.bucket);
        assertEquals("gwc", builder.prefix);
        assertEquals("https://goog.compatible.storage/api", builder.endpointUrl);
        assertEquals("goog-fake-api-key", builder.apiKey);
    }
}
