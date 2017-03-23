package org.geowebcache.storage.blobstore.memory;

import org.geowebcache.storage.AbstractBlobStoreTest;
import org.geowebcache.storage.blobstore.file.FileBlobStore;
import org.geowebcache.storage.blobstore.memory.MemoryBlobStore;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class MemoryBlobStoreWithFilesComformanceTest extends AbstractBlobStoreTest<MemoryBlobStore> {
    
    @Override
    public void createTestUnit() throws Exception {
        this.store = new MemoryBlobStore();
        this.store.setStore(new FileBlobStore(temp.getRoot().getAbsolutePath()));
    }
    
    @Before
    public void setEvents() throws Exception {
        this.events = false;
    }
    
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
}
