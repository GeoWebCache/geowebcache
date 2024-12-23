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
 * @author Kevin Smith, Boundless, 2018
 */
package org.geowebcache.azure.tests.online;

import static org.junit.Assume.assumeFalse;

import org.geowebcache.azure.AzureBlobStoreData;
import org.geowebcache.azure.AzureBlobStoreSuitabilityTest;
import org.geowebcache.azure.AzureClient;
import org.junit.Rule;
import org.junit.experimental.theories.Theories;
import org.junit.runner.RunWith;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

@RunWith(OnlineAzureBlobStoreSuitabilityIT.MyTheories.class)
public class OnlineAzureBlobStoreSuitabilityIT extends AzureBlobStoreSuitabilityTest {

    public PropertiesLoader testConfigLoader = new PropertiesLoader();

    @Rule
    public TemporaryAzureFolder tempFolder = new TemporaryAzureFolder(testConfigLoader.getProperties());

    @Override
    protected AzureBlobStoreData getConfiguration() {
        return tempFolder.getConfig();
    }

    @Override
    protected AzureClient getClient() {
        return tempFolder.getClient();
    }

    // Sorry, this bit of evil makes the Theories runner gracefully ignore the
    // tests if Azure is unavailable.  There's probably a better way to do this.
    public static class MyTheories extends Theories {

        public MyTheories(Class<?> klass) throws InitializationError {
            super(klass);
        }

        @Override
        public Statement methodBlock(FrameworkMethod method) {
            if (new PropertiesLoader().getProperties().containsKey("container")) {
                return super.methodBlock(method);
            } else {
                return new Statement() {
                    @Override
                    public void evaluate() {
                        assumeFalse("Azure unavailable", true);
                    }
                };
            }
        }
    }
}
