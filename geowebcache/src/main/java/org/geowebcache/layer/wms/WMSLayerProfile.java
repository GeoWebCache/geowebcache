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
 * @author Arne Kepp, The Open Planning Project, Copyright 2007
 *  
 */
package org.geowebcache.layer.wms;

import java.net.URLConnection;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.SRS;
import org.geowebcache.service.wms.WMSParameters;
import org.geowebcache.util.ServletUtils;
import org.geowebcache.util.wms.BBOX;
import org.geowebcache.util.wms.GridCalculator;

public class WMSLayerProfile {
    private static Log log = LogFactory
            .getLog(org.geowebcache.layer.wms.WMSLayerProfile.class);

    public static final String WMS_URL = "wmsurl";

    public static final String WMS_SRS = "srs";

    public static final String WMS_BBOX = "bbox";
    
    public static final String WMS_STYLES = "wmsstyles";

    public static final String WMS_METATILING = "metatiling";

    public static final String WMS_TRANSPARENT = "transparent";

    public static final int CACHE_NEVER = 0;

    public static final int CACHE_VALUE_UNSET = -1;

    public static final int CACHE_NEVER_EXPIRE = -2;

    public static final int CACHE_USE_WMS_BACKEND_VALUE = -4;

    protected SRS[] srs = null;

    protected BBOX[] bbox = null;

    protected BBOX[] gridBase = null;

    protected GridCalculator[] gridCalc = null;

    protected int width = 256;

    protected int height = 256;

    protected int metaWidth = 1;

    protected int metaHeight = 1;

    protected int zoomStart = 0;

    protected int zoomStop = 20;

    protected String request = "GetMap";

    protected String version = "1.1.1";

    protected String errorMime = "application/vnd.ogc.se_inimage";

    protected String transparent = null;

    protected String tiled = null;

    protected String bgcolor = null;

    protected String palette = null;

    protected String vendorParameters = null;

    protected String wmsURL[] = { "http://localhost:8080/geoserver/wms" };

    protected int curWmsURL = 0;

    protected String wmsLayers = "topp:states";

    protected String wmsStyles = null;

    protected WMSParameters wmsparams = null;
    
    protected boolean saveExpirationHeaders = false;

    protected long expireClients = CACHE_USE_WMS_BACKEND_VALUE;

    protected long expireCache = CACHE_NEVER_EXPIRE;

    /**
     * Only for testing purposes
     */
    public WMSLayerProfile() {

    }

    public WMSLayerProfile(String layerName, Properties props)
            throws GeoWebCacheException {
        log.info("Processing " + layerName);

        this.wmsLayers = layerName;

        // Can be overriden by the properties
        this.setParametersFromProperties(props);

        if (expireCache == WMSLayerProfile.CACHE_USE_WMS_BACKEND_VALUE
                || expireClients == WMSLayerProfile.CACHE_USE_WMS_BACKEND_VALUE) {
            this.saveExpirationHeaders = true;
        }
        
        if (log.isTraceEnabled()) {
            log.trace("Created a new layer: " + toString());
        }
    }

