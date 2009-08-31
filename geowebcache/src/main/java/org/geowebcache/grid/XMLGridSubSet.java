package org.geowebcache.grid;


public class XMLGridSubSet {
    
    String gridSetName;
    
    BoundingBox coverageBounds;
    
    Integer zoomStart;
    
    Integer zoomStop;
    
    
    public GridSubSet getGridSubSet(GridSetBroker gridSetBroker) {
        GridSet gridSet = gridSetBroker.get(gridSetName);
        
        return GridSubSetFactory.createGridSubSet(gridSet, coverageBounds, zoomStart, zoomStop);
    }
}
