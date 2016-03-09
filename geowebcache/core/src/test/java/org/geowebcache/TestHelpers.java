package org.geowebcache;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

import javax.imageio.ImageIO;

import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.seed.GWCTask;
import org.geowebcache.seed.SeedRequest;
import org.geowebcache.util.MockLockProvider;

/**
 * Some common utility test functions.
 * @author Ian Schneider <ischneider@opengeo.org>
 */
public class TestHelpers {

    static GridSetBroker gridSetBroker = new GridSetBroker(false, false);
    public static MockLockProvider mockProvider = new MockLockProvider();

    public static byte[] createFakeSourceImage(final WMSLayer layer) throws IOException {

        int tileWidth = layer.getGridSubset(gridSetBroker.WORLD_EPSG4326.getName()).getGridSet()
                .getTileWidth();
        int tileHeight = layer.getGridSubset(gridSetBroker.WORLD_EPSG4326.getName()).getGridSet()
                .getTileHeight();

        int width = tileWidth * layer.getMetaTilingFactors()[0];
        int height = tileHeight * layer.getMetaTilingFactors()[1];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RenderedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        String formatName = layer.getMimeTypes().get(0).getInternalName();
        ImageIO.write(image, formatName, out);
        return out.toByteArray();
    }

    public static WMSLayer createWMSLayer(final String format) {
        return createWMSLayer(format, null, null);
    }

    public static WMSLayer createWMSLayer(final String format, Integer minCacheLevel, Integer maxCacheLevel) {
        String[] urls = { "http://localhost:38080/wms" };
        List<String> formatList = Collections.singletonList(format);

        Hashtable<String, GridSubset> grids = new Hashtable<String, GridSubset>();

        GridSubset grid = GridSubsetFactory.createGridSubSet(gridSetBroker.WORLD_EPSG4326,
                new BoundingBox(-30.0, 15.0, 45.0, 30), 0, 10, minCacheLevel, maxCacheLevel);

        grids.put(grid.getName(), grid);
        int[] metaWidthHeight = { 3, 3 };

        WMSLayer layer = new WMSLayer("test:layer", urls, "aStyle", "test:layer", formatList,
                grids, new ArrayList<>(), metaWidthHeight, "vendorparam=true", false, null);

        layer.initialize(gridSetBroker);
        layer.setLockProvider(new MockLockProvider());

        return layer;
    }
    
    public static SeedRequest createRequest(WMSLayer tl, GWCTask.TYPE type, int zoomStart,
            int zoomStop) {
        String gridSet = tl.getGridSubsets().iterator().next();
        BoundingBox bounds = null;
        int threadCount = 1;
        String format = tl.getMimeTypes().get(0).getFormat();
        SeedRequest req = new SeedRequest(tl.getName(), bounds, gridSet, threadCount, zoomStart,
                zoomStop, format, type, null);
        return req;

    }

}
