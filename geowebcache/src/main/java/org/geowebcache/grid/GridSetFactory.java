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


public class GridSetFactory {
    static int DEFAULT_LEVELS = 31;
    
    private static GridSet baseGridSet(String name, SRS srs, int tileWidth, int tileHeight) {
        GridSet gridSet = new GridSet();
        
        gridSet.name = name;
        gridSet.srs = srs;
        
        gridSet.tileWidth = tileWidth;
        gridSet.tileHeight = tileHeight;
        
        return gridSet;
    }
    /**
     * Note that you should provide EITHER resolutions or scales. Providing both will cause scales to be overwritten
     * 
     * @param name
     * @param srs
     * @param extent
     * @param resolutions
     * @param scales
     * @param tileWidth
     * @param tileHeight
     * @return
     */
    public static GridSet createGridSet(String name, SRS srs, BoundingBox extent, double[] resolutions, double[] scales, int tileWidth, int tileHeight) {
        GridSet gridSet = baseGridSet(name, srs, tileWidth, tileHeight);
        
        gridSet.leftBottom[0] = extent.coords[0];
        gridSet.leftBottom[1] = extent.coords[1];
        
        gridSet.gridLevels = new Grid[resolutions.length];
        
        if(resolutions == null) {
            resolutions = new double[scales.length];
            for(int i=0; i<scales.length; i++) {
                resolutions[i] = scales[i] * 0.00028;
            }
        }
        
        for(int i=0; i<resolutions.length; i++) {
            Grid curGrid = new Grid();
            curGrid.resolution = resolutions[i];

            // OGC magic potion
            curGrid.scale = resolutions[i] / 0.00028;
                
            double mapUnitWidth = tileWidth * curGrid.resolution;
            double mapUnitHeight = tileHeight * curGrid.resolution;
            
            curGrid.extent[0] = (long) Math.ceil( (extent.getWidth() - mapUnitWidth * 0.01) / mapUnitWidth);
            curGrid.extent[1] = (long) Math.ceil( (extent.getHeight() - mapUnitHeight * 0.01) / mapUnitHeight);
            
            curGrid.name = gridSet.name + ":" + i;
            
            gridSet.gridLevels[i] = curGrid;
        }
        
        return gridSet; 
    }
    
    public static GridSet createGridSet(String name, SRS srs, BoundingBox extent, int levels, int tileWidth, int tileHeight) {
        double[] resolutions = new double[levels];
        
        double relWidth =  extent.getWidth() / tileWidth;
        double relHeight = extent.getHeight() / tileHeight;
        
        double ratio = relWidth / relHeight;
        double roundedRatio = Math.round(ratio);
        double ratioDiff = ratio - roundedRatio;
        
        // Cute 2.5% slack throughout
        if(Math.abs(ratioDiff) < 0.025) {
            // All good
            resolutions[0] = relWidth / roundedRatio;
        
        } else if(ratio < roundedRatio) {
            // Increase the width
            if(ratioDiff < 0) {
                ratio = roundedRatio;
            } else {
                ratio = roundedRatio + 1;
            }
            relWidth +=  (ratio * relHeight - relWidth);
            
            extent.coords[2] = (relWidth * tileWidth) + extent.coords[0];
        
            resolutions[0] = (extent.getWidth() / ratio) / tileWidth;
        } else {
            // Increase the height
            if(ratioDiff > 0) {
                ratio = roundedRatio;
            } else {
                ratio = roundedRatio + 1;
            }
            relHeight += ((relWidth / ratio) - relHeight);
            
            extent.coords[3] = (relHeight * tileHeight) + extent.coords[1];
            
            resolutions[0] = (extent.getWidth() / ratio) / tileWidth;
        }
        
        for(int i=1; i<levels; i++) {
            resolutions[i] = resolutions[i - 1] / 2;
        }
        
        return createGridSet(name, srs, extent, resolutions, null, tileWidth, tileHeight);
    }
}
