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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Hashtable;

import javax.imageio.ImageIO;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.layer.Grid;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.util.ServletUtils;
import org.geowebcache.util.wms.BBOX;

public class WMSRasterFilter extends RequestFilter {
    private static Log log = LogFactory.getLog(WMSRasterFilter.class);
    
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
        
        if(matrices == null || matrices.get(srs.getNumber()) == null || matrices.get(srs.getNumber())[idx[2]] == null) {
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
        
        try {
            for(int i=0; i<4; i++) {
                x = baseX + xOffsets[i];
                y = baseY - yOffsets[i];
                
                if(x > -1 && x < width && y > -1 && y < height) {
                    if( mat.getData().getSample(x, y, 0) == 0 ) {
                        hasData = true;
                    }
                }   
            }
        } catch (ArrayIndexOutOfBoundsException aioob) {
            System.out.println("x:" + x + "  y:" + y  + " (" + mat.getWidth() + " " + mat.getHeight() + ")");
        }
        
        // Was there at 
        return hasData;
    }
    
    protected synchronized void setMatrix(WMSLayer layer, SRS srs, int z) {
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
        }
    }
    
    // TODO this code needs to be merged into the layer
    protected BufferedImage loadMatrix(WMSLayer layer, SRS srs, int z) throws IOException {
        String urlStr = wmsUrl(layer,srs,z);
        
        URL wmsUrl = new URL(urlStr);
        
        HttpURLConnection conn = (HttpURLConnection) wmsUrl.openConnection();
          
        byte[] ret = ServletUtils.readStream(conn.getInputStream(), 16384, 2048);
        
        InputStream is = new ByteArrayInputStream(ret);
        
        BufferedImage img = null;
        try {
            img = ImageIO.read(is);
        } catch (IOException ioe) {
            log.error(ioe.getMessage());
        }
        
        return img;
    }
    
    protected String wmsUrl(WMSLayer layer, SRS srs, int z) {
        Grid grid = layer.getGrid(srs);
        
        int[] bounds  = null;
        BBOX bbox = null;
        
        try {
            bounds = grid.getGridCalculator().getGridBounds(z);
            int[] gridLocBounds = {bounds[0],bounds[1],bounds[2],bounds[3],z};
            bbox = grid.getGridCalculator().bboxFromGridBounds(gridLocBounds);
        } catch (GeoWebCacheException gwce) {
            log.error(gwce.getMessage());
        }

        int width = bounds[2] - bounds[0] + 1;
        int height = bounds[3] - bounds[1] + 1;
        
        StringBuilder str = new StringBuilder();
        str.append(layer.getWMSurl()[0]);
        str.append("SERVICE=WMS&REQUEST=getmap&VERSION=1.1.1");
        str.append("&LAYERS=").append(layer.getName());
        str.append("&STYLES=").append(this.styles);
        str.append("&BBOX=").append(bbox.toString());
        str.append("&WIDTH=").append(width);
        str.append("&HEIGHT=").append(height);
        str.append("&FORMAT=").append(ImageMime.tiff.getFormat());
        str.append("&FORMAT_OPTIONS=antialias:none");
        str.append("&BGCOLOR=0xFFFFFF");
        
        return str.toString();
    }
}
