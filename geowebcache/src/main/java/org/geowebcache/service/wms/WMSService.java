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
            ServiceRequest servReq, HttpServletRequest request) throws GeoWebCacheException {
        
        WMSParameters wmsParams = new WMSParameters(request);

        if(tileLayer == null) {
            throw new ServiceException("Did not find layer, layers=" + wmsParams.getLayer());
        }
        
        MimeType mime = null;
        String strFormat = wmsParams.getFormat();
        try {
            mime = MimeType.createFromFormat(strFormat);
        } catch (MimeException me) {
            throw new ServiceException("Unable to determined requested format, " + strFormat);
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
        
        String strOrigin = wmsParams.getOrigin();
        if(strOrigin != null) {
            String[] split = strOrigin.split(",");
            if(split.length != 2) {
                throw new ServiceException("Unable to parse tilesOrigin,"
                        +"should not be set anyway: " + strOrigin);
            }
            double x = Double.valueOf(split[0]);
            double y = Double.valueOf(split[1]);
            
            if(Math.abs(x + 180.0) < 0.5 
                    && x + Math.abs(y + 90.0) < 0.5) {
                // ok, fine for EPSG:4326
            } else if(Math.abs(x + 20037508.34) < 1.0 
                    && x + Math.abs(y + 20037508.34) < 1.0) {
                // ok, fine for EPSG:9000913
            } else{
                throw new ServiceException("The tilesOrigin parameter " + strOrigin 
                        + " is not accepted by GeoWebCache, please omit"
                        + " or use lower left corner of world bounds.");
            }
        }
            
        return new TileRequest(
                tileLayer.getGridLocForBounds(srsIdx,bbox),
                mime, srs);
    }
}
