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
 * <p>Copyright 2026
 */
package org.geowebcache.storage.memorycache;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.easymock.EasyMock.anyInt;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.geowebcache.util.TMSKeyBuilder.PARAMETERS_METADATA_OBJECT_PREFIX;
import static org.geowebcache.util.TMSKeyBuilder.PARAMETERS_METADATA_OBJECT_SUFFIX;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Ticker;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.logging.Level;
import org.geowebcache.config.ServerConfiguration;
import org.geowebcache.filter.parameters.ParametersUtils;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.BlobStoreAggregator;
import org.geowebcache.storage.CompositeBlobStore;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;
import org.geowebcache.storage.blobstore.file.FileBlobStore;
import org.geowebcache.util.TMSKeyBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tile cache in front of a {@link FileBlobStore}, through {@link CompositeBlobStore} as in production. */
public class CachingBlobStoreTest {

    private static final String LAYER = "roads";

    private static final String GRIDSET = "EPSG:4326";

    private static final String OTHER_GRIDSET = "EPSG:900913";

    private static final Map<String, String> STYLE_A = Map.of("STYLES", "a");

    private static final Map<String, String> STYLE_B = Map.of("STYLES", "b");

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Rule
    public LogLines log = new LogLines(CachingBlobStore.class, Level.FINE);

    @Rule
    public LogLines stateLog = new LogLines(GuardedStore.class, Level.INFO);

    private final AtomicLong now = new AtomicLong();

    private final Ticker ticker = new Ticker() {
        @Override
        public long read() {
            return now.get();
        }
    };

    private final FailingStore kv = new FailingStore(new HeapKeyValueStore(1_000_000, ticker));

    private int expireCache;

    private boolean memoryCached = true;

    /** The layer's blob store id, null for the default store. */
    private String blobStoreId;

    // volatile: the concurrency tests replace the settings while other threads read them
    private volatile MemoryCacheInfo memoryCacheInfo;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    /** Built by {@link #newCache}, closed after the test. */
    private final List<MemoryCache> caches = new ArrayList<>();

    private TileLayer layer;

    private TileLayerDispatcher layers;

    private CompositeBlobStore store;

    @Before
    public void setUp() throws Exception {
        layer = createNiceMock(TileLayer.class);
        expect(layer.getName()).andStubReturn(LAYER);
        expect(layer.getId()).andStubReturn("layer-id");
        expect(layer.isMemoryCached()).andStubAnswer(() -> memoryCached);
        expect(layer.getBlobStoreId()).andStubAnswer(() -> blobStoreId);
        expect(layer.getExpireCache(anyInt())).andStubAnswer(() -> expireCache);
        expect(layer.getGridSubsets()).andStubReturn(Set.of(GRIDSET, OTHER_GRIDSET));
        expect(layer.getMimeTypes()).andStubReturn(List.of(ImageMime.png));
        // a standalone layer, whose id is its name
        TileLayer renamed = createNiceMock(TileLayer.class);
        expect(renamed.getId()).andStubReturn("renamed");
        layers = createNiceMock(TileLayerDispatcher.class);
        expect(layers.getTileLayer(LAYER)).andStubReturn(layer);
        expect(layers.getTileLayer("renamed")).andStubReturn(renamed);
        DefaultStorageFinder storageFinder = createNiceMock(DefaultStorageFinder.class);
        expect(storageFinder.getDefaultPath()).andStubReturn(temp.getRoot().getAbsolutePath());
        BlobStoreAggregator aggregator = createNiceMock(BlobStoreAggregator.class);
        expect(aggregator.getBlobStores()).andStubReturn(List.of());
        ServerConfiguration serverConfig = createNiceMock(ServerConfiguration.class);
        expect(serverConfig.getMemoryCache()).andStubAnswer(() -> memoryCacheInfo);
        replay(layer, renamed, layers, storageFinder, aggregator, serverConfig);

        store = new CompositeBlobStore(layers, storageFinder, serverConfig, aggregator);
        // tests writing to the store alone first set the settings to null
        enableCache(false);
    }

    @After
    public void tearDown() {
        executor.shutdownNow();
        caches.forEach(MemoryCache::close);
    }

    @Test
    public void testPassthroughOffServesTilesFromCacheOnly() throws Exception {
        long before = System.currentTimeMillis();
        store.put(tile(0, 0, 0));
        long after = System.currentTimeMillis();

        TileObject read = tile(0, 0, 0);
        assertTrue(store.get(read));
        assertArrayEquals(new byte[] {1, 2, 3}, ((ByteArrayResource) read.getBlob()).getContents());
        assertThat(read.getCreated(), allOf(greaterThanOrEqualTo(before), lessThanOrEqualTo(after)));
        assertFalse(inStore(tile(0, 0, 0)));
    }

