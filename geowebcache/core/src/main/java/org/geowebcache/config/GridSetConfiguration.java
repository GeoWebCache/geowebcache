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

import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.geowebcache.grid.GridSet;

/** Interface for managing {@link GridSet} configuration elements */
public interface GridSetConfiguration extends BaseConfiguration {

    /**
     * Get a GridSet by name
     *
     * @throws NoSuchElementException if the named gridset is not available.
     */
    Optional<GridSet> getGridSet(final String name);

    /** Get all the gridsets provided by this configuration */
    Collection<GridSet> getGridSets();

    /** Get all the gridsets provided by this configuration */
    default Set<String> getGridSetNames() {
        return getGridSets().stream().map(GridSet::getName).collect(Collectors.toSet());
    }

    /**
     * Adds a new {@link GridSet} to the configuration
     *
     * @param gridSet the gridset to add
     * @throws IllegalArgumentException if this configuration is not capable of saving the specific type of gridset
     *     given, or if any required piece of information is missing or invalid in the gridset (for example, a missing
     *     or duplicated name or id, etc).
     */
    void addGridSet(final GridSet gridSet) throws UnsupportedOperationException, IllegalArgumentException;

    /**
     * Removes an existing gridset from the configuration
     *
     * @throws NoSuchElementException If there is no existing gridset by that name.
     * @throws UnsupportedOperationException if removing this gridset is not supported
     */
    void removeGridSet(String gridSetName) throws NoSuchElementException, UnsupportedOperationException;

    /**
     * Changes a gridset
     *
     * @param gridSet The modified gridset. Should have the same name as an existing gridset.
     * @throws IllegalArgumentException if this configuration is not able to save the specific type of gridset given, or
     *     if any required piece of information is missing or invalid in the gridset (for example, a missing name or id,
     *     etc).
     * @throws NoSuchElementException If there is no existing gridset by that name.
     * @throws UnsupportedOperationException if removing this gridset is not supported
     */
    void modifyGridSet(final GridSet gridSet)
            throws NoSuchElementException, IllegalArgumentException, UnsupportedOperationException;

    /**
     * Renames an existing {@link GridSet}
     *
     * @param oldName the name of the existing gridset
     * @param newName the name to rename the gridset to.
     * @throws IllegalArgumentException if the new name is in use by another gridset
     * @throws NoSuchElementException the old name is not present
     * @throws UnsupportedOperationException if renaming in not supported
     */
    void renameGridSet(final String oldName, final String newName)
            throws NoSuchElementException, IllegalArgumentException, UnsupportedOperationException;

    boolean canSave(GridSet gridset);
}
