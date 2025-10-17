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
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 */
package org.geowebcache.service.wms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import jakarta.servlet.http.HttpServletRequest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.DefaultGridsets;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.filter.security.SecurityDispatcher;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.io.FileResource;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.layer.wms.WMSSourceHelper;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.stats.RuntimeStats;
import org.geowebcache.storage.DefaultStorageBroker;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TransientCache;
import org.geowebcache.storage.blobstore.file.FileBlobStore;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@SuppressWarnings("FloatCast")
public class WMSTileFuserTest {

    GridSetBroker gridSetBroker = new GridSetBroker(Collections.singletonList(new DefaultGridsets(false, false)));
    SecurityDispatcher secDisp = mock(SecurityDispatcher.class);

    HttpServletRequest fuserRequest(TileLayer layer, GridSubset gridSubset, BoundingBox bounds, int width, int height) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        Map<String, String> params = new HashMap<>();
        params.put("layers", layer.getId());
        params.put("srs", gridSubset.getSRS().toString());
        params.put("format", "image/png");
        params.put("bbox", bounds.toString());
        params.put("width", Integer.toString(width));
        params.put("height", Integer.toString(height));
        req.setParameters(params);
        return req;
    }

    @Test
    public void testTileFuserResolution() throws Exception {

        TileLayer layer = createWMSLayer();

        // request fits inside -30.0,15.0,45.0,30
        BoundingBox bounds = new BoundingBox(-25.0, 17.0, 40.0, 22);

        // One in between
        int width = (int) bounds.getWidth() * 10;
        int height = (int) bounds.getHeight() * 10;
        GridSubset gridSubset =
                layer.getGridSubset(layer.getGridSubsets().iterator().next());
        TileLayerDispatcher tld = mock(TileLayerDispatcher.class);
        StorageBroker sb = mock(StorageBroker.class);
        Mockito.when(tld.getTileLayer("test:layer")).thenReturn(layer);
        WMSTileFuser tileFuser = new WMSTileFuser(tld, sb, fuserRequest(layer, gridSubset, bounds, width, height));
        tileFuser.setSecurityDispatcher(secDisp);
        tileFuser.determineSourceResolution();
        assertEquals(0.087890625, tileFuser.srcResolution, 0.087890625 * 0.001);

        // Zoomed too far out
        height = (int) bounds.getWidth() / 10;
        width = (int) bounds.getWidth() / 10;
        tileFuser = new WMSTileFuser(tld, sb, fuserRequest(layer, gridSubset, bounds, width, height));
        tileFuser.setSecurityDispatcher(secDisp);
        tileFuser.determineSourceResolution();
        assertEquals(0, tileFuser.srcIdx);

        // Zoomed too far in
        height = (int) bounds.getWidth() * 10000;
        width = (int) bounds.getWidth() * 10000;
        tileFuser = new WMSTileFuser(tld, sb, fuserRequest(layer, gridSubset, bounds, width, height));
        tileFuser.setSecurityDispatcher(secDisp);
        tileFuser.determineSourceResolution();
        assertEquals(10, tileFuser.srcIdx);
    }

    @Test
    public void testTileFuserLazyCanvasLoading() throws Exception {
        WMSLayer layer = createWMSLayer();
        LockProvider lockProvider = mock(LockProvider.class);
        LockProvider.Lock lock = mock(LockProvider.Lock.class);
        doReturn(lock).when(lockProvider).getLock(anyString());
        layer.setLockProvider(lockProvider);
        // request fits inside -30.0,15.0,45.0,30
        BoundingBox bounds = new BoundingBox(-25.0, 17.0, 40.0, 22);
        // One in between
        int width = (int) bounds.getWidth() * 10;
        int height = (int) bounds.getHeight() * 10;
        GridSubset gridSubset =
                layer.getGridSubset(layer.getGridSubsets().iterator().next());
        TileLayerDispatcher tld = mock(TileLayerDispatcher.class);
        Mockito.when(tld.getTileLayer("test:layer")).thenReturn(layer);
        StorageBroker sb = mock(StorageBroker.class);
        WMSTileFuser tileFuser = new WMSTileFuser(tld, sb, fuserRequest(layer, gridSubset, bounds, width, height));
        AtomicInteger count = new AtomicInteger();
        Mockito.when(sb.get(ArgumentMatchers.any(TileObject.class))).thenAnswer(invoc -> {
            TileObject stObj = (TileObject) invoc.getArguments()[0];
            final File imageTile = new File(getClass().getResource("/image.png").toURI());
            stObj.setBlob(new FileResource(imageTile));
            stObj.setCreated((new Date()).getTime());
            stObj.setBlobSize(1000);
            if (count.incrementAndGet() == 1) {
                // first time, canvas should be null
                assertNull(tileFuser.bufferedImageWrapper.canvas);
                return true;
            } else {
                assertNotNull(tileFuser.bufferedImageWrapper.canvas);
                return true;
            }
        });

        try (ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("appContextTest.xml")) {
            tileFuser.setApplicationContext(context);

            tileFuser.setSecurityDispatcher(secDisp);
            MockHttpServletResponse response = new MockHttpServletResponse();
            RuntimeStats stats = mock(RuntimeStats.class);

            assertNull(tileFuser.bufferedImageWrapper);
            tileFuser.writeResponse(response, stats);
            assertNotNull(tileFuser.bufferedImageWrapper);
            assertNotNull(tileFuser.bufferedImageWrapper.canvas);
        }
    }

    @Test
    public void testTileFuserSecurity() throws Exception {

        Mockito.doThrow(new SecurityException()).when(secDisp).checkSecurity(Mockito.any());

        TileLayer layer = createWMSLayer();

        // request fits inside -30.0,15.0,45.0,30
        BoundingBox bounds = new BoundingBox(-25.0, 17.0, 40.0, 22);

        // One in between
        int width = (int) bounds.getWidth() * 10;
        int height = (int) bounds.getHeight() * 10;
        GridSubset gridSubset =
                layer.getGridSubset(layer.getGridSubsets().iterator().next());
        TileLayerDispatcher tld = mock(TileLayerDispatcher.class);
        StorageBroker sb = mock(StorageBroker.class);
        Mockito.when(tld.getTileLayer("test:layer")).thenReturn(layer);
        WMSTileFuser tileFuser = new WMSTileFuser(tld, sb, fuserRequest(layer, gridSubset, bounds, width, height));
        tileFuser.setSecurityDispatcher(secDisp);
        MockHttpServletResponse response = new MockHttpServletResponse();
        RuntimeStats stats = mock(RuntimeStats.class);

        assertThrows(SecurityException.class, () -> tileFuser.writeResponse(response, stats));
    }

    @Test
    public void testTileFuserSecurityLayerNotNull() throws Exception {

        TileLayer layer = createWMSLayer();

        // request fits inside -30.0,15.0,45.0,30
        BoundingBox bounds = new BoundingBox(-25.0, 17.0, 40.0, 22);

        // One in between
        int width = (int) bounds.getWidth() * 10;
        int height = (int) bounds.getHeight() * 10;
        GridSubset gridSubset =
                layer.getGridSubset(layer.getGridSubsets().iterator().next());
        TileLayerDispatcher tld = mock(TileLayerDispatcher.class);
        Mockito.when(tld.getTileLayer("test:layer")).thenReturn(layer);
        StorageBroker sb = mock(StorageBroker.class);

        Mockito.when(sb.get(ArgumentMatchers.any(TileObject.class))).thenAnswer(invoc -> {
            TileObject stObj = (TileObject) invoc.getArguments()[0];
            final File imageTile = new File(getClass().getResource("/image.png").toURI());
            stObj.setBlob(new FileResource(imageTile));
            stObj.setCreated((new Date()).getTime());
            stObj.setBlobSize(1000);
            return true;
        });
        WMSTileFuser tileFuser = new WMSTileFuser(tld, sb, fuserRequest(layer, gridSubset, bounds, width, height));
        try (ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("appContextTest.xml")) {
            tileFuser.setApplicationContext(context);

            tileFuser.setSecurityDispatcher(secDisp);
            MockHttpServletResponse response = new MockHttpServletResponse();
            RuntimeStats stats = mock(RuntimeStats.class);

            tileFuser.writeResponse(response, stats);

            Mockito.verify(secDisp, times(4)).checkSecurity(ArgumentMatchers.any(ConveyorTile.class));
        }
    }

    @Test
    public void testTileFuserSubset() throws Exception {
        TileLayer layer = createWMSLayer();

        // request fits inside -30.0,15.0,45.0,30
        BoundingBox bounds = new BoundingBox(-25.0, 17.0, 40.0, 22);

        // One in between
        int width = (int) bounds.getWidth() * 10;
        int height = (int) bounds.getHeight() * 10;
        GridSubset gridSubset =
                layer.getGridSubset(layer.getGridSubsets().iterator().next());
        TileLayerDispatcher tld = mock(TileLayerDispatcher.class);
        Mockito.when(tld.getTileLayer("test:layer")).thenReturn(layer);
        StorageBroker sb = mock(StorageBroker.class);
        WMSTileFuser tileFuser = new WMSTileFuser(tld, sb, fuserRequest(layer, gridSubset, bounds, width, height));

        tileFuser.setSecurityDispatcher(secDisp);
        tileFuser.determineSourceResolution();
        tileFuser.determineCanvasLayout();

        assertTrue(tileFuser.srcBounds.contains(bounds));
        WMSTileFuser.PixelOffsets comparison = new WMSTileFuser.PixelOffsets();
        // -228, -193, -56, -6
        comparison.left = -228;
        comparison.bottom = -193;
        comparison.right = -56;
        comparison.top = -6;
        assertEquals(comparison.left, tileFuser.canvOfs.left);
        assertEquals(comparison.bottom, tileFuser.canvOfs.bottom);
        assertEquals(comparison.right, tileFuser.canvOfs.right);
        assertEquals(comparison.top, tileFuser.canvOfs.top);
    }

    @Test
    public void testTileFuserSuperset() throws Exception {
        TileLayer layer = createWMSLayer();

        // request larger than -30.0,15.0,45.0,30
        BoundingBox bounds = new BoundingBox(-35.0, 14.0, 55.0, 39);

        // One in between
        int width = (int) bounds.getWidth() * 25;
        int height = (int) bounds.getHeight() * 25;
        GridSubset gridSubset =
                layer.getGridSubset(layer.getGridSubsets().iterator().next());
        TileLayerDispatcher tld = mock(TileLayerDispatcher.class);
        Mockito.when(tld.getTileLayer("test:layer")).thenReturn(layer);
        StorageBroker sb = mock(StorageBroker.class);
        WMSTileFuser tileFuser = new WMSTileFuser(tld, sb, fuserRequest(layer, gridSubset, bounds, width, height));

        tileFuser.setSecurityDispatcher(secDisp);
        tileFuser.determineSourceResolution();
        tileFuser.determineCanvasLayout();
    }

    @Test
    public void testWriteResponse() throws Exception {
        final TileLayer layer = createWMSLayer();
        // request larger than -30.0,15.0,45.0,30
        BoundingBox bounds = new BoundingBox(-35.0, 14.0, 55.0, 39);

        // One in between
        int width = (int) bounds.getWidth() * 25;
        int height = (int) bounds.getHeight() * 25;
        layer.getGridSubset(layer.getGridSubsets().iterator().next());
        File temp = File.createTempFile("gwc", "wms");
        temp.delete();
        temp.mkdirs();
        try {
            TileLayerDispatcher dispatcher = new TileLayerDispatcher(gridSetBroker, null) {

                @Override
                public TileLayer getTileLayer(String layerName) throws GeoWebCacheException {
                    return layer;
                }
            };

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addParameter("layers", new String[] {"test:layer"});
            request.addParameter("srs", new String[] {"EPSG:4326"});
            request.addParameter("format", new String[] {"image/png8"});
            request.addParameter("width", width + "");
            request.addParameter("height", height + "");
            request.addParameter("bbox", bounds.toString());
            final File imageTile = new File(getClass().getResource("/image.png").toURI());

            StorageBroker broker = new DefaultStorageBroker(
                    new FileBlobStore(temp.getAbsolutePath()) {

                        @Override
                        public boolean get(TileObject stObj) throws StorageException {
                            stObj.setBlob(new FileResource(imageTile));
                            stObj.setCreated((new Date()).getTime());
                            stObj.setBlobSize(1000);
                            return true;
                        }
                    },
                    new TransientCache(100, 1024, 2000));

            WMSTileFuser tileFuser = new WMSTileFuser(dispatcher, broker, request);
            tileFuser.setSecurityDispatcher(secDisp);

            // Selection of the ApplicationContext associated
            try (ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("appContextTest.xml")) {
                tileFuser.setApplicationContext(context);
                MockHttpServletResponse response = new MockHttpServletResponse();

                tileFuser.writeResponse(response, new RuntimeStats(1, Arrays.asList(1), Arrays.asList("desc")));

                // check the result is a valid PNG
                assertEquals("image/png", response.getContentType());
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.getContentAsByteArray()));
                // and it's the expected size
                assertEquals(width, image.getWidth());
                assertEquals(height, image.getHeight());
            }
        } finally {
            temp.delete();
        }
    }

    private WMSLayer createWMSLayer() {
        String[] urls = {"http://localhost:38080/wms"};
        List<String> formatList = new LinkedList<>();
        formatList.add("image/png");

        Map<String, GridSubset> grids = new HashMap<>();

        GridSubset grid = GridSubsetFactory.createGridSubSet(
                gridSetBroker.getWorldEpsg4326(), new BoundingBox(-30.0, 15.0, 45.0, 30), 0, 10);

        grids.put(grid.getName(), grid);
        int[] metaWidthHeight = {3, 3};

        WMSLayer layer = new WMSLayer(
                "test:layer",
                urls,
                "aStyle",
                "test:layer",
                formatList,
                grids,
                null,
                metaWidthHeight,
                "vendorparam=true",
                false,
                null);

        layer.setLockProvider(mock(LockProvider.class));
        layer.setSourceHelper(mock(WMSSourceHelper.class));

        layer.initialize(gridSetBroker);

        return layer;
    }
}
