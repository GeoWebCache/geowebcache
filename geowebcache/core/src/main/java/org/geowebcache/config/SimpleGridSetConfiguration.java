package org.geowebcache.config;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

import org.geowebcache.grid.GridSet;

/**
 * Simple abstract superclass for most {@link GridSetConfiguration} implementations.
 */
public abstract class SimpleGridSetConfiguration implements GridSetConfiguration {
    
    private Map<String, GridSet> gridSets;
    
    public SimpleGridSetConfiguration() {
        gridSets = new HashMap<>();
    }
    
    @Override
    public Optional<GridSet> getGridSet(String name) {
        return Optional.ofNullable(gridSets.get(name)).map(GridSet::new);
    }
    
    @Override
    public Collection<GridSet> getGridSets() {
        return gridSets.values().stream()
                .map(GridSet::new) // Make sure that modifying them doesn't modify the real ones
                .collect(Collectors.toList());
    }
    
    @Override
    public void addGridSet(GridSet gridSet)
            throws UnsupportedOperationException, IllegalArgumentException {
                throw new UnsupportedOperationException();
            }
    
    @Override
    public void removeGridSet(String gridSetName)
            throws NoSuchElementException, UnsupportedOperationException {
                throw new UnsupportedOperationException();
            }
    
    @Override
    public void modifyGridSet(GridSet gridSet)
            throws NoSuchElementException, IllegalArgumentException, UnsupportedOperationException {
                throw new UnsupportedOperationException();
            }
    
    @Override
    public void renameGridSet(String oldName, String newName)
            throws NoSuchElementException, IllegalArgumentException, UnsupportedOperationException {
                throw new UnsupportedOperationException();
            }
    
    @Override
    public boolean canSave(GridSet gridset) {
        return false;
    }
    
    protected void addInternal(GridSet g) {
        gridSets.put(g.getName(), g);
    }
    
    protected void removeInternal(String name) {
        gridSets.remove(name);
    }

    @Override
    public void deinitialize() throws Exception {
        gridSets.clear();
    }
}