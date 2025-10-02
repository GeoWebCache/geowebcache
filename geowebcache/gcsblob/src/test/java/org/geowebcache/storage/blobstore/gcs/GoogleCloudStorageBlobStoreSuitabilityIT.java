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
 * @author Gabriel Roldan, Camptocamp, Copyright 2025
 */
package org.geowebcache.storage.blobstore.gcs;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.easymock.EasyMock;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.locks.NoOpLockProvider;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.BlobStoreSuitabilityTest;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.experimental.theories.DataPoints;
import org.junit.rules.TestName;

public class GoogleCloudStorageBlobStoreSuitabilityIT extends BlobStoreSuitabilityTest {

    @ClassRule
    public static GoogleCloudStorageContainerSupport containerSupport = new GoogleCloudStorageContainerSupport();

    @Rule
    public TestName testName = new TestName();

    @DataPoints
    public static String[][] persistenceLocations = {
        {},
        {"metadata.properties"},
        {"something"},
        {"something", "metadata.properties"},
        {"something/metadata.properties"}
    };

    TileLayerDispatcher tld;
    LockProvider locks;

    @Before
    public void setup() throws Exception {
        tld = EasyMock.createMock("tld", TileLayerDispatcher.class);
        locks = new NoOpLockProvider();
        EasyMock.replay(tld);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    protected Matcher<Object> existing() {
        return (Matcher) hasItemInArray(equalTo("metadata.properties"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    protected Matcher<Object> empty() {
        return (Matcher) Matchers.emptyArray();
    }

    @Override
    public BlobStore create(Object dir) throws Exception {
        // Use unique prefix per theory invocation to avoid interference between test runs
        final String prefix = testName.getMethodName() + "-" + UUID.randomUUID();
        // Create a client just for uploading test files
        GoogleCloudStorageClient uploadClient = containerSupport.createClient(prefix);
        for (String path : (String[]) dir) {
            String fullPath = prefix + "/" + path;
            byte[] data = "testAbc".getBytes(StandardCharsets.UTF_8);
            uploadClient.put(fullPath, data, "text/plain");
            assertTrue(uploadClient.get(fullPath).isPresent());
        }
        // Now create the blob store which will perform suitability checks
        return containerSupport.createBlobStore(prefix, tld);
    }
}
