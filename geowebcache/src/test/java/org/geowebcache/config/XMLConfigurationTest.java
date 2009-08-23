package org.geowebcache.config;

import java.io.InputStream;
import java.util.Iterator;
import java.util.List;

import junit.framework.TestCase;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.filter.request.RequestFilter;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubSet;
import org.geowebcache.grid.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.mime.FormatModifier;
import org.geowebcache.mime.ImageMime;

public class XMLConfigurationTest extends TestCase {
    
    public static final String LATEST_FILENAME = "geowebcache_114.xml";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    public void testLoadPre10() throws Exception {
        List<TileLayer> layers = loadResource("geowebcache_pre10.xml");
        TileLayer layer = findLayer(layers, "topp:states");
        assertTrue(layer != null);
        TileLayer layer2 = findLayer(layers, "topp:states2");
        GridSubSet grid = layer2.getGridSubSetForSRS(SRS.getSRS(2163));
        assertTrue(layer2 != null);
        assertTrue(grid != null);
    }
        
    public void testLoad10() throws Exception {
        List<TileLayer> layers = loadResource("geowebcache_10.xml");
        TileLayer layer = findLayer(layers, "topp:states");
        assertTrue(layer != null);
        //assertEquals(layer.getCachePrefix(), "/var/lib/geowebcache/topp_states");
        TileLayer layer2 = findLayer(layers, "topp:states2");
        GridSubSet grid = layer2.getGridSubSetForSRS(SRS.getSRS(2163));
        assertTrue(layer2 != null);
        assertTrue(grid != null);
    }
    
    public void testLoad101() throws Exception {
        List<TileLayer> layers = loadResource("geowebcache_101.xml");
        TileLayer layer = findLayer(layers, "topp:states");
        assertTrue(layer != null);
        //assertEquals(layer.getCachePrefix(), "/var/lib/geowebcache/topp_states");
        TileLayer layer2 = findLayer(layers, "topp:states2");
        GridSubSet grid = layer2.getGridSubSetForSRS(SRS.getSRS(2163));
        assertTrue(layer2 != null);
        assertTrue(grid != null);
        
        // The additions in 1.0.1 are allowCacheBypass and backendTimeout
        assertEquals(layer.getBackendTimeout().intValue(), 60);
        assertEquals(layer2.getBackendTimeout().intValue(), 235);
        assertEquals(layer.isCacheBypassAllowed().booleanValue(), true);
        assertEquals(layer2.isCacheBypassAllowed().booleanValue(), false);
    }
    
    public void testLoad114() throws Exception {
        List<TileLayer> layers = loadResource(LATEST_FILENAME);
        TileLayer layer = findLayer(layers, "topp:states");
        assertTrue(layer != null);
        //assertEquals(layer.getCachePrefix(), "/var/lib/geowebcache/topp_states");
        TileLayer layer2 = findLayer(layers, "topp:states2");
        GridSubSet grid = layer2.getGridSubSetForSRS(SRS.getSRS(2163));
        assertTrue(layer2 != null);
        assertTrue(grid != null);
        
        // The additions in 1.0.1 are allowCacheBypass and backendTimeout
        assertEquals(layer.getBackendTimeout().intValue(), 120);
        assertEquals(layer2.getBackendTimeout().intValue(), 120);
        assertEquals(layer.isCacheBypassAllowed().booleanValue(), true);
        assertEquals(layer2.isCacheBypassAllowed().booleanValue(), true);
        
        FormatModifier fm = layer.getFormatModifier(ImageMime.jpeg); 
        assertEquals(fm.getBgColor(), "0xDDDDDD");
        assertTrue(fm.getRequestFormat().equals(ImageMime.png));
        
        List<RequestFilter> filters = layer.getRequestFilters();
        assertEquals(filters.get(0).getName(),"testWMSRasterFilter");
        assertEquals(filters.get(1).getName(),"testFileRasterFilter");
    }
    
    private TileLayer findLayer(List<TileLayer> layers, String layerName) 
    throws GeoWebCacheException {
        Iterator<TileLayer> iter = layers.iterator();
        
        while(iter.hasNext()) {
            TileLayer layer = iter.next();
            if(layer.getName().equals(layerName)) {
                return layer;
            }
        }
        
        throw new GeoWebCacheException("Layer " + layerName + 
                " not found, set has " + layers.size() + " layers.");
    }
    
    private List<TileLayer> loadResource(String fileName) throws Exception {
        InputStream is = XMLConfiguration.class.getResourceAsStream(fileName);
        
        XMLConfiguration xmlConfig = new XMLConfiguration(is);
        
        GridSetBroker gsb = new GridSetBroker(false);
        
        List<TileLayer> list = xmlConfig.getTileLayers(false);
        
        Iterator<TileLayer> iter = list.iterator();
        while(iter.hasNext()) {
            TileLayer layer = iter.next();
            
            layer.initialize(gsb);   
        }
        
        return list;
    }
}