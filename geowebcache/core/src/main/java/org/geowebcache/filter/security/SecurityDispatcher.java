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
 * @author Kevin Smith, Boundless, 2017
 */
package org.geowebcache.filter.security;

import java.util.Collection;
import java.util.Objects;
import javax.annotation.Nullable;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.SRS;
import org.geowebcache.layer.TileLayer;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/** Aggregates and applies all SecurityFilter extensions */
public class SecurityDispatcher implements ApplicationContextAware {

    ApplicationContext applicationContext;

    /** @return All registered SecurityFilter extensions */
    public Collection<SecurityFilter> getFilters() {
        return GeoWebCacheExtensions.extensions(SecurityFilter.class, applicationContext);
    }

    /**
     * Apply security filters to a conveyor.
     *
     * @throws SecurityException if any of the filter throw it
     */
    public void checkSecurity(final ConveyorTile tile) throws SecurityException, GeoWebCacheException {
        final TileLayer layer = tile.getLayer();
        final GridSubset gridSubset = tile.getGridSubset();
        final BoundingBox bounds;
        final SRS srs;
        if (Objects.nonNull(gridSubset)) {
            bounds = gridSubset.boundsFromIndex(tile.getTileIndex());
            srs = gridSubset.getSRS();
        } else {
            bounds = null;
            srs = null;
        }

        checkSecurity(layer, bounds, srs);
    }

    /**
     * Apply all filters to a bounding box within a layer
     *
     * @throws SecurityException if any of the filter throw it
     */
    public void checkSecurity(TileLayer layer, @Nullable BoundingBox extent, @Nullable SRS srs)
            throws SecurityException, GeoWebCacheException {
        if (Objects.isNull(extent) != Objects.isNull(srs)) {
            throw new NullPointerException("Extent and srs must either both be null or both be non-null");
        }
        for (SecurityFilter filter : getFilters()) {
            filter.checkSecurity(layer, extent, srs);
        }
    }

    /**
     * Checks if the current user is authenticated and is the administrator. Will return true if there are no active
     * security filters.
     */
    public boolean isAdmin() {
        return getFilters().stream().allMatch(SecurityFilter::isAdmin);
    }

    @Override
    public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /** Returns true if there are any active security filters. */
    public boolean isSecurityEnabled() {
        return !getFilters().isEmpty();
    }
}
