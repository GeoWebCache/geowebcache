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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.imageio.ImageIO;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.Grid;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.util.ServletUtils;
import org.geowebcache.util.wms.BBOX;

public class WMSRasterFilter extends RasterFilter {
    private static Log log = LogFactory.getLog(RasterFilter.class);

    protected BufferedImage loadMatrix(TileLayer tlayer, SRS srs, int z) throws IOException {
        if(! (tlayer instanceof WMSLayer))
            return null;
        
        WMSLayer layer = (WMSLayer) tlayer;
        
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
