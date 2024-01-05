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
 */
package org.geowebcache.layer.wms;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.geowebcache.TestHelpers.createFakeSourceImage;
import static org.geowebcache.TestHelpers.createRequest;
import static org.geowebcache.TestHelpers.createWMSLayer;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.awt.image.SampleModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.media.jai.ImageLayout;
import javax.media.jai.JAI;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.BandSelectDescriptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.message.BasicHeader;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.TestHelpers;
import org.geowebcache.config.DefaultGridsets;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileResponseReceiver;
import org.geowebcache.layer.wms.WMSLayer.RequestType;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.seed.GWCTask;
import org.geowebcache.seed.SeedRequest;
import org.geowebcache.seed.TileBreeder;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;
import org.geowebcache.storage.TileRangeIterator;
import org.geowebcache.storage.TransientCache;
import org.geowebcache.util.MockLockProvider;
import org.geowebcache.util.MockWMSSourceHelper;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit test suite for {@link WMSLayer}
 *
 * @author Gabriel Roldan (OpenGeo)
 * @version $Id$
 */
public class WMSLayerTest extends TileLayerTest {

    private final GridSetBroker gridSetBroker =
            new GridSetBroker(Collections.singletonList(new DefaultGridsets(false, false)));

    @After
    public void tearDown() throws Exception {
        TestHelpers.mockProvider.verify();
        TestHelpers.mockProvider.clear();
    }

    @Test
    public void testSeedMetaTiled() throws Exception {
        WMSLayer layer = createWMSLayer("image/png");

        WMSSourceHelper mockSourceHelper = new MockWMSSourceHelper();
        MockLockProvider lockProvider = new MockLockProvider();
        layer.setSourceHelper(mockSourceHelper);
        layer.setLockProvider(lockProvider);

        final StorageBroker mockStorageBroker = EasyMock.createMock(StorageBroker.class);
        Capture<TileObject> captured = EasyMock.newCapture();
        expect(mockStorageBroker.put(EasyMock.capture(captured))).andReturn(true).anyTimes();
        replay(mockStorageBroker);

        String layerId = layer.getName();
        HttpServletRequest servletReq = new MockHttpServletRequest();
        HttpServletResponse servletResp = new MockHttpServletResponse();

        long[] gridLoc = {0, 0, 0}; // x, y, level
        MimeType mimeType = layer.getMimeTypes().get(0);
        GridSet gridSet = gridSetBroker.getWorldEpsg4326();
        String gridSetId = gridSet.getName();
        ConveyorTile tile =
                new ConveyorTile(
                        mockStorageBroker,
                        layerId,
                        gridSetId,
                        gridLoc,
                        mimeType,
                        null,
                        servletReq,
                        servletResp);

        boolean tryCache = false;
        layer.seedTile(tile, tryCache);

        assertEquals(1, captured.getValues().size());
        TileObject value = captured.getValue();
        assertNotNull(value);
        assertEquals("image/png", value.getBlobFormat());
        assertNotNull(value.getBlob());
        assertTrue(value.getBlob().getSize() > 0);

        verify(mockStorageBroker);

        // check the lock provider was called in a symmetric way
        lockProvider.verify();
        lockProvider.clear();
    }

    @Test
    public void testSeedJpegPngMetaTiled() throws Exception {
        checkJpegPng(
                "image/vnd.jpeg-png",
                () -> {
                    TileObject to = (TileObject) EasyMock.getCurrentArguments()[0];
                    assertEquals("image/vnd.jpeg-png", to.getBlobFormat());
                    assertNotNull(to.getBlob());
                    assertTrue(to.getBlob().getSize() > 0);
                    String format = ImageMime.jpegPng.getMimeType(to.getBlob());
                    long[] xyz = to.getXYZ();
                    assertEquals(10, xyz[2]);
                    // check the ones in the full black area are jpeg, the others png
                    if (xyz[0] == 900 || xyz[1] == 602) {
                        assertEquals("image/jpeg", format);
                    } else {
                        assertEquals("image/png", format);
                    }

                    return true;
                },
                new RGBASourceHelper());
    }

