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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geotools.data.ows.CRSEnvelope;
import org.geotools.data.ows.Layer;
import org.geotools.data.ows.StyleImpl;
import org.geotools.data.ows.WMSCapabilities;
import org.geotools.data.wms.WebMapServer;
import org.geotools.ows.ServiceException;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.cache.CacheFactory;
import org.geowebcache.layer.Grid;
import org.geowebcache.layer.GridCalculator;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.util.wms.BBOX;
import org.geowebcache.util.wms.Dimension;
import org.geowebcache.util.wms.ExtentHandler;
import org.geowebcache.util.wms.ExtentHandlerMap;

public class GetCapabilitiesConfiguration implements Configuration {
    private static Log log = LogFactory
    .getLog(org.geowebcache.util.GetCapabilitiesConfiguration.class);

    private CacheFactory cacheFactory = null;

    private String url = null;

    private String mimeTypes = null;

    private String metaTiling = null;

    private String vendorParameters = null;

    private ExtentHandlerMap extentHandlerMap;

    private boolean allowCacheBypass = false;

    public GetCapabilitiesConfiguration(CacheFactory cacheFactory, String url,
            String mimeTypes, String metaTiling, String allowCacheBypass) {
        this.cacheFactory = cacheFactory;
        this.url = url;
        this.mimeTypes = mimeTypes;
        this.metaTiling = metaTiling;

        if(Boolean.parseBoolean(allowCacheBypass)) {
            this.allowCacheBypass = true;
        }
        log.info("Constructing from url " + url);
    }

