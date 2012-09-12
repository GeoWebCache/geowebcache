package org.geowebcache.blobstore.file;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

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
        collisionGenerator = new FilePathGenerator(testRoot.getPath()) {
            @Override
            protected String buildKey(String parametersKvp) {
                // return a fixed key to simulate hash collisions
                return "abc";
            }
        };
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

        // first time, this will also create the path on disk
        File path = generator.tilePath(tile, ImageMime.png);
        testParameterId(path, "2314488c68b7a06b8a42afae1cc5fc1e640ec75a_0", "&style=population");
        
        // another time, it should go off the pre-calculated tile id
        path = generator.tilePath(tile, ImageMime.png);
        testParameterId(path, "2314488c68b7a06b8a42afae1cc5fc1e640ec75a_0", "&style=population");
        
        // this time with a separate tile, but same params
        tile = TileObject.createCompleteTileObject("states", new long[] {0, 0, 0}, "EPSG:2163", "png", params, null);
        path = generator.tilePath(tile, ImageMime.png);
        testParameterId(path, "2314488c68b7a06b8a42afae1cc5fc1e640ec75a_0", "&style=population");
        
        // and now a separate tile, but different params
        params.put("style", "polygon");
        tile = TileObject.createCompleteTileObject("states", new long[] {0, 0, 0}, "EPSG:2163", "png", params, null);
        path = generator.tilePath(tile, ImageMime.png);
        testParameterId(path, "c518a4ee815d2451ac0c2504abc6196c1b9b7ac8_0", "&style=polygon");
    }
    
    public void testCollisionManagement() throws Exception {
        // first time, no collisions
        Map<String, String> params = new HashMap<String, String>();
        params.put("style", "population");
        TileObject tile = TileObject.createCompleteTileObject("states", new long[] {0, 0, 0}, "EPSG:2163", "png", params, null);
        File path = collisionGenerator.tilePath(tile, ImageMime.png);
        testParameterId(path, "abc_0", "&style=population");

        // now we'll get a collision
        params = new HashMap<String, String>();
        params.put("style", "polygon");
        tile = TileObject.createCompleteTileObject("states", new long[] {0, 0, 0}, "EPSG:2163", "png", params, null);
        path = collisionGenerator.tilePath(tile, ImageMime.png);
        testParameterId(path, "abc_1", "&style=polygon");
        
        // remove the first zoom directory, assuming it was emptied somehow
        File paramCacheRoot = new File(testRoot, "states/EPSG_2163_00/abc_0");
        FileUtils.deleteDirectory(paramCacheRoot);
        
        // generate another one, it should not end up reusing the abc_0 which was removed by the user
        // (otherwise we'll end up with two sets of params having the same id in the file generator cache)
        params = new HashMap<String, String>();
        params.put("style", "hatches");
        tile = TileObject.createCompleteTileObject("states", new long[] {0, 0, 0}, "EPSG:2163", "png", params, null);
        path = collisionGenerator.tilePath(tile, ImageMime.png);
        testParameterId(path, "abc_2", "&style=hatches");
        
    }

    private void testParameterId(File path, String parameterId, String parameterKvp) throws IOException {
        File expected = new File(testRoot, "states/EPSG_2163_00_" + parameterId + "/0_0/00_00.png");
        assertEquals(expected.getPath(), path.getPath());
        File paramCacheRoot = new File(testRoot, "states/EPSG_2163_00_" + parameterId);
        assertTrue(paramCacheRoot.exists());
        File paramFile = new File(testRoot, "states/EPSG_2163_00_" + parameterId + "/parameters.txt");
        assertTrue(paramFile.exists());
        assertEquals(parameterKvp, FileUtils.readFileToString(paramFile));
    }
}