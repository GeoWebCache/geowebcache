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
package org.geowebcache.service.wms;

import javax.servlet.http.HttpServletRequest;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileRequest;
import org.geowebcache.mime.MimeType;
import org.geowebcache.service.Service;
import org.geowebcache.service.ServiceException;
import org.geowebcache.service.ServiceRequest;

public class WMSService extends Service {
    public static final String SERVICE_WMS = "wms";

    public WMSService() {
        super(SERVICE_WMS);
    }

    public ServiceRequest getServiceRequest(HttpServletRequest request) throws ServiceException {
        return new ServiceRequest(super.getLayersParameter(request));
    }

    public TileRequest getTileRequest(TileLayer tileLayer,
            HttpServletRequest request) throws GeoWebCacheException {
        
        WMSParameters wmsParams = new WMSParameters(request);

        if(tileLayer == null) {
            throw new ServiceException("Did not find layer, layers=" + wmsParams.getLayer());
        }
        
        SRS srs = new SRS(wmsParams.getSrs());
        int srsIdx = tileLayer.getSRSIndex(srs);
        return new TileRequest(
                tileLayer.getGridLocForBounds(srsIdx,wmsParams.getBBOX()),
                MimeType.createFromMimeType(wmsParams.getMime()), srs);
    }
}
