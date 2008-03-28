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
import javax.servlet.http.HttpServletResponse;

import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileRequest;
import org.geowebcache.service.Service;

public class WMSConverter extends Service {
    public static final String SERVICE_WMS = "/wms";
    
    public WMSConverter() {
        super(SERVICE_WMS);
    }
    
    public String getLayerIdentifier(HttpServletRequest request) {
        return super.getLayersParameter(request);
    }
    
    public static TileRequest convert(WMSParameters wmsParams, 
            TileLayer layer, HttpServletResponse response) {
        return new TileRequest(
                layer.gridLocForBounds(wmsParams.getBBOX()),
                wmsParams.getImageMime());
    }
    

}
