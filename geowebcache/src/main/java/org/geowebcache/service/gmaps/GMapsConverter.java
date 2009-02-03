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
 */
package org.geowebcache.service.gmaps;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.service.Service;
import org.geowebcache.service.ServiceException;
import org.geowebcache.tile.Tile;
import org.geowebcache.util.ServletUtils;

/**
 * Class to convert from Google Maps coordinates into the internal
 * representation of a tile.
 */
public class GMapsConverter extends Service {
    public static final String SERVICE_GMAPS = "gmaps";

    private static Log log = LogFactory
            .getLog(org.geowebcache.service.gmaps.GMapsConverter.class);

    public GMapsConverter() {
        super(SERVICE_GMAPS);
    }
    
    public Tile getTile(HttpServletRequest request, HttpServletResponse response) throws ServiceException {
        String layerId = super.getLayersParameter(request);

        Map<String,String[]> params = request.getParameterMap();
        String strFormat = ServletUtils.stringFromMap(params, "format");
        String strZoom = ServletUtils.stringFromMap(params, "zoom");
        String strX = ServletUtils.stringFromMap(params, "x");
        String strY = ServletUtils.stringFromMap(params, "y");
        String strCached = ServletUtils.stringFromMap(params, "cached");
        String strMetaTiled = ServletUtils.stringFromMap(params, "metatiled");
        String timeParam = ServletUtils.stringFromMap(params, "time");
        String elevParam = ServletUtils.stringFromMap(params, "elevation");

        int[] gridLoc = GMapsConverter.convert(Integer.parseInt(strZoom), 
                Integer.parseInt(strX), Integer.parseInt(strY));

        MimeType mimeType = null;
        try {
            if (strFormat == null) {
                strFormat = "image/png";
            }
            mimeType = MimeType.createFromFormat(strFormat);
        } catch (MimeException me) {
            throw new ServiceException("Unable to determine requested format, "+ strFormat);
        }
        
        // TODO: set up time, elevation and dim_* parameters and values in:
        Map<String, String> dimensions = new HashMap<String, String>();

        Tile ret = new Tile(layerId, SRS.getEPSG900913(), gridLoc, mimeType, request, response, dimensions);
        
        if(strCached != null && ! Boolean.parseBoolean(strCached)) {
            ret.setRequestHandler(Tile.RequestHandler.SERVICE);
            
            if(strMetaTiled != null && ! Boolean.parseBoolean(strMetaTiled)) {
                ret.setHint("not_cached,not_metatiled");
            } else {
                ret.setHint("not_cached");
            }
        }
        
        return ret; 
    }
    
    /** 
     * NB The following code is shared across Google Maps, Mobile Google Maps and Virtual Earth
     */
    public void handleRequest(TileLayerDispatcher tLD, Tile tile)
            throws GeoWebCacheException {
        if (tile.hint != null) {
            boolean requestTiled = true;
            
            if (tile.hint.equals("not_cached,not_metatiled")) {
                requestTiled = false;
            } else if (!tile.hint.equals("not_cached")) {
                throw new GeoWebCacheException("Hint " + tile.hint + " is not known.");
            }

            TileLayer tl = tLD.getTileLayer(tile.getLayerId());

            if(tl == null) {
                throw new GeoWebCacheException("Unknown layer " + tile.getLayerId());
            }
            
            if(! tl.isCacheBypassAllowed().booleanValue()) {
                throw new GeoWebCacheException("Layer " + tile.getLayerId() 
                        + " is not configured to allow bypassing the cache.");
            }

            tile.setTileLayer(tl);
            tl.getNoncachedTile(tile, requestTiled);
            
            Service.writeResponse(tile, false);
        }
    }

    /**
     * Convert Google's tiling coordinates into an {x,y,x}
     * 
     * see
     * http://code.google.com/apis/maps/documentation/overlays.html#Custom_Map_Types
     * 
     * @param quadKey
     * @return
     */
    public static int[] convert(int zoomLevel, int x, int y) 
    throws ServiceException {
        // Extent is the total number of tiles in y direction
        int extent = (int) Math.pow(2, zoomLevel);

        if (x < 0 || x > extent - 1) {
            throw new ServiceException("The X coordinate is not sane: " + x);
        }

        if (y < 0 || y > extent - 1) {
            throw new ServiceException("The Y coordinate is not sane: " + y);
        }

        // xPos and yPos correspond to the top left hand corner
        int[] gridLoc = { x, extent - y - 1, zoomLevel };

        return gridLoc;
    }
}
