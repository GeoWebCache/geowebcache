package org.geowebcache.arcgis.layer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.commons.io.FileUtils;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.MockWepAppContextRule;
import org.geowebcache.config.BaseConfiguration;
import org.geowebcache.config.DefaultGridsets;
import org.geowebcache.config.GridSetConfiguration;
import org.geowebcache.config.TileLayerConfiguration;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.config.XMLConfigurationBackwardsCompatibilityTest;
import org.geowebcache.config.XMLConfigurationLayerConformanceTest;
import org.geowebcache.config.XMLConfigurationProvider;
import org.geowebcache.config.XMLFileResourceProvider;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.layer.AbstractTileLayer;
import org.geowebcache.layer.TileLayer;
import org.hamcrest.CustomMatcher;
import org.hamcrest.Matcher;
import org.junit.Rule;

public class XMLConfigurationLayerConformanceWithArcGisLayersTest
        extends XMLConfigurationLayerConformanceTest {
    
    @Override
    public void setUpTestUnit() throws Exception {
        super.setUpTestUnit();
    }

    File resourceAsFile(String resource) {
        URL url = getClass().getResource(resource);
        File f;
        try {
          f = new File(url.toURI());
        } catch(URISyntaxException e) {
          f = new File(url.getPath());
        }
        return f;
    }
    
    @Override
    protected TileLayer getGoodInfo(String id, int rand) {
        ArcGISCacheLayer layer = new ArcGISCacheLayer(id);
        File tileScheme = resourceAsFile("/compactcache/Conf.xml");
        layer.setTilingScheme(tileScheme);
        layer.setBackendTimeout(rand);
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

    @Override
    protected Matcher<TileLayer> infoEquals(TileLayer expected) {
        return new CustomMatcher<TileLayer>("ArcGISCacheLayer matching "+expected.getName()+" with "+ ((ArcGISCacheLayer)expected).getBackendTimeout()){
            
            @Override
            public boolean matches(Object item) {
                return item instanceof ArcGISCacheLayer &&
                    ((ArcGISCacheLayer)item).getBackendTimeout().equals(expected.getBackendTimeout());
            }
            
        };
    }

    @Override
    protected Matcher<TileLayer> infoEquals(int expected) {
        return new CustomMatcher<TileLayer>("ArcGISCacheLayer with value"+ expected){
            
            @Override
            public boolean matches(Object item) {
                return item instanceof ArcGISCacheLayer &&
                    ((ArcGISCacheLayer)item).getBackendTimeout().equals(expected);
            }
            
        };
    }

    @Override
    protected String getExistingInfo() {
        return null;
    }

    @Override
    protected void doModifyInfo(TileLayer info, int rand) throws Exception {
        ((ArcGISCacheLayer)info).setBackendTimeout(rand);
    }

    @Override
    
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
    protected TileLayerConfiguration getConfig(MockWepAppContextRule extensions) throws Exception {
        extensions.addBean("ArcGISLayerConfigProvider", 
                new ArcGISLayerXMLConfigurationProvider(), XMLConfigurationProvider.class);
        ArcGISCacheGridsetConfiguration arcGISCacheGridsetConfiguration = new ArcGISCacheGridsetConfiguration();
        extensions.addBean("ArcGISLayerGridSetConfiguration", 
                arcGISCacheGridsetConfiguration, GridSetConfiguration.class, ArcGISCacheGridsetConfiguration.class);
        
        return super.getConfig(extensions);
    }

    
}
