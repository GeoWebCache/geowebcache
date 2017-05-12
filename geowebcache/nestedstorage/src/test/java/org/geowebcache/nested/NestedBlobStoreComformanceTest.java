package org.geowebcache.nested;

import org.geowebcache.storage.AbstractBlobStoreTest;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.blobstore.file.FileBlobStore;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

public class NestedBlobStoreComformanceTest extends AbstractBlobStoreTest<NestedBlobStore> {
    
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    
    @Rule 
    public TemporaryFolder temp2 = new TemporaryFolder(); 
    
    private BlobStore backingStore;
    private BlobStore frontStore;
    
    @Override
    public void createTestUnit() throws Exception {
        frontStore = new FileBlobStore(temp.getRoot().getAbsolutePath());
        backingStore = new FileBlobStore(temp2.getRoot().getAbsolutePath());
        this.store = configureStore();
    }
    
    private NestedBlobStore configureStore() {
        NestedBlobStoreConfig config = Mockito.mock(NestedBlobStoreConfig.class);
        Mockito.when(config.getBackingStore()).thenReturn(backingStore);
        Mockito.when(config.getFrontStore()).thenReturn(frontStore);
        NestedBlobStore store;
        store = new NestedBlobStore(config);
        return store;
    }
}
