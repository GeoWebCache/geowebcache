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
 * @author Arne Kepp, OpenGeo, Copyright 2009
 */
package org.geowebcache.grid;

import org.geowebcache.util.wms.BBOX;

/**
 * This class exists mainly to parse the old XML objects using XStream
 * 
 * The problem is that it cannot use the GridSetBroker, so we end up with one
 * GridSet per layer anyway.
 */
public class OldGrid {
    private SRS srs = null;
    
    private BBOX dataBounds = null;
    
    protected BBOX gridBounds = null;
    
    protected Integer zoomStart;
    
    protected Integer zoomStop;
    
    protected double[] resolutions = null;
    
    protected OldGrid() {
        // Empty
    }
    
    public GridSubSet convertToGridSubset() {
        if(zoomStart == null || resolutions != null) {
            zoomStart = 0;
        }
        
        if(resolutions != null) {
            zoomStop = resolutions.length -1;
        } else if(zoomStop == null) {
            zoomStop = 30;
        }
        
        if(dataBounds == null) {
            dataBounds = gridBounds;
        }
        
        GridSet gridSet;
        
        if(srs.equals(SRS.getEPSG4326()) && gridBounds.equals(BBOX.WORLD4326) && resolutions == null) {
            gridSet = GridSetBroker.WORLD_EPSG4326;
        } else if(srs.equals(SRS.getEPSG3785()) && gridBounds.equals(BBOX.WORLD3785) && resolutions == null) {
            gridSet = GridSetBroker.WORLD_EPSG3785;
        } else {
            if(resolutions != null) {
                gridSet = GridSetFactory.createGridSet(srs.toString(), srs, gridBounds, resolutions, 256, 256);
            } else {
                if(zoomStop == null) {
                    zoomStop = 30;
                }
                
                gridSet = GridSetFactory.createGridSet(srs.toString(), srs, gridBounds, zoomStop + 1, 256, 256);
            }
        }
        
        GridSubSet gridSubSet;
        
        if(this.dataBounds == null) {
            gridSubSet = GridSubSetFactory.createGridSubSet(gridSet, dataBounds, zoomStart, zoomStop);
        } else {
            gridSubSet = GridSubSetFactory.createGridSubSet(gridSet, dataBounds, zoomStart, zoomStop);
        }
        
        return gridSubSet;
    }
}
