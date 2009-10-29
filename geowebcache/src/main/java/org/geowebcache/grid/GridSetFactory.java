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


public class GridSetFactory {
    private static Log log = LogFactory.getLog(GridSetFactory.class);
    
    static int DEFAULT_LEVELS = 31;
    
    final static double EPSG4326_TO_METERS = 6378137.0 * 2.0 * Math.PI / 360.0;
    
    final static double EPSG3857_TO_METERS = 1;
    
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
    public static GridSet createGridSet( 
            String name, SRS srs, BoundingBox extent, 
            boolean alignTopLeft, double[] resolutions, double[] scaleDenoms, Double metersPerUnit,
            String[] scaleNames, int tileWidth, int tileHeight) {
        
        GridSet gridSet = baseGridSet(name, srs, tileWidth, tileHeight);
        
        gridSet.baseCoords = new double[2];
        
        if(alignTopLeft) {
            gridSet.baseCoords[0] = extent.coords[0];
            gridSet.baseCoords[1] = extent.coords[3];
            gridSet.yBaseToggle = true;
        } else {
            gridSet.baseCoords[0] = extent.coords[0];
            gridSet.baseCoords[1] = extent.coords[1];
        }
        
        gridSet.originalExtent = extent;
        
        if(metersPerUnit == null) {
            if(srs.equals(SRS.getEPSG4326())) {
                gridSet.metersPerUnit = EPSG4326_TO_METERS;
            } else if(srs.equals(SRS.getEPSG3857())) {
                gridSet.metersPerUnit = EPSG3857_TO_METERS;
            } else {
                if(resolutions == null) {
                    log.warn("GridSet "+name+" was defined without metersPerUnit, assuming 1m/unit."
                            + " All scales will be off if this is incorrect.");
                } else {
                    log.warn("GridSet "+name+" was defined without metersPerUnit. " +
                    		"Assuming 1m per SRS unit for WMTS scale output.");
                    				
                    gridSet.scaleWarning = true;
                }
                gridSet.metersPerUnit = 1.0;
            }
        } else {
            gridSet.metersPerUnit = metersPerUnit;
        }
        
        if(resolutions == null) {
            gridSet.gridLevels = new Grid[scaleDenoms.length];
        } else {
            gridSet.gridLevels = new Grid[resolutions.length];
        }
        
        for(int i=0; i<gridSet.gridLevels.length; i++) {
            Grid curGrid = new Grid();

            if(scaleDenoms != null) {
                curGrid.scaleDenom = scaleDenoms[i];
                curGrid.resolution = 0.00028 * (scaleDenoms[i] / gridSet.metersPerUnit);
            } else {
                curGrid.resolution = resolutions[i];
                curGrid.scaleDenom =  (resolutions[i] * gridSet.metersPerUnit) / 0.00028;
                //System.out.println(name+" : "+i+" : "+curGrid.scaleDenom+" : "+resolutions[i]);
            }
            
            double mapUnitWidth = tileWidth * curGrid.resolution;
            double mapUnitHeight = tileHeight * curGrid.resolution;
            
            curGrid.extent[0] = (long) Math.ceil( (extent.getWidth() - mapUnitWidth * 0.01) / mapUnitWidth);
            curGrid.extent[1] = (long) Math.ceil( (extent.getHeight() - mapUnitHeight * 0.01) / mapUnitHeight);
            
            if(scaleNames == null) {
                curGrid.name = gridSet.name + ":" + i;
            } else {
                curGrid.name = scaleNames[i];
            }
            
            gridSet.gridLevels[i] = curGrid;
        }
        
        return gridSet; 
    }
    
    /**
     * This covers the case where a number of zoom levels has been specified, but no resolutions / scale
     */
    public static GridSet createGridSet(
            String name, SRS srs, BoundingBox extent, boolean alignTopLeft,
            int levels, Double metersPerUnit, int tileWidth, int tileHeight) {
        
        double[] resolutions = new double[levels];
        
        double relWidth =  extent.getWidth() / tileWidth;
        double relHeight = extent.getHeight() / tileHeight;
        
        double ratio = relWidth / relHeight;
        double roundedRatio = Math.round(ratio);
        double ratioDiff = ratio - roundedRatio;
        
        // Cut 2.5% slack throughout
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
            
            // Do we keep the top or the bottom fixed?
            if(alignTopLeft) {
                extent.coords[1] = extent.coords[3] - (relHeight * tileHeight);
            } else {
                extent.coords[3] = (relHeight * tileHeight) + extent.coords[1];
            }
            
            resolutions[0] = (extent.getWidth() / ratio) / tileWidth;
        }
        
        for(int i=1; i<levels; i++) {
            resolutions[i] = resolutions[i - 1] / 2;
        }
        
        return createGridSet(name, srs, extent, alignTopLeft, resolutions, null, metersPerUnit, null, tileWidth, tileHeight);
    }
}
