package org.geowebcache.arcgis.layer;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.List;

import junit.framework.TestCase;

import org.geowebcache.arcgis.config.CacheInfo;
import org.geowebcache.arcgis.config.CacheInfoPersister;
import org.geowebcache.arcgis.config.LODInfo;
import org.geowebcache.arcgis.config.TileOrigin;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSubsetFactory;

/**
 * Unit test suite for the {@link GridSetBuilder} utility class
 * 
 * @author Gabriel Roldan
 * 
 */
public class GridSetBuilderTest extends TestCase {

    private GridSetBuilder builder;

    private CacheInfo cacheInfo;

    private BoundingBox layerBounds;

    private GridSet gridset;

    @Override
    public void setUp() throws Exception {
        URL url = getClass().getResource("/arcgis_09.2_conf.xml");
        CacheInfoPersister persister = new CacheInfoPersister();
        InputStream stream = url.openStream();
        Reader reader = new InputStreamReader(stream);
        try {
            cacheInfo = persister.load(reader);
        } finally {
            stream.close();
        }
        layerBounds = new BoundingBox(-10, -10, 100, 50);
        builder = new GridSetBuilder();
        gridset = builder.buildGridset("TestLayer", cacheInfo, layerBounds);
        assertNotNull(gridset);
    }

    public void testBounds() {
        assertTrue(gridset.isTopLeftAligned());
        BoundingBox bounds = gridset.getBounds();
        TileOrigin tileOrigin = cacheInfo.getTileCacheInfo().getTileOrigin();
        assertEquals(tileOrigin.getX(), bounds.getMinX());
        assertEquals(tileOrigin.getY(), bounds.getMaxY());
        assertTrue(bounds.contains(layerBounds));
    }

    public void testResolutionsAndScaleDenoms() {
        double[] resolutions = GridSubsetFactory.createGridSubSet(gridset).getResolutions();

        List<LODInfo> lodInfos = cacheInfo.getTileCacheInfo().getLodInfos();
        assertEquals(lodInfos.size(), resolutions.length);

        for (int i = 0; i < resolutions.length; i++) {
            LODInfo lodInfo = lodInfos.get(i);
            assertEquals(lodInfo.getResolution(), resolutions[i]);
            assertEquals(lodInfo.getScale(), gridset.getGridLevels()[i].getScaleDenominator(), 1e-6);
        }
    }
}
