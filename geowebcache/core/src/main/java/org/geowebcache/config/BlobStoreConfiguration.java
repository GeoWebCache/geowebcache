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

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

/** Interface for managing {@link BlobStoreInfo} configuration elements. */
public interface BlobStoreConfiguration extends BaseConfiguration {

    /**
     * Retrieves currently configured BlobStores.
     *
     * @return A List of {@link BlobStoreInfo}s currently configured via backend configuration providers.
     */
    List<BlobStoreInfo> getBlobStores();

    /**
     * Add and persist a {@link BlobStoreInfo} to this configuration.
     *
     * @param info The {@link BlobStoreInfo} configuration to add and persist into the backend configuration provider.
     * @throws IllegalArgumentException If the info is not a valid BlobStoreInfo, or can't be added/persisted for some
     *     reason (for example, an info with the same name already exists).
     */
    void addBlobStore(BlobStoreInfo info) throws IllegalArgumentException;

    /**
     * Remove a {@link BlobStoreInfo} from this configuration.
     *
     * @param name String representation of the id of the BlobStore to remove. This
     * @throws NoSuchElementException If there is no BlobStore config identified by the provided name.
     * @throws IllegalArgumentException If this configuration can't, for some reason, remove the blob store identified
     *     by the supplied name (for example, the configuration is read-only).
     */
    void removeBlobStore(String name) throws NoSuchElementException, IllegalArgumentException;

    /**
     * Modifies a {@link BlobStoreInfo} in this configuration.
     *
     * @param info The {@link BlobStoreInfo} that should be used to update an existing BlobStoreInfo with the same
     *     name/id.
     * @throws NoSuchElementException If there is no BlobStore config identified by the provided name.
     * @throws IllegalArgumentException If this configuration can't, for some reason, modify the blob store identified
     *     by the supplied name (for example, the configuration is read-only).
     */
    void modifyBlobStore(BlobStoreInfo info) throws NoSuchElementException, IllegalArgumentException;

    /**
     * Retrieve a set of {@link BlobStoreInfo} names in this configuration.
     *
     * @return A Set of names that can be used to identify each of the {@link BlobStoreInfo}s currently configured.
     */
    Set<String> getBlobStoreNames();

    /**
     * Retrieves the number of {@link BlobStoreInfo}s in this configuration.
     *
     * @return The number of {@link BlobStoreInfo}s currently configured.
     */
    int getBlobStoreCount();

    /**
     * Retrieves a {@link BlobStoreInfo} from this configuration.
     *
     * @param name The name that identifies the desired {@link BlobStoreInfo}.
     * @return An Optional wrapping the desired {@link BlobStoreInfo}, or Empty if it does not exist.
     */
    Optional<BlobStoreInfo> getBlobStore(String name);

    /**
     * Indicates if this configuration can persist the supplied {@link BlobStoreInfo}.
     *
     * @param info a {@link BlobStoreInfo} to be persisted.
     * @return True if the supplied {@link BlobStoreInfo} can be persisted, false otherwise. Implementations might
     *     return false if, for example, this configuration is read-only.
     */
    boolean canSave(BlobStoreInfo info);

    /**
     * Replaces a {@link BlobStoreInfo}'s name with a new name.
     *
     * @param oldName The name that identifies the {@link BlobStoreInfo} to be renamed.
     * @param newName The name that should replace the current name of the {@link BlobStoreInfo} identified by oldName.
     * @throws NoSuchElementException If there is no BlobStore config identified by the provided oldName.
     * @throws IllegalArgumentException If this configuration can't, for some reason, rename the blob store identified
     *     by the supplied name (for example, newName is null or invalid).
     */
    void renameBlobStore(String oldName, String newName) throws NoSuchElementException, IllegalArgumentException;

    /**
     * Indicates if this configurations contains a {@link BlobStoreInfo) identified by a given name.
     * @param name The unique name of a {@link BlobStoreInfo} for which existence is desired.
     * @return True if a {@link BlobStoreInfo} currently exists with the unique name provided, false otherwise.
     */
    boolean containsBlobStore(String name);

    /** Adds a {@link BlobStoreConfigurationListener} to this configuration. */
    void addBlobStoreListener(BlobStoreConfigurationListener listener);

    /** Removes a {@link BlobStoreConfigurationListener} from this configuration, if it is present. */
    void removeBlobStoreListener(BlobStoreConfigurationListener listener);
}
