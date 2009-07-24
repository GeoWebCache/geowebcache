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

package org.geowebcache.filter.request;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Iterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.layer.Grid;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.util.wms.BBOX;

/**
 * A raster filter uses multiple rasters, one for each zoom level,
 * as a lookup matrix. Each pixel on the raster corresponds to one
 * (256x256) tile, so the size of the matrix is 1 / 2^16.
 * 
 * To conserve memory, the layer bounds are used.
 * 
 * The raster must match the dimensions of the zoomlevel and use
 * 0x000000 for tiles that are valid.
 */
public abstract class RasterFilter extends RequestFilter {
    private static Log log = LogFactory.getLog(RasterFilter.class);
    
    public Integer zoomStart;
    
    public Integer zoomStop;
    
    public Boolean resample;
    
    public Boolean preload;
    
    public Boolean debug;
    
    public transient Hashtable<Integer,BufferedImage[]> matrices;
    
    public RasterFilter() {
        
    }
    
    public void apply(ConveyorTile convTile) throws RequestFilterException {
        int[] idx = convTile.getTileIndex().clone();
        SRS srs = convTile.getSRS();
        
        // Basic bounds test first
        try {
            convTile.getLayer().getGrid(srs).getGridCalculator().locationWithinBounds(idx);
        } catch (GeoWebCacheException gwce) {
            throw new BlankTileException(this);
        }
        
        int zoomDiff = 0;
        
        // Three scenarios below:
        // 1. z is too low , upsample if resampling is enabled
        // 2. z is within range, downsample one level and apply
        // 3. z is too large , downsample
        if(zoomStart != null && idx[2] < zoomStart) {
            if(resample == null || ! resample) {
                // Filter does not apply, zoomlevel is too low
                return;
            } else {
                // Upsample
                zoomDiff = idx[2] - zoomStart;
                idx[0] = idx[0] << (-1*zoomDiff);
                idx[1] = idx[1] << (-1*zoomDiff);
                idx[2] = zoomStart;
            }
        }else if(idx[2] < zoomStop) {
            // Sample one level higher
            idx[0] = idx[0] * 2;
            idx[1] = idx[1] * 2;
            idx[2] = idx[2] + 1;
        } else {
            // Reduce to highest supported resolution
            zoomDiff = idx[2] - zoomStop;
            idx[0] = idx[0] >> zoomDiff;
            idx[1] = idx[1] >> zoomDiff;
            idx[2] = zoomStop;
        }
        
        if(matrices == null 
                || matrices.get(srs.getNumber()) == null 
                || matrices.get(srs.getNumber())[idx[2]] == null) {
            try {
                setMatrix((WMSLayer) convTile.getLayer(), srs, idx[2], false);
            } catch(Exception e) {
                log.error("Failed to load matrix for " 
                        + this.name + ", " + srs.getNumber() + ", " + idx[2] + " : "
                        + e.getMessage());
                throw new RequestFilterException(this,500,"Failed while trying to load filter for " 
                        + idx[2] + ", please check the logs");
            }
        }

        
        if(zoomDiff == 0) {
            if(! lookup(convTile.getLayer().getGrid(srs), idx)) {
                if(debug != null && debug) {
                    throw new GreenTileException(this);
                } else {
                    throw new BlankTileException(this);
                }
            }
        } else if(zoomDiff > 0) {
            if(! lookupQuad(convTile.getLayer().getGrid(srs), idx)) {
                if(debug != null && debug) {
                    throw new GreenTileException(this);
                } else {
                    throw new BlankTileException(this);
                }
            }
        } else if(zoomDiff < 0) {
            if(! lookupSubsample(convTile.getLayer().getGrid(srs), idx, zoomDiff)) {
                if(debug != null && debug) {
                    throw new GreenTileException(this);
                } else {
                    throw new BlankTileException(this);
                }
            }
        }
    }
    
    /**
     * Loops over all the zoom levels and initializes the lookup images.
     */
    public void initialize(TileLayer layer) throws GeoWebCacheException {
        if (!(layer instanceof WMSLayer)) {
            throw new GeoWebCacheException("Unable to handle non-WMS layers for request filter init.");
        }

        if (preload != null && preload) {
            Iterator<SRS> iter = layer.getGrids().keySet().iterator();
            
            while(iter.hasNext()) {
                SRS srs = iter.next();
                
                for (int i = 0; i <= zoomStop; i++) {
                    try {
                        setMatrix(layer, srs, i, false);
                    } catch (Exception e) {
                        log.error("Failed to load matrix for " + this.name
                                + ", " + srs.getNumber() + ", " + i + " : "
                                + e.getMessage());
                    }
                }
            }
        }
    }
    
    
    /**
     * Performs a lookup against an internal raster.
     * 
     * @param grid
     * @param idx
     * @return
     */
     private boolean lookup(Grid grid, int[] idx) {
         BufferedImage mat = matrices.get(grid.getSRS().getNumber())[idx[2]];
         
         int[] gridBounds = null;
         try {
             gridBounds = grid.getGridCalculator().getGridBounds(idx[2]);
         } catch (Exception e) {
             log.error(e.getMessage());
         }
         
         // Changing index to top left hand origin
         int x = idx[0] - gridBounds[0];
         int y = gridBounds[3] - idx[1];

         return (mat.getRaster().getSample(x, y, 0) == 0);
     }
    
