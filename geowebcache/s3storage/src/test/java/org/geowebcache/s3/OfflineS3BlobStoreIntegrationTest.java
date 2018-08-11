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
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;

/**
 * Offline integration tests for {@link S3BlobStore}.
 *
 * <p>This test class is currently disabled because gwc is affect by this bug in the S3Mock library:
 * https://github.com/findify/s3mock/issues/75 which makes some of the tests fail.
 *
 * <p>A patch for this bug is currently under review, and as soon as it has been merged and
 * released, the dependency version can be upgraded and this test can be enabled.
 */
@Ignore
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

    protected S3BlobStoreInfo getConfiguration() {
        S3BlobStoreInfo config = new S3BlobStoreInfo();
        config.setAwsAccessKey("");
        config.setAwsSecretKey("");
        config.setEndpoint("http://localhost:8001");
        config.setBucket("testbucket");
        return config;
    }
}