    private void setParametersFromProperties(Properties props)
            throws GeoWebCacheException {

        String propSrs = props.getProperty(WMS_SRS);
        if (propSrs != null) {
            String[] srsar = propSrs.split(";");
            srs = new SRS[srsar.length];
            for (int i = 0; i < srsar.length; i++) {
                srs[i] = new SRS(srsar[i]);
            }
        }

        double[] maxTileWidth = new double[srs.length];
        double[] maxTileHeight = new double[srs.length];

        for (int i = 0; i < srs.length; i++) {
            SRS curSrs = srs[i];

            if (curSrs.getNumber() == 4326) {
                maxTileWidth[i] = 180.0;
                maxTileHeight[i] = 180.0;
            } else if (curSrs.getNumber() == 900913) {
                maxTileWidth[i] = 20037508.34 * 2;
                maxTileHeight[i] = 20037508.34 * 2;
            } else {
                // TODO some fancy code to guess other SRSes, throw exception
                // for now
                maxTileWidth[i] = 20037508.34 * 2;
                maxTileHeight[i] = 20037508.34 * 2;
                log
                        .error("GeoWebCache only handles EPSG:4326 and EPSG:900913!");
                throw new GeoWebCacheException(
                        "GeoWebCache only handles EPSG:4326 and EPSG:900913!");
            }
        }

        String propZoomStart = props.getProperty("zoomStart");
        if (propZoomStart != null) {
            zoomStart = Integer.parseInt(propZoomStart);
        }

        String propZoomStop = props.getProperty("zoomStop");
        if (propZoomStop != null) {
            zoomStop = Integer.parseInt(propZoomStop);
        }

        String propMetatiling = props.getProperty(WMS_METATILING);
        if (propMetatiling != null) {
            String[] metatiling = propMetatiling.split("x");
            if (metatiling != null && metatiling.length == 2) {
                metaWidth = Integer.parseInt(metatiling[0]);
                metaHeight = Integer.parseInt(metatiling[1]);
            } else {
                log.error("Unable to interpret metatiling=" + propMetatiling
                        + ", expected something like 3x3");
            }
        }

        String propGrids = props.getProperty("grid");
        String[] grids = null;
        gridBase = new BBOX[srs.length];
        if (propGrids != null) {
            grids = propGrids.split(";");
        }

        for (int i = 0; i < srs.length; i++) {
            String propGrid = null;
            if (grids != null && i < grids.length) {
                propGrid = grids[i];
            }

            if (propGrid != null) {
                gridBase[i] = new BBOX(propGrid);
                if (!gridBase[i].isSane()) {
                    throw new GeoWebCacheException("The grid " + propGrid
                            + " intepreted as " + gridBase[i].toString()
                            + " is not sane.");
                }
            } else {
                if (srs[i].getNumber() == 900913) {
                    log.info("Using default grid for EPSG:900913");
                    gridBase[i] = new BBOX(-20037508.34, -20037508.34,
                            20037508.34, 20037508.34);
                } else {
                    log.info("Using default grid for EPSG:4326 for " + srs[i]);
                    gridBase[i] = new BBOX(-180.0, -90.0, 180.0, 90.0);
                }
            }

        }

        // The following depends on metatiling and grid
        String propBboxs = props.getProperty(WMS_BBOX);
        String[] bboxs = null;
        bbox = new BBOX[srs.length];
        if (propBboxs != null) {
            bboxs = propBboxs.split(";");
        }

        for (int i = 0; i < srs.length; i++) {
            BBOX layerBounds = null;
            String propBbox = null;

            if (bboxs != null && i < bboxs.length) {
                propBbox = bboxs[i];
            }

            if (propBbox != null) {
                layerBounds = new BBOX(propBbox);

                log.info("Specified bbox " + layerBounds.toString() + ".");

                if (!layerBounds.isSane()) {
                    throw new GeoWebCacheException("The bounds " + propBbox
                            + " intepreted as " + layerBounds.toString()
                            + " is not sane.");
                } else if (!gridBase[i].contains(layerBounds)) {
                    log.error("The bbox " + propBbox + " intepreted as "
                            + layerBounds.toString()
                            + " is not contained by the grid: "
                            + gridBase[i].toString());
                } else {
                    bbox[i] = layerBounds;
                }
            } else {
                log
                        .info("Bounding box not properly specified, reverting to grid extent");
                bbox[i] = gridBase[i];
            }
        }

        // Create a grid calculator based on this information
        gridCalc = new GridCalculator[srs.length];
        for (int i = 0; i < srs.length; i++) {
            gridCalc[i] = new GridCalculator(gridBase[i], bbox[i], zoomStart,
                    zoomStop, metaWidth, metaHeight, maxTileWidth[i],
                    maxTileHeight[i]);
        }

        String propWidth = props.getProperty("width");
        if (propWidth != null) {
            width = Integer.parseInt(propWidth);
        }

        String propHeight = props.getProperty("height");
        if (propHeight != null) {
            height = Integer.parseInt(propHeight);
        }

        String propVersion = props.getProperty("version");
        if (propVersion != null) {
            version = propVersion;
        }

        String propErrorMime = props.getProperty("errormime");
        if (propErrorMime != null) {
            errorMime = propErrorMime;
        }

        String propTiled = props.getProperty("tiled");
        if (propTiled != null) {
            tiled = propTiled;
        }

        String propTransparent = props.getProperty(WMS_TRANSPARENT);
        if (propTransparent != null) {
            transparent = propTransparent;
        }

        String propBgcolor = props.getProperty("bgcolor");
        if (propBgcolor != null) {
            bgcolor = propBgcolor;
        }

        String propPalette = props.getProperty("palette");
        if (propPalette != null) {
            palette = propPalette;
        }

        String propVendorParameters = props.getProperty("vendorparameters");
        if (propVendorParameters != null) {
            vendorParameters = propVendorParameters;
        }

        String propUrl = props.getProperty(WMS_URL);
        if (propUrl != null) {
            wmsURL = propUrl.split(",");
        }

        String propLayers = props.getProperty("wmslayers");
        if (propLayers != null) {
            wmsLayers = propLayers;
        }

        String propStyles = props.getProperty(WMS_STYLES);
        if (propStyles != null) {
            wmsStyles = propStyles;
        }

        String propExpireClients = props.getProperty("expireclients");
        if (propExpireClients != null) {
            expireClients = Integer.parseInt(propExpireClients) * 1000;
        }

        String propExpireCache = props.getProperty("expireCache");
        if (propExpireCache != null) {
            expireCache = Integer.parseInt(propExpireCache) * 1000;
        }

    }

