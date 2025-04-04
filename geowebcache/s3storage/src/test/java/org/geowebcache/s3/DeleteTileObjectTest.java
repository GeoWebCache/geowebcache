package org.geowebcache.s3;

import org.geowebcache.io.Resource;
import org.geowebcache.storage.TileObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.geowebcache.s3.BulkDeleteTaskTestHelper.*;
import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class DeleteTileObjectTest {
    @Mock
    public Resource resource;

    public TileObject tileObject;

    @Before
    public void setUp() {
        tileObject = TileObject.createCompleteTileObject(LAYER_NAME, XYZ, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS, resource);
    }

    @Test
    public void testConstructor_WithDeleteTileObject_PrefixSet() {
        DeleteTileObject deleteTileObject = new DeleteTileObject(tileObject, PREFIX, false);
        assertEquals("Prefix was not set", PREFIX, deleteTileObject.getPrefix());
    }

    @Test
    public void testConstructor_WithDeleteTileObject_PrefixNull() {
        assertThrows(NullPointerException.class, () -> new DeleteTileObject(null, PREFIX, false));
    }

    @Test
    public void testConstructor_WithDeleteTileObject_PrefixEmpty() {
        DeleteTileObject deleteTileObject = new DeleteTileObject(tileObject, "", false);
        assertEquals("Prefix was not set", "", deleteTileObject.getPrefix());
    }

    @Test
    public void testConstructor_WithDeleteTileObject_TileObjectSet() {
        DeleteTileObject deleteTileObject = new DeleteTileObject(tileObject, PREFIX, false);
        assertEquals("Prefix was not set", tileObject, deleteTileObject.getTileObject());
    }

    @Test
    public void testConstructor_WithDeleteTileObject_TileObjectNull() {
        assertThrows(NullPointerException.class, () -> new DeleteTileObject(null, PREFIX, false));
    }

    @Test
    public void testConstructor_WithDeleteTileObject_SkipExistingCheckSet() {
        DeleteTileObject deleteTileObject = new DeleteTileObject(tileObject, PREFIX, true);
        assertTrue("skipExistingCheck was not set", deleteTileObject.shouldSkipExistsCheck());
    }

}
