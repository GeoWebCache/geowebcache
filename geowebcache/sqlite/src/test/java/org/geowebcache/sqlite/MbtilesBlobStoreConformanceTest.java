package org.geowebcache.sqlite;

import org.geowebcache.storage.AbstractBlobStoreTest;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import org.easymock.Capture;
import org.easymock.EasyMock;

public class MbtilesBlobStoreConformanceTest extends AbstractBlobStoreTest<MbtilesBlobStore> {
    
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    
    @Override
    public void createTestUnit() throws Exception {
        this.store = new MbtilesBlobStore(getDefaultConfiguration());
    }
    
    protected MbtilesConfiguration getDefaultConfiguration() {
        MbtilesConfiguration configuration = new MbtilesConfiguration();
        configuration.setPoolSize(1000);
        configuration.setRootDirectory(temp.getRoot().getPath());
        configuration.setTemplatePath(Utils.buildPath("{grid}", "{layer}", "{params}", "{format}", "{z}", "tiles-{x}-{y}.sqlite"));
        configuration.setRowRangeCount(500);
        configuration.setColumnRangeCount(500);
        return configuration;
    }
}
