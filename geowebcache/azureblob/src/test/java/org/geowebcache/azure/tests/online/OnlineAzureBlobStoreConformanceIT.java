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
 * @author Kevin Smith, Boundless, 2017
 */
package org.geowebcache.azure.tests.online;

import org.geowebcache.azure.AzureBlobStoreConformanceTest;
import org.geowebcache.azure.AzureBlobStoreData;
import org.junit.Assume;
import org.junit.Rule;

public class OnlineAzureBlobStoreConformanceIT extends AzureBlobStoreConformanceTest {
    public PropertiesLoader testConfigLoader = new PropertiesLoader();

    @Rule
    public TemporaryAzureFolder tempFolder = new TemporaryAzureFolder(testConfigLoader.getProperties());

    @Override
    protected AzureBlobStoreData getConfiguration() {
        Assume.assumeTrue(tempFolder.isConfigured());
        return tempFolder.getConfig();
    }
}
