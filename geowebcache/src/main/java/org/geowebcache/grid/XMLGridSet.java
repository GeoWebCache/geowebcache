package org.geowebcache.grid;


public class XMLGridSet {
    String name;
    SRS srs;
    BoundingBox extent;
    Boolean alignTopLeft;
    double[] resolutions;
    double[] scaleDenominators;
    Integer levels;
    Double metersPerUnit;
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
        
        if(resolutions != null || scaleDenominators != null) {
            return GridSetFactory.createGridSet(name, srs, extent, alignTopLeft, resolutions, scaleDenominators, metersPerUnit, scaleNames, tileWidth, tileHeight);
        } else {
            if(levels == null) {
                levels = 30;
            }
            
            return GridSetFactory.createGridSet(name, srs, extent, alignTopLeft, levels, metersPerUnit, tileWidth, tileHeight);
        }
    }
}