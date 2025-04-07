package org.geowebcache.s3.delete;

import org.geowebcache.io.Resource;
import org.geowebcache.storage.TileObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.geowebcache.s3.delete.BulkDeleteTaskTestHelper.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

@RunWith(MockitoJUnitRunner.class)
public class DeleteTileObjectTest {
    @Mock
    public Resource resource;

    public TileObject tileObject;

    @Before
    public void setUp() {
        tileObject =
                TileObject.createCompleteTileObject(LAYER_NAME, XYZ, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS, resource);
    }

    @Test
    public void testConstructor_WithDeleteTileObject_PrefixSet() {
        DeleteTileObject deleteTileObject = new DeleteTileObject(tileObject, PREFIX);
        assertEquals("Prefix was not set", PREFIX, deleteTileObject.getPrefix());
    }

    @Test
    public void testConstructor_WithDeleteTileObject_PrefixNull() {
        assertThrows(NullPointerException.class, () -> new DeleteTileObject(null, PREFIX));
    }

    @Test
    public void testConstructor_WithDeleteTileObject_PrefixEmpty() {
        DeleteTileObject deleteTileObject = new DeleteTileObject(tileObject, "");
        assertEquals("Prefix was not set", "", deleteTileObject.getPrefix());
    }

    @Test
    public void testConstructor_WithDeleteTileObject_TileObjectSet() {
        DeleteTileObject deleteTileObject = new DeleteTileObject(tileObject, PREFIX);
        assertEquals("Prefix was not set", tileObject, deleteTileObject.getTileObject());
    }

    @Test
    public void testConstructor_WithDeleteTileObject_TileObjectNull() {
        assertThrows(NullPointerException.class, () -> new DeleteTileObject(null, PREFIX));
    }
}
