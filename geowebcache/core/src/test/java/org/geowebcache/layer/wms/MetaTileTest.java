package org.geowebcache.layer.wms;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.eclipse.imagen.PlanarImage;
import org.geowebcache.config.DefaultGridsets;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.layer.MetaTile;
import org.geowebcache.mime.ApplicationMime;
import org.geowebcache.mime.ImageMime;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class MetaTileTest {

    GridSetBroker gridSetBroker = new GridSetBroker(Collections.singletonList(new DefaultGridsets(false, false)));

    @Before
    public void setUp() throws Exception {}

    @Test
    public void test1MetaTile() throws Exception {
        BoundingBox bbox = new BoundingBox(0, 0, 180, 90);
        int metaHeight = 1;
        int metaWidth = 1;

        GridSubset grid = GridSubsetFactory.createGridSubSet(gridSetBroker.getWorldEpsg4326(), bbox, 0, 30);

        long[] gridPos = {0, 0, 0};

        // int[] gridBounds, int[] tileGridPosition, int metaX, int metaY
        WMSMetaTile mt = new WMSMetaTile(
                null,
                grid,
                ImageMime.png,
                null,
                gridPos,
                metaWidth,
                metaHeight,
                Collections.singletonMap("test", "test1"));

        Assert.assertArrayEquals(new long[] {0, 0, 0, 0, 0}, mt.getMetaTileGridBounds());
    }

    @Test
    public void test2MetaTile() throws Exception {
        BoundingBox bbox = new BoundingBox(0, 0, 180, 90);
        int metaHeight = 3;
        int metaWidth = 3;

        GridSubset grid = GridSubsetFactory.createGridSubSet(gridSetBroker.getWorldEpsg4326(), bbox, 0, 30);

        long[] gridPos = {127, 63, 6};
        WMSMetaTile mt = new WMSMetaTile(
                null,
                grid,
                ImageMime.png,
                null,
                gridPos,
                metaWidth,
                metaHeight,
                Collections.singletonMap("test", "test1"));

        long[] solution = {126, 63, 127, 63, 6};
        Assert.assertArrayEquals(mt.getMetaTileGridBounds(), solution);
    }

    @Test
    public void test3MetaTile() throws Exception {
        BoundingBox bbox = new BoundingBox(0, 0, 20037508.34, 20037508.34);
        int metaHeight = 1;
        int metaWidth = 1;

        GridSubset grid = GridSubsetFactory.createGridSubSet(gridSetBroker.getWorldEpsg3857(), bbox, 0, 30);

        long[] gridPos = {0, 0, 0};
        WMSMetaTile mt = new WMSMetaTile(
                null,
                grid,
                ImageMime.png,
                null,
                gridPos,
                metaWidth,
                metaHeight,
                Collections.singletonMap("test", "test1"));

        long[] solution = {0, 0, 0, 0, 0};
        Assert.assertArrayEquals(mt.getMetaTileGridBounds(), solution);
    }

    @Test
    public void test4MetaTile() throws Exception {
        BoundingBox bbox = new BoundingBox(0, 0, 20037508.34, 20037508.34);

        int metaHeight = 3;
        int metaWidth = 3;

        GridSubset grid = GridSubsetFactory.createGridSubSet(gridSetBroker.getWorldEpsg3857(), bbox, 0, 30);

        long[] gridPos = {70, 70, 6};
        WMSMetaTile mt = new WMSMetaTile(
                null,
                grid,
                ImageMime.png,
                null,
                gridPos,
                metaWidth,
                metaHeight,
                Collections.singletonMap("test", "test1"));

        long[] solution = {69, 69, 63, 63, 6};
        Assert.assertArrayEquals(mt.getMetaTileGridBounds(), solution);
    }

    /** @throws Exception */
    @Test
    public void test5MetaTileGutter() throws Exception {
        BoundingBox bbox = new BoundingBox(0, 0, 180, 90);

        WMSLayer layer = createWMSLayer(bbox);

        GridSubset grid = GridSubsetFactory.createGridSubSet(gridSetBroker.getWorldEpsg4326(), bbox, 0, 30);

        // Set the gutter
        layer.gutter = 50;

        // Lets make a tile close to the edge, this should only have a gutter to west / south
        long[] gridPos = {127, 63, 6};
        WMSMetaTile mt = new WMSMetaTile(
                layer,
                grid,
                ImageMime.png,
                null,
                gridPos,
                layer.getMetaTilingFactors()[0],
                layer.getMetaTilingFactors()[1],
                Collections.singletonMap("test", "test1"));

        // The actual gutter is calculated right at construction time
        Map<String, String> wmsParams = mt.getWMSParams();
        Assert.assertEquals(layer.gutter.intValue(), mt.getGutter()[0]);
        Assert.assertEquals(layer.gutter.intValue(), mt.getGutter()[1]);
        Assert.assertEquals(0, mt.getGutter()[2]);
        Assert.assertEquals(0, mt.getGutter()[3]);

        int height = Integer.parseInt(wmsParams.get("HEIGHT"));

        Assert.assertEquals(256 + 50, height);

        long[] midGridPos = {83, 45, 6};
        mt = new WMSMetaTile(
                layer,
                grid,
                ImageMime.png,
                null,
                midGridPos,
                layer.getMetaTilingFactors()[0],
                layer.getMetaTilingFactors()[1],
                Collections.singletonMap("test", "test1"));

        // The actual gutter is calculated right at construction time
        wmsParams = mt.getWMSParams();
        Assert.assertEquals(mt.getGutter()[0], (int) layer.gutter);
        Assert.assertEquals(mt.getGutter()[1], (int) layer.gutter);
        Assert.assertEquals(mt.getGutter()[2], (int) layer.gutter);
        Assert.assertEquals(mt.getGutter()[3], (int) layer.gutter);

        height = Integer.parseInt(wmsParams.get("HEIGHT"));

        Assert.assertEquals(768 + 2 * 50, height);

        String[] coordStrs = wmsParams.get("BBOX").split(",");

        // Lets check some specific coordinates too
        Assert.assertTrue(Math.abs(Double.parseDouble(coordStrs[0]) - 47.26318359375) < 0.001);
        Assert.assertTrue(Math.abs(Double.parseDouble(coordStrs[3]) - 45.54931640625) < 0.001);
    }

    /** @throws Exception */
    @Test
    public void test6MetaTileNoGutterWithVector() throws Exception {
        BoundingBox bbox = new BoundingBox(0, 0, 180, 90);

        WMSLayer layer = createWMSLayer(bbox);

        GridSubset grid = GridSubsetFactory.createGridSubSet(gridSetBroker.getWorldEpsg4326(), bbox, 0, 30);

        // Set the gutter
        layer.gutter = 50;

        // Lets make a tile close to the edge, this should only have a gutter to west / south
        long[] gridPos = {127, 63, 6};
        WMSMetaTile mt = new WMSMetaTile(
                layer,
                grid,
                ApplicationMime.topojson,
                null,
                gridPos,
                layer.getMetaTilingFactors()[0],
                layer.getMetaTilingFactors()[1],
                Collections.singletonMap("test", "test1"));

        // The actual gutter is calculated right at construction time
        Map<String, String> wmsParams = mt.getWMSParams();
        Assert.assertEquals(0, mt.getGutter()[0]);
        Assert.assertEquals(0, mt.getGutter()[1]);
        Assert.assertEquals(0, mt.getGutter()[2]);
        Assert.assertEquals(0, mt.getGutter()[3]);

        int height = Integer.parseInt(wmsParams.get("HEIGHT"));

        Assert.assertEquals(256, height);
    }

    private WMSLayer createWMSLayer(BoundingBox layerBounds) {
        String[] urls = {"http://localhost:38080/wms"};
        List<String> formatList = new LinkedList<>();
        formatList.add("image/png");

        Map<String, GridSubset> grids = new HashMap<>();

        GridSubset grid = GridSubsetFactory.createGridSubSet(gridSetBroker.getWorldEpsg4326());

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

        layer.initialize(gridSetBroker);

        return layer;
    }

    // Testing the create tile operation with a meta tile buffer image
    @Test
    public void testCreateTileFromMetaTileBufferImage() throws Exception {
        // creating the meta tile image
        Color[][] colors = new Color[2][2];
        BufferedImage image = createBufferImageMetaTile(2, 2, 256, 512, colors);
        // testing the tiles extraction as a buffer image
        commonCreateTileFromMetaTileTest(colors, image);
    }

    // Testing the create tile operation with a meta tile planar image
    @Test
    public void testCreateTileFromMetaTilePlanarImage() throws Exception {
        // creating the meta tile image
        Color[][] colors = new Color[2][2];
        BufferedImage image = createBufferImageMetaTile(2, 2, 256, 512, colors);
        // testing the tiles extraction as a planar image
        commonCreateTileFromMetaTileTest(colors, PlanarImage.wrapRenderedImage(image));
    }

    // Helper class that given a meta tile image will extract the tiles and check that extracted
    // tiles are correct
    private void commonCreateTileFromMetaTileTest(Color[][] colors, RenderedImage metaTileImage) throws Exception {
        // creating the meta tile
        BoundingBox boundingBox = new BoundingBox(0, 0, 180, 90);
        int metaHeight = 2;
        int metaWidth = 2;
        GridSubset grid = GridSubsetFactory.createGridSubSet(gridSetBroker.getWorldEpsg4326(), boundingBox, 0, 21);
        long[] gridPos = {0, 0, 0};
        MetaTile metaTile = new MetaTile(grid, ImageMime.png, null, gridPos, metaWidth, metaHeight, null);
        metaTile.setImage(metaTileImage);
        // extracting the tiles using the create tile method
        int width = metaTile.getMetaTileWidth();
        int height = metaTile.getMetaTileHeight();
        checkImageBorderSameColor(metaTile.createTile(0, 0, width, height), colors[0][0]);
        checkImageBorderSameColor(metaTile.createTile(width, 0, width, height), colors[0][1]);
        checkImageBorderSameColor(metaTile.createTile(0, height, width, height), colors[1][0]);
        checkImageBorderSameColor(metaTile.createTile(width, height, width, height), colors[1][1]);
    }

    // Helper method that given an image and a color will check that the borders of the image are of
    // the same color
    private void checkImageBorderSameColor(RenderedImage image, Color color) throws Exception {
        if (image instanceof PlanarImage planarImage) {
            image = planarImage.getAsBufferedImage();
        }
        // extracting the borders pixels
        int width = image.getWidth();
        int height = image.getHeight();
        int[] borderA = (int[]) image.getData().getDataElements(0, 0, width, 1, null);
        int[] borderB = (int[]) image.getData().getDataElements(0, height - 1, width, 1, null);
        int[] borderC = (int[]) image.getData().getDataElements(0, 0, 1, height, null);
        int[] borderD = (int[]) image.getData().getDataElements(width - 1, 0, 1, height, null);
        int colorInt = color.getRGB();
        // comparing the borders pixels with the expected color
        for (int i = 0; i < width; i++) {
            if (borderA[i] != colorInt || borderB[i] != colorInt) {
                Assert.fail("Not the expected color.");
            }
        }
        for (int i = 0; i < height; i++) {
            if (borderC[i] != colorInt || borderD[i] != colorInt) {
                Assert.fail("Not the expected color.");
            }
        }
    }

    // Helper method that creates a random image with random colors
    private BufferedImage createBufferImageMetaTile(int rows, int columns, int height, int width, Color[][] colors) {
        Random random = new Random();
        BufferedImage image = new BufferedImage(columns * width, rows * height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                Color color = new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256));
                colors[i][j] = color;
                graphics.setColor(color);
                int x = j * width;
                int y = i * height;
                graphics.fill(new Rectangle2D.Float(x, y, width, height));
            }
        }
        graphics.dispose();
        return image;
    }
}
