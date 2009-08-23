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


public class GridSet {
        
    protected Grid[] gridLevels;
    
    protected double[] leftBottom = new double[2];
    
    protected String name;
    
    protected SRS srs;
    
    protected int tileWidth;
    
    protected int tileHeight;
    
    protected GridSet() {
        // Blank
    }
    
    protected BoundingBox boundsFromIndex(long[] tileIndex) {
        Grid grid = gridLevels[(int) tileIndex[2]];
        
        double width = grid.resolution * tileWidth;
        double height = grid.resolution * tileHeight;
        
        BoundingBox tileBounds = new BoundingBox(
                leftBottom[0] + width*tileIndex[0],
                leftBottom[1] + height*tileIndex[1],
                leftBottom[0] + width*(tileIndex[0] + 1),
                leftBottom[1] + height*(tileIndex[1] + 1));
        return tileBounds;
    }
    
    protected BoundingBox boundsFromRectangle(long[] rectangleExtent) {
        Grid grid = gridLevels[(int) rectangleExtent[4]];
        
        double width = grid.resolution * tileWidth;
        double height = grid.resolution * tileHeight;
        
        BoundingBox rectangleBounds = new BoundingBox(
                leftBottom[0] + width*rectangleExtent[0], 
                leftBottom[1] + height*rectangleExtent[1],
                leftBottom[0] + width*(rectangleExtent[2] + 1),
                leftBottom[1] + height*(rectangleExtent[3] + 1) );
                
        return rectangleBounds;
    }
    
    protected long[] closestIndex(BoundingBox tileBounds) {       
        double wRes = tileBounds.getWidth() / tileWidth;
        
        double bestError = Double.MAX_VALUE;
        int bestLevel = -1;
        
        for(int i=0; i< gridLevels.length; i++) {
            Grid grid = gridLevels[i];
            
            double error = Math.abs(wRes - grid.resolution);
            
            if(error < bestError) {
                bestError = error;
                bestLevel = i;
            } else {
                break;
            }
        }

        return closestIndex(bestLevel, tileBounds);
    }
    
    protected long[] closestIndex(int level, BoundingBox tileBounds) {
        Grid grid = gridLevels[level];
        
        double width = grid.resolution * tileWidth;
        double height = grid.resolution * tileHeight;
        
        long posX = (long) Math.round((tileBounds.coords[0] - leftBottom[0]) / width);
        
        long posY = (long) Math.round((tileBounds.coords[1] - leftBottom[1]) / height);
        
        long[] ret = { posX, posY, level };
        
        return ret;
    }
    
    public long[] closestRectangle(BoundingBox rectangleBounds) {       
        double rectWidth = rectangleBounds.getWidth();
        double rectHeight = rectangleBounds.getHeight();
        
        double bestError = Double.MAX_VALUE;
        int bestLevel = -1;
        
        // Now we loop over the resolutions until
        for(int i=0; i< gridLevels.length; i++) {
            Grid grid = gridLevels[i];

            double countX = rectWidth / (grid.resolution * tileWidth);
            double countY = rectHeight / (grid.resolution * tileHeight);
            
            double error = 
                Math.abs(countX - Math.round(countX)) + 
                Math.abs(countY - Math.round(countY));

            if(error < bestError) {
                bestError = error;
                bestLevel = i;
            } else if(error > bestError) {
                break;
            }
        }
        
        return closestRectangle(bestLevel, rectangleBounds);
    }
    
    protected long[] closestRectangle(int level, BoundingBox rectangeBounds) {
        Grid grid = gridLevels[level];
        
        double width = grid.resolution * tileWidth;
        double height = grid.resolution * tileHeight;
        
        long minX = (long) Math.floor((rectangeBounds.coords[0] - leftBottom[0]) / width);
        long minY = (long) Math.floor((rectangeBounds.coords[1] - leftBottom[1]) / height);
        long maxX = (long) Math.ceil(((rectangeBounds.coords[2] - leftBottom[0]) / width));
        long maxY = (long) Math.ceil(((rectangeBounds.coords[3] - leftBottom[1]) / height));
        
        // We substract one, since that's the tile at that position
        long[] ret = { minX, minY, maxX - 1, maxY - 1, level };
        
        return ret;
    }
    
    public boolean equals(Object obj) {
        if(! (obj instanceof GridSet))
            return false;
        
        GridSet other = (GridSet) obj;
        
        if(this == other)
            return true;
        
        if(! other.srs.equals(srs))
            return false;
        
        if(! other.name.equals(name))
            return false;
     
        if(tileWidth != other.tileWidth ||
                tileHeight != other.tileHeight)
            return false;
        
        if(gridLevels.length != other.gridLevels.length)
            return false;
        
        for(int i=0; i<gridLevels.length; i++) {
            if(! gridLevels[i].equals(other.gridLevels[i]))
                return false;
        }
        
        return true;
    }
    
    public BoundingBox getBounds() {
        int i;
        long[] extent = null;

        for (i = (gridLevels.length -1); i > 0; i--) {
            extent = gridLevels[i].extent;

            if (extent[0] == 1 && extent[1] == 0) {
                break;
            }
        }
        
        extent = gridLevels[i].extent;
        
        long[] ret = { 0, 0, extent[0] - 1, extent[1] - 1, i};

        return boundsFromRectangle(ret);
    }
    
    public Grid[] getGrids() {
        return gridLevels;
    }
    
    public String getName() {
        return name;
    }
    
    public SRS getSRS() {
        return srs;
    }
    
    public int getTileHeight() {
        return tileHeight;
    }
    
    public int getTileWidth() {
        return tileWidth;
    }
}
