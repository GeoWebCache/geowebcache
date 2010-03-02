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

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.util.ServletUtils;


/**
 * A GridSubSet is a GridSet + a coverage area
 */
public class GridSubset {
    protected int firstLevel = 0;
    
    protected GridSet gridSet;
    
    // {level}{minx,miny,maxx,maxy,z}
    // firstLevel + level = z
    // max zoom =  gridCoverageLevels.legth + firstLevel
    protected GridCoverage[] gridCoverageLevels; 
    
    protected boolean fullGridSetCoverage = false;
    
    protected BoundingBox originalExtent = null;
    
    protected GridSubset(GridSet gridSet) {
        this.gridSet = gridSet;
    }
    
    public BoundingBox boundsFromIndex(long[] tileIndex) {
        return gridSet.boundsFromIndex(tileIndex);
    }
    
    public BoundingBox boundsFromRectangle(long[] rectangleExtent) {
        return gridSet.boundsFromRectangle(rectangleExtent);
    }
    
    public long[] closestIndex(BoundingBox tileBounds) throws GridMismatchException {
        return gridSet.closestIndex(tileBounds);
    }
    
    public long[] closestRectangle(BoundingBox rectangleBounds) {
        return gridSet.closestRectangle(rectangleBounds);
    }
    
    public void checkCoverage(long[] index) throws OutsideCoverageException {
        if(index[2] >= firstLevel &&
                index[2] < gridCoverageLevels.length) {
            long[] coverage = gridCoverageLevels[(int) index[2]].coverage;
            
            if(index[0] >= coverage[0] &&
                    index[0] <= coverage[2]) {
                
                if(index[1] >= coverage[1] &&
                        index[1] <= coverage[3]) {
                    // Everything is good
                    return;
                }
            }

            throw new OutsideCoverageException(index, coverage);            
        }

        throw new OutsideCoverageException(index, firstLevel, gridCoverageLevels.length - 1);        
    }
    
    public void checkTileDimensions(int width, int height) 
    throws TileDimensionsMismatchException {
    
        if(width != gridSet.tileWidth || height != gridSet.tileHeight) {
            throw new TileDimensionsMismatchException(
                    width, height, 
                    gridSet.tileWidth, gridSet.tileWidth);
        }
    }
    
