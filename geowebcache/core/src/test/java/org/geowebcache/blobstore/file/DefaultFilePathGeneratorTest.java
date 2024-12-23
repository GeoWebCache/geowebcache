package org.geowebcache.blobstore.file;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.DefaultGridsets;
import org.geowebcache.grid.Grid;
import org.geowebcache.grid.GridSet;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;
import org.geowebcache.storage.blobstore.file.DefaultFilePathFilter;
import org.geowebcache.storage.blobstore.file.DefaultFilePathGenerator;
import org.geowebcache.storage.blobstore.file.FilePathGenerator;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DefaultFilePathGeneratorTest {

    FilePathGenerator generator;
    File testRoot;

    @Before
    public void setUp() throws Exception {
        testRoot = new File("./target/pathGeneratorTests");
        if (testRoot.exists()) {
            testRoot.delete();
            FileUtils.deleteDirectory(testRoot);
        }
        testRoot.mkdir();

        generator = new DefaultFilePathGenerator(testRoot.getPath());
    }

    @After
    public void tearDown() throws Exception {
        FileUtils.deleteDirectory(testRoot);
    }

    @Test
    public void testPathNoParams() throws Exception {
        TileObject tile =
                TileObject.createCompleteTileObject("states", new long[] {0, 0, 0}, "EPSG:2163", "png", null, null);
        File path = generator.tilePath(tile, ImageMime.png);

        File expected = new File(testRoot, "states/EPSG_2163_00/0_0/00_00.png");
        Assert.assertEquals(expected, path);
    }

    @Test
    public void testPathWithParams() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("style", "population");
        TileObject tile =
                TileObject.createCompleteTileObject("states", new long[] {0, 0, 0}, "EPSG:2163", "png", params, null);
        String sha1 = DigestUtils.sha1Hex("?style=population");

        // first time, this will also create the path on disk
        File path = generator.tilePath(tile, ImageMime.png);
        testParameterId(path, sha1, "?style=population");

        // another time, it should go off the pre-calculated tile id
        path = generator.tilePath(tile, ImageMime.png);
        testParameterId(path, sha1, "?style=population");

        // this time with a separate tile, but same params
        tile = TileObject.createCompleteTileObject("states", new long[] {0, 0, 0}, "EPSG:2163", "png", params, null);
        path = generator.tilePath(tile, ImageMime.png);
        testParameterId(path, sha1, "?style=population");

        // and now a separate tile, but different params
        params.put("style", "polygon");
        tile = TileObject.createCompleteTileObject("states", new long[] {0, 0, 0}, "EPSG:2163", "png", params, null);
        path = generator.tilePath(tile, ImageMime.png);
        sha1 = DigestUtils.sha1Hex("?style=polygon");
        testParameterId(path, sha1, "?style=polygon");
    }

    private void testParameterId(File path, String parameterId, String parameterKvp) throws IOException {
        File expected = new File(testRoot, "states/EPSG_2163_00_" + parameterId + "/0_0/00_00.png");
        Assert.assertEquals(expected.getPath(), path.getPath());
    }

    @Test
    public void testPathGeneratorFilterConsistency4326() throws GeoWebCacheException, IOException {
        GridSet gridSet4326 = new DefaultGridsets(true, true).worldEpsg4326();
        assertPathGeneratorFilterConsistency(gridSet4326);
    }

    @Test
    public void testPathGeneratorFilterConsistency3857() throws GeoWebCacheException, IOException {
        GridSet gridSet3857 = new DefaultGridsets(true, true).worldEpsg3857();
        assertPathGeneratorFilterConsistency(gridSet3857);
    }

    public void assertPathGeneratorFilterConsistency(GridSet gridSet4326) throws GeoWebCacheException, IOException {
        // scan a few zoom levels, odd and even
        for (int z = 0; z < 5; z++) {
            Grid grid = gridSet4326.getGrid(z);
            for (int y = 0; y < grid.getNumTilesHigh(); y++) {
                for (int x = 0; x < grid.getNumTilesWide(); x++) {
                    TileObject tile = TileObject.createCompleteTileObject(
                            "states", new long[] {x, y, z}, gridSet4326.getName(), "png", null, null);
                    File file = generator.tilePath(tile, ImageMime.png);
                    // create the file
                    if (!file.getParentFile().exists()) {
                        Assert.assertTrue(file.getParentFile().mkdirs());
                    }
                    Assert.assertTrue(file.createNewFile());
                    TileRange tr = new TileRange(
                            "states", gridSet4326.getName(), z, z, new long[][] {{x, y, x, y, z}}, ImageMime.png, null);
                    DefaultFilePathFilter filter = new DefaultFilePathFilter(tr);
                    // assert the file and its parents are accepted
                    File gridsetFolder = file.getParentFile().getParentFile();
                    Assert.assertTrue(filter.accept(gridsetFolder.getParentFile(), gridsetFolder.getName()));
                    File intermediateFolder = file.getParentFile();
                    Assert.assertTrue(filter.accept(intermediateFolder.getParentFile(), gridsetFolder.getName()));
                    Assert.assertTrue(filter.accept(file.getParentFile(), file.getName()));
                }
            }
        }
    }
}
