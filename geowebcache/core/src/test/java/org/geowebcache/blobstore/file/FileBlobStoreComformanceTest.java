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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.storage.AbstractBlobStoreTest;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.blobstore.file.FileBlobStore;
import org.geowebcache.storage.blobstore.file.LayerMetadataStore;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FileBlobStoreComformanceTest extends AbstractBlobStoreTest<FileBlobStore> {

    static final Logger LOGGER = Logging.getLogger(FileBlobStoreComformanceTest.class);

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Override
    public void createTestUnit() throws Exception {
        System.setProperty(LayerMetadataStore.PROPERTY_WAIT_AFTER_RENAME, "75");
        System.setProperty(LayerMetadataStore.PROPERTY_METADATA_MAX_RW_ATTEMPTS, "100");
        // org.apache.log4j.Logger.getRootLogger().setLevel(Level.DEBUG);
        this.store = new FileBlobStore(temp.getRoot().getAbsolutePath());
    }

    private void putLayerMetadataConcurrently(
            final int srcStoreKey, final FileBlobStore srcStore, int numberOfThreads)
            throws InterruptedException {
        @SuppressWarnings("PMD.CloseResource") // implements AutoCloseable in Java 21
        ExecutorService service = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        for (int i = 0; i < numberOfThreads; i++) {
            final int key = i;
            service.submit(
                    () -> {
                        try {
                            String threadStoreKey =
                                    "store." + srcStoreKey + ".testKey" + String.valueOf(key);
                            String value = "testValue" + String.valueOf(key);
                            // System.err.printf("Setting %s=%s%n", threadKey, value);
                            srcStore.putLayerMetadata("testLayer", threadStoreKey, value);
                        } catch (RuntimeException eh) {
                            LOGGER.log(Level.SEVERE, eh.getMessage(), eh);
                            throw eh;
                        } finally {
                            latch.countDown();
                        }
                    });
        }
        latch.await();
    }

    private void executeStoresConcurrently(int numberOfStores, int numberOfThreads)
            throws InterruptedException {
        @SuppressWarnings("PMD.CloseResource") // implements AutoCloseable in Java 21
        ExecutorService service = Executors.newFixedThreadPool(numberOfStores);
        CountDownLatch latch = new CountDownLatch(numberOfStores);
        for (int i = 0; i < numberOfStores; i++) {
            final int key = i;
            service.submit(
                    () -> {
                        try {
                            FileBlobStore nStore =
                                    new FileBlobStore(temp.getRoot().getAbsolutePath());
                            putLayerMetadataConcurrently(key, nStore, numberOfThreads);
                        } catch (InterruptedException | StorageException eh) {
                            LOGGER.log(Level.SEVERE, eh.getMessage(), eh);
                        } finally {
                            latch.countDown();
                        }
                    });
        }
        latch.await();
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
        @SuppressWarnings("PMD.CloseResource") // implements AutoCloseable in Java 21
        ExecutorService service = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        for (int i = 0; i < numberOfThreads; i++) {
            final int key = i;
            service.submit(
                    () -> {
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
    public void testConcurrentMetadataBasedOnCores() throws InterruptedException {
        assertThat(store.getLayerMetadata("testLayer", "testKey"), nullValue());
        int numberOfStores = 1;
        // number of threads based on cores * 2
        int numberOfThreads = Runtime.getRuntime().availableProcessors() * 2;
        this.putLayerMetadataConcurrently(numberOfStores, store, numberOfThreads);
        String storeKey = "store." + numberOfStores;
        // check return values in Metadata file
        for (int i = 0; i < numberOfThreads; i++) {
            assertThat(
                    store.getLayerMetadata("testLayer", storeKey + ".testKey" + String.valueOf(i)),
                    equalTo("testValue" + String.valueOf(i)));
        }
    }

    @Ignore // useful for optimistic lock testing (ignored since it could fail)
    @Test
    public void testConcurrentMetadataWithTwoStoresCPUThreads()
            throws InterruptedException, StorageException {
        // use store attribute as validator
        assertThat(store.getLayerMetadata("testLayer", "testKey"), nullValue());

        int numberOfStores = 2;
        // number of threads based on cores
        int numberOfThreads = Runtime.getRuntime().availableProcessors();

        this.executeStoresConcurrently(numberOfStores, numberOfThreads);

        // System.err.println("Number of keys = " + numberOfStores * numberOfThreads);
        // check return values in Metadata file
        for (int i = 0; i < numberOfStores; i++) {
            for (int j = 0; j < numberOfThreads; j++) {
                String storeKey = "store." + i;
                assertThat(
                        store.getLayerMetadata(
                                "testLayer", storeKey + ".testKey" + String.valueOf(j)),
                        equalTo("testValue" + String.valueOf(j)));
            }
        }
    }

    @Ignore // useful for optimistic lock testing (ignored since it could fail)
    @Test
    public void testConcurrentMetadataWithTwoStoresOneThread()
            throws InterruptedException, StorageException {
        // use store attribute as validator
        assertThat(store.getLayerMetadata("testLayer", "testKey"), nullValue());

        int numberOfStores = 2;
        int numberOfThreads = 1;

        this.executeStoresConcurrently(numberOfStores, numberOfThreads);

        // check return values in Metadata file
        for (int i = 0; i < numberOfStores; i++) {
            for (int j = 0; j < numberOfThreads; j++) {
                String storeKey = "store." + i;
                assertThat(
                        store.getLayerMetadata(
                                "testLayer", storeKey + ".testKey" + String.valueOf(j)),
                        equalTo("testValue" + String.valueOf(j)));
            }
        }
    }

    @Test
    public void testConcurrentMassiveMetadataKeys() throws InterruptedException {
        assertThat(store.getLayerMetadata("testLayer", "testKey"), nullValue());
        int numberOfThreads = 100;
        int numberOfStores = 1;
        this.putLayerMetadataConcurrently(numberOfStores, store, numberOfThreads);
        String storeKey = "store." + numberOfStores;
        // check return values in Metadata file
        for (int i = 0; i < numberOfThreads; i++) {
            assertThat(
                    store.getLayerMetadata("testLayer", storeKey + ".testKey" + String.valueOf(i)),
                    equalTo("testValue" + String.valueOf(i)));
        }
    }
}
