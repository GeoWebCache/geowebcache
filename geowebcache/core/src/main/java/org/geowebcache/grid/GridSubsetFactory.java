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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


public class GridSubsetFactory {
    private static Log log = LogFactory.getLog(GridSubsetFactory.class);
    
    public static GridSubset createGridSubSet(GridSet gridSet) {
        
        GridSubset ret = new GridSubset(gridSet);
        
        ret.firstLevel = 0;
        
        ret.gridCoverageLevels = new GridCoverage[gridSet.gridLevels.length];
        
        for(int i=0; i<ret.gridCoverageLevels.length; i++) {
            Grid level = gridSet.gridLevels[i];
            long[] tmp = { 0, 0, level.getNumTilesWide() - 1, level.getNumTilesHigh() - 1, i };
            GridCoverage gridCov = new GridCoverage(tmp);
            ret.gridCoverageLevels[i] = gridCov;
        }
       
        ret.fullGridSetCoverage = true;
        
        return ret;
    }
    
    public static GridSubset createGridSubSet(GridSet gridSet, BoundingBox extent, Integer zoomStart, Integer zoomStop) {
        if(gridSet == null) {
            log.error("Passed GridSet was null!");
        }
        
        GridSubset ret = new GridSubset(gridSet);
        
        if(zoomStart != null) {
            ret.firstLevel = zoomStart;
        } else {
            ret.firstLevel = 0;
        }
        
        if(zoomStop != null) {
            ret.gridCoverageLevels = new GridCoverage[zoomStop - ret.firstLevel + 1];
        } else {
            ret.gridCoverageLevels = new GridCoverage[gridSet.gridLevels.length - ret.firstLevel];
        }
        
        // Save the original extent provided by the user
        ret.originalExtent = extent;
        
        // Is this plain wrong? GlobalCRS84Scale, I guess the resolution forces it
        BoundingBox gridSetBounds = gridSet.getBounds();
        
        if(extent == null || extent.contains(gridSetBounds)) {
            ret.fullGridSetCoverage = true;
        }
        
        for(int i=0; i<ret.gridCoverageLevels.length; i++) {
            GridCoverage gridCov;
            
            if(extent != null) {
                gridCov = new GridCoverage(gridSet.closestRectangle(i + ret.firstLevel, extent) );
            } else {
                Grid level = gridSet.gridLevels[i + ret.firstLevel];
                long[] fullCoverage = { 0, 0, level.getNumTilesWide() - 1,
                        level.getNumTilesHigh() - 1, i + ret.firstLevel };
                gridCov = new GridCoverage(fullCoverage);
            }

            ret.gridCoverageLevels[i] = gridCov;
        }
       
        return ret;
    }
}
