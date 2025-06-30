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
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.io.ByteSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.BlobStoreListener;
import org.geowebcache.storage.BlobStoreListenerList;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;
import org.geowebcache.util.TMSKeyBuilder;
import org.jclouds.blobstore.domain.PageSet;
import org.jclouds.io.payloads.ByteSourcePayload;
import org.jclouds.openstack.swift.v1.SwiftApi;
import org.jclouds.openstack.swift.v1.blobstore.RegionScopedBlobStoreContext;
import org.jclouds.openstack.swift.v1.blobstore.RegionScopedSwiftBlobStore;
import org.jclouds.openstack.swift.v1.domain.Container;
import org.jclouds.openstack.swift.v1.domain.ObjectList;
import org.jclouds.openstack.swift.v1.domain.SwiftObject;
import org.jclouds.openstack.swift.v1.features.BulkApi;
import org.jclouds.openstack.swift.v1.features.ObjectApi;
import org.jclouds.openstack.swift.v1.options.ListContainerOptions;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

/** Unit testing for the Swift Blobstore class */
@SuppressWarnings("DirectInvocationOnMock")
public class SwiftBlobStoreTest {

    private String TEST_KEY = "test/key";

    private SwiftBlobStore swiftBlobStore;

    @Mock
    private ObjectApi objectApi;

    private TileObject sampleTileObject;

    private TMSKeyBuilder keyBuilder;

    @Mock
    private SwiftApi swiftApi;

    @Mock
    private BulkApi bulkApi;

    private BlobStoreListenerList testListeners;

    @Mock
    private RegionScopedBlobStoreContext blobStoreContext;

    @Mock
    private RegionScopedSwiftBlobStore blobStore;

    @Mock
    private PageSet pageSet;

    private static final String VALID_TEST_LAYER_NAME = "TestLayer";
    private static final String INVALID_TEST_LAYER_NAME = "NonExistentLayer";

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {

        // Initialises the annotated mocks and spies
        MockitoAnnotations.openMocks(this);

        // Create tile object for use in swift blob store methods
        Resource bytes = new ByteArrayResource("1 2 3 4 5 6 test".getBytes());
        long[] xyz = {1L, 2L, 3L};
        Map<String, String> parameters = new HashMap<>();
        parameters.put("test param key", "test param value");
        sampleTileObject = TileObject.createCompleteTileObject(
                VALID_TEST_LAYER_NAME, xyz, "EPSG:4326", "image/jpeg", parameters, bytes);

        when(swiftApi.getObjectApi(any(), any())).thenReturn(objectApi);
        when(swiftApi.getBulkApi(any())).thenReturn(bulkApi);
        when(blobStoreContext.getBlobStore(any())).thenReturn(blobStore);

        when(blobStore.list(anyString(), any())).thenReturn(pageSet);

        // Creating config
        SwiftBlobStoreInfo config = mock(SwiftBlobStoreInfo.class);
        when(config.buildApi()).thenReturn(swiftApi);
        when(config.getBlobStore()).thenReturn(blobStoreContext);
        when(config.getContainer()).thenReturn("TestContainer");
        when(config.isValid()).thenReturn(true);

        // Creating spy object for swift blob store, keybuilder and keylisteners
        swiftBlobStore = spy(new SwiftBlobStore(config, mock(TileLayerDispatcher.class)));
        keyBuilder = spy(new TMSKeyBuilder("test_prefix", mock(TileLayerDispatcher.class)));
        testListeners = spy(new BlobStoreListenerList());

        doReturn("sample/key").when(keyBuilder).forTile(sampleTileObject);

        // Setting private class properties to be able to check state in tests
        ReflectionTestUtils.setField(swiftBlobStore, "keyBuilder", keyBuilder);
        ReflectionTestUtils.setField(swiftBlobStore, "listeners", testListeners);
    }

    @After
    public void tearDown() {
        // Should close the blob store and swift api
        this.swiftBlobStore.destroy();
    }

