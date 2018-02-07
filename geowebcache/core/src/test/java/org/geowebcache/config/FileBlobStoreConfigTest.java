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

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

import java.io.File;

import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.StorageException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Preconditions;

public class FileBlobStoreConfigTest {

    private FileBlobStoreInfo config;

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Rule
    public ExpectedException ex = ExpectedException.none();

    private TileLayerDispatcher layers;

    private LockProvider lockProvider;

    @Before
    public void before() {
        config = new FileBlobStoreInfo();
        layers = mock(TileLayerDispatcher.class);
        lockProvider = mock(LockProvider.class);
    }

    @Test
    public void testCreateInstanceNoId() throws StorageException {
        ex.expect(IllegalStateException.class);
        ex.expectMessage("id not set");
        config.createInstance(layers, lockProvider);
    }

    @Test
    public void testCreateInstanceNotEnabled() throws StorageException {
        config.setId("myblobstore");
        config.setEnabled(false);
        ex.expect(IllegalStateException.class);
        ex.expectMessage("store is not enabled");
        config.createInstance(layers, lockProvider);
    }

    @Test
    public void testCreateInstanceNoBaseDirectory() throws StorageException {
        config.setId("myblobstore");
        config.setEnabled(true);
        ex.expect(IllegalStateException.class);
        ex.expectMessage("baseDirectory not provided");
        config.createInstance(layers, lockProvider);
    }

    @Test
    public void testCreateInstanceIllegalBlockSize() throws StorageException {
        config.setId("myblobstore");
        config.setEnabled(true);
        config.setFileSystemBlockSize(-2048);
        config.setBaseDirectory(tmp.getRoot().getAbsolutePath());
        ex.expect(IllegalStateException.class);
        ex.expectMessage("must be a positive integer");
        config.createInstance(layers, lockProvider);
    }

    @Test
    public void testCreateInstance() throws StorageException {
        config.setId("myblobstore");
        config.setEnabled(true);
        File root = tmp.getRoot();
        Preconditions.checkState(root.exists() && root.isDirectory());
        config.setBaseDirectory(root.getAbsolutePath());
        BlobStore store = config.createInstance(layers, lockProvider);
        assertNotNull(store);
    }

}
