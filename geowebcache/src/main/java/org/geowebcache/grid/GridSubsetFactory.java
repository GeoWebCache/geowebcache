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


public class GridSubsetFactory {
    public static GridSubset createGridSubSet(GridSet gridSet) {
        GridSubset ret = new GridSubset(gridSet);
        
        ret.firstLevel = 0;
        
        ret.gridCoverageLevels = new GridCoverage[gridSet.gridLevels.length];
        
        for(int i=0; i<ret.gridCoverageLevels.length; i++) {
            long[] tmp = {0,0,gridSet.gridLevels[i].extent[0],gridSet.gridLevels[i].extent[1], i};
            GridCoverage gridCov = new GridCoverage(tmp);
            ret.gridCoverageLevels[i] = gridCov;
        }
       
        return ret;
    }
    
    public static GridSubset createGridSubSet(GridSet gridSet, BoundingBox coverageBounds, Integer zoomStart, Integer zoomStop) {
        GridSubset ret = new GridSubset(gridSet);
        
        if(zoomStart != null) {
            ret.firstLevel = zoomStart;
        } else {
            ret.firstLevel = 0;
        }
        
        if(zoomStop != null) {
            ret.gridCoverageLevels = new GridCoverage[zoomStop - zoomStart + 1];
        } else {
            ret.gridCoverageLevels = new GridCoverage[gridSet.gridLevels.length - ret.firstLevel];
        }
        
        // Is this plain wrong? GlobalCRS84Scale, I guess the resolution forces it
        BoundingBox gridSetBouds = gridSet.getBounds();
        
        for(int i=0; i<ret.gridCoverageLevels.length; i++) {
            GridCoverage gridCov;
            
            if(coverageBounds != null) {
                gridCov = new GridCoverage(gridSet.closestRectangle(i + ret.firstLevel, coverageBounds) );
            } else {
                long[] gridExtent = gridSet.gridLevels[i + ret.firstLevel].extent;
                long[] fullCoverage = {0,0,gridExtent[0] - 1,gridExtent[1] -1, i + ret.firstLevel}; 
                gridCov = new GridCoverage(fullCoverage);
            }

            ret.gridCoverageLevels[i] = gridCov;
        }
       
        return ret;
    }
}
