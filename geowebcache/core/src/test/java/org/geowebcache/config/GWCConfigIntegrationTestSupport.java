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

import java.util.Collections;
import java.util.List;
import org.geowebcache.grid.GridSetBroker;

/**
 * Provides access to an (initially empty) GWC configuration.
 *
 * <p>Seperate implementations exist for each of the different GWC Configurations. See: *
 * {@link GWCXMLConfigIntegrationTestSupport}
 */
public abstract class GWCConfigIntegrationTestSupport {

    GridSetBroker broker = new GridSetBroker(Collections.singletonList(new DefaultGridsets(true, true)));

    /** Resets to an empty configuration; */
    public abstract void resetConfiguration() throws Exception;

    /**
     * @return The list of {@link TileLayerConfiguration}s for this GWC configuration (usually just a singleton list)
     */
    public abstract List<TileLayerConfiguration> getTileLayerConfigurations();

    /** @return The gridset broker for this configuration */
    public GridSetBroker getGridSetBroker() {
        return broker;
    }

    /** @return The {@link ServerConfiguration} for this configuration */
    public abstract ServerConfiguration getServerConfiguration();

    /** @return The {@link GridSetConfiguration} for this configuration */
    public abstract List<GridSetConfiguration> getGridSetConfigurations();

    /** @return The {@link GridSetConfiguration} for this configuration */
    public abstract GridSetConfiguration getWritableGridSetConfiguration();

    /** @return The {@link BlobStoreConfiguration} for this configuration */
    public abstract BlobStoreConfiguration getBlobStoreConfiguration();
}
