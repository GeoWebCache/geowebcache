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

import org.geowebcache.GeoWebCacheExtensionPriority;
import org.geowebcache.ReinitializingBean;
import org.springframework.beans.factory.InitializingBean;

/** Superinterface for GeoWebCache configuration beans, defining basic shared functionality */
public interface BaseConfiguration extends InitializingBean, ReinitializingBean, GeoWebCacheExtensionPriority {

    /** Default priority for configuration beans. Lower values will have higher priority. */
    public static final int BASE_PRIORITY = 50;

    /** @return non null identifier for this configuration */
    String getIdentifier();

    /**
     * The location is a string identifying where this configuration is persisted TileLayerConfiguration implementations
     * may choose whatever form is appropriate to their persistence mechanism and callers should not assume any
     * particular format. In many but not all cases this will be a URL or filesystem path.
     *
     * @return Location string for this configuration
     */
    String getLocation();

    /** Priority for sorting against other configurations of the specified type. */
    default int getPriority(Class<? extends BaseConfiguration> clazz) {
        return BASE_PRIORITY;
    }

    /** Priority for sorting against other priority extensions. */
    @Override
    default int getPriority() {
        return getPriority(BaseConfiguration.class);
    }
}
