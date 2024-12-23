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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jclouds.io.Payload;
import org.jclouds.io.payloads.BaseMutableContentMetadata;
import org.jclouds.openstack.swift.v1.domain.SwiftObject;
import org.jclouds.openstack.swift.v1.features.ObjectApi;
import org.junit.Test;

public class SwiftUploadTaskTest extends SwiftTileTest {

    private ObjectApi testObjectApi = mock(ObjectApi.class);
    private String testKey = "TestKey";

    @Test
    public void testRunWithEmptyListeners() {
        SwiftUploadTask swiftUploadTask = new SwiftUploadTask(testKey, swiftTile, testListeners, testObjectApi);
        swiftUploadTask.run();

        // Testing that the function checkExisted returns early
        verify(testObjectApi, times(0)).getWithoutBody(testKey);
        verify(swiftTile, times(0)).setExisted(anyLong());
    }

    @Test
    public void testRunWithNullObject() {
        when(testObjectApi.getWithoutBody(testKey)).thenReturn(null);
        addListener();

        SwiftUploadTask swiftUploadTask = new SwiftUploadTask(testKey, swiftTile, testListeners, testObjectApi);
        swiftUploadTask.run();

        // Testing that the function checkExisted returns early
        verify(testObjectApi, times(1)).getWithoutBody(testKey);
        verify(swiftTile, times(0)).setExisted(anyLong());
    }

    @Test
    @SuppressWarnings("PMD.CloseResource")
    public void testRunWithValidObject() {
        SwiftObject testSwiftObject = mock(SwiftObject.class);
        Payload testPayload = mock(Payload.class);
        BaseMutableContentMetadata metadata = mock(BaseMutableContentMetadata.class);
        when(metadata.getContentLength()).thenReturn(3L);
        when(testPayload.getContentMetadata()).thenReturn(metadata);
        when(testSwiftObject.getPayload()).thenReturn(testPayload);
        when(testObjectApi.getWithoutBody(testKey)).thenReturn(testSwiftObject);
        addListener();

        SwiftUploadTask swiftUploadTask = new SwiftUploadTask(testKey, swiftTile, testListeners, testObjectApi);

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
