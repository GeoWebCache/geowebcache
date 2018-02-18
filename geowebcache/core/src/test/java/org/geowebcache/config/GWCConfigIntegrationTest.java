package org.geowebcache.config;

import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.storage.BlobStoreAggregator;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * Base class for integration testing different GWC Configuration implementations
 * Uses different {@link GWCConfigIntegrationTestSupport} implementations as {@link Parameters} to access the respective GWC Configurations
 * Uses {@link GWCConfigIntegrationTestData} to populate each implementation with consistent data.
 */
@RunWith(Parameterized.class)
public abstract class GWCConfigIntegrationTest {

    @Parameters
    public static Collection<GWCConfigIntegrationTestSupport[]> data() throws Exception {
        return Arrays.asList(new GWCConfigIntegrationTestSupport[][] {
                {new GWCXMLConfigIntegrationTestSupport()}
                //TODO: Add new configurations as they are implemented
        });
    }

    @Parameter
    public GWCConfigIntegrationTestSupport testSupport;

    public GridSetBroker gridSetBroker;
    public TileLayerDispatcher tileLayerDispatcher;
    public BlobStoreAggregator blobStoreAggregator;

    @Before
    public void setUpTest() throws Exception {
        testSupport.resetConfiguration();
        GWCConfigIntegrationTestData.setUpTestData(testSupport);

        gridSetBroker = testSupport.getGridSetBroker();
        blobStoreAggregator = new BlobStoreAggregator(Collections.singletonList(testSupport.getBlobStoreConfiguration()));
        tileLayerDispatcher = new TileLayerDispatcher(gridSetBroker, testSupport.getTileLayerConfigurations());
    }
}
