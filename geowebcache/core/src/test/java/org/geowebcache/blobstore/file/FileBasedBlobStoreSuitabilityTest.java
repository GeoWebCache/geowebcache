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

import static org.geowebcache.util.FileMatchers.directoryContaining;
import static org.geowebcache.util.FileMatchers.directoryEmpty;
import static org.geowebcache.util.FileMatchers.exists;
import static org.geowebcache.util.FileMatchers.named;
import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

import java.io.File;
import java.io.IOException;

import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.CompositeBlobStore;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.SuitabilityCheckRule;
import org.hamcrest.Matcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public abstract class FileBasedBlobStoreSuitabilityTest {

    @ClassRule
    public static TemporaryFolder temp = new TemporaryFolder();

    public abstract BlobStore create(File dir) throws Exception;

    @Rule
    public SuitabilityCheckRule suitability = SuitabilityCheckRule.system();
    @Rule
    public ExpectedException exception = ExpectedException.none();
    
    static final Class<? extends Exception> EXCEPTION_CLASS = StorageException.class;

    protected static File emptyDir() throws IOException {
        File emptyDir = temp.newFolder();
        return emptyDir;
    }

    protected static File newDir() throws IOException {
        File newDir = new File(emptyDir(), "new");
        return newDir;
    }

    protected static File dirWithFile() throws IOException {
        File dir = temp.newFolder();
        File someFile = new File(dir,"file");
        someFile.createNewFile();
        return dir;
    }

    protected static File dirWithDir() throws IOException {
        File dir = temp.newFolder();
        File someDir = new File(dir,"dir");
        someDir.mkdir();
        return dir;
    }

    protected static File withMetadata(File dir) throws IOException {
        new File(dir, "metadata.properties").createNewFile();
        return dir;
    }

    @DataPoints
    public static File[] persistenceLocations() throws Exception {
        return new File[] {
                newDir(), 
                emptyDir(), withMetadata(emptyDir()), 
                dirWithFile(), withMetadata(dirWithFile()), 
                dirWithDir(), withMetadata(dirWithDir())
                };
    }

    Matcher<File> empty() {
        return either(directoryEmpty()).or(not(exists()));
    }
    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected Matcher<File> existing() {
        return directoryContaining((Matcher)hasItem(named("metadata.properties")));
    }

    public FileBasedBlobStoreSuitabilityTest() {
        super();
    }

    @Theory
    public void testEmptyOk(File persistenceLocation) throws Exception {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.EMPTY);
        assumeThat(persistenceLocation, empty());
        
        @SuppressWarnings("unused")
        BlobStore store = create(persistenceLocation);
        assertThat(store, notNullValue(BlobStore.class));
    }

    @Theory
    public void testEmptyFail(File persistenceLocation) throws Exception {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.EMPTY);
        assumeThat(persistenceLocation, not(empty()));
        
        exception.expect(EXCEPTION_CLASS);
        @SuppressWarnings("unused")
        BlobStore store = create(persistenceLocation);
    }

    @Theory
    public void testExistingOk(File persistenceLocation) throws Exception {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.EXISTING);
        assumeThat(persistenceLocation, either(empty()).or(existing()));
        
        @SuppressWarnings("unused")
        BlobStore store = create(persistenceLocation);
        assertThat(store, notNullValue(BlobStore.class));
    }

    @Theory
    public void testExistingFail(File persistenceLocation) throws Exception {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.EXISTING);
        assumeThat(persistenceLocation, not(either(empty()).or(existing())));
        
        exception.expect(EXCEPTION_CLASS);
        @SuppressWarnings("unused")
        BlobStore store = create(persistenceLocation);
    }

    @Theory
    public void testNoneOk(File persistenceLocation) throws Exception {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.NONE);
        
        @SuppressWarnings("unused")
        BlobStore store = create(persistenceLocation);
        assertThat(store, notNullValue(BlobStore.class));
    }

}