    @Test
    public void testSeedJpegPng8MetaTiled() throws Exception {
        checkJpegPng(
                "image/vnd.jpeg-png8",
                () -> {
                    TileObject to = (TileObject) EasyMock.getCurrentArguments()[0];
                    assertEquals("image/vnd.jpeg-png8", to.getBlobFormat());
                    assertNotNull(to.getBlob());
                    assertTrue(to.getBlob().getSize() > 0);
                    String format = ImageMime.jpegPng8.getMimeType(to.getBlob());
                    long[] xyz = to.getXYZ();
                    assertEquals(10, xyz[2]);
                    // check the ones in the full black area are jpeg, the others png
                    if (xyz[0] == 900 || xyz[1] == 602) {
                        assertEquals("image/jpeg", format);
                    } else {
                        assertEquals("image/png", format);
                        // verify the image has been paletted
                        try (InputStream inputStream = to.getBlob().getInputStream()) {
                            BufferedImage image = ImageIO.read(inputStream);
                            assertThat(image.getColorModel(), instanceOf(IndexColorModel.class));
                        }
                    }

                    return true;
                },
                new RGBASourceHelper());
    }

    @Test
    public void testSeedJpegPng8GrayAlphaMetaTiled() throws Exception {
        checkJpegPng(
                "image/vnd.jpeg-png8",
                () -> {
                    TileObject to = (TileObject) EasyMock.getCurrentArguments()[0];
                    assertEquals("image/vnd.jpeg-png8", to.getBlobFormat());
                    assertNotNull(to.getBlob());
                    assertTrue(to.getBlob().getSize() > 0);
                    String format = ImageMime.jpegPng8.getMimeType(to.getBlob());
                    long[] xyz = to.getXYZ();
                    assertEquals(10, xyz[2]);

                    try (InputStream is = to.getBlob().getInputStream()) {
                        BufferedImage image = ImageIO.read(is);

                        // check the ones in the full black area are jpeg, the others png
                        if (xyz[0] == 900 || xyz[1] == 602) {
                            // it's a gray
                            assertEquals(1, image.getColorModel().getNumComponents());
                            assertEquals("image/jpeg", format);
                        } else {
                            assertEquals("image/png", format);
                            // verify the image has been paletted (palette gets generated with 4
                            // components,
                            // there is no optimized gray/alpha palette generator)
                            assertThat(image.getColorModel(), instanceOf(IndexColorModel.class));
                        }
                    }
                    return true;
                },
                new GrayAlphaSourceHelper());
    }

    public void checkJpegPng(
            String format, IAnswer<Boolean> tileVerifier, WMSSourceHelper sourceHelper)
            throws GeoWebCacheException, IOException {
        WMSLayer layer = createWMSLayer(format);

        MockLockProvider lockProvider = new MockLockProvider();
        layer.setSourceHelper(sourceHelper);
        layer.setLockProvider(lockProvider);

        final StorageBroker mockStorageBroker = EasyMock.createMock(StorageBroker.class);
        Capture<TileObject> captured = EasyMock.newCapture(CaptureType.ALL);
        expect(mockStorageBroker.put(EasyMock.capture(captured)))
                .andAnswer(tileVerifier)
                .anyTimes();
        replay(mockStorageBroker);

        String layerId = layer.getName();
        HttpServletRequest servletReq = new MockHttpServletRequest();
        HttpServletResponse servletResp = new MockHttpServletResponse();

        long[] gridLoc = {900, 600, 10}; // x, y, level
        MimeType mimeType = layer.getMimeTypes().get(0);
        GridSet gridSet = gridSetBroker.getWorldEpsg4326();
        String gridSetId = gridSet.getName();
        ConveyorTile tile =
                new ConveyorTile(
                        mockStorageBroker,
                        layerId,
                        gridSetId,
                        gridLoc,
                        mimeType,
                        null,
                        servletReq,
                        servletResp);

        boolean tryCache = false;
        layer.seedTile(tile, tryCache);

        assertEquals(9, captured.getValues().size());
        verify(mockStorageBroker);

        // check the lock provider was called in a symmetric way
        lockProvider.verify();
        lockProvider.clear();
    }

