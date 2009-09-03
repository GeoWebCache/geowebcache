package org.geowebcache.grid;


public class XMLGridSet {
    String name;
    SRS srs;
    BoundingBox extent;
    Boolean alignTopLeft;
    double[] resolutions;
    double[] scales;
    Integer levels;
    String[] scaleNames;
    Integer tileHeight;
    Integer tileWidth;
    
    public String getName() {
        return name;
    }
    
    public GridSet makeGridSet() {
        if(tileWidth == null) {
            tileWidth = 256;
        }
        if(tileHeight == null) {
            tileHeight = 256;
        }
        
        if(alignTopLeft == null) {
            alignTopLeft = false;
        }
        
        if(resolutions != null || scales != null) {
            return GridSetFactory.createGridSet(name, srs, extent, alignTopLeft, resolutions, scales, scaleNames, tileWidth, tileHeight);
        } else {
            if(levels == null) {
                levels = 30;
            }
            
            return GridSetFactory.createGridSet(name, srs, extent, alignTopLeft, levels, tileWidth, tileHeight);
        }
    }
}