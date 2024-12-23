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
 * @author Kevin Smith, Boundless, 2018
 */
package org.geowebcache.blobstore.file;

import java.io.File;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.blobstore.file.FileBlobStore;

/** Tests that FileBlobStore checks the provided directory for existing data before using it. */
public class FileBlobStorePersistenceSuitabilityTest extends FileBasedBlobStoreSuitabilityTest {

    @Override
    public BlobStore create(Object dir) throws Exception {
        return new FileBlobStore(((File) dir).getAbsolutePath());
    }
}