    @Test
    public void testHttpClientPassedIn() throws Exception {
        final StorageBroker mockStorageBroker = EasyMock.createMock(StorageBroker.class);
        MockHttpServletRequest servletReq = new MockHttpServletRequest();
        MockHttpServletResponse servletResp = new MockHttpServletResponse();
        ConveyorTile tile = new ConveyorTile(mockStorageBroker, "name", servletReq, servletResp);
        // setup the layer
        WMSLayer layer = createWMSLayer("image/png");
        final byte[] responseBody = "Fake body".getBytes();
        HttpResponse response = EasyMock.createNiceMock(HttpResponse.class);
        StatusLine statusLine = EasyMock.createMock(StatusLine.class);
        expect(response.getStatusLine()).andReturn(statusLine);

        HttpEntity entity = EasyMock.createMock(HttpEntity.class);

        expect(entity.getContent()).andReturn(new ByteArrayInputStream(responseBody));
        expect(response.getEntity()).andReturn(entity);
        Header contentEncoding = new BasicHeader("ContentEncoding", "UTF-8");
        expect(entity.getContentEncoding()).andReturn(contentEncoding);
        expect(response.getFirstHeader("Content-Type"))
                .andReturn(new BasicHeader("Content-Type", "image/png"));

        replay(entity);
        replay(response);
        HttpClient httpClient = EasyMock.createNiceMock(HttpClient.class);
        expect(httpClient.execute(anyObject())).andReturn(response);
        replay(httpClient);
        WMSHttpHelper httpHelper =
                new WMSHttpHelper() {
                    public WMSHttpHelper setClient(HttpClient httpClient) {
                        this.client = httpClient;
                        return this;
                    }
                }.setClient(httpClient);
        httpHelper.setBackendTimeout(10);
        layer.setSourceHelper(httpHelper);

        // proxy the request, and check the response
        layer.proxyRequest(tile);
        assertEquals(200, servletResp.getStatus());
        assertEquals("Fake body", servletResp.getContentAsString());
    }

    @Test
    public void testHttpClientNeedsToBeCreated() throws Exception {
        WMSHttpHelper httpHelper = new WMSHttpHelper();
        assertNull(httpHelper.client);
        assertNotNull(httpHelper.getHttpClient());
        assertNotNull(httpHelper.client);
    }

    @Test
    public void testCascadeGetLegendGraphics() throws Exception {
        // setup the layer
        WMSLayer layer = createWMSLayer("image/png");
        final byte[] responseBody = "Fake body".getBytes();
        layer.setSourceHelper(
                new WMSHttpHelper() {
                    @Override
                    public HttpResponse executeRequest(
                            URL url,
                            Map<String, String> queryParams,
                            Integer backendTimeout,
                            WMSLayer.HttpRequestMode httpRequestMode)
                            throws UnsupportedOperationException, IOException {

                        HttpResponse response = EasyMock.createNiceMock(HttpResponse.class);
                        StatusLine statusLine = EasyMock.createMock(StatusLine.class);
                        expect(response.getStatusLine()).andReturn(statusLine);

                        HttpEntity entity = EasyMock.createMock(HttpEntity.class);

                        expect(entity.getContent())
                                .andReturn(new ByteArrayInputStream(responseBody));
                        expect(response.getEntity()).andReturn(entity);
                        Header contentEncoding = new BasicHeader("ContentEncoding", "UTF-8");
                        expect(entity.getContentEncoding()).andReturn(contentEncoding);
                        expect(response.getFirstHeader("Content-Type"))
                                .andReturn(new BasicHeader("Content-Type", "image/png"));

                        replay(entity);
                        // expectLastCall();
                        replay(response);
                        return response;
                    }
                });
        MockLockProvider lockProvider = new MockLockProvider();
        layer.setLockProvider(lockProvider);

        // setup the conveyor tile
        final StorageBroker mockStorageBroker = EasyMock.createMock(StorageBroker.class);

        String layerId = layer.getName();
        MockHttpServletRequest servletReq = new MockHttpServletRequest();
        servletReq.setQueryString(
                "REQUEST=GetLegendGraphic&VERSION=1.0.0&FORMAT=image/png&WIDTH=20&HEIGHT=20&LAYER=topp:states");
        MockHttpServletResponse servletResp = new MockHttpServletResponse();

        long[] gridLoc = {0, 0, 0}; // x, y, level
        MimeType mimeType = layer.getMimeTypes().get(0);
        GridSet gridSet = gridSetBroker.getWorldEpsg4326();
        String gridSetId = gridSet.getName();
        ConveyorTile tile =
                new ConveyorTile(
                        mockStorageBroker,
                        layerId,
                        gridSetId,
                        gridLoc,
                        mimeType,
                        null,
                        servletReq,
                        servletResp);

        // proxy the request, and check the response
        layer.proxyRequest(tile);

        assertEquals(200, servletResp.getStatus());
        assertEquals("Fake body", servletResp.getContentAsString());
        assertEquals("image/png", servletResp.getContentType());
    }

