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
package org.geowebcache.azure;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import io.reactivex.Flowable;
import java.nio.ByteBuffer;
import org.easymock.EasyMock;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.locks.NoOpLockProvider;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.BlobStoreSuitabilityTest;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.runner.RunWith;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.springframework.http.HttpStatus;

@RunWith(AzureBlobStoreSuitabilityTest.MyTheories.class)
public class AzureBlobStoreSuitabilityTest extends BlobStoreSuitabilityTest {

    public PropertiesLoader testConfigLoader = new PropertiesLoader();

    @Rule
    public TemporaryAzureFolder tempFolder =
            new TemporaryAzureFolder(testConfigLoader.getProperties());

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

    @SuppressWarnings("unchecked")
    @Override
    protected Matcher<Object> existing() {
        return (Matcher) hasItemInArray(equalTo("metadata.properties"));
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Matcher<Object> empty() {
        return (Matcher) Matchers.emptyArray();
    }

    @Override
    public BlobStore create(Object dir) throws Exception {
        AzureBlobStoreData info = tempFolder.getConfig();
        for (String path : (String[]) dir) {
            String fullPath = info.getPrefix() + "/" + path;
            ByteBuffer byteBuffer = ByteBuffer.wrap("testAbc".getBytes());
            int statusCode =
                    tempFolder
                            .getClient()
                            .getBlockBlobURL(fullPath)
                            .upload(Flowable.just(byteBuffer), byteBuffer.limit())
                            .blockingGet()
                            .statusCode();
            assertTrue(HttpStatus.valueOf(statusCode).is2xxSuccessful());
        }
        return new AzureBlobStore(info, tld, locks);
    }

    // Sorry, this bit of evil makes the Theories runner gracefully ignore the
    // tests if Azure is unavailable.  There's probably a better way to do this.
    public static class MyTheories extends Theories {

        public MyTheories(Class<?> klass) throws InitializationError {
            super(klass);
        }

        @Override
        public Statement methodBlock(FrameworkMethod method) {
            if (new PropertiesLoader().getProperties().containsKey("container")) {
                return super.methodBlock(method);
            } else {
                return new Statement() {
                    @Override
                    public void evaluate() {
                        assumeFalse("Azure unavailable", true);
                    }
                };
            }
        }
    }
}