    public GetCapabilitiesConfiguration(CacheFactory cacheFactory, String url,
            String mimeTypes, String metaTiling, String vendorParameters, 
            String allowCacheBypass) {
        this.cacheFactory = cacheFactory;
        this.url = url;
        this.mimeTypes = mimeTypes;
        this.metaTiling = metaTiling;
        this.vendorParameters = vendorParameters;

        if(Boolean.parseBoolean(allowCacheBypass)) {
            this.allowCacheBypass = true;
        }
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
    public List<TileLayer> getTileLayers(boolean reload) throws GeoWebCacheException {
        List<TileLayer> layers = null;

        WebMapServer wms = getWMS();
        if (wms == null) {
            throw new ConfigurationException("Unable to connect to " + this.url);
        }

        String wmsUrl = getWMSUrl(wms);
        log.info("Using " + wmsUrl + " to generate URLs for WMS requests");

        layers = getLayers(wms, wmsUrl);
        if (layers == null || layers.size() < 1) {
            log.error("Unable to find any layers based on " + url);
        } else {
            log.info("Loaded " + layers.size() + " layers from " + url);
        }

        return layers;
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
        if (queryStart != -1) {
            String preQuery = wmsUrl.substring(queryStart);
            if (preQuery.equalsIgnoreCase("?service=wms&")) {
                wmsUrl = wmsUrl.substring(0, wmsUrl.lastIndexOf("?"));
            }
        }
        return wmsUrl;
    }

    private List<TileLayer> getLayers(WebMapServer wms, String wmsUrl)
    throws GeoWebCacheException {
        List<TileLayer> layers = new LinkedList<TileLayer>();

        WMSCapabilities capabilities = wms.getCapabilities();
        if (capabilities == null) {
            throw new ConfigurationException("Unable to get capabitilies from " + wmsUrl);
        }

        List<Layer> layerList = capabilities.getLayerList();
        Iterator<Layer> layerIter = layerList.iterator();

        while (layerIter.hasNext()) {
            Layer layer = layerIter.next();
            if (layer.getName() != null) {
                WMSLayer wmsLayer = null;
                try {
                    wmsLayer = getLayer(layer, wmsUrl);
                } catch (GeoWebCacheException gwc) {
                    log.error("Error creating " + layer.getName() + ": "
                            + gwc.getMessage());
                }

                if (wmsLayer != null) {
                    // Finalize with some defaults
                    wmsLayer.isCacheBypassAllowed(allowCacheBypass);
                    wmsLayer.setBackendTimeout(120);
                    layers.add(wmsLayer);
                }
            }
        }

        return layers;
    }

    private Map<String, Dimension> parseDimensions(Map<String, 
            org.geotools.data.wms.xml.Dimension> unparsedDimensions) {
        Map<String, Dimension> dimensions = new HashMap<String, Dimension>();
        if (unparsedDimensions != null) {
            Iterator<Entry<String, org.geotools.data.wms.xml.Dimension>> dimIter = 
                unparsedDimensions.entrySet().iterator();
            while (dimIter.hasNext()) {
                Entry<String, org.geotools.data.wms.xml.Dimension> dim = dimIter.next();
                String name = dim.getKey();
                org.geotools.data.wms.xml.Dimension dimension = dim.getValue();
                if (name == null || name.length() == 0 || dimension == null || 
                        dimension.getExtent() == null) {
                    break;
                }

                ExtentHandler extentHandler = extentHandlerMap.getHandler(dimension.getUnits());
                Dimension newDimension = new Dimension(name, dimension.getUnits(), 
                        dimension.getExtent().getValue(), extentHandler);

                newDimension.setCurrent(dimension.isCurrent());
                newDimension.setUnitSymbol(dimension.getUnitSymbol());
                newDimension.setDefaultValue(dimension.getExtent().getDefaultValue());
                newDimension.setMultipleValues(dimension.getExtent().isMultipleValues());
                newDimension.setNearestValue(dimension.getExtent().getNearestValue());
                dimensions.put(name, newDimension);
            }
        }

        return dimensions;
    }

    private WMSLayer getLayer(Layer layer, String wmsUrl) throws GeoWebCacheException {

        String name = layer.getName();
        String title = layer.getTitle();
        String _abstract = layer.get_abstract();
        
        String stylesStr = "";

        List<StyleImpl> styles = layer.getStyles();

        StringBuffer buf = new StringBuffer();
        if(styles != null) {
            Iterator<StyleImpl> iter = styles.iterator();
            boolean hasOne = false;
            while(iter.hasNext()) {
                if(hasOne) {
                    buf.append(",");
                }
                buf.append(iter.next().getName());
                hasOne = true;
            }
            stylesStr = buf.toString();
        }

        Map<String, Dimension> dimensions = parseDimensions(layer.getDimensions());

        String[] wmsUrls = {wmsUrl};
        
        Hashtable<SRS,Grid> grids = new Hashtable<SRS,Grid>();
        
        Set<Object> srss = layer.getSrs();
        Map<Object, CRSEnvelope> bboxs = layer.getBoundingBoxes();
        CRSEnvelope latLongBBOX = layer.getLatLonBoundingBox();
        if (latLongBBOX != null) {
            latLongBBOX.setEPSGCode("EPSG:4326");
            bboxs.put("EPSG:4326", latLongBBOX);
            srss.add("EPSG:4326");
        }
        for (Object srsKey : srss) {
            String key = (String) srsKey;
            CRSEnvelope value = bboxs.get(srsKey);
            SRS srs = SRS.getSRS(key);
            Grid grid = null;
            
            // select st_astext(st_transform(st_setsrid(st_point(1200000, 6100000), 3021), 3006))

            if (value != null) {
                BBOX dataBounds = new BBOX(value.getMinX(), value.getMinY(), value.getMaxX(), value.getMaxY());
                if (SRS.getEPSG4326().getNumber() == srs.getNumber()) {
                    grid = new Grid(SRS.getEPSG4326(), dataBounds, BBOX.WORLD4326, GridCalculator.get4326Resolutions());
                } else if (SRS.getEPSG900913().getNumber() == srs.getNumber()) {
                    grid = new Grid(SRS.getEPSG900913(), dataBounds, BBOX.WORLD900913, GridCalculator.get900913Resolutions());
                } else {
                    grid = new Grid(srs, dataBounds, dataBounds, null);
                    grid.setResolutions(grid.getResolutions());
                }
            } else if (SRS.getEPSG3021().getNumber() == srs.getNumber()) {
                grid = new Grid(SRS.getEPSG3021(), BBOX.EUROPE3021, BBOX.EUROPE3021, GridCalculator.get3021Resolutions());
            } else if (SRS.getEPSG3006().getNumber() == srs.getNumber()) {
                grid = new Grid(SRS.getEPSG3006(), BBOX.EUROPE3006, BBOX.EUROPE3006, GridCalculator.get3006Resolutions());
            } else if (SRS.getEPSG900913().getNumber() == srs.getNumber()) {
                grid = new Grid(SRS.getEPSG900913(), BBOX.WORLD900913, BBOX.WORLD900913, GridCalculator.get900913Resolutions());
            } else if (SRS.getEPSG4326().getNumber() == srs.getNumber()) {
                grid = new Grid(SRS.getEPSG4326(), BBOX.WORLD4326, BBOX.WORLD4326, GridCalculator.get4326Resolutions());
            }
            if (grid != null) {
                grids.put(srs, grid);
            }
        }
        
        List<String> mimeFormats = null;
        if(this.mimeTypes != null) {
            String[] mimeFormatArray = this.mimeTypes.split(",");
            mimeFormats = new ArrayList<String>(mimeFormatArray.length);

            // This is stupid... but oh well, we're only doing it once
            for(int i=0;i<mimeFormatArray.length;i++) {
                mimeFormats.add(mimeFormatArray[i]);
            }
        } else {
            mimeFormats = new ArrayList<String>(3);
            mimeFormats.add("image/png");
            mimeFormats.add("image/png8");
            mimeFormats.add("image/jpeg");  
        }

        String[] metaStrings = this.metaTiling.split("x");

        int[] metaWidthHeight = { Integer.parseInt(metaStrings[0]), Integer.parseInt(metaStrings[1])};

        // TODO We're dropping the styles now...
        return new WMSLayer(name, title, _abstract, this.cacheFactory, wmsUrls, stylesStr, name, mimeFormats, 
                grids, metaWidthHeight, this.vendorParameters, dimensions);
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

    public ExtentHandlerMap getExtentHandlerMap() {
        return extentHandlerMap;
    }

    public void setExtentHandlerMap(ExtentHandlerMap extentHandlerMap) {
        this.extentHandlerMap = extentHandlerMap;
    }


}
