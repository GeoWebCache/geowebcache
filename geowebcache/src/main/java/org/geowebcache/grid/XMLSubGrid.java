package org.geowebcache.grid;


public class XMLSubGrid {
    
    String gridSetId;
    
    BoundingBox coverageBounds;
    
    Integer zoomStart;
    
    Integer zoomStop;
    
    
    public GridSubSet getGridSubSet(GridSetBroker gridSetBroker) {
        GridSet gridSet = gridSetBroker.get(gridSetId);
        
        return GridSubSetFactory.createGridSubSet(gridSet, coverageBounds, zoomStart, zoomStop);
    }
}
