/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Marius Suta, The Open Planning Project, Copyright 2008
 */
package org.geowebcache.layer;

import java.util.List;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.util.wms.BBOX;

/**
 * Grid Class - Each TileLayer keeps a list of Grid Objects
 */

public class Grid {
    private SRS gridSRS = null;
    
    protected BBOX bounds = null;
    
    protected BBOX gridBounds = null;
    
    protected double[] resolutions = null;
    
    private volatile transient GridCalculator gridCalculator;
    
    private volatile int zoomStart = 0;
    
    private volatile int zoomStop = 25;
    
    public Grid(SRS srs, BBOX bounds, BBOX gridBounds, double[] resolutions) {
        this.gridSRS = srs;
        this.bounds = bounds;
        this.gridBounds = gridBounds;
        this.resolutions = resolutions;
    }
    
    /**
     * method will set the bounds of the layer for this grid from a BBOX 
     * @param bounds - BBOX with bounds
     */
    public void setBounds(BBOX bounds) {
        this.bounds = bounds;
    }
    /**
     * method will set the bounds of the layer for this grid from a String
     * @param bounds - String containing bounds
     */
    public void setBounds(String bounds) {
        this.bounds = new BBOX(bounds);
    }
    /**
     * method will set the grid bounds (world) of the layer for this grid from a BBOX 
     * @param bounds - BBOX with bounds
     */
    public void setGridBounds(BBOX gridbounds) {
        this.gridBounds = gridbounds;
    }
    /**
     * method will set the grid bounds (world) of the layer for this grid from a String 
     * @param bounds - String containing bounds
     */
    public void setGridBounds(String gridbounds) {

        this.gridBounds = new BBOX(gridbounds);
    }
    /**
     * method set the projection supported by the layer for this grid
     * @param projection - SRS
     */
    public void setSRS(SRS srs) {
        this.gridSRS = srs;
    }
    /**
     * method returns the projection supported by the layer for this grid
     * @return
     */
    public SRS getSRS() {
        return this.gridSRS;
    }
    /**
     * method returns the bounds of the layer for this grid
     * @return
     */
    public BBOX getBounds() {
        return this.bounds;
    }
    /**
     * method returns the grid bounds of the layer for this grid
     * @return
     */
    public BBOX getGridBounds() {
        return this.gridBounds;
    }
    
    public int getZoomStart() {
        return this.zoomStart;
    }
    
    public int getZoomStop() {
        return this.zoomStop;
    }
    
    public void setResolutions(double[] resolutions) {
        this.resolutions = resolutions;
    }
    
    public double[] getResolutions() throws GeoWebCacheException {
        return getGridCalculator().getResolutions();
    }
    
    public GridCalculator getGridCalculator() throws GeoWebCacheException {
        GridCalculator ret = gridCalculator;
        if (ret == null) {
            synchronized (this) {
                ret = gridCalculator;
                if (gridCalculator == null) {
                    gridCalculator = ret = initGridCalculator();
                }
            }
        }
        return ret;
    }

    private GridCalculator initGridCalculator() throws GeoWebCacheException {
        return new GridCalculator(this);
    }
}
