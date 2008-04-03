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
package org.geowebcache.layer;

import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public abstract class MetaTile {
    private static Log log = LogFactory
    .getLog(org.geowebcache.layer.MetaTile.class);
    
    protected int[] metaTileGridBounds = null; // minx,miny,maxx,maxy,zoomlevel
    
    protected int[][] tilesGridPositions = null; // the grid positions of the individual tiles

    protected int metaX; // X metatiling factor, after adjusting to bounds

    protected int metaY; // Y metatiling factor, after adjusting to bounds

    protected SRS srs;
    
    
    protected MetaTile(SRS srs, int[] gridBounds, int[] tileGridPosition,
    		int metaX, int metaY) {
    	this.srs = srs;
        this.metaX = metaX;
        this.metaY = metaY;
        
        metaTileGridBounds = calculateMetaTileGridBounds(gridBounds, tileGridPosition);
        tilesGridPositions = calculateTilesGridPositions();   
    }
    
    /**
     * Figures out the bounds of the metatile, in terms of the gridposition
     * of all contained tiles. To get the BBOX you need to add one tilewidth
     * to the top and right.
     * 
     * It also updates metaX and metaY to the actual metatiling factors
     * 
     * @param gridBounds
     * @param tileGridPosition
     * @return
     */
    private int[] calculateMetaTileGridBounds(int[] gridBounds, int[] tileGridPosition) {
        // Sanity checks that are hopefully redundant
        //if(  ! (tileGridPosition[0] >= gridBounds[0] && tileGridPosition[0] <= gridBounds[2])
        //        &&(tileGridPosition[1] >= gridBounds[1] && tileGridPosition[1] <= gridBounds[3])
        //        &&(tileGridPosition[2] >= gridBounds[0] && tileGridPosition[2] <= gridBounds[2])
        //        &&(tileGridPosition[3] >= gridBounds[1] && tileGridPosition[3] <= gridBounds[3])) {
        //    log.error("calculateMetaTileGridBounds(): " + Arrays.toString(gridBounds) +" "+ Arrays.toString(tileGridPosition) );
        //    return null;
        //}
        
        int[] metaTileGridBounds = new int[5];
        metaTileGridBounds[0] = tileGridPosition[0] - (tileGridPosition[0] % metaX);
        metaTileGridBounds[1] = tileGridPosition[1] - (tileGridPosition[1] % metaY);
        metaTileGridBounds[2] = Math.min( metaTileGridBounds[0] + metaX - 1, gridBounds[2]);
        metaTileGridBounds[3] = Math.min( metaTileGridBounds[1] + metaY - 1, gridBounds[3]);
        metaTileGridBounds[4] = tileGridPosition[2];
        
        // Save the actual metatiling factor, important at the boundaries
        metaX = metaTileGridBounds[2] - metaTileGridBounds[0] + 1;
        metaY = metaTileGridBounds[3] - metaTileGridBounds[1] + 1;
        
        return metaTileGridBounds;
    }
    
    /**
     * Creates an array with all the grid positions, used for cache keys
     */
    private int[][] calculateTilesGridPositions() {        
        int[][] tilesGridPositions = new int[metaX * metaY][3];

        for (int y = 0; y < metaY; y++) {
            for (int x = 0; x < metaX; x++) {
                int tile = y * metaX + x;
                tilesGridPositions[tile][0] = metaTileGridBounds[0] + x;
                tilesGridPositions[tile][1] = metaTileGridBounds[1] + y;
                tilesGridPositions[tile][2] = metaTileGridBounds[4];
            }
        }
        
        return tilesGridPositions;
    }

    /**
     * The bottom left grid position and zoomlevel for this metatile, used for
     * locking.
     * 
     * @return
     */
    public int[] getMetaGridPos() {
        int[] gridPos = { metaTileGridBounds[0], metaTileGridBounds[1], metaTileGridBounds[4] };
        return gridPos;
    }
    
    /**
     * The bounds for the metatile
     * 
     * @return
     */
    public int[] getMetaTileGridBounds() {
        return metaTileGridBounds;
    }
    
    public int[][] getTilesGridPositions() {
        return tilesGridPositions;
    }
    
    public SRS getSRS() {
    	return this.srs;
    }
}
