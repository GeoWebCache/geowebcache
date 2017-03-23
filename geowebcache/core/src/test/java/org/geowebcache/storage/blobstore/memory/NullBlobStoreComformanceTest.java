package org.geowebcache.storage.blobstore.memory;

import org.geowebcache.storage.AbstractBlobStoreTest;
import org.geowebcache.storage.blobstore.memory.NullBlobStore;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class NullBlobStoreComformanceTest extends AbstractBlobStoreTest<NullBlobStore> {
    
    @Override
    public void createTestUnit() throws Exception {
        this.store = new NullBlobStore();
    }
    
    @Before
    public void setEvents() throws Exception  {
        this.events = false;
    }

    @Override
    @Ignore @Test
    public void testStoreTile() throws Exception {
        super.testStoreTile();
    }

    @Override
    @Ignore @Test
    public void testStoreTilesInMultipleLayers() throws Exception {
        super.testStoreTilesInMultipleLayers();
    }

    @Override
    @Ignore @Test
    public void testUpdateTile() throws Exception {
        super.testUpdateTile();
    }

    @Override
    @Ignore @Test
    public void testGridsets() throws Exception {
        super.testGridsets();
    }

    @Override
    @Ignore @Test
    public void testDeleteGridsetDoesntDeleteOthers() throws Exception {
        super.testDeleteGridset();
    }

    @Override
    @Ignore @Test
    public void testDeleteByParametersId() throws Exception {
        super.testDeleteByParametersId();
    }

    @Override
    @Ignore @Test
    public void testParameters() throws Exception {
        super.testParameters();
    }
    
    @Override
    @Ignore @Test
    public void testParameterIDList() throws Exception {
        super.testParameterIDList();
    }

    @Override
    @Ignore @Test
    public void testParameterList() throws Exception {
        super.testParameterList();
    }
    
    @Override
    @Ignore @Test
    public void testDeleteByParametersIdDoesNotDeleteOthers() throws Exception {
        super.testDeleteByParametersIdDoesNotDeleteOthers();
    }
    
    @Override
    @Ignore @Test
    public void testPurgeOrphans() throws Exception {
        super.testPurgeOrphans();
    }
    
    @Override
    @Ignore @Test
    public void testPurgeOrphansWithDefault() throws Exception {
        super.testPurgeOrphansWithDefault();
    }
}