    @Test
    public void testPassthroughOnWritesAndReadsThroughStore() throws Exception {
        memoryCacheInfo = null;
        store.put(tile(0, 0, 0));
        enableCache(true);
        assertTrue(store.get(tile(0, 0, 0)));

        // remove from the store only, the cache still has it
        memoryCacheInfo = null;
        store.delete(tile(0, 0, 0));
        enableCache(true);
        assertTrue(store.get(tile(0, 0, 0)));

        store.put(tile(1, 0, 1));
        assertTrue(inStore(tile(1, 0, 1)));
    }

    @Test
    public void testNoReadThroughWithPassthroughOff() throws Exception {
        memoryCacheInfo = null;
        store.put(tile(0, 0, 0));
        enableCache(false);

        assertFalse(store.get(tile(0, 0, 0)));
    }

    @Test
    public void testLayerExpireCacheSetsTimeToLive() throws Exception {
        // shorter than the global time to live, then longer
        expireCache = 10;
        store.put(tile(0, 0, 0));
        expireCache = 3600;
        store.put(tile(1, 0, 1));

        now.set(SECONDS.toNanos(10));
        assertFalse(store.get(tile(0, 0, 0)));
        now.set(SECONDS.toNanos(memoryCacheInfo.getTimeToLive() + 1));
        assertTrue(store.get(tile(1, 0, 1)));
        now.set(SECONDS.toNanos(3600));
        assertFalse(store.get(tile(1, 0, 1)));
    }

    @Test
    public void testDeleteLayer() throws Exception {
        store.put(tile(0, 0, 0));
        store.put(tile(OTHER_GRIDSET, STYLE_B, 0, 0, 0));

        store.delete(LAYER);

        assertFalse(store.get(tile(0, 0, 0)));
        assertFalse(store.get(tile(OTHER_GRIDSET, STYLE_B, 0, 0, 0)));
    }

    @Test
    public void testRenameDropsTilesOnlyWhenIdChanges() throws Exception {
        // no parameters, so no mapping file, and no layer directory for the file store rename to trip on
        store.put(tile(GRIDSET, null, 0, 0, 0));
        int calls = kv.calls.get();

        // as in GeoServer: the new name resolves to an id the rename keeps
        store.rename("old-name", LAYER);
        assertEquals(calls, kv.calls.get());
        assertTrue(store.get(tile(GRIDSET, null, 0, 0, 0)));

        // standalone layer ids are their names, so the old name is the id the tiles were cached under
        store.rename("layer-id", "renamed");
        assertFalse(store.get(tile(GRIDSET, null, 0, 0, 0)));
    }

    @Test
    public void testDeleteGridset() throws Exception {
        store.put(tile(0, 0, 0));
        store.put(tile(OTHER_GRIDSET, STYLE_A, 0, 0, 0));

        store.deleteByGridsetId(LAYER, GRIDSET);

        assertFalse(store.get(tile(0, 0, 0)));
        assertTrue(store.get(tile(OTHER_GRIDSET, STYLE_A, 0, 0, 0)));
    }

    @Test
    public void testDeleteParameters() throws Exception {
        store.put(tile(0, 0, 0));
        store.put(tile(OTHER_GRIDSET, STYLE_A, 0, 0, 0));
        store.put(tile(GRIDSET, STYLE_B, 0, 0, 0));

        store.deleteByParameters(LAYER, STYLE_A);

        assertFalse(store.get(tile(0, 0, 0)));
        assertFalse(store.get(tile(OTHER_GRIDSET, STYLE_A, 0, 0, 0)));
        assertTrue(store.get(tile(GRIDSET, STYLE_B, 0, 0, 0)));
    }

    @Test
    public void testTruncateSucceedsOnEmptyStoreWithPassthroughOff() throws Exception {
        // no parameters, so no mapping file and no layer directory in the store behind
        store.put(tile(GRIDSET, null, 0, 0, 0));

        assertTrue(store.deleteByGridsetId(LAYER, GRIDSET));
        assertFalse(store.get(tile(GRIDSET, null, 0, 0, 0)));
    }

    @Test
    public void testTileTruncatesSkipStoreWithPassthroughOff() throws Exception {
        enableCache(true);
        store.put(tile(0, 0, 1));
        enableCache(false);

        assertTrue(store.delete(tile(0, 0, 1)));
        assertTrue(store.delete(range()));
        assertTrue(store.deleteByGridsetId(LAYER, GRIDSET));

        // the store behind was not called: the tile written before passthrough was turned off is still there
        assertTrue(inStore(tile(0, 0, 1)));
        assertTrue(mappingFile().exists());

        // a layer truncate still reaches it, removing tiles and mappings
        enableCache(false);
        store.delete(LAYER);
        assertFalse(inStore(tile(0, 0, 1)));
        assertFalse(mappingFile().exists());
    }

