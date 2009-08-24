package org.geowebcache.grid;


public class XMLGridSet {
    String name;
    SRS srs;
    BoundingBox extent;
    double[] resolutions;
    double[] scales;
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
        
        if(resolutions != null || scales != null) {
            return GridSetFactory.createGridSet(name, srs, extent, resolutions, scales, tileWidth, tileHeight);
        } else {
            if(levels == null) {
                levels = 30;
            }
            
            return GridSetFactory.createGridSet(name, srs, extent, levels, tileWidth, tileHeight);
        }
    }
}