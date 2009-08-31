package org.geowebcache.grid;


public class XMLGridSubSet {
    
    String gridSetName;
    
    BoundingBox coverageBounds;
    
    Integer zoomStart;
    
    Integer zoomStop;
    
    
    public GridSubset getGridSubSet(GridSetBroker gridSetBroker) {
        GridSet gridSet = gridSetBroker.get(gridSetName);
        
        return GridSubsetFactory.createGridSubSet(gridSet, coverageBounds, zoomStart, zoomStop);
    }
}
