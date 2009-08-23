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
 * 
 */
package org.geowebcache.service.wmts;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.Conveyor;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubSet;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.service.Service;
import org.geowebcache.service.ServiceException;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.util.ServletUtils;

public class WMTSService extends Service {
    public static final String SERVICE_WMTS = "wmts";

    //private static Log log = LogFactory.getLog(org.geowebcache.service.wmts.WMTSService.class);
    
    private StorageBroker sb;
    
    private TileLayerDispatcher tld; 
    
    private GridSetBroker gsb;
    
    public WMTSService(StorageBroker sb, TileLayerDispatcher tld, GridSetBroker gsb) {
        super(SERVICE_WMTS);
        
        this.sb = sb;
        this.tld = tld;
        this.gsb = gsb;
    }

    public Conveyor getConveyor(HttpServletRequest request, HttpServletResponse response) 
    throws GeoWebCacheException {
        String[] keys = { "layer", "request", "style", "format", "tilematrixset", "tilematrix", "tilerow", "tilecol" };
        String[] values = ServletUtils.selectedStringsFromMap(request.getParameterMap(), keys);

        String req = values[1].toLowerCase();
        
        if(req == null) {
            throw new GeoWebCacheException("Missing REQUEST parameter");
        } else if(req.equals("gettile")) {
            ConveyorTile tile = new ConveyorTile(sb, values[0], request, response);
            tile.setHint(req);
            tile.setRequestHandler(Conveyor.RequestHandler.SERVICE);
            return tile;
        } else if(req.equals("getfeatureinfo") || req.equals("getcapabilities")) {
            ConveyorTile tile = new ConveyorTile(sb, values[0], request, response);
            tile.setHint(req);
            tile.setRequestHandler(ConveyorTile.RequestHandler.SERVICE);
            
            return tile; 
        } else {
            throw new GeoWebCacheException("Unknown REQUEST parameter " + req);
        }

 
    }
    
    private ConveyorTile getTile(String[] values, HttpServletRequest request, HttpServletResponse response)
    throws GeoWebCacheException {
        String layer = values[0];
        if (layer == null) {
            throw new ServiceException("Unable to parse LAYER parameter from request.");
        }

        TileLayer tileLayer = tld.getTileLayer(layer);
        
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
            mimeType = MimeType.createFromFormat(values[3]);
        } catch (MimeException me) {
            throw new ServiceException("Unable to determine requested format, " + values[3]);
        }
        
        if (values[4] == null) {
            throw new ServiceException("No TILEMATRIXSET specified");
        }
        
        GridSubSet gridSubSet = tileLayer.getGridSubSet(values[4]);
        if (gridSubSet == null) {
            throw new ServiceException("Unable to match requested TILEMATRIXSET "
                    + values[4] + " to those supported by layer");
        }
        
        if(values[5] == null) {
            throw new ServiceException("No TILEMATRIX specified");
        }
        long z = gridSubSet.getGridIndex(values[5]);
        
        if(z < 0) {
            throw new ServiceException("Unknown TILEMATRIX " + values[5]);
        }
        
        // WMTS has 0 in the top left corner -> flip y value
        if(values[6] == null) {
            throw new ServiceException("No TILEROW specified");
        }
        long[] gridExtent = gridSubSet.getGridSetExtent((int) z);
        long y = gridExtent[1] - Long.parseLong(values[6]);

        if(values[7] == null) {
            throw new ServiceException("No TILECOLUMN specified");
        }
        long x = Long.parseLong(values[7]);
        

        long[] tileIndex = {x,y,z};

        return new ConveyorTile(
                sb, layer, gridSubSet.getName(), tileIndex, mimeType, 
                modStrs[0], modStrs[1], request, response);

    }

    public void handleRequest(Conveyor conv)
            throws GeoWebCacheException {

        ConveyorTile tile = (ConveyorTile) conv;
        
        if (tile.getHint() != null) {
            if(tile.getHint().equals("getcapabilities")) {
                WMTSGetCapabilities wmsGC = new WMTSGetCapabilities(tld, gsb, tile.servletReq);      
                wmsGC.writeResponse(tile.servletResp);
                
            } else if(tile.getHint().equals("getfeatureinfo")) {
                WMTSGetFeatureInfo wmsGFI = new WMTSGetFeatureInfo(tld, tile.servletReq);      
                wmsGFI.writeResponse(tile.servletResp);
            }
        }
    }
}
