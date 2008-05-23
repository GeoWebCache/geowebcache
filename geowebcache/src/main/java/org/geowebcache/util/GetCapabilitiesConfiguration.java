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
 *  
 */
package org.geowebcache.util;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geotools.data.ows.Layer;
import org.geotools.data.ows.StyleImpl;
import org.geotools.data.ows.WMSCapabilities;
import org.geotools.data.wms.WebMapServer;
import org.geotools.ows.ServiceException;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.cache.CacheFactory;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.layer.wms.WMSLayerProfile;

public class GetCapabilitiesConfiguration implements Configuration {
    private static Log log = LogFactory
            .getLog(org.geowebcache.util.GetCapabilitiesConfiguration.class);

    private CacheFactory cacheFactory = null;

    private String url = null;

    private String mimeTypes = null;

    private String metaTiling = null;

    public GetCapabilitiesConfiguration(CacheFactory cacheFactory, String url,
            String mimeTypes, String metaTiling) {
        this.cacheFactory = cacheFactory;
        this.url = url;
        this.mimeTypes = mimeTypes;
        this.metaTiling = metaTiling;

        log.info("Constructing from url " + url);
    }

    /**
     * Identifier for this Configuration instance
     * 
     * @return the URL given to the constructor
     */
    public String getIdentifier() {
        return url;
    }

    /**
     * Gets the XML document and parses it, creates WMSLayers for the relevant
     * 
     * @return the layers described at the given URL
     */
    public Map<String,TileLayer> getTileLayers() throws GeoWebCacheException {
        HashMap<String,TileLayer> layerMap = null;

        WebMapServer wms = getWMS();
        if (wms == null) {
            throw new ConfigurationException("Unable to connect to " + this.url);
        }

        String wmsUrl = getWMSUrl(wms);
        log.info("Using " + wmsUrl + " to generate URLs for WMS requests");

        layerMap = getLayers(wms, wmsUrl);
        if (layerMap == null || layerMap.size() < 1) {
            log.error("Unable to find any layers based on " + url);
        } else {
            log.info("Loaded " + layerMap.size() + " layers from " + url);
        }

        return layerMap;
    }

    /**
     * Finds URL to WMS service and attempts to slice away the service
     * parameter, since we will add that anyway.
     * 
     * @param wms
     * @return
     */
    private String getWMSUrl(WebMapServer wms) {
        // // http://sigma.openplans.org:8080/geoserver/wms?SERVICE=WMS&
        String wmsUrl = wms.getCapabilities().getRequest().getGetCapabilities().getGet().toString();
        int queryStart = wmsUrl.lastIndexOf("?");
        String preQuery = wmsUrl.substring(queryStart);
        if (preQuery.equalsIgnoreCase("?service=wms&")) {
            wmsUrl = new String(wmsUrl.substring(0, wmsUrl.lastIndexOf("?")));
        }
        return wmsUrl;
    }

    private HashMap<String,TileLayer> getLayers(WebMapServer wms, String wmsUrl)
            throws GeoWebCacheException {
        HashMap<String,TileLayer> layerMap = new HashMap<String,TileLayer>();
        WMSCapabilities capabilities = wms.getCapabilities();
        if (capabilities == null) {
            throw new ConfigurationException("Unable to get capabitilies from "
                    + wmsUrl);
        }

        List<Layer> layerList = capabilities.getLayerList();

        Iterator<Layer> layerIter = layerList.iterator();
        while (layerIter.hasNext()) {
            Layer layer = layerIter.next();
            String name = layer.getName();
            
            if (name != null) {
                List styles = layer.getStyles();
                String stylesStr = "";
                if(styles != null) {
                    Iterator<StyleImpl> iter = styles.iterator();
                    
                    boolean hasOne = false;
                    while(iter.hasNext()) {
                        if(hasOne) {
                            stylesStr += ",";
                        }
                        stylesStr += iter.next().getName(); 
                    }
                }
                
                
                double minX = layer.getLatLonBoundingBox().getMinX();
                double minY = layer.getLatLonBoundingBox().getMinY();
                double maxX = layer.getLatLonBoundingBox().getMaxX();
                double maxY = layer.getLatLonBoundingBox().getMaxY();

                String bbox4326Str = Double.toString(minX) + ","
                        + Double.toString(minY) + "," + Double.toString(maxX)
                        + "," + Double.toString(maxY);

                
                log.info("Found layer: " + layer.getName()
                        + " with LatLon bbox " + bbox4326Str);
                
                String bbox900913Str = 
                    Double.toString(longToSphericalMercatorX(minX)) + ","
                    + Double.toString(latToSphericalMercatorY(minY)) + "," 
                    + Double.toString(longToSphericalMercatorX(maxX)) + ","
                    + Double.toString(latToSphericalMercatorY(maxY));

                WMSLayer wmsLayer = null;
                try {
                    wmsLayer = getLayer(name, wmsUrl, bbox4326Str, 
                            bbox900913Str, stylesStr);
                } catch (GeoWebCacheException gwc) {
                    log.error("Error creating " + layer.getName() + ": "
                            + gwc.getMessage());
                }

                if (wmsLayer != null) {
                    layerMap.put(name, wmsLayer);
                }
            }
        }

        return layerMap;
    }

    private WMSLayer getLayer(String name, String wmsurl, 
            String bbox4326Str, String bbox900913Str, String stylesStr)
            throws GeoWebCacheException {
        Properties props = new Properties();
        props.setProperty(WMSLayerProfile.WMS_URL, wmsurl);
        props.setProperty(WMSLayerProfile.WMS_SRS, "EPSG:4326;EPSG:900913");
        props.setProperty(WMSLayerProfile.WMS_BBOX, 
                bbox4326Str +";"+ bbox900913Str);
        props.setProperty(WMSLayerProfile.WMS_STYLES, stylesStr);
        props.setProperty(WMSLayerProfile.WMS_TRANSPARENT, "true");

        if (this.mimeTypes == null || this.mimeTypes.length() == 0) {
            props.setProperty(WMSLayer.WMS_MIMETYPES, "image/png,image/jpeg");
        } else {
            props.setProperty(WMSLayer.WMS_MIMETYPES, this.mimeTypes);
        }
        log.debug("Creating new layer " + name + " with properties: "
                + props.toString());

        if (this.metaTiling == null || this.metaTiling.length() == 0) {
            props.setProperty(WMSLayerProfile.WMS_METATILING, "3x3");
        } else {
            props.setProperty(WMSLayerProfile.WMS_METATILING, metaTiling);
        }
        WMSLayer layer = new WMSLayer(name, props, this.cacheFactory);

        return layer;
    }

    private WebMapServer getWMS() {
        try {
            return new WebMapServer(new URL(url));
        } catch (IOException ioe) {
            log.error(url + " -> " + ioe.getMessage());
        } catch (ServiceException se) {
            log.error(se.getMessage());
        }
        return null;
    }
    
    private double longToSphericalMercatorX(double x) {
        return (x/180.0)*20037508.34;
    }
    
    private double latToSphericalMercatorY(double y) {        
        if(y > 85.05112) {
            y = 85.05112;
        }
        
        if(y < -85.05112) {
            y = -85.05112;
        }
        
        y = (Math.PI/180.0)*y;
        double tmp = Math.PI/4.0 + y/2.0; 
        return 20037508.34 * Math.log(Math.tan(tmp)) / Math.PI;
    }
    
}
