/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * <p>Copyright 2018
 */
package org.geowebcache.config;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.verify;
import static org.geowebcache.util.TestUtils.isPresent;
import static org.geowebcache.util.TestUtils.notPresent;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import org.easymock.EasyMock;
import org.geowebcache.storage.UnsuitableStorageException;
import org.junit.Test;

public abstract class BlobStoreConfigurationTest
        extends ConfigurationTest<BlobStoreInfo, BlobStoreConfiguration> {

    @Override
    protected void addInfo(BlobStoreConfiguration config, BlobStoreInfo info) throws Exception {
        config.addBlobStore(info);
    }

    @Override
    protected Optional<BlobStoreInfo> getInfo(BlobStoreConfiguration config, String name)
            throws Exception {
        return config.getBlobStore(name);
    }

    @Override
    protected Collection<? extends BlobStoreInfo> getInfos(BlobStoreConfiguration config)
            throws Exception {
        return config.getBlobStores();
    }

    @Override
    protected Set<String> getInfoNames(BlobStoreConfiguration config) throws Exception {
        return config.getBlobStoreNames();
    }

    @Override
    protected void removeInfo(BlobStoreConfiguration config, String name) throws Exception {
        config.removeBlobStore(name);
    }

    @Override
    protected void renameInfo(BlobStoreConfiguration config, String oldName, String newName)
            throws Exception {
        config.renameBlobStore(oldName, newName);
    }

    @Override
    protected void modifyInfo(BlobStoreConfiguration config, BlobStoreInfo info) throws Exception {
        config.modifyBlobStore(info);
    }

    // We may or may not want to roll back on other exceptions, but it is important to roll back on
    // an UnsuitableStorageException and we should keep these tests even if we add more general
    // ones.
    @Test
    public void testRollBackOnUnsuitableStorageExceptionInAddHandler() throws Exception {
        BlobStoreInfo info = getGoodInfo("test", 1);
        BlobStoreConfigurationListener listener = createMock(BlobStoreConfigurationListener.class);
        listener.handleAddBlobStore(info);
        expectLastCall().andThrow(new UnsuitableStorageException("TEST"));
        EasyMock.replay(listener);
        config.addBlobStoreListener(listener);
        exception.expect(instanceOf(ConfigurationPersistenceException.class));
        exception.expectCause(instanceOf(UnsuitableStorageException.class));
        try {
            config.addBlobStore(info);
        } finally {
            verify(listener);
            assertThat(config.getBlobStore("test"), notPresent());
        }
    }

    @Test
    public void testRollBackOnUnsuitableStorageExceptionInModifyHandler() throws Exception {
        BlobStoreInfo info1 = getGoodInfo("test", 1);
        BlobStoreInfo info2 = getGoodInfo("test", 2);
        BlobStoreConfigurationListener listener = createMock(BlobStoreConfigurationListener.class);
        listener.handleAddBlobStore(info1);
        expectLastCall();
        listener.handleModifyBlobStore(info2);
        expectLastCall().andThrow(new UnsuitableStorageException("TEST"));
        EasyMock.replay(listener);
        config.addBlobStoreListener(listener);
        config.addBlobStore(info1);
        exception.expect(instanceOf(ConfigurationPersistenceException.class));
        exception.expectCause(instanceOf(UnsuitableStorageException.class));
        try {
            config.modifyBlobStore(info2);
        } finally {
            verify(listener);
            assertThat(config.getBlobStore("test"), isPresent(infoEquals(info1)));
        }
    }
}
