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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.layer.Grid;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.wms.WMSLayer;

public abstract class RasterFilter extends RequestFilter {
    private static Log log = LogFactory.getLog(RasterFilter.class);
    
    public int zoomStop;
    
    public String styles;
    
    public transient Hashtable<Integer,BufferedImage[]> matrices;
    
    public void apply(ConveyorTile convTile) throws RequestFilterException {
        int[] idx = convTile.getTileIndex().clone();
        SRS srs = convTile.getSRS();
        
        // Basic bounds test first
        try {
            convTile.getLayer().getGrid(srs).getGridCalculator().locationWithinBounds(idx);
        } catch (GeoWebCacheException gwce) {
            throw new BlankTileException(this);
        }
        
        if(idx[2] + 1 < zoomStop) {
            // Sample one level higher
            idx[0] = idx[0] * 2;
            idx[1] = idx[1] * 2;
            idx[2] = idx[2] + 1;
        } else {
            // Reduce to highest supported resolution
            int diff = idx[2] - zoomStop;
            idx[0] = idx[0] >> diff;
            idx[1] = idx[1] >> diff;
            idx[2] = zoomStop;
        }
        
        if(matrices == null 
                || matrices.get(srs.getNumber()) == null 
                || matrices.get(srs.getNumber())[idx[2]] == null) {
            setMatrix((WMSLayer) convTile.getLayer(), srs, idx[2]);
        }
        
        if(! lookup(convTile.getLayer().getGrid(srs), idx)) {
            throw new GreenTileException(this);
        }
    }
    
    private boolean lookup(Grid grid, int[] idx) {
        RenderedImage mat = matrices.get(grid.getSRS().getNumber())[idx[2]];
        
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
                for (int i = 0; i < 4; i++) {
                    x = baseX + xOffsets[i];
                    y = baseY - yOffsets[i];

                    if (x > -1 && x < width && y > -1 && y < height) {
                        if (mat.getData().getSample(x, y, 0) == 0) {
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
    
    /** 
     * This function will load the matrix from the appropriate source.
     * 
     * Calling this function twice for a particular combination will
     * cause the existing matrix to be overwritten.
     * 
     * @param layer
     * @param srs
     * @param z
     */
    protected synchronized void setMatrix(TileLayer layer, SRS srs, int z) {
        int srsId = srs.getNumber();
        
        if(matrices == null) {
            matrices = new Hashtable<Integer,BufferedImage[]>();
        }
        
        if(matrices.get(srsId) == null) {
            matrices.put(srsId, new BufferedImage[zoomStop + 1]);
        }
        
        if(matrices.get(srsId)[z] == null) {
            try {
                matrices.get(srsId)[z] = loadMatrix(layer,srs,z);
            } catch(IOException ioe) {
                log.error(ioe.getMessage());
            }
        } else {
            // We need to lock it
            BufferedImage oldImg = matrices.get(srsId)[z];
            synchronized(oldImg) {
                try {
                    matrices.get(srsId)[z] = loadMatrix(layer,srs,z);
                } catch(IOException ioe) {
                    log.error(ioe.getMessage());
                }
            }
        }
    }
    
    protected abstract BufferedImage loadMatrix(TileLayer layer, SRS srs, int zoomLevel) throws IOException;
}
