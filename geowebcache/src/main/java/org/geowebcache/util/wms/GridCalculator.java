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
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 */
package org.geowebcache.util.wms;

import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class GridCalculator {
    private static Log log = LogFactory
            .getLog(org.geowebcache.util.wms.GridCalculator.class);

    private BBOX gridBounds = null;

    // The following are the width of the actual layer
    private double gridWidth;

    private double gridHeight;

    // The following are for a tile, zoomed out all the way
    private double maxTileWidth;

    private double maxTileHeight;

    private int zoomStart;

    private int zoomStop;

    private int metaWidth;

    private int metaHeight;

    // Used for unprojected profiles
    private int gridConstant;

    private int[] zoomedOutGridLoc = null;
    
    private int[][] boundsGridLevels = null;

    // TODO this code does not handle coordinate systems where the base
    // height
    // is bigger than the width
    // private double layerHeight;

    public GridCalculator(BBOX gridBounds, BBOX layerBounds, int zoomStart,
            int zoomStop, int metaWidth, int metaHeight, double maxTileWidth,
            double maxTileHeight) {

        this.gridBounds = gridBounds;
        this.zoomStart = zoomStart;
        this.zoomStop = zoomStop;
        this.metaWidth = metaWidth;
        this.metaHeight = metaHeight;

        // Calculate
        gridWidth = gridBounds.coords[2] - gridBounds.coords[0];
        gridHeight = gridBounds.coords[3] - gridBounds.coords[1];

        this.maxTileWidth = maxTileWidth;
        this.maxTileHeight = maxTileHeight;
        this.gridConstant = (int) Math.round(gridWidth / gridHeight - 1.0);

        boundsGridLevels = calculateGridBounds(layerBounds);
    }

    private int[][] calculateGridBounds(BBOX layerBounds) {
        // We'll just waste a few bytes, for cheap lookups
        int[][] gridLevels = new int[zoomStop + 1][4];

        double tileWidth = maxTileWidth;
        double tileHeight = maxTileHeight;

        int tileCountX = (int) Math.round(gridWidth / maxTileWidth);
        int tileCountY = (int) Math.round(gridHeight / maxTileHeight);

        int metaLarger = (metaHeight > metaWidth) ? metaHeight : metaWidth;

        //System.out.println("lb: " +layerBounds+ " base:" +
        //  " tileWidth: " + tileWidth);

        for (int level = 0; level <= zoomStop; level++) {
            //System.out.println("--- Level "+level+"----");
            // Min X
            gridLevels[level][0] = (int) Math
                    .floor((layerBounds.coords[0] - gridBounds.coords[0])
                            / tileWidth);
            // Min Y
            gridLevels[level][1] = (int) Math
                    .floor((layerBounds.coords[1] - gridBounds.coords[1])
                            / tileHeight);
            // Max X
            gridLevels[level][2] = (int) Math
                    .ceil((layerBounds.coords[2] - gridBounds.coords[0])
                            / tileWidth) - 1;
            // Max Y
            gridLevels[level][3] = (int) Math
                    .ceil((layerBounds.coords[3] - gridBounds.coords[1])
                            / tileHeight) - 1;

            //System.out.println("postOrig: " +
            //        Arrays.toString(gridLevels[level]));
            //
            //System.out.println("tileCountX "+tileCountX + " metaLarger: "
            // + metaLarger);

            // Adjust for metatiling if appropriate
            if (tileCountX > metaLarger || tileCountY > metaLarger) {
                // Round down
                gridLevels[level][0] = gridLevels[level][0]
                        - (gridLevels[level][0] % metaWidth);
                // Round down
                gridLevels[level][1] = gridLevels[level][1]
                        - (gridLevels[level][1] % metaHeight);
                // Naive round up
                gridLevels[level][2] = gridLevels[level][2]
                        - (gridLevels[level][2] % metaWidth) + (metaWidth - 1);
                // Naive round up
                gridLevels[level][3] = gridLevels[level][3]
                        - (gridLevels[level][3] % metaHeight)
                        + (metaHeight - 1);

                //System.out.println("postAdjust: " +
                // Arrays.toString(gridLevels[level]));

                // Fix for naive round ups, imagine applying a 3x3 metatile to a
                // 4x4 grid
                if (gridLevels[level][2] >= tileCountX) {
                    gridLevels[level][2] = tileCountX - 1;
                }
                if (gridLevels[level][3] >= tileCountY) {
                    gridLevels[level][3] = tileCountY - 1;
                }
                //System.out.println("postFix: " +
                // Arrays.toString(gridLevels[level]));
            }

            // For the next round
            tileWidth = tileWidth / 2;
            tileHeight = tileHeight / 2;

            tileCountX = tileCountX * 2;
            tileCountY = tileCountY * 2;
        }
        return gridLevels;
    }

    public int[] getGridBounds(int zoomLevel) {
        return boundsGridLevels[zoomLevel].clone();
    }

    /**
     * Determines the location in a three dimensional grid based on WMS
     * recommendations.
     * 
     * It creates a grid of (2^zoomLevel x 2^zoomLevel) tiles. 0,0 denotes the
     * bottom left corner. The tile's location in this grid is determined as
     * follows:
     * 
     * <ol>
     * <li>Based on the width of the requested tile the desired zoomlevel is
     * determined.</li>
     * <li>The rounded zoomLevel is used to divide the width into 2^zoomLevel
     * segments</li>
     * <li>The min X value is used to determine the X position on this grid</li>
     * <li>The min Y value is used to determine the Y position on this grid</li>
     * </ol>
     * 
     * @param tileBounds
     *            the bounds of the requested tile
     * @return [0] = x coordinate , [1] y coordinate, [2] = zoomLevel
     */
    public int[] gridLocation(BBOX tileBounds) {
        int[] retVals = new int[3];

        double reqTileWidth = tileBounds.coords[2] - tileBounds.coords[0];

        // (Z) Zoom level
        // For EPSG 4326, reqTileWidth = 0.087 log(4096) / log(2) - 1; -> 11
        retVals[2] = (int) Math.round(Math.log(gridWidth / reqTileWidth)
                / (Math.log(2)))
                - gridConstant;

        double tileWidth = gridWidth / (Math.pow(2, retVals[2] + gridConstant));

        // X
        double xdiff = tileBounds.coords[0] - gridBounds.coords[0];
        retVals[0] = (int) Math.round(xdiff / tileWidth);
        // Y
        double ydiff = tileBounds.coords[1] - gridBounds.coords[1];
        retVals[1] = (int) Math.round(ydiff / tileWidth);

        if (log.isTraceEnabled()) {
            log.trace("x: " + retVals[0] + " y:" + retVals[1] + " z:"
                    + retVals[2]);
        }

        return retVals;
    }

    public String isInRange(int[] location) {
        // Check Z
        if (location[2] < zoomStart) {
            return "zoomlevel (" + location[2] + ")must be at least "
                    + zoomStart;
        }
        if (location[2] >= boundsGridLevels.length) {
            return "zoomlevel (" + location[2] + ") must be at most "
                    + boundsGridLevels.length;
        }

        int[] bounds = boundsGridLevels[location[2]];

        String gridDebug = " Extra debug information bounds: "
                + Arrays.toString(bounds) + " gridLoc: "
                + Arrays.toString(location);

        // Check X
        if (location[0] < bounds[0]) {
            return "gridX (" + location[0] + ") must be at least " + bounds[0]
                    + gridDebug;
        } else if (location[0] > bounds[2]) {
            return "gridX (" + location[0] + ") can be at most " + bounds[2]
                    + gridDebug;
        }

        // Check Y
        if (location[1] < bounds[1]) {
            return "gridY (" + location[1] + ") must be at least " + bounds[1]
                    + gridDebug;
        } else if (location[1] > bounds[3]) {
            return "gridY (" + location[1] + ") can be at most " + bounds[3]
                    + gridDebug;
        }

        return null;
    }

    /**
     * Uses the location on the grid to determine bounding box for a single
     * tile.
     * 
     * @param gridLoc
     * @return
     */
    public BBOX bboxFromGridLocation(int[] gridLoc) {
        double tileWidth = gridWidth / Math.pow(2, gridLoc[2] + gridConstant);

        return new BBOX(gridBounds.coords[0] + tileWidth * gridLoc[0],
                gridBounds.coords[1] + tileWidth * gridLoc[1],
                gridBounds.coords[0] + tileWidth * (gridLoc[0] + 1),
                gridBounds.coords[1] + tileWidth * (gridLoc[1] + 1));
    }

    /**
     * Uses the grid bounds to determine the bounding box, presumably for a
     * metatile.
     * 
     * Adds one tilewidth to the top and right.
     * 
     * @param gridBounds
     * @return
     */

    public BBOX bboxFromGridBounds(int[] gridLocBounds) {
        double tileWidth = gridWidth
                / Math.pow(2, gridLocBounds[4] + gridConstant);

        return new BBOX(gridBounds.coords[0] + tileWidth * gridLocBounds[0],
                gridBounds.coords[1] + tileWidth * gridLocBounds[1],
                gridBounds.coords[0] + tileWidth * (gridLocBounds[2] + 1),
                gridBounds.coords[1] + tileWidth * (gridLocBounds[3] + 1));
    }

    /**
     * Calculate the extent of the grid for the requested bounds.
     * 
     * It is up to you to verify that these bounds are within the bounds of the layer.
     * 
     * @param requestedBounds bounds for the request
     * @return the corresponding array
     */
    public int[][] coveredGridLevels(BBOX requestedBounds) {
        return calculateGridBounds(requestedBounds);
    }
    
    /**
     * Zooms in one level, returning 4 grid locations like
     * 
     * 0 1
     * 2 3
     * 
     * If a location is outside the bounds then the zoomLevel
     * (third entry) is set to -1
     * 
     * @param gridLoc
     * @return the four underlying tiles
     */
    public int[][] getZoomInGridLoc(int[] gridLoc) {
    	int[][] retVal = new int[4][3];
    	int x = gridLoc[0] * 2;
    	int y = gridLoc[1] * 2;
    	int z = gridLoc[2] + 1;
    	
    	// Now adjust where appropriate
    	
    	retVal[0][0] = retVal[2][0] = x;
    	retVal[1][0] = retVal[3][0] = x + 1;
    	
    	retVal[0][1] = retVal[1][1] = y;
    	retVal[2][1] = retVal[3][1] = y + 1;

    	retVal[0][2] = retVal[1][2] = retVal[2][2] = retVal[3][2] = z;
    	
    	// Need to check that it doesn't fall outside
    	int[] bounds = boundsGridLevels[z];
    	for(int i=0; i<4; i++) {
    		if( retVal[i][0] < bounds[0] 
    		 || retVal[i][1] < bounds[1]
    		 || retVal[i][0] > bounds[2]
    		 || retVal[i][1] > bounds[3] ) {
    			retVal[i][2] = -1;
    		}
    	}
    	
    	return retVal;
    }
    
    /**
     * Returns the the gridLocation where a single tile
     * covers the entire bounding box
     * 
     * NB: For 4326 this will retun {0,0,0} for something
     * that covers the world, but you still need {1,0,0}
     * 
     * @return
     */
    public int[] getZoomedOutGridLoc() {
    	if(zoomedOutGridLoc != null) {
    		return zoomedOutGridLoc;
    	}
    		
    	for(int i=0; i< boundsGridLevels.length; i++) {
    		if( boundsGridLevels[i][0] == boundsGridLevels[i][2] 
    		 && boundsGridLevels[i][1] == boundsGridLevels[i][3] ) {  			
    			zoomedOutGridLoc = new int[3];
    			zoomedOutGridLoc[0] = boundsGridLevels[i][0];
    			zoomedOutGridLoc[1] = boundsGridLevels[i][1];
    			zoomedOutGridLoc[2] = i;
    			
    			return zoomedOutGridLoc;
    		}
    	}
    	
    	log.error("Unable to find getZoomedOutGridLoc");
    	return null;
    }
}