    @Test
    public void testDeleteTile() throws Exception {
        store.put(tile(0, 0, 1));
        store.put(tile(1, 0, 1));

        store.delete(tile(0, 0, 1));

        assertFalse(store.get(tile(0, 0, 1)));
        assertTrue(store.get(tile(1, 0, 1)));
    }

    @Test
    public void testDeleteRange() throws Exception {
        store.put(tile(0, 0, 1));
        store.put(tile(1, 1, 1));
        store.put(tile(10, 10, 2));
        // a key that is not a tile, under a zoom level of the range
        String tileKey = key(tile(0, 0, 1));
        String stray = tileKey.substring(0, tileKey.lastIndexOf('/') + 1) + "stray";
        kv.put(stray, new byte[] {1}, 300);

        assertTrue(store.delete(range()));

        // zoom 2 is outside the range, x 1 is outside its bounds at zoom 1
        assertFalse(store.get(tile(0, 0, 1)));
        assertTrue(store.get(tile(1, 1, 1)));
        assertTrue(store.get(tile(10, 10, 2)));
        assertNull(kv.get(stray));
    }

    @Test
    public void testReadResultsLogged() throws Exception {
        memoryCacheInfo = null;
        store.put(tile(0, 0, 0));
        enableCache(true);
        expireCache = 10;
        // written by a node whose clock is 60 s behind, so already past the layer's expire server cache
        kv.put(key(tile(1, 0, 1)), cacheValue((byte) 1, System.currentTimeMillis() - 60_000), 300);

        store.get(tile(2, 0, 1));
        store.get(tile(0, 0, 0));
        store.get(tile(0, 0, 0));
        store.get(tile(1, 0, 1));
        kv.failing = true;
        store.get(tile(0, 0, 0));

        List<String> results =
                log.lines.stream().map(line -> line.split(" ")[3]).toList();
        assertThat(results, contains("miss", "store", "hit", "expired", "error"));
        assertThat(
                log.lines.get(0),
                startsWith("In-memory tile cache miss for layer roads, gridset EPSG:4326, z/x/y 1/2/0 in "));
    }

    @Test
    public void testReadThroughKeepsOnlyRemainingLifetime() throws Exception {
        memoryCacheInfo = null;
        long millis = System.currentTimeMillis();
        TileObject old = tile(0, 0, 0);
        old.setCreated(millis - 60_000);
        store.put(old);
        TileObject recent = tile(1, 0, 1);
        recent.setCreated(millis - 4_000);
        store.put(recent);
        enableCache(true);
        expireCache = 10;

        assertTrue(store.get(tile(0, 0, 0)));
        assertTrue(store.get(tile(1, 0, 1)));

        // the old tile is past the expire server cache, the recent one has at most 6 s left
        assertNull(kv.get(key(tile(0, 0, 0))));
        assertNotNull(kv.get(key(tile(1, 0, 1))));
        now.addAndGet(SECONDS.toNanos(6));
        assertNull(kv.get(key(tile(1, 0, 1))));
    }

    @Test
    public void testMappingWrittenOnceUntilTruncated() throws Exception {
        store.put(tile(0, 0, 0));
        // listed, so that truncates by parameters can still find it
        assertThat(store.getParametersMapping(LAYER), hasEntry(ParametersUtils.getId(STYLE_A), Optional.of(STYLE_A)));
        assertTrue(mappingFile().delete());

        store.put(tile(1, 0, 1));
        assertFalse(mappingFile().exists());

        store.deleteByParameters(LAYER, STYLE_A);
        store.put(tile(1, 0, 1));
        assertTrue(mappingFile().exists());

        // a layer truncate forgets the mappings too
        store.delete(LAYER);
        store.put(tile(0, 0, 0));
        assertTrue(mappingFile().exists());
    }

    @Test
    public void testConcurrentPutsWriteMappingOnce() throws Exception {
        MappingCountingStore delegate = new MappingCountingStore();
        BlobStore cached = newCache().wrap(layer, delegate);
        List<Future<Object>> puts = new ArrayList<>();
        for (int x = 0; x < 16; x++) {
            TileObject tile = tile(x, 0, 4);
            puts.add(executor.submit(() -> {
                cached.put(tile);
                return null;
            }));
        }
        for (Future<Object> put : puts) put.get();

        assertEquals(1, delegate.writes.get());
    }

