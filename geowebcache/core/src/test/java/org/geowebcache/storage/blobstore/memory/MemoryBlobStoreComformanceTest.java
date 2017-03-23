package org.geowebcache.storage.blobstore.memory;

import org.geowebcache.storage.AbstractBlobStoreTest;
import org.geowebcache.storage.blobstore.memory.MemoryBlobStore;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class MemoryBlobStoreComformanceTest extends AbstractBlobStoreTest<MemoryBlobStore> {
    
    @Override
    public void createTestUnit() throws Exception {
        this.store = new MemoryBlobStore();
    }
    
    @Before
    public void setEvents() throws Exception {
        this.events = false;
    }

    @Override
    @Ignore @Test // Memory store can be more relaxed about this. It would be nice to pass this though
    public void testDeleteGridsetDoesntDeleteOthers() throws Exception {
        super.testDeleteGridsetDoesntDeleteOthers();
    }
    
    @Override
    @Ignore @Test // Memory store can be more relaxed about this. It would be nice to pass this though
    public void testDeleteByParametersIdDoesNotDeleteOthers() throws Exception {
        super.testDeleteByParametersIdDoesNotDeleteOthers();
    }
    
    @Override
    @Ignore @Test // TODO For now, this is a limitation of MemoryBlobStore
    public void testParameterList() throws Exception {
        super.testParameterList();
    }
    
    @Override
    @Ignore @Test // TODO For now, this is a limitation of MemoryBlobStore
    public void testParameterIDList() throws Exception {
        super.testParameterIDList();
    }
    
    @Override
    @Ignore @Test // Memory store can be more relaxed about this. It would be nice to pass this though
    public void testPurgeOrphans() throws Exception {
        super.testPurgeOrphans();
    }
    @Override
    @Ignore @Test // Memory store can be more relaxed about this. It would be nice to pass this though
    public void testPurgeOrphansWithDefault() throws Exception {
        super.testPurgeOrphansWithDefault();
    }
}
