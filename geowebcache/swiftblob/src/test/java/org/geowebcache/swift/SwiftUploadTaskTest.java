package org.geowebcache.swift;

import static org.mockito.Mockito.*;

import org.jclouds.io.Payload;
import org.jclouds.io.payloads.BaseMutableContentMetadata;
import org.jclouds.openstack.swift.v1.domain.SwiftObject;
import org.jclouds.openstack.swift.v1.features.ObjectApi;

public class SwiftUploadTaskTest extends SwiftTileTest {

    private ObjectApi testObjectApi = mock(ObjectApi.class);
    private String testKey = "TestKey";

    public void setUp() throws Exception {
        super.setUp();
    }

    public void testRunWithEmptyListeners() {
        SwiftUploadTask swiftUploadTask =
                new SwiftUploadTask(testKey, swiftTile, testListeners, testObjectApi);
        swiftUploadTask.run();

        // Testing that the function checkExisted returns early
        verify(testObjectApi, times(0)).getWithoutBody(testKey);
        verify(swiftTile, times(0)).setExisted(anyLong());
    }

    public void testRunWithNullObject() {
        when(testObjectApi.getWithoutBody(testKey)).thenReturn(null);
        addListener();

        SwiftUploadTask swiftUploadTask =
                new SwiftUploadTask(testKey, swiftTile, testListeners, testObjectApi);
        swiftUploadTask.run();

        // Testing that the function checkExisted returns early
        verify(testObjectApi, times(1)).getWithoutBody(testKey);
        verify(swiftTile, times(0)).setExisted(anyLong());
    }

    public void testRunWithValidObject() {
        SwiftObject testSwiftObject = mock(SwiftObject.class);
        Payload testPayload = mock(Payload.class);
        BaseMutableContentMetadata metadata = mock(BaseMutableContentMetadata.class);
        when(metadata.getContentLength()).thenReturn(3L);
        when(testPayload.getContentMetadata()).thenReturn(metadata);
        when(testSwiftObject.getPayload()).thenReturn(testPayload);
        when(testObjectApi.getWithoutBody(testKey)).thenReturn(testSwiftObject);
        addListener();

        SwiftUploadTask swiftUploadTask =
                new SwiftUploadTask(testKey, swiftTile, testListeners, testObjectApi);

        swiftUploadTask.run();

        // Testing that the function checkExisted uses the setExisted method
        verify(testObjectApi, times(1)).getWithoutBody(testKey);
        verify(swiftTile, times(1)).setExisted(anyLong());

        // Check that the object is being uploaded.
        verify(testObjectApi).put(eq(testKey), any(Payload.class));

        // Check that the listeners are notified.
        verify(swiftTile, times(1)).notifyListeners(testListeners);
    }
}
