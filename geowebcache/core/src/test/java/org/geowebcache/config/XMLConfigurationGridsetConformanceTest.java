package org.geowebcache.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Objects;

import javax.security.auth.login.Configuration;

import org.apache.commons.io.FileUtils;
import org.geowebcache.MockWepAppContextRule;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.hamcrest.CustomMatcher;
import org.hamcrest.Matcher;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.springframework.web.context.WebApplicationContext;

public class XMLConfigurationGridsetConformanceTest extends GridSetConfigurationTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    
    protected File configDir;
    protected File configFile;
    
    private ConfigurationResourceProvider configProvider;
    
    public @Rule MockWepAppContextRule extensions = new MockWepAppContextRule();

    private boolean failNextRead = false;
    private boolean failNextWrite = false;
    
    @Override
    protected GridSetConfiguration getConfig() throws Exception {
        makeConfigFile();
        
        GridSetBroker gridSetBroker = new GridSetBroker(true, true);
        gridSetBroker.initialize();
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
        config = new XMLConfiguration(extensions.getContextProvider(), configProvider);
        extensions.addBean("XMLConfiguration", config, Configuration.class, GridSetConfiguration.class, TileLayerConfiguration.class);
        ((XMLConfiguration) config).setGridSetBroker(gridSetBroker);
        config.reinitialize();
        return config;
    }
    
    protected void makeConfigFile() throws IOException {
        if(configFile==null) {
            configDir = temp.getRoot();
            configFile = temp.newFile("geowebcache.xml");
            
            URL source = XMLConfiguration.class
                .getResource(XMLConfigurationBackwardsCompatibilityTest.LATEST_FILENAME);
            FileUtils.copyURLToFile(source, configFile);
        }
    }
    @Override
    protected Matcher<GridSet> infoEquals(GridSet expected) {
        return new CustomMatcher<GridSet>("GridSet matching "+expected.getName()+" with " + expected.getDescription()){
            
            @Override
            public boolean matches(Object item) {
                return item instanceof GridSet && ((GridSet)item).getName().equals(((GridSet)expected).getName()) &&
                    ((GridSet)item).getDescription().equals(((GridSet)expected).getDescription());
            }
            
        };
    }
    
    @Override
    protected Matcher<GridSet> infoEquals(int expected) {
        return new CustomMatcher<GridSet>("GridSet with value " + expected){
            
            @Override
            public boolean matches(Object item) {
                return item instanceof GridSet && (Objects.equals(((GridSet)item).getDescription(), Integer.toString(expected)));
            }
            
        };
    }

    @Override
    protected String getExistingInfo() {
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

    @Override
    protected void renameInfo(GridSetConfiguration config, String name1, String name2)
            throws Exception {
        Assume.assumeFalse(true);
    }
}