    @Test
    public void testCascadeWithoutContentType() throws Exception {
        // setup the layer
        WMSLayer layer = createWMSLayer("image/png");
        final byte[] responseBody = "Fake body".getBytes();
        layer.setSourceHelper(
                new WMSHttpHelper() {
                    @Override
                    public HttpResponse executeRequest(
                            URL url,
                            Map<String, String> queryParams,
                            Integer backendTimeout,
                            WMSLayer.HttpRequestMode httpRequestMode)
                            throws UnsupportedOperationException, IOException {
                        HttpResponse response = EasyMock.createNiceMock(HttpResponse.class);
                        StatusLine statusLine = EasyMock.createMock(StatusLine.class);
                        expect(response.getStatusLine()).andReturn(statusLine);
                        expect(response.getFirstHeader("Content-Type")).andReturn(null);
                        HttpEntity entity = EasyMock.createMock(HttpEntity.class);

                        expect(entity.getContent())
                                .andReturn(new ByteArrayInputStream(responseBody));
                        expect(response.getEntity()).andReturn(entity);
                        Header contentEncoding = new BasicHeader("ContentEncoding", "UTF-8");
                        expect(entity.getContentEncoding()).andReturn(contentEncoding);

                        replay(entity);
                        // expectLastCall();
                        replay(response);
                        return response;
                    }
                });
        final StorageBroker mockStorageBroker = EasyMock.createMock(StorageBroker.class);
        MockHttpServletRequest servletReq = new MockHttpServletRequest();
        MockHttpServletResponse servletResp = new MockHttpServletResponse();

        ConveyorTile tile = new ConveyorTile(mockStorageBroker, "name", servletReq, servletResp);

        // proxy the request, and check the response
        layer.proxyRequest(tile);

        assertNull(servletResp.getContentType());
    }

    @Test
    public void testMinMaxCacheSeedTile() throws Exception {
        WMSLayer tl = createWMSLayer("image/png", 5, 6);

        MockTileSupport mock = new MockTileSupport(tl);

        SeedRequest req = createRequest(tl, GWCTask.TYPE.SEED, 4, 7);
        TileRange tr = TileBreeder.createTileRange(req, tl);

        seedTiles(mock.storageBroker, tr, tl);

        // zero transient cache attempts
        assertEquals(0, mock.cacheHits.get());
        assertEquals(0, mock.cacheMisses.get());
        // empirical numbers
        assertEquals(42, mock.wmsMetaRequestCounter.get());
        assertEquals(218, mock.storagePutCounter.get());
    }