    @Test
    public void destroy() {
        this.swiftBlobStore.destroy();
        try {
            verify(swiftApi, times(1)).close();
            verify(blobStoreContext, times(1)).close();
        } catch (IOException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void addListener() {

        // Check if the listeners list is empty before adding any element
        assertTrue(testListeners.isEmpty());

        // Add a mock listener object
        BlobStoreListener swiftListener = mock(BlobStoreListener.class);
        this.swiftBlobStore.addListener(swiftListener);

        // Check if the listener was added to the list successfully
        ArrayList<BlobStoreListener> blobStoreListenersResult =
                (ArrayList<BlobStoreListener>) testListeners.getListeners();
        assertTrue(blobStoreListenersResult.contains(swiftListener));
    }

    @Test
    public void removeListener() {
        // Add a listener to the test listeners list and test it exists in the list
        BlobStoreListener swiftListener = mock(BlobStoreListener.class);
        this.testListeners.addListener(swiftListener);
        ArrayList<BlobStoreListener> testListenersList = (ArrayList<BlobStoreListener>) testListeners.getListeners();
        assertTrue(testListenersList.contains(swiftListener));

        // Call the blobstore method to delete the listener
        this.swiftBlobStore.removeListener(swiftListener);

        // Check if the listener was removed from the list successfully
        assertTrue(testListeners.isEmpty());
    }

    private Resource createTestResource(Long contentLen) {
        Resource testResource = mock(Resource.class);
        when(testResource.getSize()).thenReturn(contentLen);
        return testResource;
    }

    private TileObject createMockTileObject(Resource testResource) {
        // Using a mock object here to control methods and test method calls
        TileObject testTileObject = mock(TileObject.class);
        when(testTileObject.getBlob()).thenReturn(testResource);
        doReturn(TEST_KEY).when(this.keyBuilder).forTile(testTileObject);

        return testTileObject;
    }

    @Test
    public void testPutWhenFormatNull() {
        Resource testResource = createTestResource(3L);
        TileObject testTileObject = createMockTileObject(testResource);
        when(testTileObject.getBlobFormat()).thenReturn(null);

        try {
            this.swiftBlobStore.put(testTileObject);
            fail("Null check when tile format is null failed");
        } catch (NullPointerException e) {
            assertThat(e.getMessage(), is("Object Blob Format must not be null."));
        } catch (StorageException e) {
            fail("Should be throwing a NullPointerException.\n" + e);
        }

        verify(this.keyBuilder, times(0)).forTile(testTileObject);
    }

    @Test
    public void testPutWhenBlobIsNull() {
        TileObject testTileObject = createMockTileObject(mock(Resource.class));
        when(testTileObject.getBlob()).thenReturn(null);

        // Test when blob is null
        try {
            this.swiftBlobStore.put(testTileObject);
            fail("Null check when blob is null failed");
        } catch (NullPointerException e) {
            verify(testTileObject, times(1)).getBlob();
            assertThat(e.getMessage(), is("Object Blob must not be null."));
        } catch (StorageException e) {
            fail("Should be throwing a NullPointerException.\n" + e);
        }

        verify(this.keyBuilder, times(0)).forTile(testTileObject);
    }

    @Test(expected = RuntimeException.class)
    public void testPutWhenBlobIsAnInvalidMimeType() {
        Resource testResource = createTestResource(3L);
        TileObject testTileObject = createMockTileObject(testResource);
        // Test when a blob format is an invalid mime type
        when(testTileObject.getBlobFormat()).thenReturn("invalid mime type");
        try {
            this.swiftBlobStore.put(testTileObject);
            fail("Null check for grid check id failed");
        } catch (StorageException e) {
            fail("Should be throwing a RuntimeException caused by a MimeException.\n" + e);
        }
    }

    @Test
    @SuppressWarnings("PMD.CloseResource")
    public void get() throws StorageException {
        String thePayloadData = "Test Content";
        Date lastModified = new Date();
        ByteSourcePayload testByteSourcePayload = new ByteSourcePayload(new ByteSource() {
            @Override
            public InputStream openStream() {
                return new ByteArrayInputStream(thePayloadData.getBytes());
            }
        });

        SwiftObject swiftObject = mock(SwiftObject.class);
        when(swiftObject.getLastModified()).thenReturn(lastModified);
        when(swiftObject.getPayload()).thenReturn(testByteSourcePayload);

        when(objectApi.get("sample/key")).thenReturn(swiftObject);
        boolean result = this.swiftBlobStore.get(sampleTileObject);

        verify(keyBuilder, times(1)).forTile(sampleTileObject);
        verify(objectApi, times(1)).get("sample/key");
        verify(swiftObject, times(1)).getPayload();

        ByteArrayResource expectedByteArray = new ByteArrayResource(thePayloadData.getBytes());
        ByteArrayResource actualByteArray = (ByteArrayResource) sampleTileObject.getBlob();

        assertEquals(thePayloadData.length(), sampleTileObject.getBlobSize());
        assertArrayEquals(expectedByteArray.getContents(), actualByteArray.getContents());
        assertEquals(lastModified.getTime(), sampleTileObject.getCreated());
        assertTrue(result);

        when(objectApi.get("sample/key")).thenReturn(null);
        result = this.swiftBlobStore.get(sampleTileObject);
        assertFalse(result);
    }

    @Test
    @Ignore // unreliable test timing
    public void deleteByLayerName() throws InterruptedException {
        String layerPrefix = "layerPrefix";
        doReturn(layerPrefix).when(this.keyBuilder).forLayer(VALID_TEST_LAYER_NAME);
        doReturn(true).when(pageSet).isEmpty();

        verify(this.swiftBlobStore, times(0)).deleteByPath(eq(layerPrefix), any());
        verify(this.testListeners, times(0)).sendLayerDeleted(VALID_TEST_LAYER_NAME);
        assertTrue(this.swiftBlobStore.delete(VALID_TEST_LAYER_NAME));
        Thread.sleep(1500);
        verify(this.swiftBlobStore, times(1)).deleteByPath(eq(layerPrefix), any());
        verify(this.testListeners, times(1)).sendLayerDeleted(VALID_TEST_LAYER_NAME);

        // Test when layer name is null
        try {
            this.swiftBlobStore.delete((String) null);
            fail("Null check for layer name failed");
        } catch (NullPointerException e) {
            Thread.sleep(1500);
            verify(this.swiftBlobStore, times(0)).deleteByPath(eq(null), any());
            verify(this.testListeners, times(0)).sendLayerDeleted(null);
        }
    }

    @Test
    @Ignore // unreliable test timing
    public void deleteByTileRange() throws InterruptedException {
        TileRange testTileRange = mock(TileRange.class);
        MimeType mimeType = mock(MimeType.class);
        when(mimeType.getInternalName()).thenReturn("png");
        when(mimeType.getFormat()).thenReturn("image/png");
        when(testTileRange.getMimeType()).thenReturn(mock(MimeType.class));

        // Range bounds format: {{minx, maxx, miny, maxy, zoomLevel}, ...}
        long[][] rangebounds = {{1L, 2L, 1L, 2L, 1L}, {1L, 2L, 1L, 2L, 2L}, {1L, 2L, 1L, 2L, 3L}};
        TileRange realTestTileRange = new TileRange(
                VALID_TEST_LAYER_NAME,
                "test_gridset_id",
                1,
                2,
                rangebounds,
                mimeType,
                new HashMap<>(),
                "test_param_id");

        String testCoordinatesPrefix = "test/coord/prefix";
        SwiftObject testSwiftObject = mock(SwiftObject.class);

        doReturn(testCoordinatesPrefix).when(this.keyBuilder).coordinatesPrefix(testTileRange, true);
        doReturn("layer_id").when(this.keyBuilder).layerId(VALID_TEST_LAYER_NAME);

        // Test when object is null from the objectApi
        when(this.objectApi.get(testCoordinatesPrefix)).thenReturn(null);
        assertFalse(this.swiftBlobStore.delete(testTileRange));
        verify(keyBuilder, times(1)).coordinatesPrefix(testTileRange, true);
        verify(objectApi, times(1)).get(testCoordinatesPrefix);

        // Test when object is valid and listeners are empty
        when(this.objectApi.get(any())).thenReturn(testSwiftObject);
        assertTrue(this.swiftBlobStore.delete(realTestTileRange));
        Thread.sleep(500);
        verify(keyBuilder, times(1)).coordinatesPrefix(realTestTileRange, true);

        // Test that keybuilder is outputting the correct path
        verify(objectApi, times(1)).get("test_prefix/layer_id/test_gridset_id/test_param_id/");

        // Based on the tile range above this should be the resulting keys which should be
        // called with bulk delete.
        List<String> expectedKeys = Arrays.asList(
                // Zoom level 1
                "test_prefix/layer_id/test_gridset_id/test_param_id/1/1/2.png",
                // Zoom level 2
                "test_prefix/layer_id/test_gridset_id/test_param_id/2/1/2.png");
        verify(this.bulkApi, times(1)).bulkDelete(expectedKeys);

        // Test when object is valid and listeners are not empty
        BlobStoreListener testListener = mock(BlobStoreListener.class);
        testListeners.addListener(testListener);
        assertTrue(this.swiftBlobStore.delete(realTestTileRange));
        Thread.sleep(500);

        // Verify number of times called
        verify(this.swiftBlobStore, times(2)).delete(any(TileObject.class));
    }

    @Test
    @Ignore // fragile time wise, assumes a fast machine, Github actions are not always fast
    // should to checks in a loop, let a fast machine exit right away, but give enough time to
    // a slower one
    public void deleteByGridsetId() throws InterruptedException {
        String testGridSetID = "TestGridSetID";
        String testGridsetPrefix = "test/gridset/prefix";
        doReturn(testGridsetPrefix).when(this.keyBuilder).forGridset(VALID_TEST_LAYER_NAME, testGridSetID);

        // Test with a valid layer name and prefix and deletion unsuccessful
        doReturn(false).when(pageSet).isEmpty();
        assertTrue(this.swiftBlobStore.deleteByGridsetId(VALID_TEST_LAYER_NAME, testGridSetID));
        verify(this.keyBuilder, times(1)).forGridset(VALID_TEST_LAYER_NAME, testGridSetID);
        verify(this.swiftBlobStore, times(1)).deleteByPath(eq(testGridsetPrefix), any());
        Thread.sleep(500);
        verify(this.testListeners, times(0)).sendGridSubsetDeleted(VALID_TEST_LAYER_NAME, testGridSetID);

        // Test with a valid layer name and prefix and deletion successful
        doReturn(true).when(pageSet).isEmpty();
        assertTrue(this.swiftBlobStore.deleteByGridsetId(VALID_TEST_LAYER_NAME, testGridSetID));
        verify(this.keyBuilder, times(2)).forGridset(VALID_TEST_LAYER_NAME, testGridSetID);
        verify(this.swiftBlobStore, times(2)).deleteByPath(eq(testGridsetPrefix), any());
        Thread.sleep(500);
        verify(this.testListeners, times(1)).sendGridSubsetDeleted(VALID_TEST_LAYER_NAME, testGridSetID);

        // Test when layer name is null
        try {
            this.swiftBlobStore.deleteByGridsetId(null, testGridSetID);
            fail("Null check for layer name failed");
        } catch (NullPointerException e) {
            verify(this.keyBuilder, times(0)).forGridset(null, testGridSetID);
            verify(this.testListeners, times(0)).sendGridSubsetDeleted(null, testGridSetID);
        }

        // Test when grid set id is null
        try {
            this.swiftBlobStore.deleteByGridsetId(VALID_TEST_LAYER_NAME, null);
            fail("Null check for grid check id failed");
        } catch (NullPointerException e) {
            verify(this.keyBuilder, times(0)).forGridset(VALID_TEST_LAYER_NAME, null);
            verify(this.testListeners, times(0)).sendGridSubsetDeleted(VALID_TEST_LAYER_NAME, null);
        }
    }

    @Test
    @Ignore // unreliable test timing
    public void deleteByTileObject() throws InterruptedException {

        TileObject tileObjectWithNullName = mock(TileObject.class);
        when(tileObjectWithNullName.getLayerName()).thenReturn(null);

        // Test when layer name is null
        try {
            this.swiftBlobStore.delete(tileObjectWithNullName);
            fail("Null check for grid check id failed");
        } catch (NullPointerException e) {
        }

        String tilePrefix = "tilePrefix";
        doReturn(tilePrefix).when(this.keyBuilder).forTile(sampleTileObject);

        // Test when listeners are empty
        assertTrue(this.swiftBlobStore.delete(sampleTileObject));
        Thread.sleep(500);
        verify(this.swiftBlobStore, times(1)).deleteByPath(eq(tilePrefix), any());

        // Make listeners not empty
        BlobStoreListener testListener = mock(BlobStoreListener.class);
        testListeners.addListener(testListener);

        // Test when deletion successful
        doReturn(true).when(pageSet).isEmpty();
        assertTrue(this.swiftBlobStore.delete(sampleTileObject));
        Thread.sleep(500);
        verify(this.swiftBlobStore, times(2)).deleteByPath(eq(tilePrefix), any());
        verify(this.testListeners, times(1)).sendTileDeleted(sampleTileObject);

        // Test when deletion unsuccessful
        when(objectApi.get(VALID_TEST_LAYER_NAME)).thenReturn(mock(SwiftObject.class));
        doReturn(false).when(pageSet).isEmpty();
        assertTrue(this.swiftBlobStore.delete(sampleTileObject));
        Thread.sleep(500);
        verify(this.swiftBlobStore, times(3)).deleteByPath(eq(tilePrefix), any());
        verify(this.testListeners, times(1)).sendTileDeleted(sampleTileObject);
    }

    @Test
    public void rename() {
        // When old layer is null
        boolean result = this.swiftBlobStore.rename(VALID_TEST_LAYER_NAME, "NewLayerName");
        verify(objectApi, times(1)).get(VALID_TEST_LAYER_NAME);
        verify(this.testListeners, times(0)).sendLayerRenamed(VALID_TEST_LAYER_NAME, "NewLayerName");
        assertTrue(result);

        // When old layer is not null
        when(this.objectApi.get(VALID_TEST_LAYER_NAME)).thenReturn(mock(SwiftObject.class));
        result = this.swiftBlobStore.rename(VALID_TEST_LAYER_NAME, "NewLayerName");
        verify(objectApi, times(2)).get(VALID_TEST_LAYER_NAME);
        verify(this.testListeners, times(1)).sendLayerRenamed(VALID_TEST_LAYER_NAME, "NewLayerName");
        assertTrue(result);
    }

    @Test
    public void clear() {
        try {
            this.swiftBlobStore.clear();
            fail("This method should not work, it should throw a Unsupported Operation Exception");
        } catch (UnsupportedOperationException e) {
            assertThat(e.getMessage(), is("clear() should not be called"));
        }
    }

    @Test
    public void getLayerMetadata() {
        SwiftObject swiftObject = mock(SwiftObject.class);
        SwiftObject swiftObjectWithoutMetadata = mock(SwiftObject.class);

        when(objectApi.get("")).thenReturn(null);
        when(objectApi.get(INVALID_TEST_LAYER_NAME)).thenReturn(null);
        when(objectApi.get(VALID_TEST_LAYER_NAME)).thenReturn(swiftObject);
        when(objectApi.get("valid layer without metadata")).thenReturn(swiftObjectWithoutMetadata);

        Map<String, String> sampleMetadata = new HashMap<>();
        sampleMetadata.put("sample_key", "sample_value");

        when(swiftObject.getMetadata()).thenReturn(sampleMetadata);
        when(swiftObjectWithoutMetadata.getMetadata()).thenReturn(new HashMap<>());

        // Test if layer name is empty
        String result = this.swiftBlobStore.getLayerMetadata("", "sample_key");
        assertNull(result);

        // Test if layer name is invalid
        result = this.swiftBlobStore.getLayerMetadata(INVALID_TEST_LAYER_NAME, "sample_key");
        assertNull(result);

        // Test if metadata is null
        result = this.swiftBlobStore.getLayerMetadata("valid layer name without metadata", "sample_key");
        assertNull(result);

        // Test when layer name is valid
        result = this.swiftBlobStore.getLayerMetadata(VALID_TEST_LAYER_NAME, "sample_key");
        verify(objectApi, times(1)).get(VALID_TEST_LAYER_NAME);
        assertEquals("sample_value", result);

        // Test when layer name is valid and key is invalid
        result = this.swiftBlobStore.getLayerMetadata(VALID_TEST_LAYER_NAME, "");
        verify(objectApi, times(1)).get("");
        assertNull(result);
    }

    @Test
    public void putLayerMetadata() {

        // Test when layer is null that the method does nothing
        assertNull(this.objectApi.get(VALID_TEST_LAYER_NAME)); // null before
        swiftBlobStore.putLayerMetadata(VALID_TEST_LAYER_NAME, "test_key", "test_value");
        assertNull(this.objectApi.get(VALID_TEST_LAYER_NAME)); // null after therefore no metadata.

        // Test when layer exists but there is no existing metadata
        SwiftObject layer = mock(SwiftObject.class);
        when(objectApi.get(VALID_TEST_LAYER_NAME)).thenReturn(layer);

        swiftBlobStore.putLayerMetadata(VALID_TEST_LAYER_NAME, "test_key", "test_value");
        verify(layer, times(1)).getMetadata();

        Map<String, String> newMetadata = new HashMap<>();
        newMetadata.put("test_key", "test_value");
        verify(objectApi, times(1)).updateMetadata(VALID_TEST_LAYER_NAME, newMetadata);

        // Test method when there is existing metadata such that it gets added to
        Map<String, String> existingMetadata = new HashMap<>();
        existingMetadata.put("sample_key", "sample_value");
        when(layer.getMetadata()).thenReturn(existingMetadata);

        swiftBlobStore.putLayerMetadata(VALID_TEST_LAYER_NAME, "test_key", "test_value");
        verify(layer, times(2)).getMetadata();

        Map<String, String> updatedMetadata = new HashMap<>();
        updatedMetadata.put("test_key", "test_value");
        updatedMetadata.put("sample_key", "sample_value");
        verify(objectApi, times(1)).updateMetadata(VALID_TEST_LAYER_NAME, updatedMetadata);
    }

    @Test
    public void layerExists() {
        SwiftObject swiftObject = mock(SwiftObject.class);

        // Test when layer does exist
        when(objectApi.get(VALID_TEST_LAYER_NAME)).thenReturn(swiftObject);
        assertTrue(swiftBlobStore.layerExists(VALID_TEST_LAYER_NAME));

        // Test when layer doesn't exist
        when(objectApi.get("layer which doesn't exist")).thenReturn(null);
        assertFalse(swiftBlobStore.layerExists("layer which doesn't exist"));
    }

    @Test
    public void getParametersMapping() {

        // Setting up mock things
        String prefixPath = "sample/prefix/path";
        String testObjectName = "test object";

        SwiftObject swiftObject = mock(SwiftObject.class);
        when(swiftObject.getName()).thenReturn(testObjectName);
        List<SwiftObject> swiftObjects = new ArrayList<>();
        swiftObjects.add(swiftObject);

        ObjectList swiftObjectList = ObjectList.create(swiftObjects, mock(Container.class));
        ListContainerOptions options = new ListContainerOptions();
        options.prefix(prefixPath);
        when(this.objectApi.list(options)).thenReturn(swiftObjectList);
        doReturn(prefixPath).when(this.keyBuilder).parametersMetadataPrefix(VALID_TEST_LAYER_NAME);

        // Testing that the parameter mapping is correct when there is a valid layer but no
        // metadata.
        Map<String, Optional<Map<String, String>>> testResult =
                swiftBlobStore.getParametersMapping(VALID_TEST_LAYER_NAME);
        verify(keyBuilder, times(1)).parametersMetadataPrefix(VALID_TEST_LAYER_NAME);
        verify(objectApi, times(1)).list(options);

        Map<String, Optional<Map<String, String>>> expectedResult = new HashMap<>();
        expectedResult.put(testObjectName, Optional.ofNullable(new HashMap<>()));
        assertEquals(expectedResult, testResult);

        // Testing that the parameter mapping is correct when there is a valid layer and valid
        // metadata.
        Map<String, String> objectMetadata = new HashMap<>();
        objectMetadata.put("test_key", "test_value");
        when(swiftObject.getMetadata()).thenReturn(objectMetadata);

        testResult = swiftBlobStore.getParametersMapping(VALID_TEST_LAYER_NAME);

        // Check if the correct methods were called
        verify(keyBuilder, times(2)).parametersMetadataPrefix(VALID_TEST_LAYER_NAME);
        verify(objectApi, times(2)).list(options);

        // Check if the return value is as expected
        Map<String, Optional<Map<String, String>>> expectedResultWithMetadata = new HashMap<>();
        expectedResultWithMetadata.put(testObjectName, Optional.of(objectMetadata));
        assertEquals(expectedResultWithMetadata, testResult);
    }

    @Test
    public void deleteByParametersId() {
        String testParametersId = "testParamId";

        // Test when layer name is null
        try {
            this.swiftBlobStore.deleteByParametersId(null, testParametersId);
            fail("Null check for layer name failed");
        } catch (NullPointerException e) {
            assertThat(e.getMessage(), is("layerName"));
        }

        // Test when parameters id is null
        try {
            this.swiftBlobStore.deleteByParametersId(VALID_TEST_LAYER_NAME, null);
            fail("Null check for parameters id failed");
        } catch (NullPointerException e) {
            assertThat(e.getMessage(), is("parametersId"));
        }

        Set<String> dummyParamsPrefixes = new HashSet<>(Arrays.asList("prefix/one", "prefix/two"));
        doReturn(dummyParamsPrefixes).when(this.keyBuilder).forParameters(VALID_TEST_LAYER_NAME, testParametersId);

        // Test outcome when all deletions are successful
        doReturn(true).when(this.swiftBlobStore).deleteByPath("prefix/one");
        doReturn(true).when(this.swiftBlobStore).deleteByPath("prefix/two");
        boolean outcome = this.swiftBlobStore.deleteByParametersId(VALID_TEST_LAYER_NAME, testParametersId);
        verify(this.swiftBlobStore, times(1)).deleteByPath("prefix/one");
        verify(this.swiftBlobStore, times(1)).deleteByPath("prefix/two");
        verify(this.testListeners, times(1)).sendParametersDeleted(VALID_TEST_LAYER_NAME, testParametersId);
        assertTrue(outcome);

        // Test outcome when one of the deletion fails
        doReturn(false).when(this.swiftBlobStore).deleteByPath("prefix/one");
        outcome = this.swiftBlobStore.deleteByParametersId(VALID_TEST_LAYER_NAME, testParametersId);
        verify(this.swiftBlobStore, times(2)).deleteByPath("prefix/one");
        verify(this.swiftBlobStore, times(2)).deleteByPath("prefix/two");
        verify(this.testListeners, times(1)).sendParametersDeleted(VALID_TEST_LAYER_NAME, testParametersId);
        assertFalse(outcome);
    }

    @Test
    @Ignore // unreliable test timing
    public void deleteWhenUploadExists() throws Exception {
        BlockingQueue<Runnable> taskQueue = spy(new LinkedBlockingQueue<>(1000));
        ThreadPoolExecutor executor = spy(new ThreadPoolExecutor(
                1, 1, 60L, TimeUnit.SECONDS, taskQueue, new ThreadPoolExecutor.CallerRunsPolicy()));

        // Must create three runnables
        //    1) consumed immediately which blocks for a time
        //    2) a mock upload to cancel when we delete the path being uploaded to
        //    3) a mock upload to proceed as its not on the same path
        Runnable mockUpload1 = () -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
            }
        };
        SwiftUploadTask mockUpload2 = mock(SwiftUploadTask.class);
        SwiftUploadTask mockUpload3 = mock(SwiftUploadTask.class);

        // Matching upload where getKey() returns a file in the delete path
        when(mockUpload2.getKey()).thenReturn("path/tile.png");

        // Non-matching upload where getKey() returns a different path
        when(mockUpload3.getKey()).thenReturn("anotherpath/tile.png");

        ReflectionTestUtils.setField(swiftBlobStore, "taskQueue", taskQueue);
        ReflectionTestUtils.setField(swiftBlobStore, "executor", executor);

        // Ensure queue is empty and no tasks have been run
        verify(executor, times(0)).execute(any(Runnable.class));
        assertTrue(taskQueue.isEmpty());

        // Queue runnables
        executor.execute(mockUpload1);
        executor.execute(mockUpload2);
        executor.execute(mockUpload3);

        // Ensure first task is consumed while second is waiting
        assertEquals(2, taskQueue.size());
        assertFalse(taskQueue.contains(mockUpload1)); // Should be executing
        assertTrue(taskQueue.contains(mockUpload2)); // Should be enqueued
        assertTrue(taskQueue.contains(mockUpload3)); // Should be enqueued

        // Deletion routine which cancels upload tasks destined for the same path
        swiftBlobStore.deleteByPath("path");

        // Assert mockUpload2 is cancelled but mockUpload3 is not
        verify(taskQueue, times(1)).remove(eq(mockUpload2));
        verify(taskQueue, times(0)).remove(eq(mockUpload3));
        verify(executor, times(1)).execute(any(SwiftDeleteTask.class));
        assertEquals(2, taskQueue.size()); // mockUpload3 and a SwiftDeleteTask as above
        assertFalse(taskQueue.contains(mockUpload1));
        assertFalse(taskQueue.contains(mockUpload2));
        assertTrue(taskQueue.contains(mockUpload3));
    }
}
