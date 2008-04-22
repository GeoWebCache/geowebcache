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

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileRequest;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.service.Service;
import org.geowebcache.service.ServiceException;
import org.geowebcache.service.ServiceRequest;
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

    public ServiceRequest getServiceRequest(HttpServletRequest request)  throws ServiceException {
        return new ServiceRequest(super.getLayersParameter(request));
    }

    public TileRequest getTileRequest(TileLayer tileLayer, HttpServletRequest request)
    throws ServiceException {
        Map params = request.getParameterMap();
        
        String strFormat = ServletUtils.stringFromMap(params, "format");
        String strZoom = ServletUtils.stringFromMap(params, "zoom");
        String strX = ServletUtils.stringFromMap(params, "x");
        String strY = ServletUtils.stringFromMap(params, "y");

        int[] gridLoc = GMapsConverter.convert(Integer.parseInt(strZoom), Integer
                .parseInt(strX), Integer.parseInt(strY));
        
        SRS srs = new SRS(900913);
        
        MimeType mime = null; 
        try {
            if(strFormat == null) {
                strFormat = "image/png";
            }
            mime = MimeType.createFromFormat(strFormat);
        } catch (MimeException me) {
            throw new ServiceException("Unable to determine requested format, " + strFormat);
        }
        return new TileRequest(gridLoc,mime,srs);
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
    public static int[] convert(int zoomLevel, int x, int y) {
        // Extent is the total number of tiles in y direction
        int extent = (int) Math.pow(2, zoomLevel);

        if (x < 0 || x > extent - 1) {
            log.error("The X coordinate is not sane: " + x);
            return null;
        }

        if (y < 0 || y > extent - 1) {
            log.error("The Y coordinate is not sane: " + y);
            return null;
        }

        // xPos and yPos correspond to the top left hand corner
        int[] gridLoc = { x, extent - y - 1, zoomLevel };

        return gridLoc;
    }
}