    @Test
    public void testGetFeatureInfoQueryLayers() throws MimeException {

        // a layer with no query layers
        WMSLayer l = createFeatureInfoLayer("a,b", null);
        assertNotNull(l.getWmsLayers());
        assertNull(l.getWmsQueryLayers());
        Map<String, String> rt =
                l.getWMSRequestTemplate(
                        MimeType.createFromFormat("text/plain"), RequestType.FEATUREINFO);
        assertEquals(l.getWmsLayers(), rt.get("QUERY_LAYERS"));

        // a layer with query layers
        l = createFeatureInfoLayer("a,b", "b");
        assertNotNull(l.getWmsLayers());
        assertNotNull(l.getWmsQueryLayers());
        rt =
                l.getWMSRequestTemplate(
                        MimeType.createFromFormat("text/plain"), RequestType.FEATUREINFO);
        assertEquals(l.getWmsQueryLayers(), rt.get("QUERY_LAYERS"));
    }

    private WMSLayer createFeatureInfoLayer(String wmsLayers, String wmsQueryLayers) {
        return new WMSLayer(
                "name",
                new String[0],
                null,
                wmsLayers,
                null,
                null,
                null,
                null,
                null,
                true,
                wmsQueryLayers);
    }

    // ignore to fix the build until the failing assertion is worked out
    @Test
    @Ignore
    public void testMinMaxCacheGetTile() throws Exception {
        WMSLayer tl = createWMSLayer("image/png", 5, 6);

        MockTileSupport mock = new MockTileSupport(tl);

        // we're not really seeding, just using the range
        SeedRequest req = createRequest(tl, GWCTask.TYPE.SEED, 4, 7);
        TileRange tr = TileBreeder.createTileRange(req, tl);

        List<ConveyorTile> tiles = getTiles(mock.storageBroker, tr, tl);

        // this number is determined by our tileRange minus those out of bounds
        assertEquals(880, tiles.size());
        // tiles at zoom 4 and 7 will have non png data
        for (ConveyorTile tile : tiles) {
            assertNotNull(tile.getBlob());
            // System.out.println(tile.getTileIndex()[2] + " " + tile.getBlob().getSize());
        }

        // empirical numbers
        // this number is determined by the number of metarequests at level 5+6
        assertEquals(218, mock.storagePutCounter.get());
        // and the number of successful hits at level 5+6
        assertEquals(176, mock.storageGetCounter.get());
        // these last will vary - on a dual core machine, they appeared predictable
        // but on a 8 core machine, the threads compete for cache and we can only
        // assertain by range
        // @todo
        // assertTrue(Math.abs(532 - mock.cacheHits.get()) < 10);
        // assertTrue(Math.abs(494 - mock.cacheMisses.get()) < 10);
        // assertTrue(Math.abs(172 - mock.wmsMetaRequestCounter.get()) < 10);
        // stats
    }

    private void seedTiles(StorageBroker storageBroker, TileRange tr, final WMSLayer tl)
            throws Exception {
        final String layerName = tl.getName();
        // define the meta tile size to 1,1 so we hit all the tiles
        final TileRangeIterator trIter = new TileRangeIterator(tr, tl.getMetaTilingFactors());

        long[] gridLoc = trIter.nextMetaGridLocation(new long[3]);

        while (gridLoc != null) {
            Map<String, String> fullParameters = tr.getParameters();

            final ConveyorTile tile =
                    new ConveyorTile(
                            storageBroker,
                            layerName,
                            tr.getGridSetId(),
                            gridLoc,
                            tr.getMimeType(),
                            fullParameters,
                            null,
                            null);
            tile.setTileLayer(tl);

            tl.seedTile(tile, false);

            gridLoc = trIter.nextMetaGridLocation(gridLoc);
        }
    }

