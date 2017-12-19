package org.geowebcache.config;

import org.geowebcache.grid.GridSet;

public interface GridSetConfiguration extends BaseConfiguration {

    /**
     * Adds a new {@link GridSet} to the configuration
     * @param gridSet the gridset to add
     * @throws IllegalArgumentException
     *             if this configuration is not able of saving the specific type of gridset given, or
     *             if any required piece of information is missing or invalid in the gridset (for
     *             example, a missing or duplicated name or id, etc).
     */
    void addGridSet(final GridSet gridSet);

    /**
     * Removes an existing gridset from the configuration
     * @param gridSetName
     * @return {@code true} if the gridset was removed, {@code false} if no such gridset exists
     */
    boolean removeGridSet(String gridSetName);
}
