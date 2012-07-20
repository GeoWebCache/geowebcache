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
 * @author Arne Kepp / The Open Planning Project 2009 
 */
package org.geowebcache.rest;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.restlet.Restlet;
import org.restlet.data.Status;

public class GWCRestlet extends Restlet {

    protected static TileLayer findTileLayer(String layerName, TileLayerDispatcher layerDispatcher) {
        if (layerName == null || layerName.length() == 0) {
            throw new RestletException("Layer not specified", Status.CLIENT_ERROR_BAD_REQUEST);
        }

        if (!layerDispatcher.layerExists(layerName)) {
            throw new RestletException("Unknown layer: " + layerName, Status.CLIENT_ERROR_NOT_FOUND);
        }

        TileLayer layer;
        try {
            layer = layerDispatcher.getTileLayer(layerName);
        } catch (GeoWebCacheException gwce) {
            throw new RestletException("Encountered error: " + gwce.getMessage(),
                    Status.SERVER_ERROR_INTERNAL);
        }

        return layer;
    }
}
