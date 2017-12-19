package org.geowebcache.config;

import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.layer.TileLayerDispatcher;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public abstract class GWCConfigIntegrationTest {

    @Parameters
    public static Collection<GWCConfigIntegrationTestSupport[]> data() throws Exception {
        return Arrays.asList(new GWCConfigIntegrationTestSupport[][] {
                {new GWCXMLConfigIntegrationTestSupport()}
        });
    }

    @Parameter
    public GWCConfigIntegrationTestSupport testSupport;

    public GridSetBroker gridSetBroker;
    public TileLayerDispatcher tileLayerDispatcher;

    @Before
    public void setUpTest() throws Exception {
        GWCConfigIntegrationTestData.setUpTestData(testSupport);

        gridSetBroker = testSupport.getGridSetBroker();
        tileLayerDispatcher = new TileLayerDispatcher(gridSetBroker, testSupport.getTileLayerConfigurations());
    }
}
