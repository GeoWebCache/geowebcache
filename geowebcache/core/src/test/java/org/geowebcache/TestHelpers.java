package org.geowebcache;

import jakarta.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.geowebcache.config.DefaultGridsets;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.seed.GWCTask;
import org.geowebcache.seed.SeedRequest;
import org.geowebcache.util.MockLockProvider;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.springframework.http.HttpStatus;

/**
 * Some common utility test functions.
 *
 * @author Ian Schneider <ischneider@opengeo.org>
 */
public class TestHelpers {

    static GridSetBroker gridSetBroker =
            new GridSetBroker(Collections.singletonList(new DefaultGridsets(false, false)));
    public static MockLockProvider mockProvider = new MockLockProvider();

    public static byte[] createFakeSourceImage(final WMSLayer layer) throws IOException {

        int tileWidth = layer.getGridSubset(gridSetBroker.getWorldEpsg4326().getName())
                .getGridSet()
                .getTileWidth();
        int tileHeight = layer.getGridSubset(gridSetBroker.getWorldEpsg4326().getName())
                .getGridSet()
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
        String[] urls = {"http://localhost:38080/wms"};
        List<String> formatList = Collections.singletonList(format);

        Map<String, GridSubset> grids = new HashMap<>();

        GridSubset grid = GridSubsetFactory.createGridSubSet(
                gridSetBroker.getWorldEpsg4326(),
                new BoundingBox(-30.0, 15.0, 45.0, 30),
                0,
                10,
                minCacheLevel,
                maxCacheLevel);

        grids.put(grid.getName(), grid);
        int[] metaWidthHeight = {3, 3};

        WMSLayer layer = new WMSLayer(
                "test:layer",
                urls,
                "aStyle",
                "test:layer",
                formatList,
                grids,
                new ArrayList<>(),
                metaWidthHeight,
                "vendorparam=true",
                false,
                null);

        layer.initialize(gridSetBroker);
        layer.setLockProvider(new MockLockProvider());

        return layer;
    }

    public static SeedRequest createRequest(WMSLayer tl, GWCTask.TYPE type, int zoomStart, int zoomStop) {
        String gridSet = tl.getGridSubsets().iterator().next();
        BoundingBox bounds = null;
        int threadCount = 1;
        String format = tl.getMimeTypes().get(0).getFormat();
        SeedRequest req =
                new SeedRequest(tl.getName(), bounds, gridSet, threadCount, zoomStart, zoomStop, format, type, null);
        return req;
    }

    /** Matcher for an {@link HttpServletResponse} that checks its status. */
    @SuppressWarnings("PMD.UseDiamondOperator")
    public static Matcher<HttpServletResponse> hasStatus(HttpStatus expected) {
        return new BaseMatcher<HttpServletResponse>() {

            @Override
            public boolean matches(Object item) {
                if (item instanceof HttpServletResponse response) {
                    return expected.equals(HttpStatus.valueOf(response.getStatus()));
                }
                return false;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Http response with status ");
                describeStatus(expected, description);
            }

            protected void describeStatus(HttpStatus status, Description description) {
                description.appendValue(status.value()).appendText(" ").appendValue(status.getReasonPhrase());
            }

            @Override
            public void describeMismatch(Object item, Description description) {
                if (item instanceof HttpServletResponse response) {
                    HttpStatus status = HttpStatus.valueOf(response.getStatus());
                    description.appendText("status was ");
                    describeStatus(status, description);
                } else {
                    description.appendText("was not an HttpServletResponse");
                }
            }
        };
    }
}
