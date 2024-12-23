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

import static org.geowebcache.util.FileMatchers.directoryContaining;
import static org.geowebcache.util.FileMatchers.directoryEmpty;
import static org.geowebcache.util.FileMatchers.exists;
import static org.geowebcache.util.FileMatchers.named;
import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import java.io.File;
import java.io.IOException;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.BlobStoreSuitabilityTest;
import org.hamcrest.Matcher;
import org.junit.ClassRule;
import org.junit.experimental.theories.DataPoints;
import org.junit.rules.TemporaryFolder;

/** Tests persistence suitability checks for BlobStores that use the file system for persistence. */
public abstract class FileBasedBlobStoreSuitabilityTest extends BlobStoreSuitabilityTest {

    @ClassRule
    public static TemporaryFolder temp = new TemporaryFolder();

    @Override
    public abstract BlobStore create(Object dir) throws Exception;

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
        File someFile = new File(dir, "file");
        someFile.createNewFile();
        return dir;
    }

    protected static File dirWithDir() throws IOException {
        File dir = temp.newFolder();
        File someDir = new File(dir, "dir");
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
            emptyDir(),
            withMetadata(emptyDir()),
            dirWithFile(),
            withMetadata(dirWithFile()),
            dirWithDir(),
            withMetadata(dirWithDir())
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Matcher<Object> empty() {
        return either(directoryEmpty()).or((Matcher) not(exists()));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Matcher<Object> existing() {
        return directoryContaining((Matcher) hasItem(named("metadata.properties")));
    }

    public FileBasedBlobStoreSuitabilityTest() {
        super();
    }
}
