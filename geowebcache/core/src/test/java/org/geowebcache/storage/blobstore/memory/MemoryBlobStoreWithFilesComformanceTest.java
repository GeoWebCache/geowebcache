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
 * @author Kevin Smith, Boundless, 2017
 */
package org.geowebcache.storage.blobstore.memory;

import org.geowebcache.storage.AbstractBlobStoreTest;
import org.geowebcache.storage.blobstore.file.FileBlobStore;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class MemoryBlobStoreWithFilesComformanceTest extends AbstractBlobStoreTest<MemoryBlobStore> {

    @Override
    public void createTestUnit() throws Exception {
        this.store = new MemoryBlobStore();
        this.store.setStore(new FileBlobStore(temp.getRoot().getAbsolutePath()));
    }

    @Before
    public void setEvents() throws Exception {
        this.events = false;
    }

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
}
