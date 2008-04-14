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
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.service.Service;
import org.geowebcache.service.ServiceException;
import org.geowebcache.service.ServiceRequest;
import org.geowebcache.util.wms.BBOX;

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
        
        MimeType mime = null;
        String mimeType = wmsParams.getMime();
        try {
            mime = MimeType.createFromMimeType(mimeType);
        } catch (MimeException me) {
            throw new ServiceException("Unable to determined requested format, " + mimeType);
        }
        
        if(wmsParams.getSrs() == null) {
            throw new ServiceException("No SRS specified");
        }
        
        SRS srs = new SRS(wmsParams.getSrs());
        int srsIdx = tileLayer.getSRSIndex(srs);
        if(srsIdx < 0) {
            throw new ServiceException("Unable to match requested SRS " + wmsParams.getSrs() + " to those supported by layer");
        }
        
        BBOX bbox = wmsParams.getBBOX();
        if(bbox == null || ! bbox.isSane()) {
            throw new ServiceException("The bounding box parameter is missing or not sane");
        }
        
        return new TileRequest(
                tileLayer.getGridLocForBounds(srsIdx,bbox),
                mime, srs);
    }
}
