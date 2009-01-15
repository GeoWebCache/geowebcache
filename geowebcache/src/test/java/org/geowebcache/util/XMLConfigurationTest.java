package org.geowebcache.util;

import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import junit.framework.TestCase;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.Grid;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;

public class XMLConfigurationTest extends TestCase {
    public static String LATEST_FILENAME = "geowebcache_101.xml";
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    public void testLoadPre10() throws Exception {
        List<TileLayer> layers = loadResource("geowebcache_pre10.xml");
        TileLayer layer = findLayer(layers, "topp:states");
        assertTrue(layer != null);
        TileLayer layer2 = findLayer(layers, "topp:states2");
        Grid grid = layer2.getGrid(SRS.getSRS(2163));
        assertTrue(layer2 != null);
        assertTrue(grid != null);
    }
        
    public void testLoad10() throws Exception {
        List<TileLayer> layers = loadResource("geowebcache_10.xml");
        TileLayer layer = findLayer(layers, "topp:states");
        assertTrue(layer != null);
        assertEquals(layer.getCachePrefix(), "/var/lib/geowebcache/topp_states");
        TileLayer layer2 = findLayer(layers, "topp:states2");
        Grid grid = layer2.getGrid(SRS.getSRS(2163));
        assertTrue(layer2 != null);
        assertTrue(grid != null);
    }
    
    public void testLoad101() throws Exception {
        List<TileLayer> layers = loadResource(LATEST_FILENAME);
        TileLayer layer = findLayer(layers, "topp:states");
        assertTrue(layer != null);
        assertEquals(layer.getCachePrefix(), "/var/lib/geowebcache/topp_states");
        TileLayer layer2 = findLayer(layers, "topp:states2");
        Grid grid = layer2.getGrid(SRS.getSRS(2163));
        assertTrue(layer2 != null);
        assertTrue(grid != null);
        
        // The additions in 1.0.1 are allowCacheBypass and backendTimeout
        assertEquals(layer.getBackendTimeout().intValue(), 60);
        assertEquals(layer2.getBackendTimeout().intValue(), 235);
        assertEquals(layer.isCacheBypassAllowed().booleanValue(), true);
        assertEquals(layer2.isCacheBypassAllowed().booleanValue(), false);
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
        
        return xmlConfig.getTileLayers(false);
    }
}