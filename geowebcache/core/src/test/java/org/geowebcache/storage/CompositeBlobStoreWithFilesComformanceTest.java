package org.geowebcache.storage;

import static org.easymock.classextension.EasyMock.not;
import static org.easymock.EasyMock.or;
import static org.easymock.classextension.EasyMock.createNiceMock;
import static org.easymock.classextension.EasyMock.eq;
import static org.easymock.classextension.EasyMock.expect;

import java.util.LinkedList;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.BlobStoreConfig;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.storage.AbstractBlobStoreTest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import org.easymock.classextension.EasyMock;

public class CompositeBlobStoreWithFilesComformanceTest extends AbstractBlobStoreTest<CompositeBlobStore> {
    
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    
    private TileLayerDispatcher tld;
    private DefaultStorageFinder defaultStorageFinder;
    private XMLConfiguration configuration;
    private TileLayer defaultLayer;
    private TileLayer defaultLayer1;
    private TileLayer defaultLayer2;
    private LinkedList<BlobStoreConfig> configs;

    private String DEFAULT_LAYER="testLayer";
    private String DEFAULT_LAYER1="testLayer1";
    private String DEFAULT_LAYER2="testLayer2";
    
    @Override
    public void createTestUnit() throws Exception {
        tld = createNiceMock("tld", TileLayerDispatcher.class);
        defaultStorageFinder = createNiceMock("defaultStorageFinder", DefaultStorageFinder.class);
        configuration = createNiceMock("configuration", XMLConfiguration.class);
        
        configs = new LinkedList<>();
        expect(configuration.getBlobStores()).andStubReturn(configs);
        
        expect(defaultStorageFinder.getDefaultPath()).andStubReturn(
                temp.getRoot().getAbsolutePath());
        
        defaultLayer = createNiceMock("defaultLayer", TileLayer.class);
        defaultLayer1 = createNiceMock("defaultLayer1", TileLayer.class);
        defaultLayer2 = createNiceMock("defaultLayer2", TileLayer.class);
        expect(tld.getTileLayer(eq(DEFAULT_LAYER))).andStubReturn(defaultLayer);
        expect(tld.getTileLayer(eq(DEFAULT_LAYER1))).andStubReturn(defaultLayer1);
        expect(tld.getTileLayer(eq(DEFAULT_LAYER2))).andStubReturn(defaultLayer2);
        expect(tld.getTileLayer(not(or(eq(DEFAULT_LAYER), or(eq(DEFAULT_LAYER1), eq(DEFAULT_LAYER2)))))).andStubThrow(
                new GeoWebCacheException("layer not found"));
        
        EasyMock.replay(tld, defaultStorageFinder, configuration, defaultLayer, defaultLayer1, defaultLayer2);
        store = new CompositeBlobStore(tld, defaultStorageFinder, configuration);
    }
    
    @Before
    public void setEvents() throws Exception {
        this.events = true;
    }
}
