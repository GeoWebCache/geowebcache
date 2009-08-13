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
 * A GridSubSet is a GridSet + a coverage area
 */
public class GridSubSet {
    protected int firstLevel = 0;
    
    protected GridSet gridSet;
    
    // {level}{minx,miny,maxx,maxy,z}
    // firstLevel + level = z
    // max zoom =  gridCoverageLevels.legth + firstLevel
    protected GridCoverage[] gridCoverageLevels; 
    
    protected GridSubSet(GridSet gridSet) {
        this.gridSet = gridSet;
    }
    
    public BBOX boundsFromIndex(long[] tileIndex) {
        return gridSet.boundsFromIndex(tileIndex);
    }
    
    public BBOX boundsFromRectangle(long[] rectangleExtent) {
        return gridSet.boundsFromRectangle(rectangleExtent);
    }
    
    public long[] closestIndex(BBOX tileBounds) {
        return gridSet.closestIndex(tileBounds);
    }
    
    public long[] closestRectangle(BBOX rectangleBounds) {
        return gridSet.closestRectangle(rectangleBounds);
    }
    
    public void checkCoverage(long[] index) throws OutsideCoverageException {
        if(index[2] >= firstLevel || 
                index[2] < gridCoverageLevels.length) {
            long[] coverage = gridCoverageLevels[(int) index[2]].coverage;
            
            if(index[0] >= coverage[0] &&
                    index[0] <= coverage[2]) {
                
                if(index[1] >= coverage[1] &&
                        index[1] <= coverage[3]) {
                    // Everything is good
                    return;
                }
            } else {
                throw new OutsideCoverageException(index, coverage);
            }
        } else {
            throw new OutsideCoverageException(index, firstLevel, gridCoverageLevels.length - 1);
        }
        
    }
    
    public long[][] expandToMetaFactors(long[][] coverages, int[] metaFactors) {
        // TODO Auto-generated method stub
        return null;
    }

    public long[] getCoverage(int level) {
        return gridCoverageLevels[firstLevel + level].coverage;
    }
    
    public long[][] getCoverages() {
        long[][] ret = new long[gridCoverageLevels.length][5];
        for(int i=0; i < ret.length; i++) {
            long[] cov = this.gridCoverageLevels[i].coverage;
            long[] cur = { cov[0], cov[1], cov[2], cov[3], firstLevel + i};
            ret[i] = cur;
        }
        
        return ret;
    }
    
    public BBOX getCoverageBounds(int level) {
        long[] coverage = gridCoverageLevels[firstLevel + level].coverage;
        return gridSet.boundsFromRectangle(coverage);
    }
    
    // Returns the tightest rectangle that covers the data
    public long[] getCoverageBestFit() {
        int i;
        long[] cov = null;
        
        for(i = gridCoverageLevels.length - 1; i > 0; i--) {
            cov = gridCoverageLevels[i].coverage;
            
            if(cov[0] == cov[2] && cov[1] == cov[3]) {
                break;
            }
        }
        
        cov = gridCoverageLevels[i].coverage;
        
        long[] ret = {cov[0],cov[1],cov[2],cov[3], i + firstLevel};
        
        return ret;
    }
    
    public BBOX getCoverageBestFitBounds() {
        return boundsFromRectangle(getCoverageBestFit());
    }
    
    public long[] getCoverageIntersection(long[] reqRectangle) {
        GridCoverage gridCov = gridCoverageLevels[firstLevel + (int) reqRectangle[4]];        
        return gridCov.getIntersection(reqRectangle);
    }
    
    public long[][] getCoverageIntersections(BBOX reqBounds) {
        long[][] ret = new long[gridCoverageLevels.length][5];
        for(int i = 0; i < gridCoverageLevels.length; i++) {
             long[] reqRectangle = gridSet.closestRectangle(i + firstLevel, reqBounds);
             ret[i] = gridCoverageLevels[i].getIntersection(reqRectangle);
        }
        return ret;
    }
    
    //public long[][] getCoverageIntersections(long[] reqRectangle) {
    //    GridCoverage gridCov = gridCoverageLevels[firstLevel + (int) reqRectangle[4]];        
    //    return gridCov.getIntersection(reqRectangle);
    //}
    
    public Object getGridSet() {
        return gridSet;
    }

    public BBOX getGridSetBounds() {
        return gridSet.getBounds();
    }
        
    public String getName() {
        return gridSet.name;
    }
    
    public double[] getResolutions() {
        double[] ret = new double[firstLevel + gridCoverageLevels.length];
        
        for(int i = 0; i < ret.length; i++) {
            ret[i] = gridSet.gridLevels[i].resolution;
        }
        
        return ret;
    }
    
    public SRS getSRS() {
        return gridSet.srs;
    }
    
    public int getTileHeight() {
        return gridSet.tileHeight;
    }
    
    public int getTileWidth() {
        return gridSet.tileWidth;
    }
    
    public int getZoomStart() {
        return firstLevel;
    }
    
    public int getZoomStop() {
        return firstLevel + gridCoverageLevels.length - 1;
    }
}