package org.geowebcache.config;

import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

import org.geowebcache.grid.GridSet;

public interface GridSetConfiguration extends BaseConfiguration {
    
    
    /**
     * Get a GridSet by name
     * @param name
     * @return
     * @throw NoSuchElementException if the named gridset is not available.
     */
    GridSet getGridSet(final String name) throws NoSuchElementException;
    
    /**
     * Get all the gridsets provided by this configuration
     */
    Collection<GridSet> getGridSets();
    
    /**
     * Get all the gridsets provided by this configuration
     */
    default Set<String> getGridSetNames() {
        return getGridSets().stream()
                    .map(GridSet::getName)
                    .collect(Collectors.toSet());
    }
    
    /**
     * Adds a new {@link GridSet} to the configuration
     * @param gridSet the gridset to add
     * @throws IllegalArgumentException
     *         if this configuration is not capable of saving the specific type of gridset given, or
     *         if any required piece of information is missing or invalid in the gridset (for
     *         example, a missing or duplicated name or id, etc).
     */
    void addGridSet(final GridSet gridSet) throws UnsupportedOperationException, IllegalArgumentException;

    /**
     * Removes an existing gridset from the configuration
     * @param gridSetName
     * @throws NoSuchElementException If there is no existing gridset by that name.
     * @throws UnsupportedOperationException if removing this gridset is not supported
     */
    void removeGridSet(String gridSetName) throws NoSuchElementException, UnsupportedOperationException;
    
    /**
     * Changes a gridset
     * @param gridSet The modified gridset. Should have the same name as an existing gridset. 
     * @throws IllegalArgumentException
     *         if this configuration is not able to save the specific type of gridset given, or
     *         if any required piece of information is missing or invalid in the gridset (for
     *         example, a missing name or id, etc).  
     *         
     * @throws NoSuchElementException If there is no existing gridset by that name.
     * @throws UnsupportedOperationException if removing this gridset is not supported
     */
    void modifyGridSet(final GridSet gridSet) throws NoSuchElementException, IllegalArgumentException, UnsupportedOperationException;
    
    /**
     * Adds a new {@link GridSet} to the configuration
     * @param gridSet the gridset to add
     * @throws IllegalArgumentException if the new name is in use by another gridset
     * @throws NoSuchElementException the old name is not present
     * @throws UnsupportedOperationException if renaming in not supported
     */
    void renameGridSet(final String oldName, final String newName) throws NoSuchElementException, IllegalArgumentException, UnsupportedOperationException;
    
    boolean canSave(GridSet gridset);
}
