/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Arne Kepp, OpenGeo, Copyright 2009
 * @author David Vick, Boundless, 2017
 */
package org.geowebcache.rest.filter;

import java.io.IOException;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.filter.request.RequestFilter;
import org.geowebcache.filter.request.WMSRasterFilter;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.rest.exception.RestException;
import org.springframework.http.HttpStatus;

public class WMSRasterFilterUpdate extends XmlFilterUpdate {
    String gridSetId;
    int zoomStart;
    int zoomStop;

    @Override
    public void runUpdate(RequestFilter filter, TileLayer tl) throws IOException, RestException {
        if (!(filter instanceof WMSRasterFilter)) {
            throw new RestException(
                    "The filter " + filter.getName() + " is not a WMSRasterFilter.",
                    HttpStatus.BAD_REQUEST);
        }

        WMSRasterFilter wmsFilter = (WMSRasterFilter) filter;

        // Check that the SRS makes sense
        if (tl.getGridSubset(gridSetId) == null) {
            throw new RestException(
                    "The filter "
                            + wmsFilter.getName()
                            + " is associated with a layer that does not support "
                            + gridSetId,
                    HttpStatus.BAD_REQUEST);
        }

        // Run the actual update
        try {
            wmsFilter.update(tl, gridSetId, zoomStart, zoomStop);
        } catch (GeoWebCacheException e) {
            throw new RestException(
                    "Error updating " + wmsFilter.getName() + ": " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
