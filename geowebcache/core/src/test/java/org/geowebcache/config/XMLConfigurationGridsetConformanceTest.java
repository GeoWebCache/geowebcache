package org.geowebcache.config;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import org.apache.commons.io.FileUtils;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSetFactory;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.grid.SRS;
import org.hamcrest.CustomMatcher;
import org.hamcrest.Matcher;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class XMLConfigurationGridsetConformanceTest extends GridSetConfigurationTest {

    @Override
    protected GridSet getGoodGridSet(String id, int rand) {
        GridSet gridset = GridSetFactory.createGridSet(id, SRS.getSRS(rand), new BoundingBox(0,0,1,1), true, 3, 1.0, GridSetFactory.DEFAULT_PIXEL_SIZE_METER, 256, 256, false);
        return gridset;
    }

    @Override
    protected GridSet getBadGridSet(String id, int rand) {
        return new GridSet() {};
    }
    
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    private File configDir;
    private File configFile;
    
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
        config = new XMLConfiguration(null, configDir.getAbsolutePath());
        config.initialize(gridSetBroker);
        return config;
    }

    @Override
    protected Matcher<GridSet> GridSetEquals(GridSet expected) {
        return new CustomMatcher<GridSet>("GridSet matching "+expected.getName()+" with " + expected.getSrs()){
            
            @Override
            public boolean matches(Object item) {
                return item instanceof GridSet && ((GridSet)item).getName().equals(((GridSet)expected).getName()) &&
                    ((GridSet)item).getSrs().equals(((GridSet)expected).getSrs());
            }
            
        };
    }

    @Override
    protected String getExistingGridSet() throws Exception {
        return "topp:states";
    }

}