    @Test
    public void testFailedMappingWriteCachesTileAndRetries() throws Exception {
        MappingCountingStore delegate = new MappingCountingStore();
        delegate.failing = true;
        BlobStore cached = newCache().wrap(layer, delegate);
        cached.put(tile(0, 0, 0));
        cached.put(tile(1, 0, 1));

        // cached despite the store outage, which is logged once
        assertTrue(cached.get(tile(0, 0, 0)));
        assertEquals(
                1,
                log.lines.stream().filter(l -> l.contains("could not record")).count());

        delegate.failing = false;
        cached.put(tile(1, 1, 1));

        assertTrue(mappingFile().exists());
        assertThat(log.lines, hasItem("In-memory tile cache records parameter mappings in the blob stores again"));
    }

    @Test
    public void testMappingWrittenToNewStore() throws Exception {
        MappingCountingStore previous = new MappingCountingStore();
        MappingCountingStore moved = new MappingCountingStore();
        try (MemoryCache cache = new MemoryCache(memoryCacheInfo, layers, ticker)) {
            cache.wrap(layer, previous).put(tile(0, 0, 0));
            cache.wrap(layer, previous).put(tile(1, 0, 1));

            // the layer moved to another store
            blobStoreId = "moved";
            cache.wrap(layer, moved).put(tile(1, 1, 1));
        }

        assertEquals(1, previous.writes.get());
        assertEquals(1, moved.writes.get());
    }

    @Test
    public void testTruncateOnOtherNodeRewritesMapping() throws Exception {
        MappingCountingStore shared = new MappingCountingStore();
        // two nodes, sharing the cache and the blob store
        try (MemoryCache node = new MemoryCache(memoryCacheInfo, layers, ticker);
                MemoryCache other = new MemoryCache(memoryCacheInfo, layers, ticker)) {
            node.wrap(layer, shared).put(tile(0, 0, 0));

            other.wrap(layer, shared).deleteByParametersId(LAYER, ParametersUtils.getId(STYLE_A));
            node.wrap(layer, shared).put(tile(1, 0, 1));
        }

        // the first node learned of the removal from the cache, not from a local record
        assertEquals(2, shared.writes.get());
        assertTrue(mappingFile().exists());
    }

    @Test
    public void testFailedRemovalLeavesStoreUntouched() throws Exception {
        enableCache(true);
        store.put(tile(0, 0, 0));
        store.put(tile(OTHER_GRIDSET, STYLE_A, 0, 0, 0));
        kv.failRemovals = true;

        StorageException e = assertThrows(StorageException.class, () -> store.deleteByGridsetId(LAYER, GRIDSET));

        assertThat(e.getMessage(), containsString("not run, retry once it recovers"));
        // nothing cleared in the cache, nothing truncated in the store
        assertNotNull(kv.get(key(tile(OTHER_GRIDSET, STYLE_A, 0, 0, 0))));
        assertTrue(inStore(tile(0, 0, 0)));
    }

    @Test
    public void testTruncateRefusedWhileDown() throws Exception {
        enableCache(false);
        BlobStore cached = newCache().wrap(layer, new MappingCountingStore());
        cached.put(tile(0, 0, 0));
        kv.failing = true;
        cached.get(tile(0, 0, 0));
        int calls = kv.calls.get();

        // with passthrough off the store holds no tiles, nothing to free
        assertThrows(StorageException.class, () -> cached.delete(range()));
        assertThrows(StorageException.class, () -> cached.delete(tile(0, 0, 0)));

        // refused before the cache
        assertEquals(calls, kv.calls.get());
        kv.failing = false;
        assertNotNull(kv.get(key(tile(0, 0, 0))));
    }

    @Test
    public void testTileDeletesSkipCacheWhileDown() throws Exception {
        enableCache(true);
        MappingCountingStore delegate = new MappingCountingStore();
        BlobStore cached = newCache().wrap(layer, delegate);
        cached.put(tile(0, 0, 1));
        cached.put(tile(1, 1, 1));
        kv.failing = true;

        // disk quota deletes free the store during the outage
        assertTrue(cached.delete(range()));
        assertTrue(cached.delete(tile(1, 1, 1)));

        assertFalse(delegate.get(tile(0, 0, 1)));
        assertFalse(delegate.get(tile(1, 1, 1)));
        String message = "In-memory tile cache is unreachable, tile deletes of layer " + LAYER
                + " run on the blob store only, cached copies are served until they expire";
        assertEquals(1, log.lines.stream().filter(message::equals).count());
    }

    @Test
    public void testFailedLayerRemovalStillDeletesStore() throws Exception {
        enableCache(true);
        store.put(tile(0, 0, 0));
        kv.failing = true;

        // a removed layer cannot be truncated again, so its store goes anyway
        assertThrows(StorageException.class, () -> store.delete(LAYER));

        assertFalse(inStore(tile(0, 0, 0)));
    }