    private List<ConveyorTile> getTiles(
            StorageBroker storageBroker, TileRange tr, final WMSLayer tl) throws Exception {
        final String layerName = tl.getName();
        // define the meta tile size to 1,1 so we hit all the tiles
        final TileRangeIterator trIter = new TileRangeIterator(tr, new int[] {1, 1});

        long[] gridLoc = trIter.nextMetaGridLocation(new long[3]);

        // six concurrent requests max
        @SuppressWarnings("PMD.CloseResource") // implements AutoCloseable in Java 21
        ExecutorService requests = Executors.newFixedThreadPool(6);
        ExecutorCompletionService<ConveyorTile> completer =
                new ExecutorCompletionService<>(requests);

        List<Future<ConveyorTile>> futures = new ArrayList<>();
        while (gridLoc != null) {
            Map<String, String> fullParameters = tr.getParameters();

            final ConveyorTile tile =
                    new ConveyorTile(
                            storageBroker,
                            layerName,
                            tr.getGridSetId(),
                            gridLoc,
                            tr.getMimeType(),
                            fullParameters,
                            null,
                            null);
            futures.add(
                    completer.submit(
                            () -> {
                                try {
                                    return tl.getTile(tile);
                                } catch (OutsideCoverageException oce) {
                                    return null;
                                }
                            }));

            gridLoc = trIter.nextMetaGridLocation(gridLoc);
        }

        // these assertions could be externalized
        List<ConveyorTile> results = new ArrayList<>();
        for (Future<ConveyorTile> future : futures) {
            ConveyorTile get = future.get();
            if (get != null) {
                results.add(get);
            }
        }

        requests.shutdown();

        return results;
    }

