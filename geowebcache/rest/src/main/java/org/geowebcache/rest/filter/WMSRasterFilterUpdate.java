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
 */
package org.geowebcache.rest.filter;

import java.io.IOException;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.filter.request.RequestFilter;
import org.geowebcache.filter.request.WMSRasterFilter;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.rest.RestletException;
import org.restlet.data.Status;

public class WMSRasterFilterUpdate extends XmlFilterUpdate {
    String gridSetId;
    int zoomStart;
    int zoomStop;
    
    
    protected void runUpdate(RequestFilter filter, TileLayer tl) throws IOException, RestletException {
        if(! (filter instanceof WMSRasterFilter)) { 
            throw new RestletException("The filter " + filter.getName() + " is not a WMSRasterFilter.", 
                    Status.CLIENT_ERROR_BAD_REQUEST);
        }
        
        WMSRasterFilter wmsFilter = (WMSRasterFilter) filter;
        
        // Check that the SRS makes sense
        if (tl.getGridSubset(gridSetId) == null) {
            throw new RestletException("The filter " + wmsFilter.getName()
                    + " is associated with a layer that does not support "
                    + gridSetId, Status.CLIENT_ERROR_BAD_REQUEST);
        }

        // Run the actual update
        try {
            wmsFilter.update(tl, gridSetId, zoomStart, zoomStop);
        } catch (GeoWebCacheException e) {
            throw new RestletException("Error updating " + wmsFilter.getName()
                    + ": " + e.getMessage(), Status.SERVER_ERROR_INTERNAL);
        }
    }
}