    public long[][] expandToMetaFactors(long[][] coverages, int[] metaFactors) {
        long[][] ret = ServletUtils.arrayDeepCopy(coverages);
        
        for(int z=0; z<ret.length; z++) {
            long[] cov = ret[z];
            
            cov[0] = cov[0] - (cov[0] % metaFactors[0]);
            cov[1] = cov[1] - (cov[1] % metaFactors[1]);
            
            cov[2] = cov[2] - (cov[2] % metaFactors[0]) + (metaFactors[0] - 1);
            if(cov[2] > this.gridSet.gridLevels[z].extent[0]) {
                cov[2] = this.gridSet.gridLevels[z].extent[0];
            }
            
            cov[3] = cov[3] - (cov[3] % metaFactors[1]) + (metaFactors[1] - 1);
            if(cov[3] > this.gridSet.gridLevels[z].extent[1]) {
                cov[3] = this.gridSet.gridLevels[z].extent[1];
            }
        }
        
        return ret;
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
    
    /**
     * Convert pixel size to dots per inch
     * 
     * @return
     */
    public double getDotsPerInch() {
        return (0.0254 / this.gridSet.pixelSize);
    }
    
    public BoundingBox getCoverageBounds(int level) {
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
    
    public BoundingBox getCoverageBestFitBounds() {
        return boundsFromRectangle(getCoverageBestFit());
    }
    
    public long[] getCoverageIntersection(long[] reqRectangle) {
        GridCoverage gridCov = gridCoverageLevels[firstLevel + (int) reqRectangle[4]];        
        return gridCov.getIntersection(reqRectangle);
    }
    
    public long[][] getCoverageIntersections(BoundingBox reqBounds) {
        long[][] ret = new long[gridCoverageLevels.length][5];
        for(int i = 0; i < gridCoverageLevels.length; i++) {
             long[] reqRectangle = gridSet.closestRectangle(i + firstLevel, reqBounds);
             ret[i] = gridCoverageLevels[i].getIntersection(reqRectangle);
        }
        return ret;
    }
    
    public long[] getCoverageIntersection(int level, BoundingBox reqBounds) {
        long[] reqRectangle = gridSet.closestRectangle(level, reqBounds);
        return gridCoverageLevels[level].getIntersection(reqRectangle);
    }
    
    public long getGridIndex(String gridId) {
        for(int i = 0; i < gridCoverageLevels.length; i++) {
            if(gridSet.gridLevels[firstLevel + i].name.equals(gridId)) {
                return i;
            }
        }
        
        return -1L;
    }
    
    public String[] getGridNames() {
        String[] ret = new String[gridCoverageLevels.length];
        for(int i=0; i<gridCoverageLevels.length; i++) {
            ret[i] = gridSet.gridLevels[i + firstLevel].name;
        }
        
        return ret;
    }
    
    public GridSet getGridSet() {
        return gridSet;
    }

    public BoundingBox getGridSetBounds() {
        return gridSet.getBounds();
    }
    
    public long[] getGridSetExtent(int z) {
        return gridSet.gridLevels[z].extent;
    }
        
    public String getName() {
        return gridSet.name;
    }
    
    public BoundingBox getOriginalExtent() {
        if(this.originalExtent == null) {
            return gridSet.originalExtent;
        }
        return this.originalExtent;
    }
    
    public double[] getResolutions() {
        double[] ret = new double[firstLevel + gridCoverageLevels.length];
        
        for(int i = 0; i < ret.length; i++) {
            ret[i] = gridSet.gridLevels[i].resolution;
        }
        
        return ret;
    }
    
    public long[][] getSubGrid(long[] gridLoc) throws GeoWebCacheException {
       int idx = (int) gridLoc[2];
       
       long[][] ret = {{-1,-1,-1},{-1,-1,-1},{-1,-1,-1},{-1,-1,-1}};

       if((idx - firstLevel + 1) < gridCoverageLevels.length) {
           // Check whether this grid is doubling
           double resolutionCheck = gridSet.gridLevels[idx].resolution / 2 - gridSet.gridLevels[idx + 1].resolution;
           
           if (Math.abs(resolutionCheck) > gridSet.gridLevels[idx + 1].resolution * 0.025) {
               throw new GeoWebCacheException("The resolution is not decreasing by a factor of two for " + this.getName());
           } else {
               GridCoverage cov = gridCoverageLevels[idx + 1];
               
               long baseX = gridLoc[0] * 2;
               long baseY = gridLoc[1] * 2;
               long baseZ = idx + 1;
               
               long[] xOffset = {0,1,0,1};
               long[] yOffset = {0,0,1,1};

               
               for(int i=0; i<4; i++) {
                   if(     baseX + xOffset[i] >= cov.coverage[0] &&
                           baseX + xOffset[i] <= cov.coverage[2] &&
                           baseY + yOffset[i] >= cov.coverage[1] && 
                           baseY + yOffset[i] <= cov.coverage[3] ) {
                       
                       ret[i][0] = baseX + xOffset[i]; ret[i][1] = baseY + yOffset[i]; ret[i][2] = baseZ;
                   }
               }
           }
       }
       
       return ret;
    }
    
    /**
     * @return whether the scale is based on CRS84, even though it may not be
     */
    public boolean getScaleWarning() {
        return gridSet.scaleWarning;
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
    
    /**
     * WMTS is indexed from top left hand corner.
     * We will still return {minx,miny,maxx,maxy}, 
     * but note that the y positions have been reversed
     * 
     * @return
     */
    public long[][] getWMTSCoverages() {
        long[][] ret = new long[gridCoverageLevels.length][4];
        
        for(int i=0; i<gridCoverageLevels.length; i++) {
            Grid grid = gridSet.gridLevels[i + firstLevel];
            GridCoverage gridCov = gridCoverageLevels[i];
            
            long[] cur = {
                    gridCov.coverage[0],
                    grid.extent[1] - gridCov.coverage[3],
                    gridCov.coverage[2],
                    grid.extent[1] - gridCov.coverage[1]
            };
            
            ret[i] = cur;
        }
        
        return ret;
    }
    
    public int getZoomStart() {
        return firstLevel;
    }
    
    public int getZoomStop() {
        return firstLevel + gridCoverageLevels.length - 1;
    }
    
    /**
     * Whether the Grid Subset equals or exceeds the extent of the Grid Set
     * @return
     */
    public boolean fullGridSetCoverage() {
        return fullGridSetCoverage;
    }


}