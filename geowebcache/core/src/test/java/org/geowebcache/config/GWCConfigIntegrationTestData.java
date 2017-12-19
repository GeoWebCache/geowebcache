package org.geowebcache.config;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.filter.parameters.StringParameterFilter;
import org.geowebcache.grid.*;
import org.geowebcache.layer.wms.WMSLayer;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class GWCConfigIntegrationTestData {

    public static void setUpTestData(GWCConfigIntegrationTestSupport testSupport) throws GeoWebCacheException {
        //set up service information
        ServerConfiguration serverConfiguration = testSupport.getServerConfiguration();
        serverConfiguration.getServiceInformation().setTitle("GeoWebCache");

        //Set up layers & gridsets
        //TODO: Update to use the new api
        GridSetConfiguration gridSetConfiguration = testSupport.getGridSetConfiguration();

        GridSet epsg2163 = GridSetFactory.createGridSet("EPSG:2163", SRS.getSRS("EPSG:2163"),
                new BoundingBox(-2495667.977678598, -2223677.196231552,
                                3291070.6104286816, 959189.3312465074),
                false, null,
                new double[]{ 25000000, 1000000, 100000, 25000 },
                null, GridSetFactory.DEFAULT_PIXEL_SIZE_METER,
                null, 200, 200, false);


        TileLayerConfiguration tileLayerConfiguration = testSupport.getTileLayerConfigurations().get(0);

        Map<String, GridSubset > subSets = new HashMap<>();
        subSets.put("EPSG:4326", GridSubsetFactory.createGridSubSet(testSupport.getGridSetBroker().WORLD_EPSG4326, new BoundingBox(-129.6, 3.45,-62.1,70.9), null, null));
        subSets.put("EPSG:2163", GridSubsetFactory.createGridSubSet(epsg2163));

        StringParameterFilter parameterFilter = new StringParameterFilter();
        parameterFilter.setKey("STYLES");
        parameterFilter.setDefaultValue("population");
        parameterFilter.setValues(Arrays.asList("population", "polygon", "pophatch"));

        tileLayerConfiguration.addLayer(new WMSLayer(
                "topp:states", new String[]{"http://demo.opengeo.org/geoserver/topp/wms"}, null, null,
                Arrays.asList("image/gif", "image/jpeg", "image/png", "image/png8"),  subSets,
                Collections.singletonList(parameterFilter), null, null, true));

        //TODO: update to use new API
        testSupport.getGridSetBroker().put(epsg2163);
        tileLayerConfiguration.initialize(testSupport.getGridSetBroker());

        //Set up blobstores
        BlobStoreConfigurationCatalog blobStoreConfiguration = testSupport.getBlobStoreConfiguration();

        FileBlobStoreConfig blobStore = new FileBlobStoreConfig("defaultCache");
        blobStore.setEnabled(false);
        blobStore.setBaseDirectory("/tmp/defaultCache");
        blobStore.setFileSystemBlockSize(4096);

        //TODO: Use new API to add blobstore
        blobStoreConfiguration.getBlobStores().add(blobStore);
    }
}
