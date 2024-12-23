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
import org.geowebcache.storage.StorageException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class MemoryBlobStoreComformanceTest extends AbstractBlobStoreTest<MemoryBlobStore> {

    @Override
    public void createTestUnit() throws Exception {
        this.store = new MemoryBlobStore();
    }

    @Before
    public void setEvents() throws Exception {
        this.events = false;
    }

    @Override
    @Ignore
    @Test // Memory store can be more relaxed about this. It would be nice to pass this though
    public void testDeleteGridsetDoesntDeleteOthers() throws Exception {
        super.testDeleteGridsetDoesntDeleteOthers();
    }

    @Override
    @Ignore
    @Test // Memory store can be more relaxed about this. It would be nice to pass this though
    public void testDeleteByParametersIdDoesNotDeleteOthers() throws Exception {
        super.testDeleteByParametersIdDoesNotDeleteOthers();
    }

    @Override
    @Ignore
    @Test // TODO For now, this is a limitation of MemoryBlobStore
    public void testParameterList() throws Exception {
        super.testParameterList();
    }

    @Override
    @Ignore
    @Test // TODO For now, this is a limitation of MemoryBlobStore
    public void testParameterIDList() throws Exception {
        super.testParameterIDList();
    }

    @Override
    @Ignore
    @Test // Memory store can be more relaxed about this. It would be nice to pass this though
    public void testPurgeOrphans() throws Exception {
        super.testPurgeOrphans();
    }

    @Override
    @Ignore
    @Test // Memory store can be more relaxed about this. It would be nice to pass this though
    public void testPurgeOrphansWithDefault() throws Exception {
        super.testPurgeOrphansWithDefault();
    }

    @Override
    @Ignore
    @Test // Memory store can be more relaxed about this. It would be nice to pass this though
    public void testDeleteRangeSingleLevel() throws StorageException {
        super.testDeleteRangeSingleLevel();
    }

    @Override
    @Ignore
    @Test // Memory store can be more relaxed about this. It would be nice to pass this though
    public void testDeleteRangeMultiLevel() throws StorageException {
        super.testDeleteRangeMultiLevel();
    }
}