    /**
     * Get the WMS backend URL that should be used next according to the round
     * robin.
     * 
     * @return the next URL
     */
    protected String nextWmsURL() {
        curWmsURL = (curWmsURL + 1) % wmsURL.length;
        return wmsURL[curWmsURL];
    }

    /**
     * Gets the template for a WMS request for this profile, missing the
     * response mimetype and the boundingbox.
     * 
     * This is just painful - clone? - IOException on mimetype? -> Simplify
     * WMSParameters?
     */
    protected WMSParameters getWMSParamTemplate() {
        wmsparams = new WMSParameters();
        wmsparams.setRequest(request);
        wmsparams.setVersion(version);
        wmsparams.setLayer(wmsLayers);
        wmsparams.setErrorMime(errorMime);

        if (transparent != null) {
            wmsparams.setIsTransparent(transparent);
        }
        if (tiled != null || metaHeight > 1 || metaWidth > 1) {
            wmsparams.setIsTiled(tiled);
        }
        if (bgcolor != null) {
            wmsparams.setBgColor(bgcolor);
        }
        if (palette != null) {
            wmsparams.setPalette(palette);
        }
        if (vendorParameters != null) {
            wmsparams.setVendorParams(vendorParameters);
        }
        if (wmsStyles != null) {
            wmsparams.setStyles(wmsStyles);
        }

        return wmsparams;
    }

    protected int getSRSIndex(SRS reqSRS) {
        for (int i = 0; i < srs.length; i++) {
            if (srs[i].equals(reqSRS)) {
                return i;
            }
        }

        return -1;
    }
    
    protected void saveExpirationInformation(URLConnection backendCon) {
        this.saveExpirationHeaders = false;
        try {
        if (expireCache == WMSLayerProfile.CACHE_USE_WMS_BACKEND_VALUE) {                
            Long expire = ServletUtils.extractHeaderMaxAge(backendCon);
            if(expire == null) {
                expire = Long.valueOf(7200*1000);
            }
            log.error("Layer profile wants MaxAge from backend,"
                    + " but backend does not provide this. Setting to 7200 seconds.");
            expireCache = expire.longValue();
            log.trace("Setting expireCache to: "+ expireCache);
        }
        if (expireClients == WMSLayerProfile.CACHE_USE_WMS_BACKEND_VALUE) {
            Long expire = ServletUtils.extractHeaderMaxAge(backendCon);
            if(expire == null) {
                expire = Long.valueOf(7200*1000);
            }
            log.error("Layer profile wants MaxAge from backend,"
                    + " but backend does not provide this. Setting to 7200 seconds.");
            expireClients = expire.longValue();
            log.trace("Setting expireClients to: "+ expireClients);
        }
        } catch(Exception e) {
            // Sometimes this doesn't work (network conditions?), 
            // and it's really not worth getting caught up on it.
            e.printStackTrace();
        }
    }
}