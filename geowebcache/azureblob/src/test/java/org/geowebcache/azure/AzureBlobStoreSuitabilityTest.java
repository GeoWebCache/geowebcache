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
package org.geowebcache.azure;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.junit.Assert.assertTrue;

import com.azure.core.http.rest.Response;
import com.azure.core.util.BinaryData;
import com.azure.core.util.Context;
import com.azure.storage.blob.models.BlockBlobItem;
import com.azure.storage.blob.options.BlockBlobSimpleUploadOptions;
import org.easymock.EasyMock;
import org.geowebcache.azure.tests.container.AzuriteAzureBlobStoreSuitabilityIT;
import org.geowebcache.azure.tests.online.OnlineAzureBlobStoreSuitabilityIT;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.locks.NoOpLockProvider;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.BlobStoreSuitabilityTest;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.experimental.theories.DataPoints;
import org.springframework.http.HttpStatus;

/**
 * @see OnlineAzureBlobStoreSuitabilityIT
 * @see AzuriteAzureBlobStoreSuitabilityIT
 */
public abstract class AzureBlobStoreSuitabilityTest extends BlobStoreSuitabilityTest {

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

    protected abstract AzureBlobStoreData getConfiguration();

    protected abstract AzureClient getClient();

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
        AzureBlobStoreData info = getConfiguration();
        for (String path : (String[]) dir) {
            String fullPath = info.getPrefix() + "/" + path;
            BinaryData data = BinaryData.fromString("testAbc");
            Response<BlockBlobItem> response = getClient()
                    .getBlockBlobClient(fullPath)
                    .uploadWithResponse(new BlockBlobSimpleUploadOptions(data), null, Context.NONE);
            assertTrue(HttpStatus.valueOf(response.getStatusCode()).is2xxSuccessful());
        }
        return new AzureBlobStore(info, tld, locks);
    }
}
