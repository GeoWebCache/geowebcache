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
package org.geowebcache.config;

import java.io.Serializable;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.StorageException;

/**
 * Base class for configuration and factory of concrete {@link BlobStore} implementations.
 * <p>
 * Each realization of {@link BlobStore} should have a matching {@link BlobStoreConfig} subclass
 * that acts both as configuration and {@link #createInstance() factory}.
 * <p>
 * Instances of this concrete subclasses of this class are meant to be obtained from
 * {@link XMLConfiguration#getBlobStores()} after parsed by {@code XStream} from the
 * {@code geowebcache.xml} file.
 * <p>
 * When a blob store is defined in a module other than core, it is advisable that whatever
 * {@code XStream} configuration needed for correct parsing and encoding of the configuration object
 * is contributed through an {@link XMLConfigurationProvider}, such as class to xml element name
 * aliasing, attribute mappings, etc.
 * 
 * @since 1.8
 * @see FileBlobStoreConfig
 */
public abstract class BlobStoreConfig implements Serializable, Cloneable {

    private static final long serialVersionUID = 1L;

    private String id;

    private boolean enabled;

    private boolean _default;

    protected BlobStoreConfig() {
        //
    }

    public BlobStoreConfig(String id) {
        this.id = id;
    }

    /**
     * @return the unique identifier for the blob store; which {@link TileLayer#getBlobStoreId()}
     *         refers to.
     */
    public String getId() {
        return id;
    }

    void setId(String id) {
        this.id = id;
    }

    /**
     * @return whether the blob store is enabled ({@code true}) or not.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether the blob store is enabled ({@code true}) or not.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return whether the blob store defined by these settings is the default one (i.e. the one
     *         used when {@code TileLayer#getBlobStoreId() == null}, and hence used to preserve
     *         backwards compatibility).
     */
    public boolean isDefault() {
        return _default;
    }

    /**
     * Sets whether the blob store defined by these settings is the default one (i.e. the one used
     * when {@code TileLayer#getBlobStoreId() == null}, and hence used to preserve backwards
     * compatibility).
     */
    public void setDefault(boolean def) {
        this._default = def;
    }

    @Override
    public abstract String toString();

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
    
    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    /**
     * Factory method for this class of blobstore, configured as per this configuration object
     * properties.
     * <p>
     * May only be called if {@link #isEnabled() == true}.
     * 
     * @param layers
     * @param lockProvider
     * 
     * @throws StorageException if the blob store can't be created with this configuration settings
     * @throws IllegalStateException if {@link #isEnabled() isEnabled() == false} or
     *         {@link #getId() getId() == null}
     */
    public abstract BlobStore createInstance(TileLayerDispatcher layers, LockProvider lockProvider)
            throws StorageException;
    
    /**
     * A string description of the location used for storage such as a URI or file path
     */
    public abstract String getLocation();
}
