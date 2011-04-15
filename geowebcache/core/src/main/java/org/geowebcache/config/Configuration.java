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
 *  
 */
package org.geowebcache.config;

import java.util.List;
import java.util.Set;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.meta.ServiceInformation;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.layer.TileLayer;

public interface Configuration {

    public int initialize(GridSetBroker gridSetBroker) throws GeoWebCacheException;

    public List<? extends TileLayer> getTileLayers() throws GeoWebCacheException;

    public String getIdentifier();

    public ServiceInformation getServiceInformation() throws GeoWebCacheException;

    public boolean isRuntimeStatsEnabled();

    /**
     * @param layerIdent
     *            the layer name
     * @return the layer named {@code layerIdent} or {@code null} if no such layer exists in this
     *         configuration
     */
    public TileLayer getTileLayer(String layerIdent);

    public int getTileLayerCount();

    public Set<String> getTileLayerNames();
    
    public boolean remove(String layerName);

}