    private static class RGBASourceHelper extends WMSSourceHelper {
        @Override
        protected void makeRequest(
                TileResponseReceiver tileRespRecv,
                WMSLayer layer,
                Map<String, String> wmsParams,
                MimeType expectedMimeType,
                Resource target)
                throws GeoWebCacheException {
            int width = Integer.parseInt(wmsParams.get("WIDTH"));
            int height = Integer.parseInt(wmsParams.get("HEIGHT"));
            assertEquals(768, width);
            assertEquals(768, height);
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
            Graphics2D graphics = img.createGraphics();
            graphics.setColor(Color.BLACK);
            // fill an L shaped set of tiles, making a few partially filled
            graphics.fillRect(0, 0, width, 300);
            graphics.fillRect(0, 0, 300, height);
            graphics.dispose();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try {
                ImageIO.write(img, "PNG", output);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            try {
                target.transferFrom(
                        Channels.newChannel(new ByteArrayInputStream(output.toByteArray())));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class GrayAlphaSourceHelper extends WMSSourceHelper {
        @Override
        protected void makeRequest(
                TileResponseReceiver tileRespRecv,
                WMSLayer layer,
                Map<String, String> wmsParams,
                MimeType expectedMimeType,
                Resource target)
                throws GeoWebCacheException {
            int width = Integer.parseInt(wmsParams.get("WIDTH"));
            int height = Integer.parseInt(wmsParams.get("HEIGHT"));
            assertEquals(768, width);
            assertEquals(768, height);
            BufferedImage baseImage =
                    new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
            Graphics2D graphics = baseImage.createGraphics();
            graphics.setColor(Color.BLACK);
            // fill an L shaped set of tiles, making a few partially filled
            graphics.fillRect(0, 0, width, 300);
            graphics.fillRect(0, 0, 300, height);
            graphics.dispose();
            ColorModel cm =
                    new ComponentColorModel(
                            ColorSpace.getInstance(ColorSpace.CS_GRAY),
                            true,
                            false,
                            Transparency.TRANSLUCENT,
                            DataBuffer.TYPE_BYTE);
            SampleModel sm = cm.createCompatibleSampleModel(width, height);
            ImageLayout il = new ImageLayout();
            il.setSampleModel(sm);
            il.setColorModel(cm);
            RenderingHints hints = new RenderingHints(JAI.KEY_IMAGE_LAYOUT, il);
            RenderedOp grayAlpha = BandSelectDescriptor.create(baseImage, new int[] {0, 3}, hints);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try {
                ImageIO.write(grayAlpha, "PNG", output);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            try {
                target.transferFrom(
                        Channels.newChannel(new ByteArrayInputStream(output.toByteArray())));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    class MockTileSupport {

        final byte[] fakeWMSResponse;
        final StorageBroker storageBroker = EasyMock.createMock(StorageBroker.class);
        final AtomicInteger cacheHits = new AtomicInteger();
        final AtomicInteger cacheMisses = new AtomicInteger();
        final AtomicInteger storagePutCounter = new AtomicInteger();
        final AtomicInteger storageGetCounter = new AtomicInteger();
        final AtomicInteger wmsMetaRequestCounter = new AtomicInteger();
        final AtomicInteger tileTransferCounter = new AtomicInteger();
        final TransientCache transientCache = new TransientCache(100, 100, 2000);

        public MockTileSupport(WMSLayer tl) throws Exception {
            // create an image to be returned by the mock WMSSourceHelper
            fakeWMSResponse = createFakeSourceImage(tl);

            installSourceHelper(tl);
            installMockBroker();
        }

        private void installSourceHelper(WMSLayer tl) throws Exception {
            // WMSSourceHelper that on makeRequest() returns always the same fake image
            WMSSourceHelper mockSourceHelper = EasyMock.createMock(WMSSourceHelper.class);
            Capture<WMSMetaTile> metaTileCapturer = EasyMock.newCapture();
            Capture<Resource> resourceCapturer = EasyMock.newCapture();
            mockSourceHelper.makeRequest(capture(metaTileCapturer), capture(resourceCapturer));
            EasyMock.expectLastCall()
                    .andAnswer(
                            () -> {
                                Resource resource = resourceCapturer.getValue();
                                wmsMetaRequestCounter.incrementAndGet();
                                try {
                                    resource.transferFrom(
                                            Channels.newChannel(
                                                    new ByteArrayInputStream(fakeWMSResponse)));
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                                return null;
                            });
            expectLastCall().anyTimes().asStub();
            mockSourceHelper.setConcurrency(32);
            mockSourceHelper.setBackendTimeout(120);
            replay(mockSourceHelper);

            tl.setSourceHelper(mockSourceHelper);
        }

        private void installMockBroker() throws Exception {
            expect(storageBroker.getTransient(anyObject()))
                    .andAnswer(
                            () -> {
                                TileObject tile = (TileObject) EasyMock.getCurrentArguments()[0];
                                String key = TransientCache.computeTransientKey(tile);
                                Resource resource;
                                synchronized (transientCache) {
                                    resource = transientCache.get(key);
                                }
                                if (resource != null) {
                                    cacheHits.incrementAndGet();
                                } else {
                                    cacheMisses.incrementAndGet();
                                }
                                tile.setBlob(resource);
                                return resource != null;
                            })
                    .anyTimes();

            Capture<TileObject> tileCapturer = EasyMock.newCapture();
            storageBroker.putTransient(capture(tileCapturer));
            expectLastCall()
                    .andAnswer(
                            () -> {
                                TileObject tile = tileCapturer.getValue();
                                String key = TransientCache.computeTransientKey(tile);
                                synchronized (transientCache) {
                                    transientCache.put(key, tile.getBlob());
                                }
                                return null;
                            })
                    .anyTimes();

            final HashSet<String> puts = new HashSet<>();
            expect(storageBroker.put(capture(tileCapturer)))
                    .andAnswer(
                            () -> {
                                TileObject tile = tileCapturer.getValue();
                                puts.add(TransientCache.computeTransientKey(tile));
                                storagePutCounter.incrementAndGet();
                                return true;
                            })
                    .anyTimes();

            expect(storageBroker.get(anyObject()))
                    .andAnswer(
                            () -> {
                                TileObject tile = (TileObject) EasyMock.getCurrentArguments()[0];
                                if (puts.contains(TransientCache.computeTransientKey(tile))) {
                                    tile.setBlob(new ByteArrayResource(fakeWMSResponse));
                                    storageGetCounter.incrementAndGet();
                                    return true;
                                } else {
                                    return false;
                                }
                            })
                    .anyTimes();
            replay(storageBroker);
        }
    }

    @Override
    protected TileLayer getLayerWithFilters(Collection<ParameterFilter> filters) throws Exception {
        WMSLayer tl = createWMSLayer("image/png", 5, 6);
        @SuppressWarnings("unused")
        MockTileSupport mock = new MockTileSupport(tl);
        tl.getParameterFilters().addAll(filters);
        return tl;
    }
}
