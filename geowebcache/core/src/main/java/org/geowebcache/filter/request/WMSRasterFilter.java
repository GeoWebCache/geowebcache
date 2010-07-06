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
import java.net.URL;

import javax.imageio.ImageIO;

import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.wms.WMSHttpHelper;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.util.ServletUtils;

public class WMSRasterFilter extends RasterFilter {
    private static Log log = LogFactory.getLog(RasterFilter.class);
    
    public String wmsLayers;

    public String wmsStyles;
    
    public Integer backendTimeout;
    
    protected BufferedImage loadMatrix(TileLayer tlayer, String gridSetId, int z) throws IOException, GeoWebCacheException {
        if(! (tlayer instanceof WMSLayer)) {
            log.error("WMSRasterFilter can only be used with WMS layers.");
            return null;
        }
        
        WMSLayer layer = (WMSLayer) tlayer;
        
        if(! (layer.getSourceHelper() instanceof WMSHttpHelper)) {
            log.error("WMSRasterFilter can only be used with WMS layers.");
        }
        
        WMSHttpHelper srcHelper = (WMSHttpHelper) layer.getSourceHelper();
        
        GridSubset gridSet = layer.getGridSubset(gridSetId);
        
        int[] widthHeight = calculateWidthHeight(gridSet, z);
        
        String urlStr = wmsUrl(layer, gridSet, z, widthHeight);
        
        log.info("Updated WMS raster filter, zoom level " + z + " for " + getName() + " (" + layer.getName() +  ") , " + urlStr);
        
        URL wmsUrl = new URL(urlStr);

        if(backendTimeout == null) {
            backendTimeout = 120;
        }
        
        GetMethod getMethod = null;
        BufferedImage img = null;
        
        try {
            getMethod = srcHelper.executeRequest(wmsUrl, backendTimeout); //.WMSHttpHelper.executeRequest(wmsUrl, timeout);
            
            if(getMethod.getStatusCode() != 200) {
                throw new GeoWebCacheException("Received response code " + getMethod.getStatusCode() + "\n");
            }
            
            if(! getMethod.getResponseHeader("Content-Type").getValue().startsWith("image/")) {
                throw new GeoWebCacheException("Unexpected response content type " + getMethod.getResponseHeader("Content-Type").getValue() + " , request was " + urlStr + "\n");
            }
            
            byte[] ret = ServletUtils.readStream(getMethod.getResponseBodyAsStream(), 16384, 2048);
            
            InputStream is = new ByteArrayInputStream(ret);
            
            img = ImageIO.read(is);
            
        } finally {
            if(getMethod != null) {
                getMethod.releaseConnection();
            }
        }

        if(img.getWidth() != widthHeight[0] || img.getHeight() != widthHeight[1]) {
            String msg = "WMS raster filter has dimensions " + img.getWidth() + "," + img.getHeight()
                    + ", expected " + widthHeight[0] + "," + widthHeight[1] + "\n";
            throw new GeoWebCacheException(msg);
        }
        
        return img;
    }
        
    /**
     * Generates the URL used to create the lookup raster
     * 
     * @param layer
     * @param srs
     * @param z
     * @return
     */
    protected String wmsUrl(WMSLayer layer, GridSubset gridSubset, int z, int[] widthHeight) throws GeoWebCacheException {  
        BoundingBox bbox = gridSubset.getCoverageBounds(z);
        
        StringBuilder str = new StringBuilder();
        str.append(layer.getWMSurl()[0]);
        str.append("SERVICE=WMS&REQUEST=getmap&VERSION=1.1.1");
        
        str.append("&LAYERS=");
        if(this.wmsLayers != null) {
            str.append(this.wmsLayers);
        } else {
             str.append(layer.getName());
        }
        
        str.append("&STYLES=");
        if(this.wmsStyles != null) {
            str.append(this.wmsStyles);
        }
        
        str.append("&SRS=").append(layer.backendSRSOverride(gridSubset.getSRS()));

        str.append("&BBOX=").append(bbox.toString());
        str.append("&WIDTH=").append(widthHeight[0]);
        str.append("&HEIGHT=").append(widthHeight[1]);
        str.append("&FORMAT=").append(ImageMime.tiff.getFormat());
        str.append("&FORMAT_OPTIONS=antialias:none");
        str.append("&BGCOLOR=0xFFFFFF");
        
        return str.toString();
    }

    public void update(byte[] filterData, TileLayer layer, String gridSetId, int z)
            throws GeoWebCacheException {
        throw new GeoWebCacheException("update(byte[] filterData, TileLayer layer, String gridSetId, int z) is not appropriate for WMSRasterFilters");
    }
    
    public boolean update(TileLayer layer, String gridSetId) {
        for (int z = super.zoomStart; z <= super.zoomStop; z++) {
            try {
                this.setMatrix(layer, gridSetId, z, true);
            } catch (Exception e) {
                log.error(e.getMessage());
                if(true || log.isDebugEnabled()) {
                    e.printStackTrace();
                }
            }
        }
        return true;
    }
    
    public void update(TileLayer layer, String gridSetId, int zStart, int zStop)
    throws GeoWebCacheException {
           for (int z = zStart; z <= zStop; z++) {
               try {
                  this.setMatrix(layer, gridSetId, z, true);
               } catch (Exception e) {
                   log.error(e.getMessage());
                   if(true || log.isDebugEnabled()) {
                       e.printStackTrace();
                   }
               }
           }
    }
}
