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
 * @author Kevin Smith, Boundless, 2018
 */
package org.geowebcache.sqlite;

import static org.geowebcache.util.FileMatchers.directoryContaining;
import static org.geowebcache.util.FileMatchers.named;
import static org.hamcrest.Matchers.hasItem;

import java.io.File;
import java.io.IOException;
import org.geowebcache.blobstore.file.FileBasedBlobStoreSuitabilityTest;
import org.geowebcache.storage.BlobStore;
import org.hamcrest.Matcher;
import org.junit.experimental.theories.DataPoints;

public class MbtilesBlobStoreSuitabilityTest extends FileBasedBlobStoreSuitabilityTest {

    protected static File withMetadata(File dir) throws IOException {
        new File(dir, "metadata.sqlite").createNewFile();
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
    protected Matcher<Object> existing() {
        return directoryContaining((Matcher) hasItem(named("metadata.sqlite")));
    }

    @Override
    public BlobStore create(Object dir) throws Exception {
        MbtilesInfo info = new MbtilesInfo();
        info.setRootDirectory(((File) dir).getAbsolutePath());
        return new MbtilesBlobStore(info);
    }
}
