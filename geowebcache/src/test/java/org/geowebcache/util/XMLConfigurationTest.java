package org.geowebcache.util;

import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import junit.framework.TestCase;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.Grid;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;

public class XMLConfigurationTest extends TestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    public void testLoadPre10() throws Exception {
        Map<String, TileLayer> layers = loadResource("geowebcache_pre10.xml");
        TileLayer layer = findLayer(layers, "topp:states");
        assertTrue(layer != null);
        TileLayer layer2 = findLayer(layers, "topp:states2");
        Grid grid = layer2.getGrid(SRS.getSRS(2163));
        assertTrue(layer2 != null);
        assertTrue(grid != null);
    }
        
    public void testLoad10() throws Exception {
        Map<String, TileLayer> layers = loadResource("geowebcache_10.xml");
        TileLayer layer = findLayer(layers, "topp:states");
        assertTrue(layer != null);
        assertEquals(layer.getCachePrefix(), "/var/lib/geowebcache/topp_states");
        TileLayer layer2 = findLayer(layers, "topp:states2");
        Grid grid = layer2.getGrid(SRS.getSRS(2163));
        assertTrue(layer2 != null);
        assertTrue(grid != null);
    }
    
    private TileLayer findLayer(Map<String, TileLayer> layers, String layerName) 
    throws GeoWebCacheException {
        Iterator<Entry<String, TileLayer>> iter = layers.entrySet().iterator();
        
        while(iter.hasNext()) {
            Entry<String, TileLayer> entry = iter.next();
            if(entry.getValue().getName().equals(layerName)) {
                return entry.getValue();
            }
        }
        
        throw new GeoWebCacheException("Layer " + layerName + 
                " not found, set has " + layers.size() + " layers.");
    }
    
    private Map<String, TileLayer> loadResource(String fileName) throws Exception {
        InputStream is = XMLConfiguration.class.getResourceAsStream(fileName);
        
        XMLConfiguration xmlConfig = new XMLConfiguration();
        
        return xmlConfig.getTileLayers(is);
    }
}