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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.util.wms.BBOX;

/**
 * Grid Class - Each TileLayer keeps a list of Grid Objects
 */

public class Grid {
    private static Log log = LogFactory.getLog(org.geowebcache.layer.Grid.class);

    private SRS srs = null;
    
    protected BBOX dataBounds = null;
    
    protected BBOX gridBounds = null;
    
    protected int zoomStart;
    
    protected int zoomStop;
    
    protected double[] resolutions = null;
    
    private transient boolean staticResolutions = false;
    
    private volatile transient GridCalculator gridCalculator;
    
    public Grid(SRS srs, BBOX bounds, BBOX gridBounds, double[] resolutions) {
        this.srs = srs;
        this.dataBounds = bounds;
        this.gridBounds = gridBounds;
        this.resolutions = resolutions;
    }
    
    /**
     * method will set the bounds of the layer for this grid from a BBOX 
     * @param bounds - BBOX with bounds
     */
    public void setBounds(BBOX bounds) {
        this.dataBounds = bounds;
    }
    /**
     * method will set the bounds of the layer for this grid from a String
     * @param bounds - String containing bounds
     */
    public void setBounds(String bounds) {
        this.dataBounds = new BBOX(bounds);
    }
    /**
     * method will set the grid bounds (world) of the layer for this grid from a BBOX 
     * @param dataBounds - BBOX with bounds
     */
    public void setGridBounds(BBOX gridbounds) {
        this.gridBounds = gridbounds;
    }
    /**
     * method will set the grid bounds (world) of the layer for this grid from a String 
     * @param dataBounds - String containing bounds
     */
    public void setGridBounds(String gridbounds) {

        this.gridBounds = new BBOX(gridbounds);
    }
    /**
     * method set the projection supported by the layer for this grid
     * @param projection - SRS
     */
    public void setSRS(SRS srs) {
        this.srs = srs;
    }
    /**
     * method returns the projection supported by the layer for this grid
     * @return
     */
    public SRS getSRS() {
        return this.srs;
    }
    /**
     * method returns the bounds of the layer for this grid
     * @return
     */
    public BBOX getBounds() {
        return this.dataBounds;
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
    
    public boolean hasStaticResolutions() {
        return this.staticResolutions;
    }
    
    //public void setResolutions(double[] resolutions) {
    //    this.resolutions = resolutions;
    //}
    
    public double[] getResolutions() throws GeoWebCacheException {
        return getGridCalculator().getResolutions();
    }
    
    /** 
     * Use double locking to get the calculator to avoid performance hit.
     * 
     * @return
     * @throws GeoWebCacheException
     */
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
        if (resolutions != null) {
            staticResolutions = true;
            zoomStart = 0;
            zoomStop = resolutions.length - 1;
        } else {
            if (zoomStart < 0 || zoomStop < zoomStart || zoomStop == 0) {
                log.debug("Missing values, setting zoomStart,zoomStop to 0,30");
                zoomStart = 0;
                zoomStop = 30;
            }
        }
        
        GridCalculator gridCalc = new GridCalculator(this);
        return gridCalc;
    }
}
