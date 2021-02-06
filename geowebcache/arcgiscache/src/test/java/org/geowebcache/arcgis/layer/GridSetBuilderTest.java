package org.geowebcache.arcgis.layer;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.List;
import org.geowebcache.arcgis.config.CacheInfo;
import org.geowebcache.arcgis.config.CacheInfoPersister;
import org.geowebcache.arcgis.config.LODInfo;
import org.geowebcache.arcgis.config.TileOrigin;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSubsetFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test suite for the {@link GridSetBuilder} utility class
 *
 * @author Gabriel Roldan
 */
public class GridSetBuilderTest {

    private GridSetBuilder builder;

    private CacheInfo cacheInfo;

    private BoundingBox layerBounds;

    private GridSet gridset;

    @Before
    public void setUp() throws Exception {
        URL url = getClass().getResource("/arcgis_09.2_conf.xml");
        CacheInfoPersister persister = new CacheInfoPersister();
        try (InputStream stream = url.openStream();
                Reader reader = new InputStreamReader(stream)) {
            cacheInfo = persister.load(reader);
        }
        layerBounds = new BoundingBox(-10, -10, 100, 50);
        builder = new GridSetBuilder();
        gridset = builder.buildGridset("TestLayer", cacheInfo, layerBounds);
        Assert.assertNotNull(gridset);
    }

    @Test
    public void testBounds() {
        Assert.assertTrue(gridset.isTopLeftAligned());
        BoundingBox bounds = gridset.getBounds();
        TileOrigin tileOrigin = cacheInfo.getTileCacheInfo().getTileOrigin();
        Assert.assertEquals(tileOrigin.getX(), bounds.getMinX(), 0d);
        Assert.assertEquals(tileOrigin.getY(), bounds.getMaxY(), 0d);
        Assert.assertTrue(bounds.contains(layerBounds));
    }

    @Test
    public void testResolutionsAndScaleDenoms() {
        double[] resolutions = GridSubsetFactory.createGridSubSet(gridset).getResolutions();

        List<LODInfo> lodInfos = cacheInfo.getTileCacheInfo().getLodInfos();
        Assert.assertEquals(lodInfos.size(), resolutions.length);

        for (int i = 0; i < resolutions.length; i++) {
            LODInfo lodInfo = lodInfos.get(i);
            Assert.assertEquals(lodInfo.getResolution(), resolutions[i], 0d);
            Assert.assertEquals(lodInfo.getScale(), gridset.getGrid(i).getScaleDenominator(), 1e-6);
        }
    }
}
