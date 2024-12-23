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

import static org.junit.Assert.assertTrue;

import org.geowebcache.azure.AzureBlobStoreData;
import org.geowebcache.azure.AzureBlobStoreIntegrationTest;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;

public class OnlineAzureBlobStoreIntegrationIT extends AzureBlobStoreIntegrationTest {

    private PropertiesLoader testConfigLoader = new PropertiesLoader();

    @Rule
    public TemporaryAzureFolder tempFolder = new TemporaryAzureFolder(testConfigLoader.getProperties());

    @Override
    protected AzureBlobStoreData getConfiguration() {
        Assume.assumeTrue("Configuration not found or incomplee", tempFolder.isConfigured());
        return tempFolder.getConfig();
    }

    @Test
    public void testCreatesStoreMetadataOnStart() {
        String prefix = tempFolder.getConfig().getPrefix();
        // if the file does not exist a StorageException will be thrown
        String key = prefix + "/metadata.properties";
        boolean exists = tempFolder.getClient().getBlockBlobClient(key).exists();
        assertTrue("blob " + key + " does not exist", exists);
    }
}
