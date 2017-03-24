package org.geowebcache.s3;

import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.classextension.EasyMock.createMock;
import static org.easymock.classextension.EasyMock.createNiceMock;
import static org.easymock.classextension.EasyMock.replay;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Stream;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.locks.NoOpLockProvider;
import org.geowebcache.storage.AbstractBlobStoreTest;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;

import org.easymock.classextension.EasyMock;

public class S3BlobStoreConformanceTest extends AbstractBlobStoreTest<S3BlobStore> {
    public PropertiesLoader testConfigLoader = new PropertiesLoader();
    
    @Rule
    public TemporaryS3Folder tempFolder = new TemporaryS3Folder(testConfigLoader.getProperties());
    
    @Override
    public void createTestUnit() throws Exception {
        Assume.assumeTrue(tempFolder.isConfigured());
        S3BlobStoreConfig config = tempFolder.getConfig();
        
        TileLayerDispatcher layers = createMock(TileLayerDispatcher.class);
        LockProvider lockProvider = new NoOpLockProvider();
        Stream.of("testLayer", "testLayer1", "testLayer2")
            .map(name-> {
                TileLayer mock = createMock(name, TileLayer.class);
                expect(mock.getName()).andStubReturn(name);
                expect(mock.getId()).andStubReturn(name);
                expect(mock.getGridSubsets()).andStubReturn(Collections.singleton("testGridSet"));
                expect(mock.getMimeTypes()).andStubReturn(Arrays.asList(org.geowebcache.mime.ImageMime.png));
                try {
                    expect(layers.getTileLayer(eq(name))).andStubReturn(mock);
                } catch (GeoWebCacheException e) {
                    fail();
                }
                return mock;
            }).forEach(EasyMock::replay);
        replay(layers);
        store = new S3BlobStore(config, layers, lockProvider);
    }

}
