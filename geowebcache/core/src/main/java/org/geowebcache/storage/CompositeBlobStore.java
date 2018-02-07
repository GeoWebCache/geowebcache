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
 * @author Gabriel Roldan, Boundless Spatial Inc, Copyright 2015
 */

package org.geowebcache.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.BlobStoreInfo;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.config.FileBlobStoreInfo;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.storage.blobstore.file.FileBlobStore;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;

/**
 * A composite {@link BlobStore} that multiplexes tile operations to configured blobstores based on
 * {@link BlobStoreInfo#getId() blobstore id} and TileLayers {@link TileLayer#getBlobStoreId()
 * BlobStoreId} matches.
 * <p>
 * Tile operations for {@link TileLayer}s with no configured {@link TileLayer#getBlobStoreId()
 * BlobStoreId} (i.e. {@code null}) are redirected to the "default blob store", which is either
 * <b>the</b> one configured as the {@link BlobStoreInfo#isDefault() default} one, or a
 * {@link FileBlobStore} following the {@link DefaultStorageFinder#getDefaultPath() legacy cache
 * directory lookup mechanism}, if no blobstore is set as default.
 * <p>
 * At construction time, {@link BlobStore} instances will be created for all
 * {@link BlobStoreInfo#isEnabled() enabled} configs.
 * 
 * @since 1.8
 */
public class CompositeBlobStore implements BlobStore {

    private static Log log = LogFactory.getLog(CompositeBlobStore.class);

    public static final String DEFAULT_STORE_DEFAULT_ID = "_DEFAULT_STORE_";

    @VisibleForTesting
    Map<String, LiveStore> blobStores = new ConcurrentHashMap<>();

    private TileLayerDispatcher layers;

    private DefaultStorageFinder defaultStorageFinder;

    private LockProvider lockProvider;

    private final ReadWriteLock configLock = new ReentrantReadWriteLock();

    private final BlobStoreListenerList listeners = new BlobStoreListenerList();

    @VisibleForTesting
    static final class LiveStore {
        BlobStoreInfo config;

        BlobStore liveInstance;

        public LiveStore(BlobStoreInfo config, @Nullable BlobStore store) {
            Preconditions.checkArgument(config.isEnabled() == (store != null));
            this.config = config;
            this.liveInstance = store;
        }
    }

    /**
     * Create a composite blob store that multiplexes tile operations to configured blobstores based
     * on {@link BlobStoreInfo#getId() blobstore id} and TileLayers
     * {@link TileLayer#getBlobStoreId() BlobStoreId} matches.
     * 
     * @param layers used to get the layer's {@link TileLayer#getBlobStoreId() blobstore id}
     * @param defaultStorageFinder to resolve the location of the cache directory for the legacy
     *        blob store when no {@link BlobStoreInfo#isDefault() default blob store} is given
     * @param configuration the configuration as read from {@code geowebcache.xml} containing the
     *        configured {@link XMLConfiguration#getBlobStores() blob stores}
     * @throws ConfigurationException if there's a configuration error like a store confing having
     *         no id, or two store configs having the same id, or more than one store config being
     *         marked as the default one, or the default store is not
     *         {@link BlobStoreInfo#isEnabled() enabled}
     * @throws StorageException if the live {@code BlobStore} instance can't be
     *         {@link BlobStoreInfo#createInstance() created} of an enabled
     *         {@link BlobStoreInfo}
     */
    public CompositeBlobStore(TileLayerDispatcher layers,
            DefaultStorageFinder defaultStorageFinder, XMLConfiguration configuration)
            throws StorageException, ConfigurationException {

        this.layers = layers;
        this.defaultStorageFinder = defaultStorageFinder;
        this.lockProvider = configuration.getLockProvider();
        this.blobStores = loadBlobStores(configuration.getBlobStores());
    }

    @Override
    public boolean delete(String layerName) throws StorageException {
        return readFunctionUnsafe(()->store(layerName).delete(layerName));
    }

    @Override
    public boolean deleteByGridsetId(String layerName, String gridSetId) throws StorageException {
        return readFunctionUnsafe(()->store(layerName).deleteByGridsetId(layerName, gridSetId));
    }

    @Override
    public boolean delete(TileObject obj) throws StorageException {
        return readFunctionUnsafe(()->store(obj.getLayerName()).delete(obj));
    }

    @Override
    public boolean delete(TileRange obj) throws StorageException {
        return readFunctionUnsafe(()->store(obj.getLayerName()).delete(obj));
    }

    @Override
    public boolean get(TileObject obj) throws StorageException {
        return readFunctionUnsafe(()->store(obj.getLayerName()).get(obj));
    }

