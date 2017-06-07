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
 * @author Stuart Adam, Ordnance Survey, Copyright 2017
 */
package org.geowebcache.nested;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.config.BlobStoreConfig;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.StorageException;

/**
 * Plain old java object representing the configuration for a nested blob store.
 */
public class NestedBlobStoreConfig extends BlobStoreConfig {

    static Log log = LogFactory.getLog(NestedBlobStoreConfig.class);

    private static final long serialVersionUID = 9072751143836460389L;

    public void setFrontStoreConfig(BlobStoreConfig frontStore) {
        this.frontStoreConfig = frontStore;
    }

    public void setBackingStoreConfig(BlobStoreConfig backingStore) {
        this.backingStoreConfig = backingStore;
    }

    private BlobStoreConfig frontStoreConfig;

    private BlobStoreConfig backingStoreConfig;

    private BlobStore frontStore;

    private BlobStore backingStore;

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public BlobStore createInstance(TileLayerDispatcher layers, LockProvider lockProvider)
            throws StorageException {

        checkNotNull(layers);
        checkState(getId() != null);
        checkState(isEnabled(),
                "Can't call NestedBlobStoreConfig.createInstance() as blob store is not enabled");
        backingStore = backingStoreConfig.createInstance(layers, lockProvider);
        frontStore = frontStoreConfig.createInstance(layers, lockProvider);
        BlobStore store = new NestedBlobStore(this);
        return store;
    }

    @Override
    public String getLocation() {
        return "Composite";
    }

    public BlobStore getFrontStore() {
        return frontStore;
    }

    public BlobStore getBackingStore() {
        return backingStore;
    }
}
