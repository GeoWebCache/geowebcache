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
    
    /**
     * The base cordinates, used to map tile indexes
     * to coordinate bounding boxes. These can either
     * be top left or bottom left, so must be kept private
     */
    protected double[] baseCoords;
    
    protected BoundingBox originalExtent;
    
    /**
     * Whether the y-coordinate of baseCoords is at
     * the top (true) or at the bottom (false)
     */
    protected boolean yBaseToggle = false;
    
    /**
     * By default the coordinates are {x,y},
     * this flag reverses the output for WMTS getcapabilities
     */
    protected boolean yCoordinateFirst = false;
    
    protected boolean scaleWarning = false;
       
    protected double metersPerUnit;
    
    protected double pixelSize;
    
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
       
        long y = tileIndex[1];
        if(yBaseToggle) {
            y = y - grid.extent[1];
        }
        
        BoundingBox tileBounds = new BoundingBox(
                baseCoords[0] + width*tileIndex[0],
                baseCoords[1] + height*(y),
                baseCoords[0] + width*(tileIndex[0] + 1),
                baseCoords[1] + height*(y + 1));
        return tileBounds;
    }
    
    protected BoundingBox boundsFromRectangle(long[] rectangleExtent) {
        Grid grid = gridLevels[(int) rectangleExtent[4]];
        
        double width = grid.resolution * tileWidth;
        double height = grid.resolution * tileHeight;
        
        long bottomY = rectangleExtent[1];
        long topY = rectangleExtent[3];
        
        if(yBaseToggle) {
            bottomY = bottomY - grid.extent[1];
            topY = topY - grid.extent[1];
        }
        
        BoundingBox rectangleBounds = new BoundingBox(
                baseCoords[0] + width*rectangleExtent[0], 
                baseCoords[1] + height*(bottomY),
                baseCoords[0] + width*(rectangleExtent[2] + 1),
                baseCoords[1] + height*(topY + 1) );
                
        return rectangleBounds;
    }
    
    protected long[] closestIndex(BoundingBox tileBounds) throws GridMismatchException {       
        double wRes = tileBounds.getWidth() / tileWidth;
        
        double bestError = Double.MAX_VALUE;
        int bestLevel = -1;
        double bestResolution = -1.0;
        
        for(int i=0; i< gridLevels.length; i++) {
            Grid grid = gridLevels[i];
            
            double error = Math.abs(wRes - grid.resolution);
            
            if(error < bestError) {
                bestError = error;
                bestResolution = grid.resolution;
                bestLevel = i;
            } else {
                break;
            }
        }
        
        if(Math.abs(wRes - bestResolution) > (0.1*wRes)) {
            throw new ResolutionMismatchException(wRes, bestResolution);
        }

        return closestIndex(bestLevel, tileBounds);
    }
    
    protected long[] closestIndex(int level, BoundingBox tileBounds) 
    throws GridAlignmentMismatchException {
        Grid grid = gridLevels[level];
        
        double width = grid.resolution * tileWidth;
        double height = grid.resolution * tileHeight;
        
        double x = (tileBounds.getMinX() - baseCoords[0]) / width;
        
        double y = (tileBounds.getMinY() - baseCoords[1]) / height;
            
        long posX = (long) Math.round(x);
        
        long posY = (long) Math.round(y);
        
        if(x - posX > 0.1 || y - posY > 0.1) {
            throw new GridAlignmentMismatchException(x,posX,y,posY);
        }
        
        if(yBaseToggle) {
            posY = posY + grid.extent[1];
        }
        
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
            } else if(error >= bestError) {
                break;
            }
        }
        
        return closestRectangle(bestLevel, rectangleBounds);
    }
    
    protected long[] closestRectangle(int level, BoundingBox rectangeBounds) {
        Grid grid = gridLevels[level];
        
        double width = grid.resolution * tileWidth;
        double height = grid.resolution * tileHeight;
        
        
        long minX = (long) Math.floor((rectangeBounds.getMinX() - baseCoords[0]) / width);
        long minY = (long) Math.floor((rectangeBounds.getMinY() - baseCoords[1])/ height);
        long maxX = (long) Math.ceil(((rectangeBounds.getMaxX() - baseCoords[0]) / width));
        long maxY = (long) Math.ceil(((rectangeBounds.getMaxY() - baseCoords[1]) / height));
        
        if(yBaseToggle) {
            minY = minY + grid.extent[1];
            maxY = maxY + grid.extent[1];
        }
        
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
        
        if(yBaseToggle != other.yBaseToggle)
            return false;
        
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
    
    /**
     * Returns the top left corner of the grid in the 
     * order used by the coordinate system. (Bad idea)
     * 
     * Used for WMTS GetCapabilities
     * 
     * @param gridIndex
     * @return
     */
    public double[] getOrderedTopLeftCorner(int gridIndex) {
        // First we will find the x,y pair, then we'll flip it if necessary
        double[] leftTop = new double[2];
        
        if(yBaseToggle) {
            leftTop[0] = baseCoords[0];
            leftTop[1] = baseCoords[1]; 
        } else {
            // We don't actually store the top coordinate, need to calculate it
            Grid grid = gridLevels[gridIndex];
            
            double dTileHeight = tileHeight;
            double dGridExtent = grid.extent[1];
            
            double top = baseCoords[1] + dTileHeight * grid.resolution * dGridExtent;
            
            // Round off if we are within 0.5% of an integer value
            if(Math.abs(top - Math.round(top)) < (top / 200)) {
                top = Math.round(top);
            }
            
            leftTop[0] = baseCoords[0];
            leftTop[1] = top;
        }
        
        // Y coordinate first?
        if(yCoordinateFirst) {
           double[] ret =  {leftTop[1], leftTop[0]};
           return ret;
        }
        
        return leftTop;
    }
    
    public String getName() {
        return name;
    }
    
    public SRS getSRS() {
        return srs;
    }
    
    public boolean getScaleWarning() {
        return scaleWarning;
    }
    
    public int getTileHeight() {
        return tileHeight;
    }
    
    public int getTileWidth() {
        return tileWidth;
    }
    
    public String guessMapUnits() {
        if(113000 > metersPerUnit && metersPerUnit > 110000) {
            return "degrees";
        } else if(1100 > metersPerUnit && metersPerUnit > 900) {      
            return "kilometers";
        } else if(1.1 > metersPerUnit && metersPerUnit > 0.9) {
            return "meters";
        } else if(0.4 > metersPerUnit && metersPerUnit > 0.28) {
            return "feet";
        } else if(0.03 > metersPerUnit && metersPerUnit > 0.02) {
            return "inches";
        } else if(0.02 > metersPerUnit && metersPerUnit > 0.005) {
            return "centimeters";
        } else if(0.002 > metersPerUnit && metersPerUnit > 0.0005) {
            return "millimeters";
        } else {
            return "unknown";
        }
    }

    public boolean isTopLeftAligned() {
        return this.yBaseToggle;
    }
}
