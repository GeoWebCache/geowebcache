package org.geowebcache.blobstore.file;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.geowebcache.config.DefaultGridsets;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.blobstore.file.FilePathGenerator;
import org.geowebcache.storage.blobstore.file.XYZFilePathGenerator;
import org.geowebcache.storage.blobstore.file.XYZFilePathGenerator.Convention;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ZXYFilePathGeneratorTest {

    FilePathGenerator generator;
    File testRoot;

    @Parameterized.Parameter
    public Convention convention;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {{Convention.TMS}, {Convention.XYZ}});
    }

    @Before
    public void setUp() throws Exception {
        testRoot = new File("./target/pathGeneratorTests");
        if (testRoot.exists()) {
            testRoot.delete();
            FileUtils.deleteDirectory(testRoot);
        }
        testRoot.mkdir();

        TileLayerDispatcher layers = createMock(TileLayerDispatcher.class);
        String name = "states";
        TileLayer mock = createMock(name, TileLayer.class);
        expect(mock.getName()).andStubReturn(name);
        expect(mock.getId()).andStubReturn(name);
        GridSet wgs84Grid = new DefaultGridsets(false, false).worldEpsg4326();
        expect(mock.getGridSubset("EPSG:4326")).andStubReturn(GridSubsetFactory.createGridSubSet(wgs84Grid));
        expect(mock.getMimeTypes()).andStubReturn(Arrays.asList(org.geowebcache.mime.ImageMime.png));
        expect(layers.getTileLayer(eq(name))).andStubReturn(mock);
        replay(layers, mock);

        generator = new XYZFilePathGenerator(testRoot.getPath(), layers, convention);
    }

    @Test
    public void testPathNoParams() throws Exception {
        TileObject tile =
                TileObject.createCompleteTileObject("states", new long[] {0, 0, 0}, "EPSG:4326", "png", null, null);
        File path = generator.tilePath(tile, ImageMime.png);

        File expected = new File(testRoot, "states/EPSG_4326/0/0/0.png");
        assertEquals(expected, path);
    }

    @Test
    public void testPathWithParams() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("style", "population");
        TileObject tile =
                TileObject.createCompleteTileObject("states", new long[] {0, 0, 0}, "EPSG:4326", "png", params, null);
        String sha1 = DigestUtils.sha1Hex("?style=population");

        // first time, this will also create the path on disk
        File path = generator.tilePath(tile, ImageMime.png);
        testParameterId(path, sha1, "?style=population");

        // another time, it should go off the pre-calculated tile id
        path = generator.tilePath(tile, ImageMime.png);
        testParameterId(path, sha1, "?style=population");

        // this time with a separate tile, but same params
        tile = TileObject.createCompleteTileObject("states", new long[] {0, 0, 0}, "EPSG:4326", "png", params, null);
        path = generator.tilePath(tile, ImageMime.png);
        testParameterId(path, sha1, "?style=population");

        // and now a separate tile, but different params
        params.put("style", "polygon");
        tile = TileObject.createCompleteTileObject("states", new long[] {0, 0, 0}, "EPSG:4326", "png", params, null);
        path = generator.tilePath(tile, ImageMime.png);
        sha1 = DigestUtils.sha1Hex("?style=polygon");
        testParameterId(path, sha1, "?style=polygon");
    }

    @Test
    public void testPathTMS() throws Exception {
        Assume.assumeTrue(convention == Convention.TMS);

        TileObject tile =
                TileObject.createCompleteTileObject("states", new long[] {0, 0, 2}, "EPSG:4326", "png", null, null);
        File path = generator.tilePath(tile, ImageMime.png);

        File expected = new File(testRoot, "states/EPSG_4326/2/0/0.png");
        assertEquals(expected, path);
    }

    @Test
    public void testPathSlippy() throws Exception {
        Assume.assumeTrue(convention == Convention.XYZ);

        TileObject tile =
                TileObject.createCompleteTileObject("states", new long[] {0, 0, 2}, "EPSG:4326", "png", null, null);
        File path = generator.tilePath(tile, ImageMime.png);

        File expected = new File(testRoot, "states/EPSG_4326/2/0/3.png");
        assertEquals(expected, path);
    }

    private void testParameterId(File path, String parameterId, String parameterKvp) throws IOException {
        File expected = new File(testRoot, "states/EPSG_4326_" + parameterId + "/0/0/0.png");
        assertEquals(expected.getPath(), path.getPath());
    }
}
