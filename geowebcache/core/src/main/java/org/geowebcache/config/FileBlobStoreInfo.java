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

import static com.google.common.base.Preconditions.checkState;

import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.BlobStoreListener;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.blobstore.file.FileBlobStore;

/**
 * Configration and factory for {@link FileBlobStore}.
 * 
 * @since 1.8
 * @see BlobStoreInfo
 */
public class FileBlobStoreInfo extends BlobStoreInfo {

    private static final long serialVersionUID = -6470560864068854508L;

    private String baseDirectory;

    private int fileSystemBlockSize;

    public FileBlobStoreInfo() {
        super();
    }

    public FileBlobStoreInfo(String id) {
        super(id);
    }

    public String getBaseDirectory() {
        return baseDirectory;
    }

    public void setBaseDirectory(String baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    /**
     * A positive integer representing the file system block size (usually 4096, 8292, or 16384,
     * depending on the <a href="http://en.wikipedia.org/wiki/File_system">file system</a>) of the
     * file system where the {@link #getBaseDirectory() base directory} resides.
     * <p>
     * This value is used to pad the size of tile files to the actual size of the file on disk
     * before notifying {@link BlobStoreListener#tileDeleted}, {@link BlobStoreListener#tileStored},
     * or {@link BlobStoreListener#tileUpdated}.
     * 
     * @return the block size of the file system where the {@link #getBaseDirectory() base
     *         directory} resides.
     */
    public int getFileSystemBlockSize() {
        return fileSystemBlockSize;
    }

    /**
     * Sets the block size of the file system where the {@link #getBaseDirectory() base directory}
     * resides.
     */

    public void setFileSystemBlockSize(int fileSystemBlockSize) {
        this.fileSystemBlockSize = fileSystemBlockSize;
    }

    @Override
    public String toString() {
        return new StringBuilder("FileBlobStore[id:").append(getName()).append(", enabled:")
                .append(isEnabled()).append(", baseDirectory:").append(baseDirectory)
                .append(", fileSystemBlockSize:").append(fileSystemBlockSize).append(']').toString();
    }

    @Override
    public BlobStore createInstance(TileLayerDispatcher layers, LockProvider lockProvider) throws StorageException {
        checkState(getName() != null, "id not set");
        checkState(isEnabled(),
                "Can't call FileBlobStoreConfig.createInstance() is blob store is not enabled");
        checkState(baseDirectory != null, "baseDirectory not provided");
        checkState(fileSystemBlockSize >= 0, "fileSystemBlockSize must be a positive integer: %s",
                fileSystemBlockSize);
        FileBlobStore fileBlobStore = new FileBlobStore(baseDirectory);
        if(fileSystemBlockSize > 0){
            fileBlobStore.setBlockSize(fileSystemBlockSize);
        }
        return fileBlobStore;
    }

    @Override
    public String getLocation() {
        return getBaseDirectory();
    }

}