    @Override
    public void put(TileObject obj) throws StorageException {
        readActionUnsafe(()->store(obj.getLayerName()).put(obj));
    }

    @Deprecated
    @Override
    public void clear() throws StorageException {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void destroy() {
        destroy(blobStores);
    }

    private void destroy(Map<String, LiveStore> blobStores) {
        for (LiveStore bs : blobStores.values()) {
            try {
                if (bs.config.isEnabled()) {
                    bs.liveInstance.destroy();
                }
            } catch (Exception e) {
                log.error("Error disposing BlobStore " + bs.config.getName(), e);
            }
        }
        blobStores.clear();
    }

    /**
     * Adds the listener to all enabled blob stores
     */
    @Override
    public void addListener(BlobStoreListener listener) {
        readAction(()->{
            this.listeners.addListener(listener);// save it for later in case setBlobStores is
            // called
            for (LiveStore bs : blobStores.values()) {
                if (bs.config.isEnabled()) {
                    bs.liveInstance.addListener(listener);
                }
            }
        });
    }

    /**
     * Removes the listener from all the enabled blob stores
     */
    @Override
    public boolean removeListener(BlobStoreListener listener) {
        return readFunction(()->{
            this.listeners.removeListener(listener);
            return blobStores.values().stream()
                .filter(bs->bs.config.isEnabled())
                .map(bs->bs.liveInstance.removeListener(listener))
                .collect(Collectors.reducing((x,y)->x||y)) // Don't use anyMatch or findFirst as we don't want it to shortcut
                .orElse(false);
        });
    }
    
    @Override
    public boolean rename(String oldLayerName, String newLayerName) throws StorageException {
        return readFunctionUnsafe(()->{
            for (LiveStore bs : blobStores.values()) {
                BlobStoreInfo config = bs.config;
                if (config.isEnabled()) {
                    if (bs.liveInstance.rename(oldLayerName, newLayerName)) {
                        return true;
                    }
                }
            }
            return false;
        });
    }

    @Override
    public String getLayerMetadata(String layerName, String key) {
        return readFunction(()->store(layerName).getLayerMetadata(layerName, key));
    }

    @Override
    public void putLayerMetadata(String layerName, String key, String value) {
        readAction(()->{
            store(layerName).putLayerMetadata(layerName, key, value);
        });
    }

    @Override
    public boolean layerExists(String layerName) {
        return readFunction(()->blobStores.values().stream()
                .anyMatch(bs->bs.config.isEnabled() && bs.liveInstance.layerExists(layerName)));
    }

    private BlobStore store(String layerId) throws StorageException {

        LiveStore store;
        try {
            store = forLayer(layerId);
        } catch (GeoWebCacheException e) {
            throw new StorageException(e.getMessage(), e);
        }
        if (!store.config.isEnabled()) {
            throw new StorageException("Attempted to use a blob store that's disabled: "
                    + store.config.getName());
        }

        return store.liveInstance;
    }

    /**
     * @throws StorageException if the blobstore is not enabled or does not exist
     * @throws GeoWebCacheException if the layer is not found
     */
    private LiveStore forLayer(String layerName) throws StorageException, GeoWebCacheException {
        TileLayer layer;
        try {
            layer = layers.getTileLayer(layerName);
        } catch (GeoWebCacheException e) {
            throw e;
        }
        String storeId = layer.getBlobStoreId();
        LiveStore store;
        if (null == storeId) {
            store = defaultStore();
        } else {
            store = blobStores.get(storeId);
        }
        if (store == null) {
            throw new StorageException("No BlobStore with id '" + storeId + "' found");
        }
        return store;
    }

    private LiveStore defaultStore() throws StorageException {
        LiveStore store = blobStores.get(CompositeBlobStore.DEFAULT_STORE_DEFAULT_ID);
        if (store == null) {
            throw new StorageException("No default BlobStore has been defined");
        }
        return store;
    }

    public void setBlobStores(Iterable<? extends BlobStoreInfo> configs) throws StorageException,
            ConfigurationException {
        configLock.writeLock().lock();
        try {
            Map<String, LiveStore> newStores = loadBlobStores(configs);
            Map<String, LiveStore> oldStores = this.blobStores;
            this.blobStores = newStores;
            for (LiveStore ls : oldStores.values()) {
                if (ls.liveInstance != null) {
                    ls.liveInstance.destroy();
                }
            }
        } finally {
            configLock.writeLock().unlock();
        }
    }

    /**
     * Loads the blob stores from the list of configuration objects
     * 
     * @param configs the list of blob store configurations
     * @return a mapping of blob store id to {@link LiveStore} containing the configuration itself
     *         and the live instance if the blob store is enabled
     * @throws ConfigurationException if there's a configuration error like a store confing having
     *         no id, or two store configs having the same id, or more than one store config being
     *         marked as the default one, or the default store is not
     *         {@link BlobStoreInfo#isEnabled() enabled}
     * @throws StorageException if the live {@code BlobStore} instance can't be
     *         {@link BlobStoreInfo#createInstance() created} of an enabled
     *         {@link BlobStoreInfo}
     */
    Map<String, LiveStore> loadBlobStores(Iterable<? extends BlobStoreInfo> configs)
            throws StorageException, ConfigurationException {

        Map<String, LiveStore> stores = new HashMap<>();

        BlobStoreInfo defaultStore = null;

        try {
            for (BlobStoreInfo config : configs) {
                final String id = config.getName();
                final boolean enabled = config.isEnabled();
                if (Strings.isNullOrEmpty(id)) {
                    throw new ConfigurationException("No id provided for blob store " + config);
                }
                if (stores.containsKey(id)) {
                    throw new ConfigurationException("Duplicate blob store id: " + id
                            + ". Check your configuration.");
                }
                if (CompositeBlobStore.DEFAULT_STORE_DEFAULT_ID.equals(id)) {
                    throw new ConfigurationException(CompositeBlobStore.DEFAULT_STORE_DEFAULT_ID
                            + " is a reserved identifier, please don't use it in the configuration");
                }

                BlobStore store = null;
                if (enabled) {
                    store = config.createInstance(layers, lockProvider);
                }

                LiveStore liveStore = new LiveStore(config, store);
                stores.put(config.getName(), liveStore);

                if (config.isDefault()) {
                    if (defaultStore == null) {
                        if (!enabled) {
                            throw new ConfigurationException(
                                    "The default blob store can't be disabled: " + config.getName());
                        }

                        defaultStore = config;
                        stores.put(CompositeBlobStore.DEFAULT_STORE_DEFAULT_ID, liveStore);
                    } else {
                        throw new ConfigurationException("Duplicate default blob store: "
                                + defaultStore.getName() + " and " + config.getName());
                    }
                }
            }

            if (!stores.containsKey(CompositeBlobStore.DEFAULT_STORE_DEFAULT_ID)) {

                FileBlobStoreInfo config = new FileBlobStoreInfo();
                config.setEnabled(true);
                config.setDefault(true);
                config.setBaseDirectory(defaultStorageFinder.getDefaultPath());
                BlobStore store;
                store = new FileBlobStore(config.getBaseDirectory());

                stores.put(CompositeBlobStore.DEFAULT_STORE_DEFAULT_ID,
                        new LiveStore(config, store));
            }
        } catch (ConfigurationException | StorageException e) {
            destroy(stores);
            throw e;
        }

        return new ConcurrentHashMap<>(stores);
    }

    @Override
    public boolean deleteByParametersId(String layerName, String parametersId)
            throws StorageException {
        return readFunctionUnsafe(()->store(layerName).deleteByParametersId(layerName, parametersId));
    }
    
    @Override
    public Set<Map<String, String>> getParameters(String layerName) {
        return readFunction(()-> store(layerName).getParameters(layerName));
    }
    
    @Override
    public Set<String> getParameterIds(String layerName) {
        return readFunction(()->store(layerName).getParameterIds(layerName));
    }

    @FunctionalInterface
    static interface StorageAction {
        void run() throws StorageException;
    }
    @FunctionalInterface
    static interface StorageAccessor<T> {
        T get() throws StorageException;
    }
    
    
    protected <T> T readFunctionUnsafe(StorageAccessor<T> function) throws StorageException {
        configLock.readLock().lock();
        try {
            return function.get();
        } finally {
            configLock.readLock().unlock();
        }
    }
    
    protected <T> T readFunction(StorageAccessor<T> function) {
        try {
            return readFunctionUnsafe(function);
        } catch (StorageException e) {
            throw Throwables.propagate(e);
        }
    }
    
    protected void readActionUnsafe(StorageAction function) throws StorageException {
        readFunctionUnsafe((StorageAccessor<Void>)()->{function.run();return null;});
    }
    
    protected void readAction(StorageAction function) {
        readFunction((StorageAccessor<Void>)()->{function.run();return null;});
    }

    public Map<String,Optional<Map<String, String>>> getParametersMapping(String layerName) {
        return readFunction(()->store(layerName).getParametersMapping(layerName));
    }
}