    @Test
    public void testRemovalFailingAfterStoreTruncateThrows() throws Exception {
        enableCache(true);
        PausingStore delegate = new PausingStore();
        BlobStore cached = newCache().wrap(layer, delegate);
        cached.put(tile(0, 0, 0));
        String otherLayer = "other-layer/" + GRIDSET + "/png/default/1/0/0.png";
        kv.put(otherLayer, new byte[] {1}, 300);
        delegate.onTruncate = () -> kv.failRemovals = true;

        StorageException e = assertThrows(StorageException.class, () -> cached.delete(LAYER));

        assertThat(e.getMessage(), containsString("may be stale until they expire"));
        // no clear of the whole cache after the failure
        kv.failRemovals = false;
        assertNotNull(kv.get(otherLayer));
    }

    @Test
    public void testDisabledCacheLeavesTilesToStore() throws Exception {
        memoryCacheInfo = settings(new TestSettings(), true);
        memoryCacheInfo.setEnabled(false);
        store.put(tile(0, 0, 0));

        enableCache(false);
        assertFalse(store.get(tile(0, 0, 0)));
        assertTrue(inStore(tile(0, 0, 0)));
    }

    @Test
    public void testLayerNotOptedInBypassesCache() throws Exception {
        memoryCached = false;
        store.put(tile(0, 0, 0));
        store.get(tile(0, 0, 0));
        store.delete(tile(0, 0, 0));

        // tile calls never reach the cache, single tile deletes included
        assertEquals(0, kv.calls.get());
        memoryCached = true;
        assertFalse(store.get(tile(0, 0, 0)));
    }

    @Test
    public void testTruncateOfLayerNotOptedInClearsCache() throws Exception {
        store.put(tile(0, 0, 0));
        // the layer stopped using the cache, or the node running the truncate does not see it using it yet
        memoryCached = false;

        store.deleteByGridsetId(LAYER, GRIDSET);

        memoryCached = true;
        assertFalse(store.get(tile(0, 0, 0)));
    }

    @Test
    public void testCacheFailureKeepsTruncateOfLayerNotOptedIn() throws Exception {
        enableCache(true);
        memoryCached = false;
        store.put(tile(0, 0, 0));
        kv.failing = true;

        assertTrue(store.deleteByGridsetId(LAYER, GRIDSET));
        assertFalse(inStore(tile(0, 0, 0)));
    }

    @Test
    public void testOnlyChangedSettingsCloseStore() throws Exception {
        store.put(tile(0, 0, 0));

        // as a configuration reload with unchanged settings
        memoryCacheInfo = settings(memoryCacheInfo.getBackend(), false);
        store.get(tile(0, 0, 0));
        assertFalse(kv.closed);

        enableCache(false);
        store.get(tile(0, 0, 0));
        assertTrue(kv.closed);
    }

    @Test
    public void testUninstalledBackendDropsTiles() throws Exception {
        UnresolvedKeyValueStoreInfo removed = new UnresolvedKeyValueStoreInfo("removedTileCache", "<backend/>");
        memoryCacheInfo = settings(removed, false);

        store.put(tile(0, 0, 0));

        assertFalse(store.get(tile(0, 0, 0)));
        assertFalse(inStore(tile(0, 0, 0)));
        assertThat(stateLog.lines, hasItem("In-memory tile cache unusable, backend removedTileCache is not installed"));
    }

    @Test
    public void testUnusableCacheStillTruncatesStore() throws Exception {
        memoryCacheInfo = settings(new UnresolvedKeyValueStoreInfo("removedTileCache", "<backend/>"), true);
        store.put(tile(0, 0, 0));

        // the cache never held a tile, a failure there would fail every truncate until the settings change
        assertTrue(store.deleteByGridsetId(LAYER, GRIDSET));
        assertFalse(inStore(tile(0, 0, 0)));
    }

    @Test
    public void testBackendLinkageErrorDropsTiles() throws Exception {
        backendError = new NoClassDefFoundError("com/hazelcast/client/HazelcastClient");
        enableCache(false);

        store.put(tile(0, 0, 0));

        assertFalse(store.get(tile(0, 0, 0)));
        assertFalse(inStore(tile(0, 0, 0)));
        assertThat(stateLog.lines, hasItem("In-memory tile cache unusable, could not create backend TestSettings"));
    }

    @Test
    public void testInvalidTimeToLiveDropsTiles() throws Exception {
        memoryCacheInfo.setTimeToLive(-1);

        store.put(tile(0, 0, 0));

        assertFalse(store.get(tile(0, 0, 0)));
        assertFalse(inStore(tile(0, 0, 0)));
    }

