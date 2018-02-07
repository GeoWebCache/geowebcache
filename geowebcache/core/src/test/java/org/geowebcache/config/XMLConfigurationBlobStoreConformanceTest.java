package org.geowebcache.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.commons.io.FileUtils;
import org.geowebcache.grid.GridSetBroker;
import org.hamcrest.CustomMatcher;
import org.hamcrest.Matcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.web.context.WebApplicationContext;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.List;

/**
 *
 */
public class XMLConfigurationBlobStoreConformanceTest extends BlobStoreConfigurationTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    private File configDir;
    private File configFile;
    private boolean failNextRead;
    private boolean failNextWrite;

    @Override
    public void failNextRead() {
        failNextRead = true;
    }

    @Override
    public void failNextWrite() {
        failNextWrite = true;
    }

    @Override
    protected BlobStoreConfiguration getConfig() throws Exception {
        if(configFile==null) {
            // create a temp XML config
            configDir = temp.getRoot();
            configFile = temp.newFile(XMLConfiguration.DEFAULT_CONFIGURATION_FILE_NAME);
            // copy the example XML to the temp config file
            URL source = XMLConfiguration.class.getResource("geowebcache_190.xml");
            FileUtils.copyURLToFile(source, configFile);
        }
        // initialize the config with an XMLFileResourceProvider that uses the temp config file
        GridSetBroker gridSetBroker = new GridSetBroker(true, true);
        ConfigurationResourceProvider configProvider =
            new XMLFileResourceProvider(XMLConfiguration.DEFAULT_CONFIGURATION_FILE_NAME,
                (WebApplicationContext)null, configDir.getAbsolutePath(), null) {
                    @Override
                    public InputStream in() throws IOException {
                        if(failNextRead) {
                            failNextRead = false;
                            throw new IOException("Test failure on read");
                        }
                        return super.in();
                    }

                    @Override
                    public OutputStream out() throws IOException {
                        if(failNextWrite) {
                            failNextWrite = false;
                            throw new IOException("Test failure on write");
                        }
                        return super.out();
                    }
                };
        config = new XMLConfiguration(null, configProvider);
        config.initialize(gridSetBroker);
        return config;
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
    protected String getExistingInfo() throws Exception {
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
}
