package org.geowebcache.blobstore.file;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.blobstore.file.FilePathGenerator;

public class FilePathGeneratorTest extends TestCase {
    
    FilePathGenerator generator;
    FilePathGenerator collisionGenerator;
    File testRoot;

    @Override
    protected void setUp() throws Exception {
        testRoot = new File("./target/pathGeneratorTests");
        if(testRoot.exists()) {
            testRoot.delete();
            FileUtils.deleteDirectory(testRoot);
        }
        testRoot.mkdir();
        
        generator = new FilePathGenerator(testRoot.getPath());
        collisionGenerator = new FilePathGenerator(testRoot.getPath());
    }

    public void testPathNoParams() throws Exception {
        TileObject tile = TileObject.createCompleteTileObject("states", new long[] {0, 0, 0}, "EPSG:2163", "png", null, null);
        File path = generator.tilePath(tile, ImageMime.png);
        
        File expected = new File(testRoot, "states/EPSG_2163_00/0_0/00_00.png");
        assertEquals(expected, path);
    }
    
    public void testPathWithParams() throws Exception {
        Map<String, String> params = new HashMap<String, String>();
        params.put("style", "population");
        TileObject tile = TileObject.createCompleteTileObject("states", new long[] {0, 0, 0}, "EPSG:2163", "png", params, null);
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
        assertEquals(expected.getPath(), path.getPath());
    }
}