    @Test
    public void testUnknownFormatVersionIsMiss() throws Exception {
        kv.put(key(tile(0, 0, 0)), cacheValue((byte) 2, System.currentTimeMillis()), 300);
        assertFalse(store.get(tile(0, 0, 0)));

        // truncated values, as written by a broken client, are misses too
        kv.put(key(tile(0, 0, 0)), new byte[0], 300);
        assertFalse(store.get(tile(0, 0, 0)));
        kv.put(key(tile(0, 0, 0)), new byte[] {1, 0, 0}, 300);
        assertFalse(store.get(tile(0, 0, 0)));
    }

    @Test
    public void testCallsSkippedWhileDown() throws Exception {
        BlobStore cached = newCache().wrap(layer, new MappingCountingStore());
        kv.failing = true;
        cached.get(tile(0, 0, 0));
        cached.put(tile(0, 0, 0));
        // a miss, as the write was dropped
        assertFalse(cached.get(tile(0, 0, 0)));
        assertEquals(1, kv.calls.get());

        // after the first retry interval (1 s, plus jitter) one call probes, then calls flow again
        kv.failing = false;
        now.addAndGet(SECONDS.toNanos(2));
        cached.put(tile(0, 0, 0));
        assertTrue(cached.get(tile(0, 0, 0)));
    }

    @Test
    public void testOutageLoggedOncePerTransition() throws Exception {
        BlobStore cached = newCache().wrap(layer, new MappingCountingStore());
        getAt(cached, 0, true);
        getAt(cached, 0, true);
        getAt(cached, 2, false);
        getAt(cached, 2, false);
        // failing again soon after the recovery continues the outage
        getAt(cached, 3, true);
        getAt(cached, 10, false);
        // a recovery held for longer than the longest retry interval ends it
        getAt(cached, 50, true);

        assertThat(
                stateLog.lines,
                contains(
                        "In-memory tile cache is unreachable, tiles are served without it until it recovers",
                        "In-memory tile cache is reachable again after 2 s",
                        "In-memory tile cache is unreachable again",
                        "In-memory tile cache is reachable again after 10 s",
                        "In-memory tile cache is unreachable, tiles are served without it until it recovers"));
    }

    @Test
    public void testReadThroughDuringTruncateDoesNotSurvive() throws Exception {
        enableCache(true);
        PausingStore delegate = new PausingStore();
        BlobStore cached = newCache().wrap(layer, delegate);
        delegate.put(tile(0, 0, 0));
        Future<Boolean> read = executor.submit(() -> cached.get(tile(0, 0, 0)));
        assertTrue(delegate.paused.await(10, SECONDS));
        // the read caches its stale tile once the store is truncated, before the last cache removal
        delegate.onTruncate = () -> {
            delegate.resume.countDown();
            return read.get(10, SECONDS);
        };

        assertTrue(cached.delete(LAYER));
        assertTrue(read.get());
        assertNull(kv.get(key(tile(0, 0, 0))));
    }

    @Test
    public void testReadThroughAfterTruncateExpires() throws Exception {
        enableCache(true);
        // longer than the time to live, which still bounds a tile read through
        expireCache = 3600;
        PausingStore delegate = new PausingStore();
        BlobStore cached = newCache().wrap(layer, delegate);
        delegate.put(tile(0, 0, 0));
        Future<Boolean> read = executor.submit(() -> cached.get(tile(0, 0, 0)));
        assertTrue(delegate.paused.await(10, SECONDS));

        assertTrue(cached.delete(LAYER));
        delegate.resume.countDown();
        assertTrue(read.get(10, SECONDS));

        // the known window: a read of the store before its truncate, caching after the last removal, leaves a stale
        // tile, served no longer than the time to live
        assertNotNull(kv.get(key(tile(0, 0, 0))));
        now.set(SECONDS.toNanos(memoryCacheInfo.getTimeToLive() + 1));
        assertNull(kv.get(key(tile(0, 0, 0))));
    }

    @Test
    public void testReadThroughKeepsNewerPut() throws Exception {
        enableCache(true);
        PausingStore delegate = new PausingStore();
        BlobStore cached = newCache().wrap(layer, delegate);
        delegate.put(tile(0, 0, 0));
        Future<Boolean> read = executor.submit(() -> cached.get(tile(0, 0, 0)));
        assertTrue(delegate.paused.await(10, SECONDS));

        TileObject newer = tile(0, 0, 0);
        newer.setBlob(new ByteArrayResource(new byte[] {4, 5, 6}));
        cached.put(newer);
        delegate.resume.countDown();
        assertTrue(read.get(10, SECONDS));

        TileObject served = tile(0, 0, 0);
        assertTrue(cached.get(served));
        assertArrayEquals(new byte[] {4, 5, 6}, ((ByteArrayResource) served.getBlob()).getContents());
    }

