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
package org.geowebcache.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.config.BlobStoreConfiguration;
import org.geowebcache.config.BlobStoreConfigurationListener;
import org.geowebcache.config.BlobStoreInfo;
import org.geowebcache.config.ServerConfiguration;
import org.geowebcache.config.meta.ServiceInformation;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.util.CompositeIterable;
import org.springframework.util.Assert;

/**
 * Serves {@link BlobStoreInfo}s from the {@link BlobStoreConfiguration}s
 *
 * <p>Not to be confused with {@link CompositeBlobStore}.
 */
public class BlobStoreAggregator {

    private List<BlobStoreConfiguration> configs;
    private static Logger log = Logging.getLogger(BlobStoreAggregator.class.getName());
    private ServiceInformation serviceInformation;
    private TileLayerDispatcher layers;

    /** Used to delegate calls to {@link BlobStoreConfiguration} objects */
    public BlobStoreAggregator(List<BlobStoreConfiguration> configs, TileLayerDispatcher layers) {
        this.configs = configs == null ? new ArrayList<>() : configs;
        this.layers = layers;
        initialize();
    }

    /** Used to delegate calls to {@link BlobStoreConfiguration} objects */
    public BlobStoreAggregator() {
        reInit();
    }

    /**
     * Add a new {@link BlobStoreConfiguration} to the BlobStoreAggregator configs configuration list.
     *
     * @param config a new {@link BlobStoreConfiguration} to add to the configs list
     */
    public void addConfiguration(BlobStoreConfiguration config) {
        initialize(config);
        List<BlobStoreConfiguration> newList = new ArrayList<>(getConfigs());
        newList.add(config);
        this.configs = newList;
    }

