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
package org.geowebcache.service.wms;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.Conveyor;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.service.Service;
import org.geowebcache.service.ServiceException;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.util.Configuration;
import org.geowebcache.util.ServletUtils;
import org.geowebcache.util.wms.BBOX;

public class WMSService extends Service {
    public static final String SERVICE_WMS = "wms";

    private static Log log = LogFactory.getLog(org.geowebcache.service.wms.WMSService.class);

    // Proxy requests that are not getmap ?
    private boolean proxyRequests = false;
    
    // Proxy requests that are not tiled=true?
    private boolean proxyNonTiledRequests = false;
    
    private StorageBroker sb;
    
    private TileLayerDispatcher tld; 
    
    public WMSService(StorageBroker sb, TileLayerDispatcher tld) {
        super(SERVICE_WMS);
        
        this.sb = sb;
        this.tld = tld;
    }

    public ConveyorTile getConveyor(HttpServletRequest request, HttpServletResponse response) 
            throws GeoWebCacheException {
        String[] keys = { "layers", "request", "tiled", "cached", "metatiled" };
        String[] values = ServletUtils.selectedStringsFromMap(request.getParameterMap(), keys);

        // Look for requests that are not getmap
        String req = values[1];
        if (req != null && !req.equalsIgnoreCase("getmap")) {
            // One more chance
            if(values[0] == null || values[0].length() == 0) {    
                values[0] = ServletUtils.stringFromMap(request.getParameterMap(), "layer");
            }
            
            ConveyorTile tile = new ConveyorTile(sb, values[0], request, response);
            tile.setHint(req.toLowerCase());
            tile.setRequestHandler(ConveyorTile.RequestHandler.SERVICE);
            return tile;
        }

        // Check whether this request is missing tiled=true
        if (proxyNonTiledRequests
                && (values[2] == null || !values[2].equalsIgnoreCase("true"))) {
            ConveyorTile tile = new ConveyorTile(sb, values[0], request, response);
            tile.setHint(req);
            tile.setRequestHandler(Conveyor.RequestHandler.SERVICE);
            return tile;
        }

        // Look for layer
        String layers = values[0];
        if (layers == null) {
            throw new ServiceException("Unable to parse layers parameter from request.");
        }

        TileLayer tileLayer = tld.getTileLayer(layers);
        
        String[] paramKeys = { "format","srs","bbox"};
        String[] paramValues = ServletUtils.selectedStringsFromMap(request.getParameterMap(), paramKeys);

        String[] modStrs = null;
        if(tileLayer instanceof WMSLayer) {
            modStrs = ((WMSLayer) tileLayer).getModifiableParameters(request.getParameterMap());
        }
         
        if(modStrs == null) {
            modStrs = new String[2];
            modStrs[0] = null;
            modStrs[1] = null;
        }
                
        MimeType mimeType = null;
        try {
            mimeType = MimeType.createFromFormat(paramValues[0]);
        } catch (MimeException me) {
            throw new ServiceException("Unable to determine requested format, "
                    + paramValues[0]);
        }
        
        if (paramValues[1] == null) {
            throw new ServiceException("No SRS specified");
        }
        
        SRS srs = SRS.getSRS(paramValues[1]);
        if (! tileLayer.supportsSRS(srs)) {
            throw new ServiceException("Unable to match requested SRS "
                    + paramValues[1] + " to those supported by layer");
        }
   
        BBOX bbox = new BBOX(paramValues[2]);
        if (bbox == null || !bbox.isSane()) {
            throw new ServiceException(
                    "The bounding box parameter is missing or not sane");
        }

        int[] tileIndex = tileLayer.getGridLocForBounds(srs, bbox);
        
        return new ConveyorTile(
                sb, layers, srs, tileIndex, mimeType, 
                modStrs[0], modStrs[1], request, response);
    }

    public void handleRequest(Conveyor conv)
            throws GeoWebCacheException {

        ConveyorTile tile = (ConveyorTile) conv;
        
        if (tile.getHint() != null) {
            if(tile.getHint().equalsIgnoreCase("getcapabilities")) {
                WMSRequests.handleGetCapabilities(tld, tile.servletResp);
            } else {
                WMSRequests.handleProxy(tld, tile);
            }
        } else {
            throw new GeoWebCacheException(
                    "The WMS Service would love to help, "
                  + "but has no idea what you're trying to do?"
                  + "Please include request URL if you file a bug report.");
        }
    }
    
    public void setConfig(List<Configuration> getCapConfigs) {
        WMSRequests.getCapConfigs = getCapConfigs;
        log.info("Got passed " + getCapConfigs.size() + " configuration objects for getCapabilities.");
    }
    
    public void setProxyRequests(String trueFalse) {
        this.proxyRequests = Boolean.parseBoolean(trueFalse);
        if(this.proxyRequests) {
            log.info("Will proxy requests to backend that are not getmap or getcapabilities.");
        } else {
            log.info("Will NOT proxy non-getMap requests to backend.");
        }
    }
    
    public void setProxyNonTiledRequests(String trueFalse) {
        this.proxyNonTiledRequests = Boolean.parseBoolean(trueFalse);
        if(this.proxyNonTiledRequests) {
            log.info("Will proxy requests that miss tiled=true to backend.");
        } else {
            log.info("Will NOT proxy requests that miss tiled=true to backend.");
        }
    }
}
