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
 * @author Gabriel Roldan, Boundless Spatial Inc, Copyright 2015
 */
package org.geowebcache.config;

import java.io.Serial;
import java.io.Serializable;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.BlobStoreAggregator;
import org.geowebcache.storage.StorageException;

/**
 * Base class for configuration and factory of concrete {@link BlobStore} implementations.
 *
 * <p>Each realization of {@link BlobStore} should have a matching {@link BlobStoreInfo} subclass that acts both as
 * configuration and {@link #createInstance(TileLayerDispatcher, LockProvider)} factory}.
 *
 * <p>Instances of this concrete subclasses of this class are meant to be obtained from
 * {@link BlobStoreAggregator#getBlobStores()}.
 *
 * <p>When a blob store is defined in a module other than core, it is advisable that whatever {@code XStream}
 * configuration needed for correct parsing and encoding of the configuration object is contributed through an
 * {@link XMLConfigurationProvider}, such as class to xml element name aliasing, attribute mappings, etc.
 *
 * @since 1.8
 * @see FileBlobStoreInfo
 */
public abstract class BlobStoreInfo implements Serializable, Cloneable, Info {

    @Serial
    private static final long serialVersionUID = 1L;

    private String name;

    private boolean enabled;

    private boolean _default;

    protected BlobStoreInfo() {
        //
    }

    public BlobStoreInfo(String name) {
        this.name = name;
    }

    /**
     * Returns this {@link Info Info}s name. For now, this just returns this BlobStoreInfo's ID.
     *
     * @return A String representing the name of this Info implementation.
     */
    @Override
    public String getName() {
        return name;
    }

    /** @return the unique identifier for the blob store; which {@link TileLayer#getBlobStoreId()} refers to. */
    public String getId() {
        return getName();
    }

    /**
     * Set this BlobStoreIngo's unique name.
     *
     * @param name The unique name to set.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Set this BlobStoreInfo's unique id.
     *
     * @param id The unique id to set.
     */
    void setId(String id) {
        setName(id);
    }

    /** @return whether the blob store is enabled ({@code true}) or not. */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether the blob store is enabled ({@code true}) or not.
     *
     * @param enabled True if this BlobStoreInfo should be enabled, false if not.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return whether the blob store defined by these settings is the default one (i.e. the one used when
     *     {@code TileLayer#getBlobStoreId() == null}, and hence used to preserve backwards compatibility).
     */
    public boolean isDefault() {
        return _default;
    }

    /**
     * Sets whether the blob store defined by these settings is the default one (i.e. the one used when
     * {@code TileLayer#getBlobStoreId() == null}, and hence used to preserve backwards compatibility).
     *
     * @param def True if this BlobStoreInfo should be the default, false otherwise.
     */
    public void setDefault(boolean def) {
        this._default = def;
    }

    @Override
    public abstract String toString();

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    /**
     * Factory method for this class of blobstore, configured as per this configuration object properties.
     *
     * <p>May only be called if {@link #isEnabled() == true}.
     *
     * @return A BlobStore implementation.
     * @throws StorageException if the blob store can't be created with this configuration settings
     * @throws IllegalStateException if {@link #isEnabled() isEnabled() == false} or {@link #getId() getId() == null}
     */
    public abstract BlobStore createInstance(TileLayerDispatcher layers, LockProvider lockProvider)
            throws StorageException;

    /**
     * A string description of the location used for storage such as a URI or file path
     *
     * @return String representation of this BlobStoreInfo's location.
     */
    public abstract String getLocation();

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (_default ? 1231 : 1237);
        result = prime * result + (enabled ? 1231 : 1237);
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        BlobStoreInfo other = (BlobStoreInfo) obj;
        if (_default != other._default) return false;
        if (enabled != other.enabled) return false;
        if (name == null) {
            if (other.name != null) return false;
        } else if (!name.equals(other.name)) return false;
        return true;
    }
}
