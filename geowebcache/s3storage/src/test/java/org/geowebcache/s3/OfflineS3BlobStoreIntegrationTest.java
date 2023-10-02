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
 */
package org.geowebcache.s3;

import io.findify.s3mock.S3Mock;
import org.geowebcache.mime.MimeException;
import org.geowebcache.storage.StorageException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Offline integration tests for {@link S3BlobStore}.
 *
 * <p>
 */
@Ignore // this test fails very often on the AppVeyor build and frequently on Travis, disabling
public class OfflineS3BlobStoreIntegrationTest extends AbstractS3BlobStoreIntegrationTest {

    private static S3Mock api;

    @BeforeClass
    public static void beforeClass() {
        api = new S3Mock.Builder().withPort(8001).withInMemoryBackend().build();
        api.start();
    }

    @AfterClass
    public static void afterClass() {
        api.stop();
    }

    @Override
    protected S3BlobStoreInfo getConfiguration() {
        S3BlobStoreInfo config = new S3BlobStoreInfo();
        config.setAwsAccessKey("");
        config.setAwsSecretKey("");
        config.setEndpoint("http://localhost:8001");
        config.setBucket("testbucket");
        return config;
    }

    @Override
    @Test
    public void testTruncateOptimizationIfNoListeners() throws StorageException, MimeException {
        super.testTruncateOptimizationIfNoListeners();
    }

    @Override
    @Ignore // randomly fails
    @Test
    public void testTruncateShortCutsIfNoTilesInGridsetPrefix()
            throws StorageException, MimeException {
        super.testTruncateShortCutsIfNoTilesInGridsetPrefix();
    }
}
