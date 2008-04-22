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
package org.geowebcache.service.mgmaps;

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
public class MGMapsConverter extends Service {
    public static final String SERVICE_MGMAPS = "mgmaps";

    private static Log log = LogFactory
            .getLog(org.geowebcache.service.mgmaps.MGMapsConverter.class);

    public MGMapsConverter() {
        super(SERVICE_MGMAPS);
    }

    public ServiceRequest getServiceRequest(HttpServletRequest request)
            throws ServiceException {
        return new ServiceRequest(super.getLayersParameter(request));
    }

    public TileRequest getTileRequest(TileLayer tileLayer,
            HttpServletRequest request) throws ServiceException {
        Map params = request.getParameterMap();

        String mimeType = ServletUtils.stringFromMap(params, "format");
        String strZoom = ServletUtils.stringFromMap(params, "zoom");
        String strX = ServletUtils.stringFromMap(params, "x");
        String strY = ServletUtils.stringFromMap(params, "y");

        int[] gridLoc = MGMapsConverter.convert(Integer.parseInt(strZoom),
                Integer.parseInt(strX), Integer.parseInt(strY));

        SRS srs = new SRS(900913);

        MimeType mime = null;
        try {
            if (mimeType == null) {
                mimeType = "image/png";
            }
            mime = MimeType.createFromMimeType(mimeType);
        } catch (MimeException me) {
            throw new ServiceException("Unable to determine requested format, "
                    + mimeType);
        }
        return new TileRequest(gridLoc, mime, srs);
    }

    /**
     * Convert Google's tiling coordinates into an {x,y,x}
     * 
     * see
     * http://code.google.com/apis/maps/documentation/overlays.html#Custom_Map_Types
     * 
     * Modified by JaakL for mgmaps zoom understanding: zoom = 17 - zoom
     * 
     * @param quadKey
     * @return
     */
    public static int[] convert(int zoomLevel, int x, int y) throws ServiceException {
        if(zoomLevel > 17) {
            throw new ServiceException("Zoomlevel cannot be greater than 17 for Mobile GMaps");
        }
        // Extent is the total number of tiles in y direction
        int newZoom = 17 - zoomLevel;
        int extent = (int) Math.pow(2, newZoom);

        if (x < 0 || x > extent - 1) {
            log.error("The X coordinate is not sane: " + x);
            return null;
        }

        if (y < 0 || y > extent - 1) {
            log.error("The Y coordinate is not sane: " + y);
            return null;
        }

        // xPos and yPos correspond to the top left hand corner
        int[] gridLoc = { x, extent - y - 1, newZoom };

        return gridLoc;
    }
}
