package org.geowebcache.diskquota.jdbc;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;

import junit.framework.TestCase;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.easymock.classextension.EasyMock;
import org.geowebcache.config.Configuration;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.config.XMLConfigurationBackwardsCompatibilityTest;
import org.geowebcache.diskquota.DiskQuotaMonitor;
import org.geowebcache.diskquota.storage.TilePageCalculator;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.storage.DefaultStorageFinder;

public class JDBCQuotaStoreTest extends TestCase {

    JDBCQuotaStore store;

    File targetDir;

    DefaultStorageFinder cacheDirFinder;

    TileLayerDispatcher layerDispatcher;

    TilePageCalculator tilePageCalculator;

    private BasicDataSource dataSource;

    @Override
    protected void setUp() throws Exception {
        // prepare a mock target directory for tiles
        targetDir = new File("target", "mockStore");
        FileUtils.deleteDirectory(targetDir);
        targetDir.mkdirs();

        cacheDirFinder = EasyMock.createMock(DefaultStorageFinder.class);
        EasyMock.expect(cacheDirFinder.getDefaultPath()).andReturn(targetDir.getAbsolutePath())
                .anyTimes();
        EasyMock.expect(
                cacheDirFinder.findEnvVar(EasyMock.eq(DiskQuotaMonitor.GWC_DISKQUOTA_DISABLED)))
                .andReturn(null).anyTimes();
        EasyMock.replay(cacheDirFinder);

        XMLConfiguration xmlConfig = loadXMLConfig();
        LinkedList<Configuration> configList = new LinkedList<Configuration>();
        configList.add(xmlConfig);

        layerDispatcher = new TileLayerDispatcher(new GridSetBroker(true, true), configList);

        tilePageCalculator = new TilePageCalculator(layerDispatcher);

        // prepare a connection pool for tests against a H2 database
        dataSource = setupDataSource();
        SQLDialect dialect = getDialect();

        // setup the quota store
        store = new JDBCQuotaStore(cacheDirFinder, tilePageCalculator);
        store.setDataSource(dataSource);
        store.setDialect(dialect);

        // finally initialize the store
        store.initialize();
    }
    
    @Override
    protected void tearDown() throws Exception {
        dataSource.close();
    }

    private SQLDialect getDialect() {
        return new H2Dialect();
    }

    private XMLConfiguration loadXMLConfig() {
        InputStream is = null;
        XMLConfiguration xmlConfig = null;
        try {
            is = XMLConfiguration.class
                    .getResourceAsStream(XMLConfigurationBackwardsCompatibilityTest.LATEST_FILENAME);
            xmlConfig = new XMLConfiguration(is);
        } catch (Exception e) {
            // Do nothing
        } finally {
            IOUtils.closeQuietly(is);
        }

        return xmlConfig;
    }

    private BasicDataSource setupDataSource() throws IOException {
        // cleanup previous eventual db
        File[] files = new File("./target").listFiles(new FilenameFilter() {
            
            public boolean accept(File dir, String name) {
                return name.startsWith("quota-h2");
            }
        });
        for (File file : files) {
           assertTrue(file.delete()); 
        }
        
        BasicDataSource dataSource = new BasicDataSource();

        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:./target/quota-h2");
        dataSource.setUsername("sa");
        dataSource.setPoolPreparedStatements(true);
        dataSource.setAccessToUnderlyingConnectionAllowed(true);
        dataSource.setMinIdle(1);
        dataSource.setMaxActive(4);
        // if we cannot get a connection within 5 seconds give up
        dataSource.setMaxWait(5000);
        return dataSource;
    }
    
    public void testTableSetup() {
        
    }
}
