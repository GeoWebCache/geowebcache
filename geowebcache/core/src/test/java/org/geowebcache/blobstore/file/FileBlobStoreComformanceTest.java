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
 * @author Kevin Smith, Boundless, 2017
 */
package org.geowebcache.blobstore.file;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.geowebcache.storage.AbstractBlobStoreTest;
import org.geowebcache.storage.blobstore.file.FileBlobStore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FileBlobStoreComformanceTest extends AbstractBlobStoreTest<FileBlobStore> {

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Override
    public void createTestUnit() throws Exception {
        this.store = new FileBlobStore(temp.getRoot().getAbsolutePath());
    }

    @Test
    public void testMetadataWithPointInKey() throws Exception {
        assertThat(store.getLayerMetadata("testLayer", "test.Key"), nullValue());
        store.putLayerMetadata("testLayer", "test.Key", "testValue");
        assertThat(store.getLayerMetadata("testLayer", "test.Key"), equalTo("testValue"));
    }

    @Test
    public void testConcurrentMetadataWithPointInKey() throws InterruptedException {
        assertThat(store.getLayerMetadata("testLayer", "test.Key"), nullValue());
        int numberOfThreads = 2;
        ExecutorService service = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        for (int i = 0; i < numberOfThreads; i++) {
            final int key = i;
            service.submit(
                    () -> {
                        // Sleep thread randomly before adding information into metadata file.
                        try {
                            Thread.sleep(Math.round((Math.random() * 500) + 1));
                        } catch (InterruptedException e) {
                            // Handle exception
                        }
                        store.putLayerMetadata(
                                "testLayer", "test.Key." + String.valueOf(key), "testValue");
                        latch.countDown();
                    });
        }
        latch.await();
        assertThat(store.getLayerMetadata("testLayer", "test.Key.1"), equalTo("testValue"));
        assertThat(store.getLayerMetadata("testLayer", "test.Key.0"), equalTo("testValue"));
    }

    @Test
    public void testConcurrentMassiveMetadataKeys() throws InterruptedException {
        assertThat(store.getLayerMetadata("testLayer", "testKey"), nullValue());
        int numberOfThreads = 50;
        ExecutorService service = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        for (int i = 0; i < numberOfThreads; i++) {
            final int key = i;
            service.submit(
                    () -> {
                        // Sleep thread randomly before adding information into metadata file.
                        try {
                        	long sleep = Math.round((Math.random() * numberOfThreads * 500) + 1);
                            Thread.sleep(sleep);
                        } catch (InterruptedException e) {
                            // Handle exception
                        }
                        store.putLayerMetadata(
                                "testLayer",
                                "testKey" + String.valueOf(key),
                                "testValue" + String.valueOf(key));
                        latch.countDown();
                    });
        }
        latch.await();
        for (int i = 0; i < numberOfThreads; i++) {
            assertThat(
                    store.getLayerMetadata("testLayer", "testKey" + String.valueOf(i)),
                    equalTo("testValue" + String.valueOf(i)));
        }
    }
}