   /**
    * Performs a lookup against an internal raster. The sampling is
    * actually done against 4 pixels, idx should already have been
    * modified to use one level higher than strictly necessary.
    * 
    * @param grid
    * @param idx
    * @return
    */
    private boolean lookupQuad(Grid grid, int[] idx) {
        BufferedImage mat = matrices.get(grid.getSRS().getNumber())[idx[2]];
        
        int[] gridBounds = null;
        try {
            gridBounds = grid.getGridCalculator().getGridBounds(idx[2]);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        
        // Changing index to top left hand origin
        int baseX = idx[0] - gridBounds[0];
        int baseY = gridBounds[3] - idx[1];

        int width = mat.getWidth();
        int height = mat.getHeight();

        int x = baseX; 
        int y = baseY;
        
        // We're checking 4 samples. The base is bottom left hand corner
        boolean hasData = false;

        // BL, BR, TL, TR
        int[] xOffsets = {0,1,0,1};
        int[] yOffsets = {0,0,1,1};
        
        // Lock, in case someone wants to replace the matrix
        synchronized (mat) {
            try {
                for (int i = 0; i < 4 && ! hasData; i++) {
                    x = baseX + xOffsets[i];
                    y = baseY - yOffsets[i];

                    if (x > -1 && x < width && y > -1 && y < height) {
                        if (mat.getRaster().getSample(x, y, 0) == 0) {
                            hasData = true;
                        }
                    }
                }
            } catch (ArrayIndexOutOfBoundsException aioob) {
                log.error("x:" + x + "  y:" + y + " (" + mat.getWidth() + " " + mat.getHeight() + ")");
            }
        }
        
        return hasData;
    }
    
    private boolean lookupSubsample(Grid grid, int[] idx, int zoomDiff) {
        BufferedImage mat = matrices.get(grid.getSRS().getNumber())[idx[2]];
        
        int sampleChange = 1 << (-1* zoomDiff);
        
        int[] gridBounds = null;
        try {
            gridBounds = grid.getGridCalculator().getGridBounds(idx[2]);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        
        // Changing index to top left hand origin
        int baseX = idx[0] - gridBounds[0];
        int baseY = gridBounds[3] - idx[1];
        
        int width = mat.getWidth();
        int height = mat.getHeight();

        int startX = Math.max(0, baseX);
        int stopX = Math.min(width, baseX + sampleChange);
        int startY = Math.min(baseY, height - 1);
        int stopY = Math.max(0,  baseY - sampleChange);
        
        int x = -1;
        int y = -1; 
        
        // Lock, in case someone wants to replace the matrix
        synchronized (mat) {            
            try {
                // Try center and edges first 
                x = (stopX + startX)/2;
                y = (startY + stopY)/2;
                if (mat.getRaster().getSample(x,y,0) == 0
                        || mat.getRaster().getSample(stopX -1, stopY + 1, 0) == 0
                        || mat.getRaster().getSample(stopX -1, startY   , 0) == 0
                        || mat.getRaster().getSample(startX  , stopY + 1, 0) == 0) {
                    return true;
                }
                
                // Do the hard work, loop over all pixels
                x = startX;
                y = startY;
                
                // Left to right
                while(x < stopX) {
                    // Bottom to top
                    while(y > stopY) {
                        if (mat.getRaster().getSample(x, y, 0) == 0) {
                            return true;
                        }
                        y--;
                    }
                    x++;
                    y = startY;
                }
            } catch (ArrayIndexOutOfBoundsException aioob) {
                log.error("x:" + x + "  y:" + y + " (" + mat.getWidth() + " " + mat.getHeight() + ")");
            }
        }
        
        return false;
    }
    
    /** 
     * This function will load the matrix from the appropriate source.
     * 
     * @param layer Access to the layer, to make the object simpler
     * @param srs
     * @param z (zoom level)
     * @param replace Whether to update if a matrix exists
     */
    public synchronized void setMatrix(TileLayer layer, SRS srs, int z,
            boolean replace) throws IOException, GeoWebCacheException {
        int srsId = srs.getNumber();

        if (matrices == null) {
            matrices = new Hashtable<Integer, BufferedImage[]>();
        }

        if (matrices.get(srsId) == null) {
            matrices.put(srsId, new BufferedImage[zoomStop + 1]);
        }

        if (matrices.get(srsId)[z] == null) {
            matrices.get(srsId)[z] = loadMatrix(layer, srs, z);

        } else if (replace) {
            BufferedImage oldImg = matrices.get(srsId)[z];
            BufferedImage[] matArray = matrices.get(srsId);

            // Get the replacement
            BufferedImage newImg = loadMatrix(layer, srs, z);

            // We need to lock it
            synchronized (oldImg) {
                matArray[z] = newImg;
            }
        }
    }
    
    /**
     * Helper function for calculating width and height
     * 
     * @param grid
     * @param z
     * @return
     * @throws GeoWebCacheException
     */
    protected int[] calculateWidthHeight(Grid grid, int z) throws GeoWebCacheException {
        int[] bounds = grid.getGridCalculator().getGridBounds(z);

        int[] widthHeight = new int[2];
        widthHeight[0] = bounds[2] - bounds[0] + 1;
        widthHeight[1] = bounds[3] - bounds[1] + 1;
        
        return widthHeight; 
    }

    /**
     * Helper function for calculating the bounding box
     * 
     * @param grid
     * @param z
     * @return
     * @throws GeoWebCacheException
     */
    protected BBOX calculateBbox(Grid grid, int z) throws GeoWebCacheException {
        int[] bounds = grid.getGridCalculator().getGridBounds(z);
        int[] gridLocBounds = {bounds[0],bounds[1],bounds[2],bounds[3],z};
        return grid.getGridCalculator().bboxFromGridBounds(gridLocBounds);
    }
    
    protected abstract BufferedImage loadMatrix(TileLayer layer, SRS srs, int zoomLevel) 
    throws IOException, GeoWebCacheException;
}
