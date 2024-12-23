/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Dana Lambert, Catalyst IT Ltd NZ, Copyright 2020
 */
package org.geowebcache.swift;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.storage.BlobStoreListener;
import org.geowebcache.storage.BlobStoreListenerList;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;
import org.jclouds.io.Payload;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class SwiftTileTest {

    protected SwiftTile swiftTile;
    protected BlobStoreListenerList testListeners;

    private static final String VALID_TEST_LAYER_NAME = "TestLayer";
    private static final Resource testBytes = new ByteArrayResource("1 2 3 4 5 6 test".getBytes());
    private long[] xyz = {1L, 2L, 3L};
    private Map<String, String> parameters = Collections.singletonMap("testKey", "testValue1");

    @Before
    public void setUp() throws Exception {
        createValidSwiftTile();
    }

    protected void addListener() {
        BlobStoreListener swiftListener = mock(BlobStoreListener.class);
        this.testListeners.addListener(swiftListener);
    }

    public void createValidSwiftTile() throws IOException {
        // Create tile object for use in swift blob store methods
        TileObject sampleTileObject = spy(TileObject.createCompleteTileObject(
                VALID_TEST_LAYER_NAME, xyz, "EPSG:4326", "image/jpeg", parameters, testBytes));
        when(sampleTileObject.getParametersId()).thenReturn("1234");

        swiftTile = spy(new SwiftTile(sampleTileObject));
        testListeners = spy(new BlobStoreListenerList());
    }

    @Test(expected = IOException.class)
    public void testSwiftTileWhenBlobInputStreamThrowsIOException() throws IOException {
        Resource invalidResource = mock(Resource.class);
        when(invalidResource.getSize()).thenReturn(3L);

        when(invalidResource.getInputStream()).thenThrow(IOException.class);

        TileObject sampleTileObject = TileObject.createCompleteTileObject(
                VALID_TEST_LAYER_NAME, xyz, "EPSG:4326", "image/jpeg", parameters, invalidResource);

        swiftTile = new SwiftTile(sampleTileObject);
    }

    @Test
    public void testPutWhenFormatNull() throws IOException {
        TileObject sampleTileObject = spy(TileObject.createCompleteTileObject(
                VALID_TEST_LAYER_NAME, xyz, "EPSG:4326", null, parameters, testBytes));

        try {
            swiftTile = new SwiftTile(sampleTileObject);
            Assert.fail("Null check for tile format failed.");
        } catch (NullPointerException e) {
            assertThat(e.getMessage(), is("Object Blob Format must not be null."));
        } catch (StorageException e) {
            Assert.fail("Should be throwing a NullPointerException.\n" + e);
        }
    }

    @Test
    public void testPutWhenBlobIsNull() throws IOException {
        TileObject sampleTileObject = spy(TileObject.createCompleteTileObject(
                VALID_TEST_LAYER_NAME, xyz, "EPSG:4326", null, parameters, testBytes));
        when(sampleTileObject.getBlob()).thenReturn(null);

        // Test when blob is null
        try {
            swiftTile = new SwiftTile(sampleTileObject);
            Assert.fail("Null check when blob is null failed");
        } catch (NullPointerException e) {
            assertThat(e.getMessage(), is("Object Blob must not be null."));
        } catch (StorageException e) {
            Assert.fail("Should be throwing a NullPointerException.\n" + e);
        }
    }

    @Test
    public void testGetPayloadMetadataOutputLength() throws IOException {
        Long testOutputLengthValue = 5L;
        ReflectionTestUtils.setField(swiftTile, "outputLength", testOutputLengthValue);

        try (Payload testPayload = swiftTile.getPayload()) {
            Assert.assertEquals(
                    testOutputLengthValue, testPayload.getContentMetadata().getContentLength());
        }
    }

    @Test
    public void testGetPayloadMetadataMimeType() throws IOException {
        String testBlobFormat = "image/png";
        ReflectionTestUtils.setField(swiftTile, "blobFormat", testBlobFormat);

        try (Payload testPayload = swiftTile.getPayload()) {
            Assert.assertEquals(testBlobFormat, testPayload.getContentMetadata().getContentType());
        }
    }

    @Test
    public void testSetExisted() {
        Long testNewSize = 6L;

        ReflectionTestUtils.setField(swiftTile, "existed", false);
        ReflectionTestUtils.setField(swiftTile, "oldSize", 0L);

        swiftTile.setExisted(testNewSize);
        Assert.assertTrue((boolean) ReflectionTestUtils.getField(swiftTile, "existed"));
        Assert.assertEquals(testNewSize, ReflectionTestUtils.getField(swiftTile, "oldSize"));
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
                        anyString(), anyString(), anyString(), any(), anyLong(), anyLong(), anyInt(), anyLong());
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

    @Test
    public void testTestToString() { // Parameters id is null?
        String testString = swiftTile.toString();
        String expectedString = "TestLayer, EPSG:4326, image/jpeg, 1234, xyz=1,2,3";

        Assert.assertEquals(expectedString, testString);
    }
}
