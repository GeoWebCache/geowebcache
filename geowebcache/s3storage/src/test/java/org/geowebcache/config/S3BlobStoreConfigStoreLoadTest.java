package org.geowebcache.config;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.locks.NoOpLockProvider;
import org.geowebcache.s3.Access;
import org.geowebcache.s3.PropertiesLoader;
import org.geowebcache.s3.S3BlobStoreInfo;
import org.geowebcache.s3.S3BlobStoreConfigProvider;
import org.geowebcache.s3.TemporaryS3Folder;
import org.geowebcache.storage.StorageException;
import org.geowebcache.util.ApplicationContextProvider;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.web.context.WebApplicationContext;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class S3BlobStoreConfigStoreLoadTest {

    private XMLConfiguration config;

    private static final Log log = LogFactory.getLog(S3BlobStoreConfigStoreLoadTest.class);

    public PropertiesLoader testConfigLoader = new PropertiesLoader();

    @Rule
    public TemporaryS3Folder tempFolder = new TemporaryS3Folder(testConfigLoader.getProperties());

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Mock
    ApplicationContextProvider context;

    @Mock
    TileLayerDispatcher dispatch;

    private File configDir;

    private File configFile;

    @Mock
    private WebApplicationContext webCtx;

    private Map<String, XMLConfigurationProvider> providers;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSaveLoadS3BlobStoresAccess() throws Exception {
        Assume.assumeTrue(tempFolder.isConfigured());
        setupXMLConfig();
        S3BlobStoreInfo store1 = createConfig("1");
        S3BlobStoreInfo store2 = createConfig("2");
        store2.setAccess(Access.PRIVATE);

        saveConfig(store1, store2);
        validateAndLoadSavedConfig();
    }

    private void saveConfig(S3BlobStoreInfo store1, S3BlobStoreInfo store2) throws IOException {
        List<BlobStoreInfo> blobStores = config.getBlobStores();
        blobStores.add(store1);
        blobStores.add(store2);
        config.save();
    }

    private void validateAndLoadSavedConfig()
            throws SAXException, IOException, ConfigurationException, FileNotFoundException {
        validateSavedConfig();
        loadSavedConfig();
    }

    private void loadSavedConfig() {
        try {
            XMLConfiguration configLoad = new XMLConfiguration(context, configDir.getAbsolutePath(),
                    null);
            GridSetBroker gridSetBroker = new GridSetBroker(true, true);
            configLoad.initialize(gridSetBroker);
            createFromSavedConfig(configLoad);
        } catch (StorageException | GeoWebCacheException e) {
            log.error(e.getMessage());
            fail("Error loading from " + configFile + " " + e.getMessage());
        }
    }

    private void createFromSavedConfig(BlobStoreConfiguration configLoad) throws StorageException {
        List<BlobStoreInfo> blobStores2 = configLoad.getBlobStores();
        for (BlobStoreInfo blobStoreConfig : blobStores2) {
            LockProvider lockProvider = new NoOpLockProvider();
            blobStoreConfig.createInstance(dispatch, lockProvider);
        }
    }

    private void validateSavedConfig()
            throws SAXException, IOException, ConfigurationException, FileNotFoundException {
        try {
            XMLConfiguration
                    .validate(XMLConfiguration.loadDocument(new FileInputStream(configFile)));
        } catch (SAXParseException e) {
            log.error(e.getMessage());
            fail("Error validating from " + configFile + " " + e.getMessage());
        }
    }

    private S3BlobStoreInfo createConfig(String id) {
        S3BlobStoreInfo store = new S3BlobStoreInfo(id);
        store.setDefault(true);
        store.setEnabled(true);
        Properties properties = testConfigLoader.getProperties();
        store.setBucket(properties.getProperty("bucket"));
        store.setAwsAccessKey(properties.getProperty("accessKey"));
        store.setAwsSecretKey(properties.getProperty("secretKey"));
        return store;
    }

    private void setupXMLConfig() throws IOException, GeoWebCacheException {
        configDir = temp.getRoot();
        configFile = temp.newFile("geowebcache.xml");

        URL source = XMLConfiguration.class
                .getResource(XMLConfigurationBackwardsCompatibilityTest.LATEST_FILENAME);
        FileUtils.copyURLToFile(source, configFile);

        providers = new HashMap<String, XMLConfigurationProvider>();
        Mockito.when(context.getApplicationContext()).thenReturn(webCtx, webCtx, webCtx, webCtx);
        Mockito.when(webCtx.getBeansOfType(XMLConfigurationProvider.class)).thenReturn(providers,
                providers);
        S3BlobStoreConfigProvider provider = new S3BlobStoreConfigProvider();
        Mockito.when(webCtx.getBean("S3BlobStore")).thenReturn(provider, provider);
        providers.put("S3BlobStore", provider);
        GridSetBroker gridSetBroker = new GridSetBroker(true, true);
        config = new XMLConfiguration(context, configDir.getAbsolutePath());
        config.initialize(gridSetBroker);
    }

}
