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

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.verify;
import static org.geowebcache.util.TestUtils.isPresent;
import static org.geowebcache.util.TestUtils.notPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.storage.UnsuitableStorageException;
import org.junit.Test;

public abstract class BlobStoreConfigurationTest extends ConfigurationTest<BlobStoreInfo, BlobStoreConfiguration> {

    @Override
    protected void addInfo(BlobStoreConfiguration config, BlobStoreInfo info) throws Exception {
        config.addBlobStore(info);
    }

    @Override
    protected Optional<BlobStoreInfo> getInfo(BlobStoreConfiguration config, String name) throws Exception {
        return config.getBlobStore(name);
    }

    @Override
    protected Collection<? extends BlobStoreInfo> getInfos(BlobStoreConfiguration config) throws Exception {
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
    protected void renameInfo(BlobStoreConfiguration config, String oldName, String newName) throws Exception {
        config.renameBlobStore(oldName, newName);
    }

    @Override
    protected void modifyInfo(BlobStoreConfiguration config, BlobStoreInfo info) throws Exception {
        config.modifyBlobStore(info);
    }

    @Test
    public void testRollBackOnUnsuitableStorageExceptionInAddHandler() throws Exception {
        BlobStoreInfo info = getGoodInfo("test", 1);
        BlobStoreConfigurationListener listener = createMock(BlobStoreConfigurationListener.class);
        listener.handleAddBlobStore(info);
        expectLastCall().andThrow(new UnsuitableStorageException("TEST"));
        EasyMock.replay(listener);
        config.addBlobStoreListener(listener);
        ConfigurationPersistenceException exception =
                assertThrows(ConfigurationPersistenceException.class, () -> config.addBlobStore(info));
        assertThat(exception.getCause(), instanceOf(UnsuitableStorageException.class));
        verify(listener);
        assertThat(config.getBlobStore("test"), notPresent());
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
        ConfigurationPersistenceException exception =
                assertThrows(ConfigurationPersistenceException.class, () -> config.modifyBlobStore(info2));
        assertThat(exception.getCause(), instanceOf(UnsuitableStorageException.class));
        verify(listener);
        assertThat(config.getBlobStore("test"), isPresent(infoEquals(info1)));
    }

    @Test
    public void testRollBackOnSupressedUnsuitableStorageExceptionInModifyHandler() throws Exception {
        BlobStoreInfo info1 = getGoodInfo("test", 1);
        BlobStoreInfo info2 = getGoodInfo("test", 2);
        IMocksControl control = EasyMock.createControl();
        BlobStoreConfigurationListener listener1 = control.createMock(BlobStoreConfigurationListener.class);
        BlobStoreConfigurationListener listener2 = control.createMock(BlobStoreConfigurationListener.class);
        control.checkOrder(true);
        listener1.handleAddBlobStore(info1);
        expectLastCall();
        listener2.handleAddBlobStore(info1);
        expectLastCall();
        listener1.handleModifyBlobStore(info2);
        expectLastCall().andThrow(new UnsuitableStorageException("TEST"));
        listener2.handleModifyBlobStore(info2);
        expectLastCall().andThrow(new IOException("Supressing Exception"));
        control.replay();
        config.addBlobStoreListener(listener1);
        config.addBlobStoreListener(listener2);
        config.addBlobStore(info1);
        ConfigurationPersistenceException exception =
                assertThrows(ConfigurationPersistenceException.class, () -> config.modifyBlobStore(info2));
        assertThat(exception.getCause(), instanceOf(IOException.class));
        control.verify();
        assertThat(config.getBlobStore("test"), isPresent(infoEquals(info1)));
    }

    @Test
    public void testRollBackOnSupressedUnsuitableStorageExceptionInAddHandler() throws Exception {
        BlobStoreInfo info1 = getGoodInfo("test", 1);
        IMocksControl control = EasyMock.createControl();
        BlobStoreConfigurationListener listener1 = control.createMock(BlobStoreConfigurationListener.class);
        BlobStoreConfigurationListener listener2 = control.createMock(BlobStoreConfigurationListener.class);
        control.checkOrder(true);
        listener1.handleAddBlobStore(info1);
        expectLastCall().andThrow(new UnsuitableStorageException("TEST"));
        listener2.handleAddBlobStore(info1);
        expectLastCall().andThrow(new IOException("Supressing Exception"));
        control.replay();
        config.addBlobStoreListener(listener1);
        config.addBlobStoreListener(listener2);
        ConfigurationPersistenceException exception =
                assertThrows(ConfigurationPersistenceException.class, () -> config.addBlobStore(info1));
        assertThat(exception.getCause(), instanceOf(IOException.class));
        control.verify();
        assertThat(config.getBlobStore("test"), notPresent());
    }

    @Test
    public void testListenerHearsAdd() throws Exception {
        BlobStoreConfigurationListener listener = EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        this.config.addBlobStoreListener(listener);
        BlobStoreInfo goodInfo = this.getGoodInfo("test", 1);
        listener.handleAddBlobStore(EasyMock.eq(goodInfo));
        EasyMock.expectLastCall().once();
        EasyMock.replay(listener);
        this.addInfo(this.config, goodInfo);
        EasyMock.verify(listener);
    }

    @Test
    public void testListenerHearsRemove() throws Exception {
        BlobStoreInfo goodInfo = this.getGoodInfo("test", 1);
        this.addInfo(this.config, goodInfo);
        BlobStoreConfigurationListener listener = EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        this.config.addBlobStoreListener(listener);
        listener.handleRemoveBlobStore(EasyMock.eq(goodInfo));
        EasyMock.expectLastCall().once();
        EasyMock.replay(listener);
        this.removeInfo(this.config, "test");
        EasyMock.verify(listener);
    }

    @Test
    public void testListenerHearsModify() throws Exception {
        BlobStoreInfo goodInfo = this.getGoodInfo("test", 1);
        this.addInfo(this.config, goodInfo);
        BlobStoreConfigurationListener listener = EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        this.config.addBlobStoreListener(listener);
        listener.handleModifyBlobStore(EasyMock.eq(goodInfo));
        EasyMock.expectLastCall().once();
        EasyMock.replay(listener);
        this.doModifyInfo(goodInfo, 2);
        this.config.modifyBlobStore(goodInfo);
        EasyMock.verify(listener);
    }

    @Test
    public void testListenerHearsRename() throws Exception {
        BlobStoreInfo goodInfo = this.getGoodInfo("test", 1);
        BlobStoreInfo expectedInfo = this.getGoodInfo("newName", 1);
        this.addInfo(this.config, goodInfo);
        BlobStoreConfigurationListener listener = EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        this.config.addBlobStoreListener(listener);
        listener.handleRenameBlobStore(EasyMock.eq("test"), EasyMock.eq(expectedInfo));
        EasyMock.expectLastCall().once();
        EasyMock.replay(listener);
        this.config.renameBlobStore("test", "newName");
        EasyMock.verify(listener);
    }

    @Test
    public void testMultipleListenersHearAdd() throws Exception {
        BlobStoreConfigurationListener listener1 =
                EasyMock.createMock("listener1", BlobStoreConfigurationListener.class);
        BlobStoreConfigurationListener listener2 =
                EasyMock.createMock("listener2", BlobStoreConfigurationListener.class);
        this.config.addBlobStoreListener(listener1);
        this.config.addBlobStoreListener(listener2);
        BlobStoreInfo goodInfo = this.getGoodInfo("test", 1);
        listener1.handleAddBlobStore(EasyMock.eq(goodInfo));
        EasyMock.expectLastCall().once();
        listener2.handleAddBlobStore(EasyMock.eq(goodInfo));
        EasyMock.expectLastCall().once();
        EasyMock.replay(listener1, listener2);
        this.addInfo(this.config, goodInfo);
        EasyMock.verify(listener1, listener2);
    }

    @Test
    public void testRemoveListener() throws Exception {
        BlobStoreConfigurationListener listener1 =
                EasyMock.createMock("listener1", BlobStoreConfigurationListener.class);
        BlobStoreConfigurationListener listener2 =
                EasyMock.createMock("listener2", BlobStoreConfigurationListener.class);
        this.config.addBlobStoreListener(listener1);
        this.config.addBlobStoreListener(listener2);
        this.config.removeBlobStoreListener(listener1);
        BlobStoreInfo goodInfo = this.getGoodInfo("test", 1);
        listener2.handleAddBlobStore(EasyMock.eq(goodInfo));
        EasyMock.expectLastCall().once();
        EasyMock.replay(listener1, listener2);
        this.addInfo(this.config, goodInfo);
        EasyMock.verify(listener1, listener2);
    }

    @Test
    public void testListenerDoesntHearFailureToAddBadInfo() throws Exception {
        BlobStoreConfigurationListener listener = EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        this.config.addBlobStoreListener(listener);
        BlobStoreInfo badInfo = this.getBadInfo("test", 1);
        // listener.handleAddBlobStore(EasyMock.eq(badInfo)); EasyMock.expectLastCall().once();
        EasyMock.replay(listener);
        try {
            this.addInfo(this.config, badInfo);
        } catch (IllegalArgumentException ex) {
            // Do Nothing
        }
        EasyMock.verify(listener);
    }

    @Test
    public void testListenerDoesntHearFailureToAddDuplicate() throws Exception {
        BlobStoreConfigurationListener listener = EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        BlobStoreInfo goodInfo = this.getGoodInfo("test", 1);
        this.addInfo(this.config, goodInfo);
        BlobStoreInfo goodInfo2 = this.getGoodInfo("test", 2);
        // listener.handleAddBlobStore(EasyMock.eq(badInfo)); EasyMock.expectLastCall().once();
        EasyMock.replay(listener);
        this.config.addBlobStoreListener(listener);
        try {
            this.addInfo(this.config, goodInfo2);
        } catch (IllegalArgumentException ex) {
            // Do Nothing
        }
        EasyMock.verify(listener);
    }

    @Test
    public void testListenerDoesntHearFailureToAddDueToBackend() throws Exception {
        BlobStoreConfigurationListener listener = EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        this.config.addBlobStoreListener(listener);
        BlobStoreInfo goodInfo = this.getGoodInfo("test", 1);
        // listener.handleAddBlobStore(EasyMock.eq(badInfo)); EasyMock.expectLastCall().once();
        EasyMock.replay(listener);
        this.failNextWrite();
        try {
            this.addInfo(this.config, goodInfo);
        } catch (ConfigurationPersistenceException ex) {
            // Do Nothing
        }
        EasyMock.verify(listener);
    }

    @Test
    public void testListenerDoesntHearFailureToModifyBadInfo() throws Exception {
        BlobStoreConfigurationListener listener = EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        BlobStoreInfo goodInfo = this.getGoodInfo("test", 1);
        this.addInfo(this.config, goodInfo);
        this.config.addBlobStoreListener(listener);
        BlobStoreInfo badInfo = this.getBadInfo("test", 1);
        EasyMock.replay(listener);
        try {
            this.modifyInfo(this.config, badInfo);
        } catch (IllegalArgumentException ex) {
            // Do Nothing
        }
        EasyMock.verify(listener);
    }

    @Test
    public void testListenerDoesntHearFailureToModifyDoesntExist() throws Exception {
        BlobStoreConfigurationListener listener = EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        this.config.addBlobStoreListener(listener);
        BlobStoreInfo goodInfo = this.getGoodInfo("test", 1);
        EasyMock.replay(listener);
        try {
            this.modifyInfo(this.config, goodInfo);
        } catch (NoSuchElementException ex) {
            // Do Nothing
        }
        EasyMock.verify(listener);
    }

    @Test
    public void testListenerDoesntHearFailureToModifyDueToBackend() throws Exception {
        BlobStoreConfigurationListener listener = EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        BlobStoreInfo goodInfo = this.getGoodInfo("test", 1);
        this.addInfo(this.config, goodInfo);
        this.config.addBlobStoreListener(listener);
        goodInfo = this.getGoodInfo("test", 2);
        EasyMock.replay(listener);
        this.failNextWrite();
        try {
            this.modifyInfo(this.config, goodInfo);
        } catch (ConfigurationPersistenceException ex) {
            // Do Nothing
        }
        EasyMock.verify(listener);
    }

    @Test
    public void testListenerDoesntHearFailureToRemoveDoesntExist() throws Exception {
        BlobStoreConfigurationListener listener = EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        this.config.addBlobStoreListener(listener);
        EasyMock.replay(listener);
        try {
            this.removeInfo(this.config, "test");
        } catch (NoSuchElementException ex) {
            // Do Nothing
        }
        EasyMock.verify(listener);
    }

    @Test
    public void testListenerDoesntHearFailureToRemoveDueToBackend() throws Exception {
        BlobStoreConfigurationListener listener = EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        BlobStoreInfo goodInfo = this.getGoodInfo("test", 1);
        this.addInfo(this.config, goodInfo);
        this.config.addBlobStoreListener(listener);
        EasyMock.replay(listener);
        this.failNextWrite();
        try {
            this.removeInfo(this.config, "test");
        } catch (ConfigurationPersistenceException ex) {
            // Do Nothing
        }
        EasyMock.verify(listener);
    }

    @Test
    public void testListenerDoesntHearFailureToRenameDoesntExist() throws Exception {
        BlobStoreConfigurationListener listener = EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        this.config.addBlobStoreListener(listener);
        EasyMock.replay(listener);
        try {
            this.renameInfo(this.config, "test", "test2");
        } catch (NoSuchElementException ex) {
            // Do Nothing
        }
        EasyMock.verify(listener);
    }

    @Test
    public void testListenerDoesntHearFailureToRenameDuplicate() throws Exception {
        BlobStoreConfigurationListener listener = EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        BlobStoreInfo goodInfo = this.getGoodInfo("test", 1);
        this.addInfo(this.config, goodInfo);
        BlobStoreInfo goodInfo2 = this.getGoodInfo("test2", 1);
        this.addInfo(this.config, goodInfo2);
        this.config.addBlobStoreListener(listener);
        EasyMock.replay(listener);
        try {
            this.renameInfo(this.config, "test", "test2");
        } catch (IllegalArgumentException ex) {
            // Do Nothing
        }
        EasyMock.verify(listener);
    }

    @Test
    public void testListenerDoesntHearFailureToRenameDueToBackend() throws Exception {
        BlobStoreConfigurationListener listener = EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        BlobStoreInfo goodInfo = this.getGoodInfo("test", 1);
        this.addInfo(this.config, goodInfo);
        this.config.addBlobStoreListener(listener);
        EasyMock.replay(listener);
        this.failNextWrite();
        try {
            this.removeInfo(this.config, "test");
        } catch (ConfigurationPersistenceException ex) {
            // Do Nothing
        }
        EasyMock.verify(listener);
    }

    // Exceptions during add handlers

    @Test
    public void testExceptionInAddListenerIsWrapped() throws Exception {
        BlobStoreConfigurationListener listener = EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        BlobStoreInfo goodInfo = this.getGoodInfo("test", 1);
        this.config.addBlobStoreListener(listener);
        listener.handleAddBlobStore(goodInfo);
        GeoWebCacheException ex = new GeoWebCacheException("TEST");
        EasyMock.expectLastCall().andThrow(ex);
        EasyMock.replay(listener);

        ConfigurationPersistenceException exception =
                assertThrows(ConfigurationPersistenceException.class, () -> addInfo(config, goodInfo));
        assertThat(exception.getCause(), sameInstance(ex));
    }

    @Test
    public void testExceptionInAddListenerNotRolledBack() throws Exception {
        BlobStoreConfigurationListener listener = EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        BlobStoreInfo goodInfo = this.getGoodInfo("test", 1);
        this.config.addBlobStoreListener(listener);
        listener.handleAddBlobStore(goodInfo);
        GeoWebCacheException ex = new GeoWebCacheException("TEST");
        EasyMock.expectLastCall().andThrow(ex);
        EasyMock.replay(listener);

        try {
            this.addInfo(this.config, goodInfo);
        } catch (ConfigurationPersistenceException ex2) {
            // Do Nothing
        }

        assertThat(this.getInfo(config, "test"), isPresent(equalTo(goodInfo)));
    }

    @Test
    public void testExceptionInAddListenerDoesntBlockOtherListeners() throws Exception {
        BlobStoreConfigurationListener listener1 =
                EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        BlobStoreConfigurationListener listener2 =
                EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        BlobStoreInfo goodInfo = this.getGoodInfo("test", 1);
        this.config.addBlobStoreListener(listener1);
        this.config.addBlobStoreListener(listener2);
        GeoWebCacheException ex1 = new GeoWebCacheException("TEST1");
        listener1.handleAddBlobStore(goodInfo);
        EasyMock.expectLastCall().andThrow(ex1);
        listener2.handleAddBlobStore(goodInfo);
        EasyMock.expectLastCall().once();

        EasyMock.replay(listener1, listener2);

        try {
            this.addInfo(this.config, goodInfo);
        } catch (ConfigurationPersistenceException ex3) {
            // Do Nothing
        }

        EasyMock.verify(listener2);
    }

    @Test
    public void testExceptionInAddListenerRecordsSuppressedExceptions() throws Exception {
        BlobStoreConfigurationListener listener1 =
                EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        BlobStoreConfigurationListener listener2 =
                EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        BlobStoreInfo goodInfo = this.getGoodInfo("test", 1);
        this.config.addBlobStoreListener(listener1);
        this.config.addBlobStoreListener(listener2);
        Exception ex1 = new GeoWebCacheException("TEST1");
        Exception ex2 = new IOException("TEST2");
        listener1.handleAddBlobStore(goodInfo);
        EasyMock.expectLastCall().andThrow(ex1);
        listener2.handleAddBlobStore(goodInfo);
        EasyMock.expectLastCall().andThrow(ex2);

        EasyMock.replay(listener1, listener2);

        ConfigurationPersistenceException exception =
                assertThrows(ConfigurationPersistenceException.class, () -> addInfo(config, goodInfo));
        assertThat(
                exception.getCause(),
                allOf(sameInstance(ex2), hasProperty("suppressed", arrayContainingInAnyOrder(sameInstance(ex1)))));
    }
    // Exceptions during modify handlers

    @Test
    public void testExceptionInModifyListenerIsWrapped() throws Exception {
        BlobStoreConfigurationListener listener = EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        BlobStoreInfo goodInfo = prepForModify();

        this.config.addBlobStoreListener(listener);
        GeoWebCacheException ex = new GeoWebCacheException("TEST");
        listener.handleModifyBlobStore(goodInfo);
        EasyMock.expectLastCall().andThrow(ex);
        EasyMock.replay(listener);

        ConfigurationPersistenceException exception =
                assertThrows(ConfigurationPersistenceException.class, () -> modifyInfo(config, goodInfo));
        assertThat(exception.getCause(), sameInstance(ex));
    }

    /**
     * Set up the configuration for a modify test with an info named "test".
     *
     * @return an info named "test" with a different value
     */
    protected BlobStoreInfo prepForModify() throws Exception {
        BlobStoreInfo goodInfo = this.getGoodInfo("test", 1);
        this.addInfo(this.config, goodInfo);
        goodInfo = this.getGoodInfo("test", 2);
        return goodInfo;
    }

    @Test
    public void testExceptionInModifyListenerNotRolledBack() throws Exception {
        BlobStoreConfigurationListener listener = EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        BlobStoreInfo goodInfo = prepForModify();

        this.config.addBlobStoreListener(listener);
        GeoWebCacheException ex = new GeoWebCacheException("TEST");
        listener.handleModifyBlobStore(goodInfo);
        EasyMock.expectLastCall().andThrow(ex);
        EasyMock.replay(listener);

        try {
            this.modifyInfo(this.config, goodInfo);
        } catch (ConfigurationPersistenceException ex2) {
            // Do Nothing
        }

        assertThat(this.getInfo(config, "test"), isPresent(infoEquals(2)));
    }

    @Test
    public void testExceptionInModifyListenerDoesntBlockOtherListeners() throws Exception {
        BlobStoreConfigurationListener listener1 =
                EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        BlobStoreConfigurationListener listener2 =
                EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        BlobStoreInfo goodInfo = prepForModify();
        this.config.addBlobStoreListener(listener1);
        this.config.addBlobStoreListener(listener2);
        GeoWebCacheException ex1 = new GeoWebCacheException("TEST1");
        listener1.handleModifyBlobStore(goodInfo);
        EasyMock.expectLastCall().andThrow(ex1);
        listener2.handleModifyBlobStore(goodInfo);
        EasyMock.expectLastCall().once();

        EasyMock.replay(listener1, listener2);

        try {
            this.modifyInfo(this.config, goodInfo);
        } catch (ConfigurationPersistenceException ex3) {
            // Do Nothing
        }

        EasyMock.verify(listener2);
    }

    @Test
    public void testExceptionInModifyListenerRecordsSuppressedExceptions() throws Exception {
        BlobStoreConfigurationListener listener1 =
                EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        BlobStoreConfigurationListener listener2 =
                EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        BlobStoreInfo goodInfo = prepForModify();
        this.config.addBlobStoreListener(listener1);
        this.config.addBlobStoreListener(listener2);
        Exception ex1 = new GeoWebCacheException("TEST1");
        Exception ex2 = new IOException("TEST2");
        listener1.handleModifyBlobStore(goodInfo);
        EasyMock.expectLastCall().andThrow(ex1);
        listener2.handleModifyBlobStore(goodInfo);
        EasyMock.expectLastCall().andThrow(ex2);

        EasyMock.replay(listener1, listener2);

        ConfigurationPersistenceException exception =
                assertThrows(ConfigurationPersistenceException.class, () -> modifyInfo(config, goodInfo));
        assertThat(
                exception,
                hasProperty(
                        "cause",
                        allOf(
                                sameInstance(ex2),
                                hasProperty("suppressed", arrayContainingInAnyOrder(sameInstance(ex1))))));
    }

    // Exceptions during rename handlers
    /**
     * Set up the configuration for a rename test with an info named "test".
     *
     * @return an info named "test2"
     */
    protected BlobStoreInfo prepForRename() throws Exception {
        BlobStoreInfo goodInfo = this.getGoodInfo("test", 1);
        this.addInfo(this.config, goodInfo);
        goodInfo = this.getGoodInfo("test2", 1);
        return goodInfo;
    }

    @Test
    public void testExceptionInRenameListenerIsWrapped() throws Exception {
        BlobStoreConfigurationListener listener = EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        BlobStoreInfo goodInfo = prepForRename();

        this.config.addBlobStoreListener(listener);
        GeoWebCacheException ex = new GeoWebCacheException("TEST");
        listener.handleRenameBlobStore("test", goodInfo);
        EasyMock.expectLastCall().andThrow(ex);
        EasyMock.replay(listener);

        ConfigurationPersistenceException exception =
                assertThrows(ConfigurationPersistenceException.class, () -> renameInfo(config, "test", "test2"));
        assertThat(exception.getCause(), sameInstance(ex));
    }

    @Test
    public void testExceptionInRenameListenerNotRolledBack() throws Exception {
        BlobStoreConfigurationListener listener = EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        BlobStoreInfo goodInfo = prepForRename();

        this.config.addBlobStoreListener(listener);
        GeoWebCacheException ex = new GeoWebCacheException("TEST");
        listener.handleRenameBlobStore("test", goodInfo);
        EasyMock.expectLastCall().andThrow(ex);
        EasyMock.replay(listener);

        try {
            this.renameInfo(this.config, "test", "test2");
        } catch (ConfigurationPersistenceException ex2) {
            // Do Nothing
        }

        assertThat(this.getInfo(config, "test2"), isPresent(infoEquals(goodInfo)));
    }

    @Test
    public void testExceptionInRenameListenerDoesntBlockOtherListeners() throws Exception {
        BlobStoreConfigurationListener listener1 =
                EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        BlobStoreConfigurationListener listener2 =
                EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        BlobStoreInfo goodInfo = prepForRename();
        this.config.addBlobStoreListener(listener1);
        this.config.addBlobStoreListener(listener2);
        GeoWebCacheException ex1 = new GeoWebCacheException("TEST1");
        listener1.handleRenameBlobStore("test", goodInfo);
        EasyMock.expectLastCall().andThrow(ex1);
        listener2.handleRenameBlobStore("test", goodInfo);
        EasyMock.expectLastCall().once();

        EasyMock.replay(listener1, listener2);

        try {
            this.renameInfo(this.config, "test", "test2");
        } catch (ConfigurationPersistenceException ex3) {
            // Do Nothing
        }

        EasyMock.verify(listener2);
    }

    @Test
    public void testExceptionInRenameListenerRecordsSuppressedExceptions() throws Exception {
        BlobStoreConfigurationListener listener1 =
                EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        BlobStoreConfigurationListener listener2 =
                EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        BlobStoreInfo goodInfo = prepForRename();
        this.config.addBlobStoreListener(listener1);
        this.config.addBlobStoreListener(listener2);
        Exception ex1 = new GeoWebCacheException("TEST1");
        Exception ex2 = new IOException("TEST2");
        listener1.handleRenameBlobStore("test", goodInfo);
        EasyMock.expectLastCall().andThrow(ex1);
        listener2.handleRenameBlobStore("test", goodInfo);
        EasyMock.expectLastCall().andThrow(ex2);

        EasyMock.replay(listener1, listener2);

        ConfigurationPersistenceException exception =
                assertThrows(ConfigurationPersistenceException.class, () -> renameInfo(config, "test", "test2"));
        assertThat(
                exception,
                hasProperty(
                        "cause",
                        allOf(
                                sameInstance(ex2),
                                hasProperty("suppressed", arrayContainingInAnyOrder(sameInstance(ex1))))));
    }

    // Exceptions during remove handlers

    /**
     * Set up the configuration for a remove test with an info named "test".
     *
     * @return the info that was added
     */
    protected BlobStoreInfo prepForRemove() throws Exception {
        BlobStoreInfo goodInfo = this.getGoodInfo("test", 1);
        this.addInfo(this.config, goodInfo);
        return goodInfo;
    }

    @Test
    public void testExceptionInRemoveListenerIsWrapped() throws Exception {
        BlobStoreConfigurationListener listener = EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        BlobStoreInfo goodInfo = prepForRemove();

        this.config.addBlobStoreListener(listener);
        GeoWebCacheException ex = new GeoWebCacheException("TEST");
        listener.handleRemoveBlobStore(goodInfo);
        EasyMock.expectLastCall().andThrow(ex);
        EasyMock.replay(listener);

        ConfigurationPersistenceException exception =
                assertThrows(ConfigurationPersistenceException.class, () -> removeInfo(config, "test"));
        assertThat(exception.getCause(), sameInstance(ex));
    }

    @Test
    public void testExceptionInRemoveListenerNotRolledBack() throws Exception {
        BlobStoreConfigurationListener listener = EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        BlobStoreInfo goodInfo = prepForRemove();

        this.config.addBlobStoreListener(listener);
        GeoWebCacheException ex = new GeoWebCacheException("TEST");
        listener.handleRemoveBlobStore(goodInfo);
        EasyMock.expectLastCall().andThrow(ex);
        EasyMock.replay(listener);

        try {
            this.removeInfo(this.config, "test");
        } catch (ConfigurationPersistenceException ex2) {
            // Do Nothing
        }

        assertThat(this.getInfo(config, "test"), notPresent());
    }

    @Test
    public void testExceptionInRemoveListenerDoesntBlockOtherListeners() throws Exception {
        BlobStoreConfigurationListener listener1 =
                EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        BlobStoreConfigurationListener listener2 =
                EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        BlobStoreInfo goodInfo = prepForRemove();
        this.config.addBlobStoreListener(listener1);
        this.config.addBlobStoreListener(listener2);
        GeoWebCacheException ex1 = new GeoWebCacheException("TEST1");
        listener1.handleRemoveBlobStore(goodInfo);
        EasyMock.expectLastCall().andThrow(ex1);
        listener2.handleRemoveBlobStore(goodInfo);
        EasyMock.expectLastCall().once();

        EasyMock.replay(listener1, listener2);

        try {
            this.removeInfo(this.config, "test");
        } catch (ConfigurationPersistenceException ex3) {
            // Do Nothing
        }

        EasyMock.verify(listener2);
    }

    @Test
    public void testExceptionInRemoveListenerRecordsSuppressedExceptions() throws Exception {
        BlobStoreConfigurationListener listener1 =
                EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        BlobStoreConfigurationListener listener2 =
                EasyMock.createMock("listener", BlobStoreConfigurationListener.class);
        BlobStoreInfo goodInfo = prepForRemove();
        this.config.addBlobStoreListener(listener1);
        this.config.addBlobStoreListener(listener2);
        Exception ex1 = new GeoWebCacheException("TEST1");
        Exception ex2 = new IOException("TEST2");
        listener1.handleRemoveBlobStore(goodInfo);
        EasyMock.expectLastCall().andThrow(ex1);
        listener2.handleRemoveBlobStore(goodInfo);
        EasyMock.expectLastCall().andThrow(ex2);

        EasyMock.replay(listener1, listener2);

        ConfigurationPersistenceException exception =
                assertThrows(ConfigurationPersistenceException.class, () -> removeInfo(config, "test"));
        assertThat(
                exception,
                hasProperty(
                        "cause",
                        allOf(
                                sameInstance(ex2),
                                hasProperty("suppressed", arrayContainingInAnyOrder(sameInstance(ex1))))));
    }
}