    /**
     * Indicates if this configurations contains a {@link BlobStoreInfo) identified by a given name.
     *
     * @param blobStoreInfoName the name of a {@link BlobStoreInfo} for which existence is desired.
     *
     * @return True if a {@link BlobStoreInfo} currently exists with the unique name provided, false otherwise.
     */
    public boolean blobStoreExists(final String blobStoreInfoName) {
        for (BlobStoreConfiguration configuration : getConfigs()) {
            Optional<BlobStoreInfo> blob = configuration.getBlobStore(blobStoreInfoName);
            if (blob.isPresent()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the blob store info named after the {@code blobStoreName} parameter.
     *
     * @throws GeoWebCacheException if no such blob store info exists
     */
    public BlobStoreInfo getBlobStore(final String blobStoreName) throws GeoWebCacheException {
        Objects.requireNonNull(blobStoreName, "blobStoreName is null");

        return getConfigs().stream()
                .map(c -> c.getBlobStore(blobStoreName))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseThrow(() -> new GeoWebCacheException("Thread "
                        + Thread.currentThread().getName()
                        + " Unknown blob store "
                        + blobStoreName
                        + ". Check the logfiles,"
                        + " it may not have loaded properly."));
    }

    /**
     * * Reinitialization is tricky, because we can't really just lock all the blobstores, because this would cause
     * people to queue on something that we may not want to exist post reinit.
     *
     * <p>So we'll just set the current blobstore set free, ready for garbage collection, and generate a new one.
     */
    public void reInit() {
        List<BlobStoreConfiguration> extensions = GeoWebCacheExtensions.extensions(BlobStoreConfiguration.class);
        this.configs = new ArrayList<>(extensions);
        this.layers = GeoWebCacheExtensions.bean(TileLayerDispatcher.class);
        initialize();
    }
    /**
     * Retrieves the number of {@link BlobStoreInfo}s in the blobstore configuration.
     *
     * @return The number of {@link BlobStoreInfo}s currently configured.
     */
    public int getBlobStoreCount() {
        int count = 0;
        for (BlobStoreConfiguration configuration : getConfigs()) {
            count += configuration.getBlobStoreCount();
        }
        return count;
    }

    /**
     * Retrieve a list of all {@link BlobStoreInfo} names for each configuration.
     *
     * @return A list of names that can be used to identify each of the {@link BlobStoreInfo}s currently configured.
     */
    public List<String> getBlobStoreNames() {
        List<String> names = new ArrayList<>();
        for (BlobStoreConfiguration configuration : getConfigs()) {
            names.addAll(configuration.getBlobStoreNames());
        }
        return names;
    }

    /**
     * Returns a list of all the blob stores. The consumer may still have to initialize each blob stores!
     *
     * <p>Modifications to the returned blob stores do not change the internal list of blob stores, but blob stores ARE
     * mutable.
     *
     * @return a list view of this blob store aggregators's internal blob stores
     */
    public Iterable<BlobStoreInfo> getBlobStores() {
        List<Iterable<BlobStoreInfo>> perConfigBlobStores =
                new ArrayList<>(getConfigs().size());

        for (BlobStoreConfiguration config : getConfigs()) {
            perConfigBlobStores.add(config.getBlobStores());
        }

        return new CompositeIterable<>(perConfigBlobStores);
    }

    private void initialize() {
        log.fine("Thread initBlobStore(), initializing");

        for (BlobStoreConfiguration config : getConfigs()) {
            initialize(config);
        }
    }

    private int initialize(BlobStoreConfiguration config) {
        if (config == null) {
            throw new IllegalStateException("BlobStoreConfiguration got a null GWC configuration object");
        }

        String configIdent;
        try {
            configIdent = config.getIdentifier();
        } catch (Exception gwce) {
            log.log(Level.SEVERE, "Error obtaining identify from BlobStoreConfiguration " + config, gwce);
            return 0;
        }

        if (configIdent == null) {
            log.warning("Got a GWC configuration with no identity, ignoring it:" + config);
            return 0;
        }

        int blobStoreCount = config.getBlobStoreCount();

        if (blobStoreCount <= 0) {
            log.config("BlobStoreConfiguration " + config.getIdentifier() + " contained no blob store infos.");
        }

        // Check whether there is any general service information
        if (this.serviceInformation == null && config instanceof ServerConfiguration configuration) {
            log.fine("Reading service information.");
            this.serviceInformation = configuration.getServiceInformation();
        }
        return blobStoreCount;
    }

    /**
     * Service information such as you or your company's details that you want provided in capabilities documents.
     *
     * @return ServiceInformation
     */
    public ServiceInformation getServiceInformation() {
        return this.serviceInformation;
    }

    /** @see org.springframework.beans.factory.DisposableBean#destroy() */
    public void destroy() throws Exception {
        //
    }

    /**
     * Finds out which {@link BlobStoreConfiguration} contains the given blob store, removes it, and saves the
     * configuration.
     *
     * @param blobStoreName the name of the blob store to remove
     */
    public synchronized void removeBlobStore(final String blobStoreName) throws IllegalArgumentException {
        for (BlobStoreConfiguration config : getConfigs()) {
            if (config.containsBlobStore(blobStoreName)) {
                config.removeBlobStore(blobStoreName);
                return;
            }
        }
        throw new NoSuchElementException("No configuration found containing blob store " + blobStoreName);
    }
    /**
     * Adds a blob store and returns the {@link BlobStoreConfiguration} to which the blob stores was added.
     *
     * @param bs the blob store to add
     * @throws IllegalArgumentException if the given blob store can't be added to any configuration managed by this blob
     *     store aggregator.
     */
    public synchronized void addBlobStore(final BlobStoreInfo bs) throws IllegalArgumentException {
        for (BlobStoreConfiguration c : getConfigs()) {
            if (c.canSave(bs)) {
                c.addBlobStore(bs);
                return;
            }
        }
        throw new IllegalArgumentException("No configuration found capable of saving " + bs);
    }

    /**
     * Renames an existing blob store.
     *
     * @param oldName The name of the existing blob store
     * @param newName The name to rename the blob store to
     */
    public synchronized void renameBlobStore(final String oldName, final String newName)
            throws NoSuchElementException, IllegalArgumentException {
        BlobStoreConfiguration config = getConfiguration(oldName);
        config.renameBlobStore(oldName, newName);

        // update layers
        for (TileLayer layer : layers.getLayerList()) {
            if (oldName.equals(layer.getBlobStoreId())) {
                layer.setBlobStoreId(newName);
                layers.modify(layer);
            }
        }
    }

    /**
     * Replaces the given blob store and returns the Blob Store's configuration, saving the configuration.
     *
     * @param bs The blob store to modify
     */
    public synchronized void modifyBlobStore(final BlobStoreInfo bs) throws IllegalArgumentException {
        BlobStoreConfiguration config = getConfiguration(bs);
        // TODO: this won't work with GetCapabilitiesConfiguration
        config.modifyBlobStore(bs);
    }

    /**
     * Returns the BlobStoreConfiguration for the given BlobStoreInfo, if found in list of blob store configurations.
     *
     * @param bs BlobStoreInfo for this configuration
     * @return The BlobStoreConfiguration for BlobStoreInfo
     */
    public BlobStoreConfiguration getConfiguration(BlobStoreInfo bs) throws IllegalArgumentException {
        Assert.notNull(bs, "blobStore is null");
        return getConfiguration(bs.getName());
    }

    /**
     * Returns the BlobStoreConfiguration for the given Blob Store Name, if found in list of blob store configurations.
     */
    public BlobStoreConfiguration getConfiguration(final String blobStoreName) throws IllegalArgumentException {
        Assert.notNull(blobStoreName, "blobStoreName is null");
        for (BlobStoreConfiguration c : getConfigs()) {
            if (c.containsBlobStore(blobStoreName)) {
                return c;
            }
        }
        throw new IllegalArgumentException("No configuration found containing blob store " + blobStoreName);
    }

    /**
     * Adds a {@link BlobStoreConfigurationListener} to each {@link BlobStoreConfiguration} tracked by this aggregator
     *
     * @param listener the listener
     */
    public void addListener(BlobStoreConfigurationListener listener) {
        for (BlobStoreConfiguration c : getConfigs()) {
            c.addBlobStoreListener(listener);
        }
    }

    /**
     * Removes a {@link BlobStoreConfigurationListener} from each {@link BlobStoreConfiguration} tracked by this
     * aggregator
     *
     * @param listener the listener
     */
    public void removeListener(BlobStoreConfigurationListener listener) {
        for (BlobStoreConfiguration c : getConfigs()) {
            c.removeBlobStoreListener(listener);
        }
    }

    /** Get a list of all the configurations that are being aggregated. */
    protected List<BlobStoreConfiguration> getConfigs() {
        return configs;
    }
}
