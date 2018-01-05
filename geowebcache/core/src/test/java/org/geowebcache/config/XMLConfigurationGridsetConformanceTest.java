package org.geowebcache.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Objects;

import org.apache.commons.io.FileUtils;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSetFactory;
import org.geowebcache.grid.SRS;
import org.hamcrest.CustomMatcher;
import org.hamcrest.Matcher;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;

public class XMLConfigurationGridsetConformanceTest extends GridSetConfigurationTest {

    @Override
    protected GridSet getGoodInfo(String id, int rand) {
        GridSet gridset = GridSetFactory.createGridSet(id, SRS.getSRS(rand), new BoundingBox(0,0,1,1), true, 3, 1.0, GridSetFactory.DEFAULT_PIXEL_SIZE_METER, 256, 256, false);
        return gridset;
    }

    @Override
    protected GridSet getBadInfo(String id, int rand) {
        return new GridSet() {};
    }
    
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    private File configDir;
    private File configFile;
    private ConfigurationResourceProvider configProvider;
    
    private boolean failNextRead = false;
    private boolean failNextWrite = false;
    
    @Override
    protected GridSetConfiguration getConfig() throws Exception {
        if(configFile==null) {
            configDir = temp.getRoot();
            configFile = temp.newFile("geowebcache.xml");
            
            URL source = XMLConfiguration.class
                .getResource(XMLConfigurationBackwardsCompatibilityTest.LATEST_FILENAME);
            FileUtils.copyURLToFile(source, configFile);
        }
        
        GridSetBroker gridSetBroker = new GridSetBroker(true, true);
        configProvider = new XMLFileResourceProvider(XMLConfiguration.DEFAULT_CONFIGURATION_FILE_NAME,
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

    @Override
    protected Matcher<GridSet> infoEquals(GridSet expected) {
        return new CustomMatcher<GridSet>("GridSet matching "+expected.getName()+" with " + expected.getSrs()){
            
            @Override
            public boolean matches(Object item) {
                return item instanceof GridSet && ((GridSet)item).getName().equals(((GridSet)expected).getName()) &&
                    ((GridSet)item).getSrs().equals(((GridSet)expected).getSrs());
            }
            
        };
    }

    @Override
    protected String getExistingInfo() throws Exception {
        return "EPSG:2163";
    }

    @Override
    public void failNextRead() {
        failNextRead = true;
    }

    @Override
    public void failNextWrite() {
        failNextWrite = true;
    }

}
