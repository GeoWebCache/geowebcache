package org.geowebcache.swift;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import junit.framework.TestCase;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.storage.BlobStoreListener;
import org.geowebcache.storage.BlobStoreListenerList;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;
import org.jclouds.io.Payload;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class SwiftTileTest extends TestCase {

    protected SwiftTile swiftTile;
    protected BlobStoreListenerList testListeners;

    private static final String VALID_TEST_LAYER_NAME = "TestLayer";
    private static final Resource testBytes = new ByteArrayResource("1 2 3 4 5 6 test".getBytes());
    private long[] xyz = {1L, 2L, 3L};
    private Map<String, String> parameters = Collections.singletonMap("testKey", "testValue1");

    public void setUp() throws Exception {
        super.setUp();
        createValidSwiftTile();
    }

    protected void addListener() {
        BlobStoreListener swiftListener = mock(BlobStoreListener.class);
        this.testListeners.addListener(swiftListener);
    }

    public void createValidSwiftTile() throws IOException {
        // Create tile object for use in swift blob store methods
        TileObject sampleTileObject =
                spy(
                        TileObject.createCompleteTileObject(
                                VALID_TEST_LAYER_NAME,
                                xyz,
                                "EPSG:4326",
                                "image/jpeg",
                                parameters,
                                testBytes));
        when(sampleTileObject.getParametersId()).thenReturn("1234");

        swiftTile = spy(new SwiftTile(sampleTileObject));
        testListeners = spy(new BlobStoreListenerList());
    }

    // TODO: There must be a better way of achieving this: there needs to be some handing of the
    // IOExceptions from getInputStream()
    //       and SwiftTile constructor. Throwing the exception does not work with the expected
    // exception while putting try/catch seems
    //       messy. Not sure if there is a better way of achieving this though?
    @Test(expected = IOException.class)
    public void testSwiftTileWhenBlobInputStreamThrowsIOException() {
        Resource invalidResource = mock(Resource.class);
        when(invalidResource.getSize()).thenReturn(3L);

        try {
            when(invalidResource.getInputStream()).thenThrow(IOException.class);

            TileObject sampleTileObject =
                    TileObject.createCompleteTileObject(
                            VALID_TEST_LAYER_NAME,
                            xyz,
                            "EPSG:4326",
                            "image/jpeg",
                            parameters,
                            invalidResource);

            swiftTile = new SwiftTile(sampleTileObject);
        } catch (IOException e) {
        }
    }

    @Test
    public void testPutWhenFormatNull() throws IOException {
        TileObject sampleTileObject =
                spy(
                        TileObject.createCompleteTileObject(
                                VALID_TEST_LAYER_NAME,
                                xyz,
                                "EPSG:4326",
                                null,
                                parameters,
                                testBytes));

        try {
            swiftTile = new SwiftTile(sampleTileObject);
            fail("Null check for tile format failed.");
        } catch (NullPointerException e) {
            assertThat(e.getMessage(), is("Object Blob Format must not be null."));
        } catch (StorageException e) {
            fail("Should be throwing a NullPointerException.\n" + e);
        }
    }

    @Test
    public void testPutWhenBlobIsNull() throws IOException {
        TileObject sampleTileObject =
                spy(
                        TileObject.createCompleteTileObject(
                                VALID_TEST_LAYER_NAME,
                                xyz,
                                "EPSG:4326",
                                null,
                                parameters,
                                testBytes));
        when(sampleTileObject.getBlob()).thenReturn(null);

        // Test when blob is null
        try {
            swiftTile = new SwiftTile(sampleTileObject);
            fail("Null check when blob is null failed");
        } catch (NullPointerException e) {
            assertThat(e.getMessage(), is("Object Blob must not be null."));
        } catch (StorageException e) {
            fail("Should be throwing a NullPointerException.\n" + e);
        }
    }

    @Test
    public void testGetPayloadMetadataOutputLength() {
        Long testOutputLengthValue = 5L;
        ReflectionTestUtils.setField(swiftTile, "outputLength", testOutputLengthValue);

        Payload testPayload = swiftTile.getPayload();
        assertEquals(testOutputLengthValue, testPayload.getContentMetadata().getContentLength());
    }

    @Test
    public void testGetPayloadMetadataMimeType() {
        String testBlobFormat = "image/png";
        ReflectionTestUtils.setField(swiftTile, "blobFormat", testBlobFormat);

        Payload testPayload = swiftTile.getPayload();
        assertEquals(testBlobFormat, testPayload.getContentMetadata().getContentType());
    }

    public void testSetExisted() {
        Long testNewSize = 6L;

        ReflectionTestUtils.setField(swiftTile, "existed", false);
        ReflectionTestUtils.setField(swiftTile, "oldSize", 0L);

        swiftTile.setExisted(testNewSize);
        assertTrue((boolean) ReflectionTestUtils.getField(swiftTile, "existed"));
        assertEquals(testNewSize, ReflectionTestUtils.getField(swiftTile, "oldSize"));
    }

    private void checkListenersNotifications(int sendTileUpdatedTimes, int sendTileStoredTimes) {
        verify(testListeners, times(sendTileUpdatedTimes))
                .sendTileUpdated(
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        anyLong(),
                        anyLong(),
                        anyInt(),
                        anyLong(),
                        anyLong());
        verify(testListeners, times(sendTileStoredTimes))
                .sendTileStored(
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        anyLong(),
                        anyLong(),
                        anyInt(),
                        anyLong());
    }

    @Test
    public void testNotifyListenersWhenEmptyAndExisted() {
        ReflectionTestUtils.setField(swiftTile, "existed", true);
        swiftTile.notifyListeners(testListeners);

        // Listeners are not notified
        checkListenersNotifications(0, 0);
    }

    @Test
    public void testNotifyListenersWhenEmptyAndNotExisted() {
        ReflectionTestUtils.setField(swiftTile, "existed", false);
        swiftTile.notifyListeners(testListeners);

        // Listeners are not notified
        checkListenersNotifications(0, 0);
    }

    @Test
    public void testNotifyListenersWhenNotEmptyAndExisted() {
        ReflectionTestUtils.setField(swiftTile, "existed", true);
        addListener();
        swiftTile.notifyListeners(testListeners);

        // Listeners are notified that the tile is updated.
        checkListenersNotifications(1, 0);
    }

    @Test
    public void testNotifyListenersWhenNotEmptyAndNotExisted() {
        ReflectionTestUtils.setField(swiftTile, "existed", false);
        addListener();
        swiftTile.notifyListeners(testListeners);

        // Listeners are notified that the tile is stored
        checkListenersNotifications(0, 1);
    }

    public void testTestToString() { // Parameters id is null?
        String testString = swiftTile.toString();
        String expectedString = "TestLayer, EPSG:4326, image/jpeg, 1234, xyz=1,2,3";

        assertEquals(expectedString, testString);
    }
}
