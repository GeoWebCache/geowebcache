package org.geowebcache.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

import org.apache.commons.io.FileUtils;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.MockWepAppContextRule;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.layer.AbstractTileLayer;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.wms.WMSLayer;
import org.hamcrest.CustomMatcher;
import org.hamcrest.Matcher;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.web.context.WebApplicationContext;


public class XMLConfigurationLayerConformanceTest extends LayerConfigurationTest {

    protected ConfigurationResourceProvider configProvider;
    
    public @Rule MockWepAppContextRule extensions = new MockWepAppContextRule();

    protected boolean failNextRead = false;
    protected boolean failNextWrite = false;
    
    @Override
    protected TileLayer getGoodInfo(String id, int rand) {
        WMSLayer layer = new WMSLayer(id, new String[] {"http://example.com/"}, null, 
                Integer.toString(rand),null, null, null, null,
                null, false, null);
        return layer;
    }

    @Override
    protected TileLayer getBadInfo(String id, int rand) {
        return new AbstractTileLayer() {
            {
                this.name=id;
            }

            @Override
            protected boolean initializeInternal(GridSetBroker gridSetBroker) {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public String getStyles() {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public ConveyorTile getTile(ConveyorTile tile)
                    throws GeoWebCacheException, IOException, OutsideCoverageException {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public ConveyorTile getNoncachedTile(ConveyorTile tile) throws GeoWebCacheException {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public void seedTile(ConveyorTile tile, boolean tryCache)
                    throws GeoWebCacheException, IOException {
                // TODO Auto-generated method stub
                
            }

            @Override
            public ConveyorTile doNonMetatilingRequest(ConveyorTile tile)
                    throws GeoWebCacheException {
                // TODO Auto-generated method stub
                return null;
            }
            
        };
    }
    
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    protected File configDir;
    protected File configFile;
    
    @Override
    protected TileLayerConfiguration getConfig() throws Exception {
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
        TileLayerConfiguration config = new XMLConfiguration(extensions.getContextProvider(), configProvider);
        config.setGridSetBroker(gridSetBroker);
        config.initialize();
        
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
    protected Matcher<TileLayer> infoEquals(TileLayer expected) {
        return new CustomMatcher<TileLayer>("Layer matching "+expected.getId()+" with "+ ((WMSLayer)expected).getWmsLayers()){
            
            @Override
            public boolean matches(Object item) {
                return item instanceof WMSLayer && ((WMSLayer)item).getId().equals(((WMSLayer)expected).getId()) &&
                    ((WMSLayer)item).getWmsLayers().equals(((WMSLayer)expected).getWmsLayers());
            }
            
        };
    }
    @Override
    protected Matcher<TileLayer> infoEquals(int expected) {
        return new CustomMatcher<TileLayer>("Layer with value"+ expected){
            
            @Override
            public boolean matches(Object item) {
                return item instanceof WMSLayer &&
                    ((WMSLayer)item).getWmsLayers().equals(expected);
            }
            
        };
    }

    @Override
    protected String getExistingInfo() {
        return "topp:states";
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
    protected void renameInfo(TileLayerConfiguration config, String name1, String name2)
            throws Exception {
        Assume.assumeFalse(true);
    }

    @Override
    protected void doModifyInfo(TileLayer info, int rand) throws Exception {
        ((WMSLayer)info).setWmsLayers(Integer.toString(rand));
    }

    @Override 
    @Ignore // TODO Need to implement a clone/deep copy/modification proxy to make this safe.
    @Test
    public void testModifyCallRequiredToChangeInfoFromGetInfo() throws Exception {
        super.testModifyCallRequiredToChangeInfoFromGetInfo();
    }

    @Override
    @Ignore // TODO Need to implement a clone/deep copy/modification proxy to make this safe.
    @Test
    public void testModifyCallRequiredToChangeInfoFromGetInfos() throws Exception {
        super.testModifyCallRequiredToChangeInfoFromGetInfos();
    }

    @Override
    @Ignore // TODO Need to implement a clone/deep copy/modification proxy to make this safe.
    @Test
    public void testModifyCallRequiredToChangeExistingInfoFromGetInfo() throws Exception {
        super.testModifyCallRequiredToChangeExistingInfoFromGetInfo();
    }

    @Override
    @Ignore // TODO Need to implement a clone/deep copy/modification proxy to make this safe.
    @Test
    public void testModifyCallRequiredToChangeExistingInfoFromGetInfos() throws Exception {
        super.testModifyCallRequiredToChangeExistingInfoFromGetInfos();
    }

    
    
}
