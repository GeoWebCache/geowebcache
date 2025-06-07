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
package org.geowebcache.azure.tests.online;

import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.assertTrue;

import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.specialized.BlockBlobClient;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Stream;
import org.geowebcache.azure.AzureBlobStoreData;
import org.geowebcache.azure.AzureClient;
import org.junit.rules.ExternalResource;

/**
 * The TemporaryAzureFolder provides a path prefix for Azure storage and deletes all resources under the given prefix at
 * shutdown.
 */
public class TemporaryAzureFolder extends ExternalResource {

    private Properties properties;

    private String container;

    private String accountName;

    private String accountKey;

    private String temporaryPrefix;

    private AzureClient client;

    public TemporaryAzureFolder(Properties properties) {
        this.properties = properties;
        this.container = properties.getProperty("container");
        this.accountName = properties.getProperty("accountName");
        this.accountKey = properties.getProperty("accountKey");
    }

    @Override
    protected void before() throws Throwable {
        if (!isConfigured()) {
            return;
        }
        this.temporaryPrefix = "tmp_" + UUID.randomUUID().toString().replace("-", "");
        client = new AzureClient(getConfig());
    }

    @Override
    protected void after() {
        if (!isConfigured()) {
            return;
        }
        try {
            delete();
        } finally {
            temporaryPrefix = null;
        }
    }

    public AzureClient getClient() {
        checkState(isConfigured(), "client not configured.");
        return client;
    }

    public AzureBlobStoreData getConfig() {
        checkState(isConfigured(), "Azure connection not configured.");
        AzureBlobStoreData config = new AzureBlobStoreData();
        config.setContainer(container);
        config.setAccountName(accountName);
        config.setAccountKey(accountKey);
        config.setPrefix(temporaryPrefix);
        if (properties.getProperty("serviceURL") != null) {
            config.setServiceURL(properties.getProperty("serviceURL"));
        }
        if (properties.getProperty("maxConnections") != null) {
            config.setMaxConnections(Integer.valueOf(properties.getProperty("maxConnections")));
        }
        if (properties.getProperty("useHTTPS") != null) {
            config.setUseHTTPS(Boolean.valueOf(properties.getProperty("useHTTPS")));
        }
        if (properties.getProperty("proxyHost") != null) {
            config.setProxyHost(properties.getProperty("proxyHost"));
        }
        if (properties.getProperty("proxyPort") != null) {
            config.setProxyPort(Integer.valueOf(properties.getProperty("proxyPort")));
        }
        if (properties.getProperty("proxyUsername") != null) {
            config.setProxyUsername(properties.getProperty("proxyUsername"));
        }
        if (properties.getProperty("proxyPassword") != null) {
            config.setProxyPassword(properties.getProperty("proxyPassword"));
        }
        return config;
    }

    public void delete() {
        checkState(isConfigured(), "client not configured.");
        if (temporaryPrefix == null) {
            return;
        }

        try (Stream<BlobItem> blobs = client.listBlobs(temporaryPrefix)) {
            blobs.forEach(blob -> {
                BlockBlobClient blockBlobURL = client.getBlockBlobClient(blob.getName());
                assertTrue("Expected success while deleting " + blob.getName(), blockBlobURL.deleteIfExists());
            });
        }
    }

    public boolean isConfigured() {
        return container != null && accountName != null && accountKey != null;
    }
}
