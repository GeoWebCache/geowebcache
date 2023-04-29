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
 * @author Andrea Aime, GeoSolutions, Copyright 2019
 */
package org.geowebcache.azure;

import static org.junit.Assert.assertTrue;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.http.HttpStatus;

public class OnlineAzureBlobStoreIntegrationTest extends AbstractAzureBlobStoreIntegrationTest {

    @Rule
    public TemporaryAzureFolder tempFolder =
            new TemporaryAzureFolder(testConfigLoader.getProperties());

    @Override
    protected AzureBlobStoreData getConfiguration() {
        Assume.assumeTrue("Configuration not found or incomplee", tempFolder.isConfigured());
        return tempFolder.getConfig();
    }

    @Test
    public void testCreatesStoreMetadataOnStart() {
        String prefix = tempFolder.getConfig().getPrefix();
        // if the file does not exist a StorageException will be thrown
        int status =
                tempFolder
                        .getClient()
                        .getBlockBlobURL(prefix + "/metadata.properties")
                        .getProperties()
                        .blockingGet()
                        .statusCode();
        assertTrue(
                "Expected success but got " + status, HttpStatus.valueOf(status).is2xxSuccessful());
    }
}
