package org.geowebcache.blobstore.file;

import org.geowebcache.storage.AbstractBlobStoreTest;
import org.geowebcache.storage.blobstore.file.FileBlobStore;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class FileBlobStoreComformanceTest extends AbstractBlobStoreTest<FileBlobStore> {
    
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    
    @Override
    public void createTestUnit() throws Exception {
        this.store = new FileBlobStore(temp.getRoot().getAbsolutePath());
    }
}
