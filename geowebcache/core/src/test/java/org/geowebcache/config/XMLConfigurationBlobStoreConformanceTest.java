package org.geowebcache.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.commons.io.FileUtils;
import org.geowebcache.MockWepAppContextRule;
import org.hamcrest.CustomMatcher;
import org.hamcrest.Matcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Objects;

/**
 *
 */
public class XMLConfigurationBlobStoreConformanceTest extends BlobStoreConfigurationTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    private File configDir;
    private File configFile;

    @Override
    public void failNextRead() {
        configSource.setFailNextRead(true);
    }

    @Override
    public void failNextWrite() {
        configSource.setFailNextWrite(true);
    }

    public @Rule MockWepAppContextRule extensions = new MockWepAppContextRule();
    public @Rule MockWepAppContextRule extensions2 = new MockWepAppContextRule(false);
    
    @Override
    protected BlobStoreConfiguration getConfig() throws Exception {
        makeConfigFile();
        return getConfig(extensions);
    }
    
    protected void makeConfigFile() throws IOException {
        if(configFile==null) {
            configDir = temp.getRoot();
            configFile = temp.newFile("geowebcache.xml");
            
            URL source = XMLConfiguration.class
                .getResource("geowebcache_190.xml");
            FileUtils.copyURLToFile(source, configFile);
        }
    }
    @Override
    protected BlobStoreConfiguration getSecondConfig() throws Exception {
        return getConfig(extensions2);
    }
    
    TestXMLConfigurationSource configSource = new TestXMLConfigurationSource();
    
    protected BlobStoreConfiguration getConfig(MockWepAppContextRule extensions) throws Exception {
        return configSource.create(extensions, configDir);
    }

    @Test
    public void testBlobStoreConfigIsLoaded() throws Exception {
        // get the blobstores from the config (from test resource geowebcache_190.xml)
        final List<BlobStoreInfo> blobStores = config.getBlobStores();
        assertEquals("Unexpected number of BlobStoreInfo elements configured", 1, blobStores.size());
        // get the 1 configured BlobStoreInfo
        BlobStoreInfo info = blobStores.get(0);
        assertFalse("Unexpected BlobStoreInfo default setting", info.isDefault());
        assertFalse("Unexpected BlobStoreInfo enabled setting", info.isEnabled());
        assertTrue("Unexpected BlobeStoreInfo class type", FileBlobStoreInfo.class.isAssignableFrom(info.getClass()));
        // cast the info to a FileBlobStoreInfo
        final FileBlobStoreInfo fileInfo = FileBlobStoreInfo.class.cast(info);
        assertEquals("Unexpected FileBlobStoreInfo filesystem block size", 4096, fileInfo.getFileSystemBlockSize());
        assertEquals("Unexpected FileBlobStoreInfo location value", "/tmp/defaultCache", fileInfo.getBaseDirectory());
    }

    @Override
    protected void doModifyInfo(BlobStoreInfo info, int rand) throws Exception {
        ((FileBlobStoreInfo)info).setFileSystemBlockSize(rand);
    }

    @Override
    protected BlobStoreInfo getGoodInfo(String id, int rand) throws Exception {
        FileBlobStoreInfo info = new FileBlobStoreInfo(id);
        info.setEnabled(false);
        info.setDefault(false);
        info.setBaseDirectory("/tmp/defaultCache");
        info.setFileSystemBlockSize(rand);
        return info;
    }

    @Override
    protected BlobStoreInfo getBadInfo(String id, int rand) throws Exception {
        FileBlobStoreInfo info = new FileBlobStoreInfo(id) {
            @Override
            public String getName() {
                return null;
            }
        };
        return info;
    }

    @Override
    protected String getExistingInfo() {
        return "defaultCache";
    }

    @Override
    protected Matcher<BlobStoreInfo> infoEquals(BlobStoreInfo expected) {
        return new CustomMatcher<BlobStoreInfo>("BlobStoreInfo Matcher"){
            @Override
            public boolean matches(Object item) {
                return expected.equals(item);
            }
        };
    }
    
    @Override
    protected Matcher<BlobStoreInfo> infoEquals(int expected) {
        return new CustomMatcher<BlobStoreInfo>("BlobStoreInfo with value " + expected){
            
            @Override
            public boolean matches(Object item) {
                return item instanceof BlobStoreInfo && (Objects.equals(((BlobStoreInfo)item).getName(), Integer.toString(expected)));
            }
            
        };
    }
}
