/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Arne Kepp, OpenGeo, Copyright 2009
 */
package org.geowebcache.filter.request;

import java.io.Serial;
import java.io.Serializable;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.layer.TileLayer;

/** This is just a dummy class. Should be abstract, but that gets tricky with XStream */
public abstract class RequestFilter implements Serializable {

    @Serial
    private static final long serialVersionUID = -1058186522442111377L;

    private String name;

    /** Apply the filter to the */
    public abstract void apply(ConveyorTile convTile) throws RequestFilterException;

    /** The name of the filter, as chosen by the user. It should be unique, but this is not enforced. */
    public String getName() {
        return name;
    }

    /** @param name the name to set */
    void setName(String name) {
        this.name = name;
    }

    /** Optional initialization */
    public abstract void initialize(TileLayer layer) throws GeoWebCacheException;

    /**
     * Optional updates, filters should implement at least one. The first two are used for filters that can update
     * themselves (eg. WMSRasterFilter), the last one for those that need input.
     */
    public abstract boolean update(TileLayer layer, String gridSetId);

    public abstract void update(TileLayer layer, String gridSetId, int zoomStart, int zoomStop)
            throws GeoWebCacheException;

    public abstract void update(byte[] filterData, TileLayer layer, String gridSetId, int z)
            throws GeoWebCacheException;
}
