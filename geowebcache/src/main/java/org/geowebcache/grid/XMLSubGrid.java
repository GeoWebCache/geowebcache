package org.geowebcache.grid;

import org.geowebcache.util.wms.BBOX;

public class XMLSubGrid {
    
    String gridSetId;
    
    BBOX coverageBounds;
    
    Integer zoomStart;
    
    Integer zoomStop;
    
    
    public GridSubSet getGridSubSet(GridSetBroker gridSetBroker) {
        GridSet gridSet = gridSetBroker.get(gridSetId);
        
        return GridSubSetFactory.createGridSubSet(gridSet, coverageBounds, zoomStart, zoomStop);
    }
}
