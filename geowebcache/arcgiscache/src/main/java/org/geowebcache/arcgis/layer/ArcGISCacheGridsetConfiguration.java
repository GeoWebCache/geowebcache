package org.geowebcache.arcgis.layer;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.SimpleGridSetConfiguration;
import org.geowebcache.grid.GridSet;

public class ArcGISCacheGridsetConfiguration extends SimpleGridSetConfiguration {
    
    @Override
    public String getIdentifier() {
        return "ArcGIS Cache Generated Gridsets";
    }
    
    @Override
    public String getLocation() {
        return "";
    }
    
    @Override
    public void afterPropertiesSet() throws GeoWebCacheException {
    }

    @Override
    protected void addInternal(GridSet gs) {
        super.addInternal(gs);
    }
    
}
