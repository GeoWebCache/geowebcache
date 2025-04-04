package org.geowebcache.s3;

import org.junit.Test;

import static org.geowebcache.s3.BulkDeleteTaskTestHelper.*;
import static org.junit.Assert.*;

public class DeleteTileLayerTest {
    private static final String PATH_WITH_PREFIX = "prefix/layer-id/";
    private static final String PATH_WITHOUT_PREFIX = "/layer-id/";

    @Test
    public void testConstructor_WithDeleteTileLayer_PrefixSet() {
        DeleteTileLayer deleteTileLayer = new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME);
        assertEquals("Prefix was not set", PREFIX, deleteTileLayer.getPrefix());
    }

    @Test
    public void testConstructor_WithDeleteTileLayer_PrefixNull() {
        assertThrows(NullPointerException.class, () -> new DeleteTileLayer(null, BUCKET, LAYER_ID, LAYER_NAME));
    }

    @Test
    public void testConstructor_WithDeleteTileLayer_PrefixEmpty() {
        DeleteTileLayer deleteTileLayer = new DeleteTileLayer("", BUCKET, LAYER_ID, LAYER_NAME);
        assertEquals("Prefix was not set", "", deleteTileLayer.getPrefix());
    }

    @Test
    public void testConstructor_WithDeleteTileLayer_BucketSet() {
        DeleteTileLayer deleteTileLayer = new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME);
        assertEquals("Bucket was not set", BUCKET, deleteTileLayer.getBucket());
    }

    @Test
    public void testConstructor_WithDeleteTileLayer_BucketNull() {
        assertThrows(NullPointerException.class, () -> new DeleteTileLayer(PREFIX, null, LAYER_ID, LAYER_NAME));
    }

    @Test
    public void testConstructor_WithDeleteTileLayer_BucketEmpty() {
        assertThrows(IllegalArgumentException.class, () -> new DeleteTileLayer(PREFIX, "", LAYER_ID, LAYER_NAME));
    }

    @Test
    public void testConstructor_WithDeleteTileLayer_LayerId() {
        DeleteTileLayer deleteTileLayer = new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME);
        assertEquals("LayerId was not set", LAYER_ID, deleteTileLayer.getLayerId());
    }

    @Test
    public void testConstructor_WithDeleteTileLayer_LayerIdNull() {
        assertThrows(NullPointerException.class, () -> new DeleteTileLayer(PREFIX, BUCKET, null, LAYER_NAME));
    }

    @Test
    public void testConstructor_WithDeleteTileLayer_LayerIdEmpty() {
        assertThrows(IllegalArgumentException.class, () -> new DeleteTileLayer(PREFIX, BUCKET, "", LAYER_NAME));
    }


    @Test
    public void testConstructor_WithDeleteTileLayer_LayerName() {
        DeleteTileLayer deleteTileLayer = new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME);
        assertEquals("LayerName was not set", LAYER_NAME, deleteTileLayer.getLayerName());
    }

    @Test
    public void testConstructor_WithDeleteTileLayer_LayerNameNull() {
        assertThrows(NullPointerException.class, () -> new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, null));
    }

    @Test
    public void testConstructor_WithDeleteTileLayer_LayerNameEmpty() {
        assertThrows(IllegalArgumentException.class, () -> new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, ""));
    }


    @Test
    public void testConstructor_WithDeleteTileLayer_PathWithPrefix() {
        DeleteTileLayer deleteTileLayer = new DeleteTileLayer(PREFIX, BUCKET, LAYER_ID, LAYER_NAME);
        assertEquals("Path with prefix is wrong", PATH_WITH_PREFIX, deleteTileLayer.path());
    }

    @Test
    public void testConstructor_WithDeleteTileLayer_PathWithoutPrefix() {
        DeleteTileLayer deleteTileLayer = new DeleteTileLayer("", BUCKET, LAYER_ID, LAYER_NAME);
        assertEquals("Path without prefix is wrong", PATH_WITHOUT_PREFIX, deleteTileLayer.path());
    }

}