    @Test
    public void testTruncateDuringSeedLeavesNoEarlierTile() throws Exception {
        int seeders = 4;
        AtomicBoolean truncating = new AtomicBoolean();
        AtomicBoolean done = new AtomicBoolean();
        Queue<TileObject> before = new ConcurrentLinkedQueue<>();
        CountDownLatch seeded = new CountDownLatch(seeders);
        List<Future<Void>> seeds = new ArrayList<>();
        for (int y = 0; y < seeders; y++) {
            long row = y;
            seeds.add(executor.submit(() -> {
                // new tiles only, a tile written again after the truncate started may stay
                for (long x = 0; !done.get(); x++) {
                    TileObject tile = tile(x, row, 10);
                    store.put(tile);
                    // false after the put returned: it ended before the truncate started
                    if (!truncating.get()) before.add(tile);
                    if (x == 50) seeded.countDown();
                }
                return null;
            }));
        }
        assertTrue(seeded.await(30, SECONDS));
        truncating.set(true);
        long[][] bounds = {{0, 0, Integer.MAX_VALUE, seeders - 1, 10}};
        assertTrue(store.delete(new TileRange(LAYER, GRIDSET, 10, 10, bounds, ImageMime.png, STYLE_A)));
        done.set(true);
        for (Future<Void> seed : seeds) seed.get(30, SECONDS);

        List<TileObject> left =
                before.stream().filter(tile -> kv.get(key(tile)) != null).toList();
        assertThat(left, empty());
    }

    @Test
    public void testSettingsSaveWhileCacheHangsDoesNotStallTiles() throws Exception {
        // every call waits, then fails, as with a hung member before the client gives up
        kv.hangMillis = 500;
        kv.failing = true;
        List<Future<Void>> requests = new ArrayList<>();
        for (int y = 0; y < 16; y++) {
            long row = y;
            requests.add(executor.submit(() -> {
                for (long x = 0; x < 20; x++) store.get(tile(x, row, 10));
                return null;
            }));
        }
        // the save: new settings rebuild the cache, the stores reload under the configLock write lock
        enableCache(false);
        requests.add(executor.submit(() -> {
            store.setBlobStores(List.of());
            return null;
        }));

        // calling the cache on every request, each thread would take 20 x 500 ms
        await().atMost(5, SECONDS).until(() -> requests.stream().allMatch(Future::isDone));
        for (Future<Void> request : requests) request.get();
    }

    /** A cache with state of its own, as on another node, sharing {@link #kv}. */
    private MemoryCache newCache() {
        MemoryCache cache = new MemoryCache(memoryCacheInfo, layers, ticker);
        caches.add(cache);
        return cache;
    }

    private void getAt(BlobStore cached, long seconds, boolean failing) throws StorageException {
        now.set(SECONDS.toNanos(seconds));
        kv.failing = failing;
        cached.get(tile(0, 0, 0));
    }

    private void enableCache(boolean passthrough) {
        memoryCacheInfo = settings(new TestSettings(), passthrough);
    }

    private static MemoryCacheInfo settings(KeyValueStoreInfo backend, boolean passthrough) {
        MemoryCacheInfo info = new MemoryCacheInfo();
        info.setEnabled(true);
        info.setPassthrough(passthrough);
        info.setBackend(backend);
        return info;
    }

    private boolean inStore(TileObject tile) throws StorageException {
        memoryCacheInfo = null;
        return store.get(tile);
    }

    private String key(TileObject tile) {
        return new TMSKeyBuilder(null, layers).forTile(tile);
    }

    /** Zoom levels 0 and 1 of {@link #STYLE_A}, column and row 0 only. */
    private static TileRange range() {
        long[][] bounds = {{0, 0, 0, 0, 0}, {0, 0, 0, 0, 1}};
        return new TileRange(LAYER, GRIDSET, 0, 1, bounds, ImageMime.png, STYLE_A);
    }

    /** A cache entry for the tile content of {@link #tile}, as any node writes it. */
    private static byte[] cacheValue(byte version, long created) {
        return ByteBuffer.allocate(Byte.BYTES + Long.BYTES + 3)
                .put(version)
                .putLong(created)
                .put(new byte[] {1, 2, 3})
                .array();
    }

