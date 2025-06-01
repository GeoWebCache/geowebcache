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
 * @author Kevin Smith, Boundless, 2017
 */
package org.geowebcache.storage;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.captureLong;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.geq;
import static org.easymock.EasyMock.isNull;
import static org.geowebcache.util.FileMatchers.resource;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.describedAs;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.geowebcache.config.DefaultGridsets;
import org.geowebcache.filter.parameters.ParametersUtils;
import org.geowebcache.filter.parameters.StringParameterFilter;
import org.geowebcache.grid.Grid;
import org.geowebcache.grid.GridSet;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.mime.ImageMime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public abstract class AbstractBlobStoreTest<TestClass extends BlobStore> {

    protected TestClass store;

    protected boolean events = true;

    /** Set up the test store in {@link #store}. */
    @Before
    public abstract void createTestUnit() throws Exception;

    /** Override and add tear down assertions after calling super */
    @After
    public void destroyTestUnit() throws Exception {
        // Might be null if an Assumption failed during createTestUnit
        if (Objects.nonNull(store)) {
            store.destroy();
        }
    }

    @Test
    public void testEmpty() throws Exception {
        TileObject fromCache = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet", "image/png", null);
        assertThat(store.get(fromCache), equalTo(false));
        // assertThat(fromCache, hasProperty("status", is(Status.MISS)));
    }

    @Test
    public void testStoreTile() throws Exception {
        BlobStoreListener listener = EasyMock.createNiceMock(BlobStoreListener.class);
        store.addListener(listener);
        TileObject toCache = TileObject.createCompleteTileObject(
                "testLayer",
                new long[] {0L, 0L, 0L},
                "testGridSet",
                "image/png",
                null,
                new ByteArrayResource("1,2,4,5,6 test".getBytes(StandardCharsets.UTF_8)));
        final long size = toCache.getBlobSize();
        TileObject fromCache = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet", "image/png", null);

        if (events) {
            listener.tileStored(
                    eq("testLayer"),
                    eq("testGridSet"),
                    eq("image/png"),
                    eq(null),
                    eq(0L),
                    eq(0L),
                    eq(0),
                    geq(size) // Some stores have minimum block sizes and so have to pad this
                    );
            EasyMock.expectLastCall();
        }

        EasyMock.replay(listener);

        store.put(toCache);

        EasyMock.verify(listener);

        assertThat(store.get(fromCache), is(true));
        // assertThat(fromCache, hasProperty("status", is(Status.HIT)));
        assertThat(fromCache, hasProperty("blobSize", is((int) size)));

        assertThat(
                fromCache,
                hasProperty(
                        "blob", resource(new ByteArrayResource("1,2,4,5,6 test".getBytes(StandardCharsets.UTF_8)))));
    }

    @Test
    public void testStoreTilesInMultipleLayers() throws Exception {
        BlobStoreListener listener = EasyMock.createNiceMock(BlobStoreListener.class);
        store.addListener(listener);
        TileObject toCache1 = TileObject.createCompleteTileObject(
                "testLayer1",
                new long[] {0L, 0L, 0L},
                "testGridSet",
                "image/png",
                null,
                new ByteArrayResource("1,2,4,5,6 test".getBytes(StandardCharsets.UTF_8)));
        TileObject toCache2 = TileObject.createCompleteTileObject(
                "testLayer2",
                new long[] {0L, 0L, 0L},
                "testGridSet",
                "image/png",
                null,
                new ByteArrayResource("7,8,9,10 test".getBytes(StandardCharsets.UTF_8)));
        final long size1 = toCache1.getBlobSize();
        final long size2 = toCache2.getBlobSize();
        TileObject fromCache1 = TileObject.createQueryTileObject(
                "testLayer1", new long[] {0L, 0L, 0L}, "testGridSet", "image/png", null);
        TileObject fromCache2_1 = TileObject.createQueryTileObject(
                "testLayer2", new long[] {0L, 0L, 0L}, "testGridSet", "image/png", null);
        TileObject fromCache2_2 = TileObject.createQueryTileObject(
                "testLayer2", new long[] {0L, 0L, 0L}, "testGridSet", "image/png", null);

        if (events) {
            listener.tileStored(
                    eq("testLayer1"), eq("testGridSet"), eq("image/png"), eq(null), eq(0L), eq(0L), eq(0), geq(size1));
            listener.tileStored(
                    eq("testLayer2"), eq("testGridSet"), eq("image/png"), eq(null), eq(0L), eq(0L), eq(0), geq(size2));
        }

        EasyMock.replay(listener);

        store.put(toCache1);
        assertThat(store.get(fromCache2_1), is(false));
        assertThat(fromCache2_1, hasProperty("blobSize", is(0)));

        store.put(toCache2);
        EasyMock.verify(listener);

        assertThat(store.get(fromCache1), is(true));
        assertThat(fromCache1, hasProperty("blobSize", is((int) size1)));
        assertThat(
                fromCache1,
                hasProperty(
                        "blob", resource(new ByteArrayResource("1,2,4,5,6 test".getBytes(StandardCharsets.UTF_8)))));
        assertThat(store.get(fromCache2_2), is(true));
        assertThat(fromCache2_2, hasProperty("blobSize", is((int) size2)));
        assertThat(
                fromCache2_2,
                hasProperty("blob", resource(new ByteArrayResource("7,8,9,10 test".getBytes(StandardCharsets.UTF_8)))));
    }

    @Test
    public void testDeleteTile() throws Exception {
        BlobStoreListener listener = EasyMock.createNiceMock(BlobStoreListener.class);
        store.addListener(listener);
        TileObject toCache = TileObject.createCompleteTileObject(
                "testLayer",
                new long[] {0L, 0L, 0L},
                "testGridSet",
                "image/png",
                null,
                new ByteArrayResource("1,2,4,5,6 test".getBytes(StandardCharsets.UTF_8)));
        TileObject remove = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet", "image/png", null);
        TileObject fromCache = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet", "image/png", null);

        Capture<Long> sizeCapture = EasyMock.newCapture();
        if (events) {
            listener.tileStored(
                    eq("testLayer"),
                    eq("testGridSet"),
                    eq("image/png"),
                    eq(null),
                    eq(0L),
                    eq(0L),
                    eq(0),
                    captureLong(sizeCapture));
            EasyMock.expectLastCall();
        }

        EasyMock.replay(listener);

        store.put(toCache);
        EasyMock.verify(listener);
        long storedSize = 0;
        if (events) {
            storedSize = sizeCapture.getValue();
        }
        EasyMock.reset(listener);
        if (events) {
            listener.tileDeleted(
                    eq("testLayer"),
                    eq("testGridSet"),
                    eq("image/png"),
                    eq(null),
                    eq(0L),
                    eq(0L),
                    eq(0),
                    eq(storedSize));
            EasyMock.expectLastCall();
        }
        EasyMock.replay(listener);
        store.delete(remove);
        EasyMock.verify(listener);
        assertThat(store.get(fromCache), is(false));
        assertThat(fromCache, hasProperty("blobSize", is(0)));
    }

    @Test
    public void testUpdateTile() throws Exception {
        BlobStoreListener listener = EasyMock.createNiceMock(BlobStoreListener.class);
        store.addListener(listener);
        TileObject toCache1 = TileObject.createCompleteTileObject(
                "testLayer",
                new long[] {0L, 0L, 0L},
                "testGridSet",
                "image/png",
                null,
                new ByteArrayResource("1,2,4,5,6 test".getBytes(StandardCharsets.UTF_8)));
        TileObject toCache2 = TileObject.createCompleteTileObject(
                "testLayer",
                new long[] {0L, 0L, 0L},
                "testGridSet",
                "image/png",
                null,
                new ByteArrayResource("7,8,9,10 test".getBytes(StandardCharsets.UTF_8)));
        final long size2 = toCache2.getBlobSize();
        TileObject fromCache = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet", "image/png", null);

        Capture<Long> sizeCapture = EasyMock.newCapture();
        if (events) {
            listener.tileStored(
                    eq("testLayer"),
                    eq("testGridSet"),
                    eq("image/png"),
                    eq(null),
                    eq(0L),
                    eq(0L),
                    eq(0),
                    captureLong(sizeCapture));
            EasyMock.expectLastCall();
        }
        EasyMock.replay(listener);

        store.put(toCache1);
        EasyMock.verify(listener);
        long storedSize = 0;
        if (events) {
            storedSize = sizeCapture.getValue();
        }
        EasyMock.reset(listener);
        if (events) {
            listener.tileUpdated(
                    eq("testLayer"),
                    eq("testGridSet"),
                    eq("image/png"),
                    eq(null),
                    eq(0L),
                    eq(0L),
                    eq(0),
                    geq(size2),
                    eq(storedSize));
            EasyMock.expectLastCall();
        }
        EasyMock.replay(listener);
        store.put(toCache2);
        EasyMock.verify(listener);
        assertThat(store.get(fromCache), is(true));
        assertThat(fromCache, hasProperty("blobSize", is((int) size2)));
        assertThat(
                fromCache,
                hasProperty("blob", resource(new ByteArrayResource("7,8,9,10 test".getBytes(StandardCharsets.UTF_8)))));
    }

    @Test
    public void testGridsets() throws Exception {
        BlobStoreListener listener = EasyMock.createNiceMock(BlobStoreListener.class);
        store.addListener(listener);
        TileObject toCache1 = TileObject.createCompleteTileObject(
                "testLayer",
                new long[] {0L, 0L, 0L},
                "testGridSet1",
                "image/png",
                null,
                new ByteArrayResource("1,2,4,5,6 test".getBytes(StandardCharsets.UTF_8)));
        TileObject toCache2 = TileObject.createCompleteTileObject(
                "testLayer",
                new long[] {0L, 0L, 0L},
                "testGridSet2",
                "image/png",
                null,
                new ByteArrayResource("7,8,9,10 test".getBytes(StandardCharsets.UTF_8)));
        final long size1 = toCache1.getBlobSize();
        final long size2 = toCache2.getBlobSize();
        TileObject remove = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet1", "image/png", null);
        TileObject fromCache1_1 = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet1", "image/png", null);
        TileObject fromCache2_1 = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet2", "image/png", null);
        TileObject fromCache1_2 = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet1", "image/png", null);
        TileObject fromCache2_2 = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet2", "image/png", null);
        TileObject fromCache2_3 = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet2", "image/png", null);

        Capture<Long> sizeCapture1 = EasyMock.newCapture();
        Capture<Long> sizeCapture2 = EasyMock.newCapture();
        if (events) {
            listener.tileStored(
                    eq("testLayer"),
                    eq("testGridSet1"),
                    eq("image/png"),
                    eq(null),
                    eq(0L),
                    eq(0L),
                    eq(0),
                    captureLong(sizeCapture1));
            EasyMock.expectLastCall();
            listener.tileStored(
                    eq("testLayer"),
                    eq("testGridSet2"),
                    eq("image/png"),
                    eq(null),
                    eq(0L),
                    eq(0L),
                    eq(0),
                    captureLong(sizeCapture2));
            EasyMock.expectLastCall();
        }

        EasyMock.replay(listener);

        store.put(toCache1);
        assertThat(store.get(fromCache2_1), is(false));
        store.put(toCache2);
        EasyMock.verify(listener);
        long storedSize1 = 0;
        if (events) {
            storedSize1 = sizeCapture1.getValue();
        }
        assertThat(store.get(fromCache1_1), is(true));
        assertThat(fromCache1_1, hasProperty("blobSize", is((int) size1)));
        assertThat(
                fromCache1_1,
                hasProperty(
                        "blob", resource(new ByteArrayResource("1,2,4,5,6 test".getBytes(StandardCharsets.UTF_8)))));
        assertThat(store.get(fromCache2_2), is(true));
        assertThat(fromCache2_2, hasProperty("blobSize", is((int) size2)));
        assertThat(
                fromCache2_2,
                hasProperty("blob", resource(new ByteArrayResource("7,8,9,10 test".getBytes(StandardCharsets.UTF_8)))));
        EasyMock.reset(listener);
        if (events) {
            listener.tileDeleted(
                    eq("testLayer"),
                    eq("testGridSet1"),
                    eq("image/png"),
                    eq(null),
                    eq(0L),
                    eq(0L),
                    eq(0),
                    eq(storedSize1));
            EasyMock.expectLastCall();
        }
        EasyMock.replay(listener);
        store.delete(remove);
        EasyMock.verify(listener);
        assertThat(store.get(fromCache1_2), is(false));
        assertThat(fromCache1_2, hasProperty("blobSize", is(0)));
        assertThat(store.get(fromCache2_3), is(true));
        assertThat(fromCache2_3, hasProperty("blobSize", is((int) size2)));
        assertThat(
                fromCache2_3,
                hasProperty("blob", resource(new ByteArrayResource("7,8,9,10 test".getBytes(StandardCharsets.UTF_8)))));
    }

    @Test
    public void testDeleteGridset() throws Exception {
        BlobStoreListener listener = EasyMock.createNiceMock(BlobStoreListener.class);
        store.addListener(listener);
        TileObject toCache1 = TileObject.createCompleteTileObject(
                "testLayer",
                new long[] {0L, 0L, 0L},
                "testGridSet1",
                "image/png",
                null,
                new ByteArrayResource("1,2,4,5,6 test".getBytes(StandardCharsets.UTF_8)));
        final long size1 = toCache1.getBlobSize();

        TileObject fromCache1_2 = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet1", "image/png", null);

        if (events) {
            listener.tileStored(
                    eq("testLayer"), eq("testGridSet1"), eq("image/png"), eq(null), eq(0L), eq(0L), eq(0), geq(size1));
            EasyMock.expectLastCall();
        }

        EasyMock.replay(listener);

        store.put(toCache1);
        EasyMock.verify(listener);
        EasyMock.reset(listener);
        if (events) {
            listener.gridSubsetDeleted(eq("testLayer"), eq("testGridSet1"));
            EasyMock.expectLastCall();
        }
        EasyMock.replay(listener);
        assertThat(store.deleteByGridsetId("testLayer", "testGridSet1"), is(true));
        EasyMock.verify(listener);
        assertNoTile(fromCache1_2);
    }

    @Test
    public void testDeleteGridsetDoesntDeleteOthers() throws Exception {
        TileObject toCache1 = TileObject.createCompleteTileObject(
                "testLayer",
                new long[] {0L, 0L, 0L},
                "testGridSet1",
                "image/png",
                null,
                new ByteArrayResource("1,2,4,5,6 test".getBytes(StandardCharsets.UTF_8)));
        TileObject toCache2 = TileObject.createCompleteTileObject(
                "testLayer",
                new long[] {0L, 0L, 0L},
                "testGridSet2",
                "image/png",
                null,
                new ByteArrayResource("7,8,9,10 test".getBytes(StandardCharsets.UTF_8)));
        final long size1 = toCache1.getBlobSize();
        final long size2 = toCache2.getBlobSize();

        TileObject fromCache1_1 = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet1", "image/png", null);
        TileObject fromCache2_1 = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet2", "image/png", null);
        TileObject fromCache1_2 = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet1", "image/png", null);
        TileObject fromCache2_2 = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet2", "image/png", null);
        TileObject fromCache2_3 = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet2", "image/png", null);

        store.put(toCache1);
        assertThat(store.get(fromCache2_1), is(false));
        store.put(toCache2);
        assertThat(store.get(fromCache1_1), is(true));
        assertThat(fromCache1_1, hasProperty("blobSize", is((int) size1)));
        assertThat(
                fromCache1_1,
                hasProperty(
                        "blob", resource(new ByteArrayResource("1,2,4,5,6 test".getBytes(StandardCharsets.UTF_8)))));
        assertThat(store.get(fromCache2_2), is(true));
        assertThat(fromCache2_2, hasProperty("blobSize", is((int) size2)));
        assertThat(
                fromCache2_2,
                hasProperty("blob", resource(new ByteArrayResource("7,8,9,10 test".getBytes(StandardCharsets.UTF_8)))));
        store.deleteByGridsetId("testLayer", "testGridSet1");

        assertNoTile(fromCache1_2);

        assertThat(store.get(fromCache2_3), is(true));
        assertThat(fromCache2_3, hasProperty("blobSize", is((int) size2)));
        assertThat(
                fromCache2_3,
                hasProperty("blob", resource(new ByteArrayResource("7,8,9,10 test".getBytes(StandardCharsets.UTF_8)))));
    }

    @Test
    public void testParameters() throws Exception {
        BlobStoreListener listener = EasyMock.createNiceMock(BlobStoreListener.class);
        store.addListener(listener);
        Map<String, String> params1 = Collections.singletonMap("testKey", "testValue1");
        Map<String, String> params2 = Collections.singletonMap("testKey", "testValue2");
        TileObject toCache1 = TileObject.createCompleteTileObject(
                "testLayer",
                new long[] {0L, 0L, 0L},
                "testGridSet",
                "image/png",
                params1,
                new ByteArrayResource("1,2,4,5,6 test".getBytes(StandardCharsets.UTF_8)));
        TileObject toCache2 = TileObject.createCompleteTileObject(
                "testLayer",
                new long[] {0L, 0L, 0L},
                "testGridSet",
                "image/png",
                params2,
                new ByteArrayResource("7,8,9,10 test".getBytes(StandardCharsets.UTF_8)));
        final long size1 = toCache1.getBlobSize();
        final long size2 = toCache2.getBlobSize();

        TileObject remove = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet", "image/png", params1);
        TileObject fromCache1_1 = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet", "image/png", params1);
        TileObject fromCache2_1 = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet", "image/png", params2);
        TileObject fromCache1_2 = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet", "image/png", params1);
        TileObject fromCache2_2 = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet", "image/png", params2);
        TileObject fromCache2_3 = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet", "image/png", params2);

        Capture<Long> sizeCapture1 = EasyMock.newCapture();
        Capture<Long> sizeCapture2 = EasyMock.newCapture();
        Capture<String> pidCapture1 = EasyMock.newCapture();
        Capture<String> pidCapture2 = EasyMock.newCapture();
        if (events) {
            listener.tileStored(
                    eq("testLayer"),
                    eq("testGridSet"),
                    eq("image/png"),
                    capture(pidCapture1),
                    eq(0L),
                    eq(0L),
                    eq(0),
                    captureLong(sizeCapture1));
            EasyMock.expectLastCall();
            listener.tileStored(
                    eq("testLayer"),
                    eq("testGridSet"),
                    eq("image/png"),
                    capture(pidCapture2),
                    eq(0L),
                    eq(0L),
                    eq(0),
                    captureLong(sizeCapture2));
            EasyMock.expectLastCall();
        }

        EasyMock.replay(listener);

        store.put(toCache1);
        assertThat(store.get(fromCache2_1), is(false));
        store.put(toCache2);
        EasyMock.verify(listener);
        long storedSize1 = 0;
        if (events) {
            storedSize1 = sizeCapture1.getValue();
            // parameter id strings should be non-null and not equal to one another
            assertThat(pidCapture1.getValue(), notNullValue());
            assertThat(pidCapture2.getValue(), notNullValue());
            assertThat(pidCapture2.getValue(), not(pidCapture1.getValue()));
        }

        assertThat(store.get(fromCache1_1), is(true));
        assertThat(fromCache1_1, hasProperty("blobSize", is((int) size1)));
        assertThat(
                fromCache1_1,
                hasProperty(
                        "blob", resource(new ByteArrayResource("1,2,4,5,6 test".getBytes(StandardCharsets.UTF_8)))));
        assertThat(store.get(fromCache2_2), is(true));
        assertThat(fromCache2_2, hasProperty("blobSize", is((int) size2)));
        assertThat(
                fromCache2_2,
                hasProperty("blob", resource(new ByteArrayResource("7,8,9,10 test".getBytes(StandardCharsets.UTF_8)))));
        EasyMock.reset(listener);
        if (events) {
            listener.tileDeleted(
                    eq("testLayer"),
                    eq("testGridSet"),
                    eq("image/png"),
                    eq(pidCapture1.getValue()),
                    eq(0L),
                    eq(0L),
                    eq(0),
                    eq(storedSize1));
        }
        EasyMock.replay(listener);
        store.delete(remove);
        EasyMock.verify(listener);

        assertNoTile(fromCache1_2);

        assertThat(store.get(fromCache2_3), is(true));
        assertThat(fromCache2_3, hasProperty("blobSize", is((int) size2)));
        assertThat(
                fromCache2_3,
                hasProperty("blob", resource(new ByteArrayResource("7,8,9,10 test".getBytes(StandardCharsets.UTF_8)))));
    }

    @Test
    public void testMetadata() throws Exception {
        assertThat(store.getLayerMetadata("testLayer", "testKey"), nullValue());
        store.putLayerMetadata("testLayer", "testKey", "testValue");
        assertThat(store.getLayerMetadata("testLayer", "testKey"), equalTo("testValue"));
    }

    @Test
    public void testMetadataWithEqualsInKey() throws Exception {
        assertThat(store.getLayerMetadata("testLayer", "test=Key"), nullValue());
        store.putLayerMetadata("testLayer", "test=Key", "testValue");
        assertThat(store.getLayerMetadata("testLayer", "test=Key"), equalTo("testValue"));
    }

    @Test
    public void testMetadataWithEqualsInValue() throws Exception {
        assertThat(store.getLayerMetadata("testLayer", "testKey"), nullValue());
        store.putLayerMetadata("testLayer", "testKey", "test=Value");
        assertThat(store.getLayerMetadata("testLayer", "testKey"), equalTo("test=Value"));
    }

    @Test
    public void testMetadataWithAmpInKey() throws Exception {
        assertThat(store.getLayerMetadata("testLayer", "test&Key"), nullValue());
        store.putLayerMetadata("testLayer", "test&Key", "testValue");
        assertThat(store.getLayerMetadata("testLayer", "test&Key"), equalTo("testValue"));
    }

    @Test
    public void testMetadataWithAmpInValue() throws Exception {
        assertThat(store.getLayerMetadata("testLayer", "testKey"), nullValue());
        store.putLayerMetadata("testLayer", "testKey", "test&Value");
        assertThat(store.getLayerMetadata("testLayer", "testKey"), equalTo("test&Value"));
    }

    @Test
    public void testMetadataWithPercentInKey() throws Exception {
        assertThat(store.getLayerMetadata("testLayer", "test%Key"), nullValue());
        store.putLayerMetadata("testLayer", "test%Key", "testValue");
        assertThat(store.getLayerMetadata("testLayer", "test%Key"), equalTo("testValue"));
    }

    @Test
    public void testMetadataWithPercentInValue() throws Exception {
        assertThat(store.getLayerMetadata("testLayer", "testKey"), nullValue());
        store.putLayerMetadata("testLayer", "testKey", "test%Value");
        assertThat(store.getLayerMetadata("testLayer", "testKey"), equalTo("test%Value"));
    }

    @Test
    public void testParameterList() throws Exception {
        Map<String, String> params1 = Collections.singletonMap("testKey", "testValue1");
        Map<String, String> params2 = Collections.singletonMap("testKey", "testValue2");
        TileObject toCache1 = TileObject.createCompleteTileObject(
                "testLayer",
                new long[] {0L, 0L, 0L},
                "testGridSet",
                "image/png",
                params1,
                new ByteArrayResource("1,2,4,5,6 test".getBytes(StandardCharsets.UTF_8)));
        TileObject toCache2 = TileObject.createCompleteTileObject(
                "testLayer",
                new long[] {0L, 0L, 0L},
                "testGridSet",
                "image/png",
                params2,
                new ByteArrayResource("7,8,9,10 test".getBytes(StandardCharsets.UTF_8)));

        assertThat(store.getParameters("testLayer"), empty());
        store.put(toCache1);
        assertThat(store.getParameters("testLayer"), containsInAnyOrder(params1));
        store.put(toCache2);
        assertThat(store.getParameters("testLayer"), containsInAnyOrder(params1, params2));
    }

    @Test
    public void testParameterIDList() throws Exception {
        Map<String, String> params1 = Collections.singletonMap("testKey", "testValue1");
        Map<String, String> params2 = Collections.singletonMap("testKey", "testValue2");
        String params1Id = ParametersUtils.getId(params1);
        String params2Id = ParametersUtils.getId(params2);
        TileObject toCache1 = TileObject.createCompleteTileObject(
                "testLayer",
                new long[] {0L, 0L, 0L},
                "testGridSet",
                "image/png",
                params1,
                new ByteArrayResource("1,2,4,5,6 test".getBytes(StandardCharsets.UTF_8)));
        TileObject toCache2 = TileObject.createCompleteTileObject(
                "testLayer",
                new long[] {0L, 0L, 0L},
                "testGridSet",
                "image/png",
                params2,
                new ByteArrayResource("7,8,9,10 test".getBytes(StandardCharsets.UTF_8)));

        assertThat(store.getParameterIds("testLayer"), empty());
        store.put(toCache1);
        assertThat(store.getParameterIds("testLayer"), containsInAnyOrder(params1Id));
        store.put(toCache2);
        assertThat(store.getParameterIds("testLayer"), containsInAnyOrder(params1Id, params2Id));
    }

    @Test
    public void testEmptyParameterListIsNotNull() throws Exception {
        assertThat(store.getParameters("testLayer"), empty());
    }

    @Test
    public void testDeleteByParametersId() throws Exception {
        Map<String, String> params1 = Collections.singletonMap("testKey", "testValue1");
        String paramID1 = ParametersUtils.getId(params1);
        Map<String, String> params2 = Collections.singletonMap("testKey", "testValue2");
        String paramID2 = ParametersUtils.getId(params2);
        TileObject toCache1 = TileObject.createCompleteTileObject(
                "testLayer",
                new long[] {0L, 0L, 0L},
                "testGridSet",
                "image/png",
                params1,
                new ByteArrayResource("1,2,4,5,6 test".getBytes(StandardCharsets.UTF_8)));
        TileObject toCache2 = TileObject.createCompleteTileObject(
                "testLayer",
                new long[] {0L, 0L, 0L},
                "testGridSet",
                "image/png",
                params2,
                new ByteArrayResource("7,8,9,10 test".getBytes(StandardCharsets.UTF_8)));
        BlobStoreListener listener = EasyMock.createNiceMock(BlobStoreListener.class);
        store.addListener(listener);
        final long size1 = toCache1.getBlobSize();
        final long size2 = toCache2.getBlobSize();

        TileObject fromCache1_1 = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet", "image/png", params1);
        TileObject fromCache2_1 = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet", "image/png", params2);
        TileObject fromCache1_2 = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet", "image/png", params1);
        TileObject fromCache2_2 = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet", "image/png", params2);

        if (events) {
            listener.tileStored(
                    eq("testLayer"),
                    eq("testGridSet"),
                    eq("image/png"),
                    eq(paramID1),
                    eq(0L),
                    eq(0L),
                    eq(0),
                    geq(size1));
            listener.tileStored(
                    eq("testLayer"),
                    eq("testGridSet"),
                    eq("image/png"),
                    eq(paramID2),
                    eq(0L),
                    eq(0L),
                    eq(0),
                    geq(size2));
        }

        EasyMock.replay(listener);

        store.put(toCache1);
        assertThat(store.get(fromCache2_1), is(false));
        store.put(toCache2);
        EasyMock.verify(listener);
        assertThat(store.get(fromCache1_1), is(true));
        assertThat(fromCache1_1, hasProperty("blobSize", is((int) size1)));
        assertThat(
                fromCache1_1,
                hasProperty(
                        "blob", resource(new ByteArrayResource("1,2,4,5,6 test".getBytes(StandardCharsets.UTF_8)))));
        assertThat(store.get(fromCache2_2), is(true));
        assertThat(fromCache2_2, hasProperty("blobSize", is((int) size2)));
        assertThat(
                fromCache2_2,
                hasProperty("blob", resource(new ByteArrayResource("7,8,9,10 test".getBytes(StandardCharsets.UTF_8)))));
        EasyMock.reset(listener);
        if (events) {
            listener.parametersDeleted(eq("testLayer"), eq(paramID1));
        }
        EasyMock.replay(listener);
        store.deleteByParametersId("testLayer", paramID1);
        EasyMock.verify(listener);

        assertNoTile(fromCache1_2);
    }

    @Test
    public void testDeleteByParametersIdDoesNotDeleteOthers() throws Exception {
        Map<String, String> params1 = Collections.singletonMap("testKey", "testValue1");
        String paramID1 = ParametersUtils.getId(params1);
        Map<String, String> params2 = Collections.singletonMap("testKey", "testValue2");
        TileObject toCache1 = TileObject.createCompleteTileObject(
                "testLayer",
                new long[] {0L, 0L, 0L},
                "testGridSet",
                "image/png",
                params1,
                new ByteArrayResource("1,2,4,5,6 test".getBytes(StandardCharsets.UTF_8)));
        TileObject toCache2 = TileObject.createCompleteTileObject(
                "testLayer",
                new long[] {0L, 0L, 0L},
                "testGridSet",
                "image/png",
                params2,
                new ByteArrayResource("7,8,9,10 test".getBytes(StandardCharsets.UTF_8)));
        final long size2 = toCache2.getBlobSize();

        TileObject fromCache2_3 = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet", "image/png", params2);

        store.put(toCache1);
        store.put(toCache2);
        store.deleteByParametersId("testLayer", paramID1);

        await().atMost(30, SECONDS) // give stores with async deletes a chance to complete
                .untilAsserted(() -> assertThat(store.get(fromCache2_3), is(true)));
        assertThat(fromCache2_3, hasProperty("blobSize", is((int) size2)));
        assertThat(
                fromCache2_3,
                hasProperty("blob", resource(new ByteArrayResource("7,8,9,10 test".getBytes(StandardCharsets.UTF_8)))));
    }

    @Test
    public void testPurgeOrphans() throws Exception {
        TileLayer layer = EasyMock.createNiceMock("layer", TileLayer.class);
        EasyMock.expect(layer.getName()).andStubReturn("testLayer");
        StringParameterFilter testFilter = new StringParameterFilter();
        testFilter.setDefaultValue("DEFAULT");
        testFilter.setKey("testKey");
        testFilter.setValues(Arrays.asList("testValue2"));
        EasyMock.expect(layer.getParameterFilters()).andStubReturn(Arrays.asList(testFilter));
        EasyMock.replay(layer);

        Map<String, String> params1 = Collections.singletonMap("testKey", "testValue1");
        String paramID1 = ParametersUtils.getId(params1);
        Map<String, String> params2 = Collections.singletonMap("testKey", "testValue2");
        String paramID2 = ParametersUtils.getId(params2);
        TileObject toCache1 = TileObject.createCompleteTileObject(
                "testLayer",
                new long[] {0L, 0L, 0L},
                "testGridSet",
                "image/png",
                params1,
                new ByteArrayResource("1,2,4,5,6 test".getBytes(StandardCharsets.UTF_8)));
        TileObject toCache2 = TileObject.createCompleteTileObject(
                "testLayer",
                new long[] {0L, 0L, 0L},
                "testGridSet",
                "image/png",
                params2,
                new ByteArrayResource("7,8,9,10 test".getBytes(StandardCharsets.UTF_8)));
        BlobStoreListener listener = EasyMock.createNiceMock(BlobStoreListener.class);
        store.addListener(listener);
        final long size1 = toCache1.getBlobSize();
        final long size2 = toCache2.getBlobSize();

        TileObject fromCache1_1 = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet", "image/png", params1);
        TileObject fromCache2_1 = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet", "image/png", params2);
        TileObject fromCache1_2 = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet", "image/png", params1);
        TileObject fromCache2_2 = TileObject.createQueryTileObject(
                "testLayer", new long[] {0L, 0L, 0L}, "testGridSet", "image/png", params2);

        if (events) {
            listener.tileStored(
                    eq("testLayer"),
                    eq("testGridSet"),
                    eq("image/png"),
                    eq(paramID1),
                    eq(0L),
                    eq(0L),
                    eq(0),
                    geq(size1));
            listener.tileStored(
                    eq("testLayer"),
                    eq("testGridSet"),
                    eq("image/png"),
                    eq(paramID2),
                    eq(0L),
                    eq(0L),
                    eq(0),
                    geq(size2));
        }

        EasyMock.replay(listener);

        store.put(toCache1);
        assertThat(store.get(fromCache2_1), is(false));
        store.put(toCache2);
        EasyMock.verify(listener);
        assertThat(store.get(fromCache1_1), is(true));
        assertThat(fromCache1_1, hasProperty("blobSize", is((int) size1)));
        assertThat(
                fromCache1_1,
                hasProperty(
                        "blob", resource(new ByteArrayResource("1,2,4,5,6 test".getBytes(StandardCharsets.UTF_8)))));
        assertThat(store.get(fromCache2_2), is(true));
        assertThat(fromCache2_2, hasProperty("blobSize", is((int) size2)));
        assertThat(
                fromCache2_2,
                hasProperty("blob", resource(new ByteArrayResource("7,8,9,10 test".getBytes(StandardCharsets.UTF_8)))));
        EasyMock.reset(listener);
        if (events) {
            listener.parametersDeleted(eq("testLayer"), eq(paramID1));
        }
        EasyMock.replay(listener);
        store.purgeOrphans(layer);
        EasyMock.verify(listener);
        assertNoTile(fromCache1_2);
    }

    protected void cacheTile(
            String layerName,
            long x,
            long y,
            int z,
            String gridSetId,
            String format,
            Map<String, String> parameters,
            String content)
            throws StorageException {
        TileObject to = TileObject.createCompleteTileObject(
                layerName,
                new long[] {x, y, z},
                gridSetId,
                format,
                parameters,
                new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)));
        store.put(to);
    }

    protected void assertTile(
            String layerName,
            long x,
            long y,
            int z,
            String gridSetId,
            String format,
            Map<String, String> parameters,
            String content)
            throws StorageException {
        TileObject to =
                TileObject.createQueryTileObject(layerName, new long[] {x, y, z}, gridSetId, format, parameters);
        assertThat(store.get(to), describedAs("get a tile", is(true)));
        assertThat(to, hasProperty("blob", resource(new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)))));
    }

    protected void assertNoTile(
            String layerName, long x, long y, int z, String gridSetId, String format, Map<String, String> parameters)
            throws StorageException {
        TileObject to =
                TileObject.createQueryTileObject(layerName, new long[] {x, y, z}, gridSetId, format, parameters);
        assertNoTile(to);
    }

    private void assertNoTile(TileObject to) {
        await().atMost(30, SECONDS) // give stores with async deletes a chance to complete
                .untilAsserted(() -> assertThat(store.get(to), describedAs("don't get a tile", is(false))));
        assertThat(to, hasProperty("blob", nullValue()));
        assertThat(to, hasProperty("blobSize", is(0)));
    }

    @Test
    public void testPurgeOrphansWithDefault() throws Exception {
        TileLayer layer = EasyMock.createNiceMock("layer", TileLayer.class);
        final String layerName = "testLayer";
        final String paramKey = "testKey";

        EasyMock.expect(layer.getName()).andStubReturn(layerName);
        StringParameterFilter testFilter = new StringParameterFilter();
        testFilter.setDefaultValue("DEFAULT");
        testFilter.setKey(paramKey);
        testFilter.setValues(Arrays.asList("keep1", "keep2", "keep3"));
        EasyMock.expect(layer.getParameterFilters()).andStubReturn(Arrays.asList(testFilter));
        EasyMock.replay(layer);

        Map<String, String> params1 = Collections.singletonMap(paramKey, "purge1");
        String paramID1 = ParametersUtils.getId(params1);
        Map<String, String> params2 = Collections.singletonMap(paramKey, "keep1");
        String paramID2 = ParametersUtils.getId(params2);
        final String gridset = "testGridSet";
        final String format = "image/png";
        BlobStoreListener listener = EasyMock.createNiceMock(BlobStoreListener.class);
        store.addListener(listener);

        if (events) {
            listener.tileStored(eq(layerName), eq(gridset), eq(format), eq(paramID1), eq(0L), eq(0L), eq(0), anyLong());
            listener.tileStored(eq(layerName), eq(gridset), eq(format), eq(paramID2), eq(0L), eq(0L), eq(0), anyLong());
            listener.tileStored(eq(layerName), eq(gridset), eq(format), isNull(), eq(0L), eq(0L), eq(0), anyLong());
        }

        EasyMock.replay(listener);

        cacheTile(layerName, 0, 0, 0, gridset, format, params1, "purge");
        cacheTile(layerName, 0, 0, 0, gridset, format, params2, "keep param");
        cacheTile(layerName, 0, 0, 0, gridset, format, null, "keep default");
        EasyMock.verify(listener);
        assertTile(layerName, 0, 0, 0, gridset, format, params1, "purge");
        assertTile(layerName, 0, 0, 0, gridset, format, params2, "keep param");
        assertTile(layerName, 0, 0, 0, gridset, format, null, "keep default");
        EasyMock.reset(listener);
        if (events) {
            listener.parametersDeleted(eq(layerName), eq(paramID1));
        }
        EasyMock.replay(listener);
        store.purgeOrphans(layer);
        EasyMock.verify(listener);
        assertNoTile(layerName, 0, 0, 0, gridset, format, params1);
        assertTile(layerName, 0, 0, 0, gridset, format, params2, "keep param");
        assertTile(layerName, 0, 0, 0, gridset, format, null, "keep default");
    }

    @Test
    public void testDeleteRangeSingleLevel() throws StorageException {
        TileLayer layer = EasyMock.createNiceMock("layer", TileLayer.class);
        final String layerName = "testLayer";
        EasyMock.expect(layer.getName()).andStubReturn(layerName);
        GridSet gridSet = new DefaultGridsets(true, false).worldEpsg4326();
        final String format = ImageMime.png.getFormat();
        String content = "sample";
        String gridsetId = gridSet.getName();

        // store full world coverage for zoom levels 0, 1, 2
        setupFullCoverage(layerName, gridSet, format, content, gridsetId, 0, 2);

        // delete sub-range at zoom level 2
        TileRange range =
                new TileRange(layerName, gridsetId, 2, 2, new long[][] {{0, 0, 2, 2, 2}}, ImageMime.png, null);
        store.delete(range);

        // check tiles in range have have been deleted, but others are there
        assertTileRangeEmpty(layerName, gridSet, format, range);
        assertTile(layerName, 0, 0, 0, gridsetId, format, null, content);
        assertTile(layerName, 1, 0, 0, gridsetId, format, null, content);
        assertTile(layerName, 0, 0, 1, gridsetId, format, null, content);
    }

    @Test
    public void testDeleteRangeMultiLevel() throws StorageException {
        TileLayer layer = EasyMock.createNiceMock("layer", TileLayer.class);
        final String layerName = "testLayer";
        EasyMock.expect(layer.getName()).andStubReturn(layerName);
        GridSet gridSet = new DefaultGridsets(true, false).worldEpsg4326();
        final String format = ImageMime.png.getFormat();
        String content = "sample";
        String gridsetId = gridSet.getName();

        // store full world coverage for zoom levels 0, 1, 2
        setupFullCoverage(layerName, gridSet, format, content, gridsetId, 0, 2);

        // delete sub-range at zoom level 2
        TileRange range = new TileRange(
                layerName, gridsetId, 1, 2, new long[][] {{0, 0, 2, 2, 1}, {0, 0, 2, 2, 2}}, ImageMime.png, null);
        store.delete(range);

        // check tiles in range have have been deleted, but others are there
        assertTileRangeEmpty(layerName, gridSet, format, range);
        assertTile(layerName, 0, 0, 0, gridsetId, format, null, content);
        assertTile(layerName, 1, 0, 0, gridsetId, format, null, content);
    }

    public void setupFullCoverage(
            String layerName, GridSet gridSet, String format, String content, String gridsetId, int minZ, int maxZ)
            throws StorageException {
        for (int z = minZ; z <= maxZ; z++) {
            Grid grid = gridSet.getGrid(z);
            for (int x = 0; x < grid.getNumTilesWide(); x++) {
                for (int y = 0; y < grid.getNumTilesHigh(); y++) {
                    cacheTile(layerName, x, y, z, gridsetId, format, null, content);
                }
            }
        }
    }

    public void assertTileRangeEmpty(String layerName, GridSet gridSet, String format, TileRange range)
            throws StorageException {
        for (int z = range.getZoomStart(); z <= range.getZoomStop(); z++) {
            long[] bounds = range.rangeBounds(z);
            for (long x = bounds[0]; x <= bounds[2]; x++) {
                for (long y = bounds[1]; y < bounds[2]; y++) {
                    assertNoTile(layerName, x, y, z, gridSet.getName(), format, null);
                }
            }
        }
    }
}
