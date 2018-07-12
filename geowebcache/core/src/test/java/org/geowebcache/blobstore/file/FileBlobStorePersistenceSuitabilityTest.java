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
 * @author Kevin Smith, Boundless, 2018
 */
package org.geowebcache.blobstore.file;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;

import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.CompositeBlobStore;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.SuitabilityCheckRule;
import org.geowebcache.storage.blobstore.file.FileBlobStore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public class FileBlobStorePersistenceSuitabilityTest {
    
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    
    @Rule
    public SuitabilityCheckRule suitability = SuitabilityCheckRule.system();
    
    @Rule
    public ExpectedException exception = ExpectedException.none();
    
    Class<? extends Exception> exceptionClass = StorageException.class;
    
    File emptyDir() throws IOException {
        File emptyDir = temp.newFolder();
        return emptyDir;
    }
    
    File newDir() throws IOException {
        File newDir = new File(emptyDir(), "new");
        return newDir;
    }
    
    File dirWithFile() throws IOException {
        File dir = temp.newFolder();
        File someFile = new File(dir,"file");
        someFile.createNewFile();
        return dir;
    }
    
    File dirWithDir() throws IOException {
        File dir = temp.newFolder();
        File someDir = new File(dir,"dir");
        someDir.mkdir();
        return dir;
    }
    
    File withMetadata(File dir) throws IOException {
        new File(dir, "metadata.properties").createNewFile();
        return dir;
    }
        
    @Test
    public void testNewWithEmptyOK() throws Exception {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.EMPTY);
        
        @SuppressWarnings("unused")
        BlobStore store = create(newDir());
        assertThat(store, notNullValue(BlobStore.class));
    }
    
    @Test
    public void testEmptyWithEmptyOK() throws Exception {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.EMPTY);
        
        @SuppressWarnings("unused")
        BlobStore store = create(emptyDir());
        assertThat(store, notNullValue(BlobStore.class));
    }
    
    @Test
    public void testOnlyMetaWithEmptyFail() throws Exception {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.EMPTY);
        
        exception.expect(exceptionClass);
        @SuppressWarnings("unused")
        BlobStore store = create(withMetadata(emptyDir()));
    }
    
    @Test
    public void testHasDirWithEmptyFail() throws Exception {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.EMPTY);
        
        exception.expect(exceptionClass);
        @SuppressWarnings("unused")
        BlobStore store = create(dirWithDir());
    }
    
    @Test
    public void testDirWithFileMetaWithEmptyFail() throws Exception {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.EMPTY);
        
        exception.expect(exceptionClass);
        @SuppressWarnings("unused")
        BlobStore store = create(withMetadata(dirWithFile()));
    }
    
    @Test
    public void testFileWithEmptyFail() throws Exception {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.EMPTY);
        
        exception.expect(exceptionClass);
        @SuppressWarnings("unused")
        BlobStore store = create(dirWithFile());
    }
    
    @Test
    public void testFileWithMetaWithEmptyFail() throws Exception {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.EMPTY);
        
        exception.expect(exceptionClass);
        @SuppressWarnings("unused")
        BlobStore store = create(withMetadata(dirWithFile()));
    }
    
    @Test
    public void testNewWithExistingOK() throws Exception {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.EXISTING);
        
        @SuppressWarnings("unused")
        BlobStore store = create(newDir());
        assertThat(store, notNullValue(BlobStore.class));
    }
    
    @Test
    public void testEmptyWithExistingOK() throws Exception {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.EXISTING);
        
        @SuppressWarnings("unused")
        BlobStore store = create(emptyDir());
        assertThat(store, notNullValue(BlobStore.class));
    }
    
    @Test
    public void testOnlyMetaWithExistingFail() throws Exception {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.EXISTING);
        
        @SuppressWarnings("unused")
        BlobStore store = create(withMetadata(emptyDir()));
        assertThat(store, notNullValue(BlobStore.class));
    }
    
    @Test
    public void testHasDirWithExistingFail() throws Exception {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.EXISTING);
        
        exception.expect(exceptionClass);
        @SuppressWarnings("unused")
        BlobStore store = create(dirWithDir());
    }
    
    @Test
    public void testDirWithFileMetaWithExistingFail() throws Exception {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.EXISTING);
        
        @SuppressWarnings("unused")
        BlobStore store = create(withMetadata(dirWithFile()));
        assertThat(store, notNullValue(BlobStore.class));
    }
    
    @Test
    public void testFileWithExistingFail() throws Exception {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.EXISTING);
        
        exception.expect(exceptionClass);
        @SuppressWarnings("unused")
        BlobStore store = create(dirWithFile());
    }
    
    @Test
    public void testFileWithMetaWithExistingFail() throws Exception {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.EXISTING);
        
        @SuppressWarnings("unused")
        BlobStore store = create(withMetadata(dirWithFile()));
        assertThat(store, notNullValue(BlobStore.class));
    }
    
    @Test
    public void testNewWithNoneOK() throws Exception {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.NONE);
        
        @SuppressWarnings("unused")
        BlobStore store = create(newDir());
        assertThat(store, notNullValue(BlobStore.class));
    }
    
    @Test
    public void testEmptyWithNoneOK() throws Exception {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.NONE);
        
        @SuppressWarnings("unused")
        BlobStore store = create(emptyDir());
        assertThat(store, notNullValue(BlobStore.class));
        }
    
    @Test
    public void testOnlyMetaWithNoneFail() throws Exception {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.NONE);
        
        @SuppressWarnings("unused")
        BlobStore store = create(withMetadata(emptyDir()));
        assertThat(store, notNullValue(BlobStore.class));
    }
    
    @Test
    public void testHasDirWithNoneFail() throws Exception {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.NONE);
        
        @SuppressWarnings("unused")
        BlobStore store = create(dirWithDir());
        assertThat(store, notNullValue(BlobStore.class));
    }
    
    @Test
    public void testDirWithFileMetaWithNoneFail() throws Exception {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.NONE);
        
        @SuppressWarnings("unused")
        BlobStore store = create(withMetadata(dirWithFile()));
        assertThat(store, notNullValue(BlobStore.class));
    }
    
    @Test
    public void testFileWithNoneFail() throws Exception {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.NONE);
        
        @SuppressWarnings("unused")
        BlobStore store = create(dirWithFile());
        assertThat(store, notNullValue(BlobStore.class));
    }
    
    @Test
    public void testFileWithMetaWithNoneFail() throws Exception {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.NONE);
        
        @SuppressWarnings("unused")
        BlobStore store = create(withMetadata(dirWithFile()));
        assertThat(store, notNullValue(BlobStore.class));
    }
    
    public BlobStore create(File dir) throws StorageException {
        return new FileBlobStore(dir.getAbsolutePath());
    }
}