    /** The {@link #STYLE_A} mapping the file store keeps next to the layer's tiles. */
    private File mappingFile() {
        String name = PARAMETERS_METADATA_OBJECT_PREFIX + ParametersUtils.getId(STYLE_A);
        return new File(new File(temp.getRoot(), LAYER), name + PARAMETERS_METADATA_OBJECT_SUFFIX);
    }

    private static TileObject tile(long x, long y, long z) {
        return tile(GRIDSET, STYLE_A, x, y, z);
    }

    private static TileObject tile(String gridset, Map<String, String> parameters, long x, long y, long z) {
        ByteArrayResource blob = new ByteArrayResource(new byte[] {1, 2, 3});
        return TileObject.createCompleteTileObject(
                LAYER, new long[] {x, y, z}, gridset, ImageMime.png.getFormat(), parameters, blob);
    }

    /** Fails every call, or only removals, on demand. */
    private static class FailingStore implements KeyValueStore {

        private final KeyValueStore delegate;

        volatile boolean failing;

        volatile boolean failRemovals;

        /** How long a failing call waits before it throws. */
        volatile long hangMillis;

        volatile boolean closed;

        final AtomicInteger calls = new AtomicInteger();

        FailingStore(KeyValueStore delegate) {
            this.delegate = delegate;
        }

        private void check(boolean removal) {
            calls.incrementAndGet();
            if (!failing && !(removal && failRemovals)) return;
            sleep(hangMillis);
            throw new IllegalStateException("Simulated cache failure");
        }

        @Override
        public byte[] get(String key) {
            check(false);
            return delegate.get(key);
        }

        @Override
        public void put(String key, byte[] value, long ttlSeconds) {
            check(false);
            delegate.put(key, value, ttlSeconds);
        }

        @Override
        public void putIfAbsent(String key, byte[] value, long ttlSeconds) {
            check(false);
            delegate.putIfAbsent(key, value, ttlSeconds);
        }

        @Override
        public void remove(String key) {
            check(true);
            delegate.remove(key);
        }

        @Override
        public void removeByPrefix(String prefix) {
            check(true);
            delegate.removeByPrefix(prefix);
        }

        @Override
        public void removeMatching(String prefix, Predicate<String> filter) {
            check(true);
            delegate.removeMatching(prefix, filter);
        }

        @Override
        public void clear() {
            check(false);
            delegate.clear();
        }

        @Override
        public void close() {
            closed = true;
            delegate.close();
        }
    }

    /** Counts mapping writes, slowed down so that concurrent puts overlap, and fails them on demand. */
    private class MappingCountingStore extends FileBlobStore {

        final AtomicInteger writes = new AtomicInteger();

        volatile boolean failing;

        MappingCountingStore() throws StorageException {
            super(temp.getRoot().getAbsolutePath());
        }

        @Override
        public void putParametersMetadata(String layerName, String parametersId, Map<String, String> parameters)
                throws StorageException {
            if (failing) throw new StorageException("Simulated mapping write failure");
            writes.incrementAndGet();
            sleep(50);
            super.putParametersMetadata(layerName, parametersId, parameters);
        }
    }

    /**
     * Reads the whole tile, as the S3 store does, then pauses until resumed, so that a truncate can run between the
     * read and the cache write that follows it.
     */
    private class PausingStore extends FileBlobStore {

        final CountDownLatch paused = new CountDownLatch(1);

        final CountDownLatch resume = new CountDownLatch(1);

        /** Runs after the layer truncate of the store, when set. */
        volatile Callable<?> onTruncate;

        PausingStore() throws StorageException {
            super(temp.getRoot().getAbsolutePath());
        }

        @Override
        public boolean get(TileObject obj) throws StorageException {
            if (!super.get(obj)) return false;
            try (InputStream in = obj.getBlob().getInputStream()) {
                obj.setBlob(new ByteArrayResource(in.readAllBytes()));
            } catch (IOException e) {
                throw new StorageException("Read failed", e);
            }
            // paused with the file closed: on Windows an open file blocks the rename that starts a layer truncate
            paused.countDown();
            try {
                if (!resume.await(10, SECONDS)) throw new StorageException("Read never resumed");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new StorageException("Read interrupted", e);
            }
            return true;
        }

        @Override
        public boolean delete(String layerName) throws StorageException {
            boolean result = super.delete(layerName);
            if (onTruncate == null) return result;
            try {
                onTruncate.call();
            } catch (Exception e) {
                throw new StorageException("Truncate hook failed", e);
            }
            return result;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Thrown by {@link TestSettings#createStore} when set. */
    private Error backendError;

    /** Hands the shared {@link FailingStore} to every cache built from these settings. */
    private class TestSettings implements KeyValueStoreInfo {

        @Override
        public KeyValueStore createStore() {
            if (backendError != null) throw backendError;
            return kv;
        }
    }
}
