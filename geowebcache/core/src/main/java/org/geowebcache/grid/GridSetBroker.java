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
 * @author Arne Kepp, OpenGeo, Copyright 2009
 */
package org.geowebcache.grid;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.config.ConfigurationAggregator;
import org.geowebcache.config.DefaultGridsets;
import org.geowebcache.config.GridSetConfiguration;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.layer.TileLayerDispatcher;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Exposes {@link GridSet}s from all {@link GridSetConfiguration}s
 */
public class GridSetBroker implements ConfigurationAggregator<GridSetConfiguration>, ApplicationContextAware, InitializingBean {
    private static Log log = LogFactory.getLog(GridSetBroker.class);
    
    private List<GridSetConfiguration> configurations;

    private DefaultGridsets defaults;

    private ApplicationContext applicationContext;

    public GridSetBroker() {
    }
    
    @Deprecated // use GridSetBroker(Collections.singletonList(new DefaultGridset(useEPSG900913, boolean useGWC11xNames)))
    public GridSetBroker(boolean useEPSG900913, boolean useGWC11xNames) {
        configurations = new LinkedList<>();
        defaults = new DefaultGridsets(useEPSG900913, useGWC11xNames);
        configurations.add(defaults);
    }
    
    public GridSetBroker(List<GridSetConfiguration> configurations) {
        this.configurations = configurations;
        defaults = configurations.stream()
            .filter(DefaultGridsets.class::isInstance)
            .findFirst()
            .map(DefaultGridsets.class::cast)
            .get();
    }

    public void afterPropertiesSet() {
        getConfigurations();
    }
    
    public @Nullable GridSet get(String gridSetId) {
        return getGridSet(gridSetId).orElse(null);
    }

    protected Optional<GridSet> getGridSet(String name) {
        for (GridSetConfiguration c : getConfigurations()) {
            Optional<GridSet> gridSet = c.getGridSet(name);
            if (gridSet.isPresent()) {
                GridSet set = gridSet.get();
                return Optional.of(set);
            }
        }
        return Optional.empty();
    }

    /**
     * @return the names of the gridsets that are internally defined
     */
    public Set<String> getEmbeddedNames() {
        return defaults.getGridSetNames();
    }

    public Set<String> getNames() {
        return getGridSetNames();
    }
    
    public Set<String> getGridSetNames() {
        return getConfigurations().stream()
                .map(GridSetConfiguration::getGridSetNames)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    public Collection<GridSet> getGridSets() {
        return getConfigurations().stream()
                .map(GridSetConfiguration::getGridSets)
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(
                        GridSet::getName, 
                        g->g, 
                        (g1, g2)->g1, // Prefer the first one 
                        HashMap::new))
                .values();
    }

    public synchronized void put(GridSet gridSet) {
        remove(gridSet.getName());
        addGridSet(gridSet);
    }
    
    public void addGridSet(GridSet gridSet) {
        log.debug("Adding " + gridSet.getName());
        getConfigurations().stream()
            .filter(c->c.canSave(gridSet))
            .findFirst()
            .orElseThrow(()-> new UnsupportedOperationException("No Configuration is able to save gridset "+gridSet.getName()))
            .addGridSet(gridSet);
    }

    /**
     * Blindly removes a gridset from this gridset broker.
     * <p>
     * This method doesn't check whether there's any layer referencing the gridset nor removes it
     * from the {@link XMLConfiguration}. For such a thing, check
     * {@link TileLayerDispatcher#removeGridset(String)}, which cascades to this method.
     * </p>
     * 
     * @param gridSetName
     * @return
     */
    public synchronized GridSet remove(final String gridSetName) {
        return getGridSet(gridSetName).map(g -> {
                removeGridSet(gridSetName);
                return g;
            }).orElse(null);
    }

    public synchronized void removeGridSet(final String gridSetName) {
        getConfigurations().stream()
            .filter(c->c.getGridSet(gridSetName).isPresent())
            .forEach(c->{c.removeGridSet(gridSetName);});
    }
    
    public DefaultGridsets getDefaults() {
        if(defaults==null) {
            synchronized(this) {
                if(defaults==null) {
                    try {
                        Iterator<? extends DefaultGridsets> it = 
                                getConfigurations(DefaultGridsets.class).iterator();
                        defaults=it.next();
                        if(it.hasNext()) {
                            log.warn(
                                "GridSetBroker has more than one DefaultGridSets configuration");
                        }
                    } catch (NoSuchElementException ex) {
                        throw new IllegalStateException(
                                "GridSetBroker has no DefaultGridsets configuration", ex);
                    }
                }
            }
        }
        return defaults;
    }
    
    public GridSet getWorldEpsg4326() {
        return getDefaults().worldEpsg4326();
    }

    public GridSet getWorldEpsg3857() {
        return getDefaults().worldEpsg3857();
    }

    
    @SuppressWarnings("unchecked")
    public <GSC extends GridSetConfiguration> List<? extends GSC> getConfigurations(Class<GSC> type) {
        return (List<? extends GSC>) getConfigurations().stream().filter(type::isInstance).collect(Collectors.toList());
    }

    private Collection<GridSetConfiguration> getConfigurations() {
        // We set DefaultGridsets in the constructor, need to account for it.
        if(this.configurations==null || (this.configurations.size() == 1 && this.configurations.get(0) instanceof DefaultGridsets)) {
            synchronized(this) {
                if(this.configurations==null || (this.configurations.size() == 1 && this.configurations.get(0).equals(defaults))) {
                    if(Objects.nonNull(applicationContext)) {
                        configurations = GeoWebCacheExtensions.configurations(GridSetConfiguration.class, applicationContext);
                    } else {
                        log.warn("GridSetBroker.initialize() called without having set application context");
                        configurations = GeoWebCacheExtensions.configurations(GridSetConfiguration.class);
                    }
                    if (defaults != null && !configurations.contains(defaults)) {
                        configurations.add(defaults);
                    }
                }
            }
        }
        return this.configurations;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}