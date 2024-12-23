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
 * <p>Copyright 2018
 */
package org.geowebcache.config;

import java.util.List;

/**
 * Interface for aggregators of {@link BaseConfiguration} implementations.
 *
 * @see org.geowebcache.layer.TileLayerDispatcher
 * @see org.geowebcache.grid.GridSetBroker
 * @see org.geowebcache.storage.BlobStoreAggregator
 */
public interface ConfigurationAggregator<C extends BaseConfiguration> {
    <T extends C> List<? extends T> getConfigurations(Class<T> clazz);
}
