package org.geowebcache.layer;

import org.geowebcache.util.wms.BBOX;

/**
 * Grid Class - Each TileLayer keeps a list of Grid Objects
 * 
 * @author Marius Suta
 * 
 */

public class Grid {
    private BBOX bounds = null;

    private BBOX gridbounds = null;

    private SRS projection = null;

    public void setBounds(BBOX bounds) {
        this.bounds = bounds;
    }

    public void setBounds(String bounds) {

        this.bounds = new BBOX(bounds);
    }

    public void setGridBounds(BBOX gridbounds) {
        this.gridbounds = gridbounds;
    }

    public void setGridBounds(String gridbounds) {

        this.gridbounds = new BBOX(gridbounds);
    }

    public void setProjection(SRS projection) {
        this.projection = projection;
    }
    
    public SRS getProjection() {
    	return this.projection;
    }
    
    public BBOX getBounds() {
    	return this.bounds;
    }
    
    public BBOX getGridBounds() {
    	return this.gridbounds;
    }

}
