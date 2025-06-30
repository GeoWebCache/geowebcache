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
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 */
package org.geowebcache.layer;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.config.BaseConfiguration;
import org.geowebcache.config.ConfigurationAggregator;
import org.geowebcache.config.ServerConfiguration;
import org.geowebcache.config.TileLayerConfiguration;
import org.geowebcache.config.meta.ServiceInformation;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.util.CompositeIterable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.Assert;

/** Serves tile layers from the {@link TileLayerConfiguration}s available in the application context. */
public class TileLayerDispatcher
        implements DisposableBean,
                InitializingBean,
                ApplicationContextAware,
                ConfigurationAggregator<TileLayerConfiguration> {

    TileLayerDispatcherFilter tileLayerDispatcherFilter;

    private List<TileLayerConfiguration> configs;

    private GridSetBroker gridSetBroker;

    private ServiceInformation serviceInformation;

    private ApplicationContext applicationContext;

    /**
     * Used for testing only, in production use {@link #TileLayerDispatcher(GridSetBroker, TileLayerDispatcherFilter)}
     * instead, configurations are loaded from the application context, the {@code configs} parameter will be
     * overwritten
     */
    public TileLayerDispatcher(
            GridSetBroker gridSetBroker,
            List<TileLayerConfiguration> configs,
            TileLayerDispatcherFilter tileLayerDispatcherFilter) {
        this.gridSetBroker = gridSetBroker;
        this.configs = configs == null ? new ArrayList<>() : configs;
        this.tileLayerDispatcherFilter = tileLayerDispatcherFilter;
    }

    public TileLayerDispatcher(GridSetBroker gridSetBroker, TileLayerDispatcherFilter tileLayerDispatcherFilter) {
        this.gridSetBroker = gridSetBroker;
        this.tileLayerDispatcherFilter = tileLayerDispatcherFilter;
    }

    public boolean layerExists(final String layerName) {
        for (TileLayerConfiguration configuration : configs) {
            Optional<TileLayer> layer = configuration.getLayer(layerName);
            if (layer.isPresent()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the layer named after the {@code layerName} parameter.
     *
     * @throws GeoWebCacheException if no such layer exists
     */
    public TileLayer getTileLayer(final String layerName) throws GeoWebCacheException {
        Preconditions.checkNotNull(layerName, "layerName is null");

        for (TileLayerConfiguration configuration : configs) {
            Optional<TileLayer> layer = configuration.getLayer(layerName);
            if (layer.isPresent()) {
                return layer.get();
            }
        }
        throw new GeoWebCacheException("Thread "
                + Thread.currentThread().getName()
                + " Unknown layer "
                + layerName
                + ". Check the logfiles,"
                + " it may not have loaded properly.");
    }

    public int getLayerCount() {
        int count = 0;
        for (TileLayerConfiguration configuration : configs) {
            count += configuration.getLayerCount();
        }
        return count;
    }

    public Set<String> getLayerNames() {
        Set<String> names = new HashSet<>();
        for (TileLayerConfiguration configuration : configs) {
            names.addAll(configuration.getLayerNames());
        }
        return names;
    }

    /**
     * Returns a list of all the layers. The consumer may still have to initialize each layer!
     *
     * <p>Modifications to the returned layer do not change the internal list of layers, but layers ARE mutable.
     *
     * @return a list view of this tile layer dispatcher's internal layers
     */
    @SuppressWarnings("unchecked")
    public Iterable<TileLayer> getLayerList() {
        List<Iterable<TileLayer>> perConfigLayers = new ArrayList<>(configs.size());

        for (TileLayerConfiguration config : configs) {
            perConfigLayers.add((Iterable<TileLayer>) config.getLayers());
        }

        Iterable<TileLayer> result = new CompositeIterable<>(perConfigLayers);
        return result;
    }

    /**
     * This is the same as {@link #getLayerList()} filtered based on the tileLayerDispatcherFilter.
     *
     * @return all layers, but filtered based on the tileLayerDispatcherFilter.
     */
    public Iterable<TileLayer> getLayerListFiltered() {
        Iterable<TileLayer> result = getLayerList();
        if (tileLayerDispatcherFilter != null) {
            result = Iterables.filter(result, x -> !tileLayerDispatcherFilter.exclude(x));
        }
        return result;
    }

    public ServiceInformation getServiceInformation() {
        return this.serviceInformation;
    }

    /** @param serviceInformation the serviceInformation to set */
    public void setServiceInformation(ServiceInformation serviceInformation) {
        this.serviceInformation = serviceInformation;
    }

    /** @see org.springframework.beans.factory.DisposableBean#destroy() */
    @Override
    public void destroy() throws Exception {
        //
    }

    /**
     * Finds out which {@link TileLayerConfiguration} contains the given layer, removes it, and saves the configuration.
     *
     * @param layerName the name of the layer to remove
     */
    public synchronized void removeLayer(final String layerName) throws IllegalArgumentException {
        for (TileLayerConfiguration config : configs) {
            if (config.containsLayer(layerName)) {
                config.removeLayer(layerName);
                return;
            }
        }
        throw new NoSuchElementException("No configuration found containing layer " + layerName);
    }

    /**
     * Adds a layer and returns the {@link TileLayerConfiguration} to which the layer was added.
     *
     * @param tl the layer to add
     * @throws IllegalArgumentException if the given tile layer can't be added to any configuration managed by this tile
     *     layer dispatcher.
     */
    public synchronized void addLayer(final TileLayer tl) throws IllegalArgumentException {
        for (TileLayerConfiguration c : configs) {
            if (c.canSave(tl)) {
                c.addLayer(tl);
                return;
            }
        }
        throw new IllegalArgumentException("No configuration found capable of saving " + tl);
    }

    /**
     * Renames an existing layer.
     *
     * @param oldName The name of the existing layer
     * @param newName The name to rename the layer to
     */
    public synchronized void rename(final String oldName, final String newName)
            throws NoSuchElementException, IllegalArgumentException {
        TileLayerConfiguration config = getConfiguration(oldName);
        config.renameLayer(oldName, newName);
    }

    /**
     * Replaces the given layer and returns the Layer's configuration, saving the configuration.
     *
     * @param tl The layer to modify
     */
    public synchronized void modify(final TileLayer tl) throws IllegalArgumentException {
        TileLayerConfiguration config = getConfiguration(tl);
        // TODO: this won't work with GetCapabilitiesConfiguration
        config.modifyLayer(tl);
    }

    public TileLayerConfiguration getConfiguration(TileLayer tl) throws IllegalArgumentException {
        Assert.notNull(tl, "layer is null");
        return getConfiguration(tl.getName());
    }

    public TileLayerConfiguration getConfiguration(final String tileLayerName) throws IllegalArgumentException {
        Assert.notNull(tileLayerName, "tileLayerName is null");
        for (TileLayerConfiguration c : configs) {
            if (c.containsLayer(tileLayerName)) {
                return c;
            }
        }
        throw new IllegalArgumentException("No configuration found containing layer " + tileLayerName);
    }

    public synchronized void addGridSet(final GridSet gridSet) throws IllegalArgumentException, IOException {
        if (null != gridSetBroker.get(gridSet.getName())) {
            throw new IllegalArgumentException("GridSet " + gridSet.getName() + " already exists");
        }
        saveGridSet(gridSet);
    }

    private void saveGridSet(final GridSet gridSet) throws IOException {
        gridSetBroker.addGridSet(gridSet);
    }

    public synchronized void removeGridSet(String gridsetToRemove) {
        if (StreamSupport.stream(getLayerList().spliterator(), true)
                .anyMatch(g -> Objects.nonNull(g.getGridSubset(gridsetToRemove)))) {
            throw new IllegalStateException("Can not remove gridset " + gridsetToRemove + " as it is used by layers");
        }
        gridSetBroker.removeGridSet(gridsetToRemove);
    }

    public synchronized void removeGridSetRecursive(String gridsetToRemove) {
        List<TileLayer> deletedLayers = new ArrayList<>();
        try {
            for (TileLayer tl : getLayerList()) {
                if (Objects.nonNull(tl.getGridSubset(gridsetToRemove))) {
                    this.removeLayer(tl.getName());
                    deletedLayers.add(tl);
                }
            }
        } catch (NoSuchElementException e) {
            IllegalStateException wrappedException = new IllegalStateException(
                    "Layer was found referencing gridset but was missing during recursive delete", e);
            try {
                deletedLayers.forEach(this::addLayer);
            } catch (RuntimeException exceptionOnRestore) {
                wrappedException.addSuppressed(exceptionOnRestore);
            }
            throw wrappedException;
        }

        try {
            gridSetBroker.removeGridSet(gridsetToRemove);
        } catch (RuntimeException exceptionOnRestore) {
            try {
                deletedLayers.forEach(this::addLayer);
            } catch (RuntimeException ex) {
                exceptionOnRestore.addSuppressed(ex);
            }
            throw exceptionOnRestore;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends TileLayerConfiguration> List<? extends T> getConfigurations(Class<T> clazz) {
        if (clazz == TileLayerConfiguration.class) {
            return (List<? extends T>) Collections.unmodifiableList(configs);
        } else {
            return configs.stream().filter(clazz::isInstance).map(clazz::cast).collect(Collectors.toList());
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.configs = GeoWebCacheExtensions.configurations(TileLayerConfiguration.class, applicationContext);

        Map<String, BaseConfiguration> config = applicationContext.getBeansOfType(BaseConfiguration.class);
        if (config != null && !config.isEmpty()) {
            for (Entry<String, BaseConfiguration> e : config.entrySet()) {
                if (ServerConfiguration.class.isAssignableFrom(e.getValue().getClass())) {
                    setServiceInformation(((ServerConfiguration) e.getValue()).getServiceInformation());
                }
            }
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        if (this.applicationContext != null)
            throw new IllegalStateException("Application context has already been set");
        Objects.requireNonNull(applicationContext);
        this.applicationContext = applicationContext;
    }

    /** @deprecated use GeoWebCacheExtensions.reinitializeConfigurations instead */
    @Deprecated
    public void reInit() { // do not know how to get rid of it, it's used in mock testing...
        GeoWebCacheExtensions.reinitialize(this.applicationContext);
    }
}
