package org.geowebcache.grid;

import org.geowebcache.util.wms.BBOX;

public class XMLGridSet {
    String name;
    SRS srs;
    BBOX extent;
    double[] resolutions;
    Integer levels;
    Integer tileHeight;
    Integer tileWidth;
    
    public GridSet makeGridSet() {
        if(tileWidth == null) {
            tileWidth = 256;
        }
        if(tileHeight == null) {
            tileHeight = 256;
        }
        
        if(resolutions != null) {
            return GridSetFactory.createGridSet(name, srs, extent, resolutions, tileWidth, tileHeight);
        } else {
            if(levels == null) {
                levels = 30;
            }
            
            return GridSetFactory.createGridSet(name, srs, extent, levels, tileWidth, tileHeight);
        }
    }
}