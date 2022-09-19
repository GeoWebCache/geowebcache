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
 * <p>Copyright 2018
 */
package org.geowebcache.config;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.storage.BlobStoreAggregator;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Base class for integration testing different GWC Configuration implementations Uses different
 * {@link GWCConfigIntegrationTestSupport} implementations as {@link Parameters} to access the
 * respective GWC Configurations Uses {@link GWCConfigIntegrationTestData} to populate each
 * implementation with consistent data.
 */
@RunWith(Parameterized.class)
public abstract class GWCConfigIntegrationTest {

    @Parameters
    public static Collection<GWCConfigIntegrationTestSupport[]> data() throws Exception {
        return Arrays.asList(
                new GWCConfigIntegrationTestSupport[][] {{new GWCXMLConfigIntegrationTestSupport()}
                    // TODO: Add new configurations as they are implemented
                });
    }

    @Parameter public GWCConfigIntegrationTestSupport testSupport;

    public GridSetBroker gridSetBroker;
    public TileLayerDispatcher tileLayerDispatcher;
    public BlobStoreAggregator blobStoreAggregator;

    @Before
    public void setUpTest() throws Exception {
        testSupport.resetConfiguration();
        GWCConfigIntegrationTestData.setUpTestData(testSupport);

        gridSetBroker = testSupport.getGridSetBroker();
        tileLayerDispatcher =
                new TileLayerDispatcher(
                        gridSetBroker, testSupport.getTileLayerConfigurations(), null);
        blobStoreAggregator =
                new BlobStoreAggregator(
                        Collections.singletonList(testSupport.getBlobStoreConfiguration()),
                        tileLayerDispatcher);
    }
}
