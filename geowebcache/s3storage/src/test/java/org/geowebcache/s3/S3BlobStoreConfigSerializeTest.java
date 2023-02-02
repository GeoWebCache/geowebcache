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
 * @author Kevin Smith, Boundless, Copyright 2017
 */
package org.geowebcache.s3;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.XStreamException;
import org.geowebcache.util.PropertyRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class S3BlobStoreConfigSerializeTest {

    @Test
    public void testNoAccess() throws Exception {
        S3BlobStoreConfigProvider provider = new S3BlobStoreConfigProvider();
        XStream xs = provider.getConfiguredXStream(new XStream());
        S3BlobStoreInfo config =
                (S3BlobStoreInfo)
                        xs.fromXML(
                                "<S3BlobStore><id>test</id><enabled>false</enabled><useHTTPS>true</useHTTPS></S3BlobStore>");
        assertThat(
                config, hasProperty("accessControlList", is(CannedAccessControlList.PublicRead)));
    }

    @Test
    public void testPublicAccess() throws Exception {
        S3BlobStoreConfigProvider provider = new S3BlobStoreConfigProvider();
        XStream xs = provider.getConfiguredXStream(new XStream());
        S3BlobStoreInfo config =
                (S3BlobStoreInfo)
                        xs.fromXML(
                                "<S3BlobStore><id>test</id><enabled>false</enabled><access>PUBLIC</access><useHTTPS>true</useHTTPS></S3BlobStore>");
        assertThat(
                config, hasProperty("accessControlList", is(CannedAccessControlList.PublicRead)));
    }

    @Test
    public void testPrivateAccess() throws Exception {
        S3BlobStoreConfigProvider provider = new S3BlobStoreConfigProvider();
        XStream xs = provider.getConfiguredXStream(new XStream());
        S3BlobStoreInfo config =
                (S3BlobStoreInfo)
                        xs.fromXML(
                                "<S3BlobStore><id>test</id><enabled>false</enabled><access>PRIVATE</access><useHTTPS>true</useHTTPS></S3BlobStore>");
        assertThat(
                config,
                hasProperty(
                        "accessControlList", is(CannedAccessControlList.BucketOwnerFullControl)));
    }

    @Test
    public void testPrivateAccessLowerCase() throws Exception {
        S3BlobStoreConfigProvider provider = new S3BlobStoreConfigProvider();
        XStream xs = provider.getConfiguredXStream(new XStream());
        S3BlobStoreInfo config =
                (S3BlobStoreInfo)
                        xs.fromXML(
                                "<S3BlobStore><id>test</id><enabled>false</enabled><access>private</access><useHTTPS>true</useHTTPS></S3BlobStore>");
        assertThat(
                config,
                hasProperty(
                        "accessControlList", is(CannedAccessControlList.BucketOwnerFullControl)));
    }

    @Test
    public void testPublicAccessLowerCase() throws Exception {
        S3BlobStoreConfigProvider provider = new S3BlobStoreConfigProvider();
        XStream xs = provider.getConfiguredXStream(new XStream());
        S3BlobStoreInfo config =
                (S3BlobStoreInfo)
                        xs.fromXML(
                                "<S3BlobStore><id>test</id><enabled>false</enabled><access>public</access><useHTTPS>true</useHTTPS></S3BlobStore>");
        assertThat(
                config, hasProperty("accessControlList", is(CannedAccessControlList.PublicRead)));
    }

    @Rule public ExpectedException exception = ExpectedException.none();

    @Test
    public void testInvalidAccess() throws Exception {
        S3BlobStoreConfigProvider provider = new S3BlobStoreConfigProvider();
        XStream xs = provider.getConfiguredXStream(new XStream());
        exception.expect(XStreamException.class);
        xs.fromXML(
                "<S3BlobStore><id>test</id><enabled>false</enabled><access>NOT_A_REAL_ACCESS_TYPE</access><useHTTPS>true</useHTTPS></S3BlobStore>");
    }

    @Rule public PropertyRule envParametrization = PropertyRule.system("ALLOW_ENV_PARAMETRIZATION");
    @Rule public PropertyRule awsSecretKey = PropertyRule.system("AWS_SECRET_KEY");
    @Rule public PropertyRule awsAccessKey = PropertyRule.system("AWS_ACCESS_KEY");
    @Rule public PropertyRule bucket = PropertyRule.system("BUCKET");

    /**
     * Test that the AWS keys are not converted from parameter form in xstream if
     * ALLOW_ENV_PARAMETRIZATION
     *
     * @throws Exception if something goes wrong
     */
    @Test
    public void testAWSKeysNotConverted() throws Exception {
        envParametrization.setValue("true");
        awsSecretKey.setValue("secret");
        awsAccessKey.setValue("access");
        bucket.setValue("myBucket");
        S3BlobStoreConfigProvider provider = new S3BlobStoreConfigProvider();
        XStream xs = provider.getConfiguredXStream(new XStream());
        S3BlobStoreInfo config =
                (S3BlobStoreInfo)
                        xs.fromXML(
                                "<S3BlobStore default=\"false\"><id>coviddatavizblob</id><enabled>true</enabled>"
                                        + "<bucket>${BUCKET}</bucket><prefix>blobpre99</prefix><awsAccessKey>${AWS_ACCESS_KEY}</awsAccessKey>"
                                        + "<awsSecretKey>${AWS_SECRET_KEY}</awsSecretKey><access>PUBLIC</access><maxConnections>50</maxConnections>"
                                        + "<useHTTPS>true</useHTTPS><useGzip>false</useGzip></S3BlobStore>");
        assertThat(config, hasProperty("awsSecretKey", equalTo("${AWS_SECRET_KEY}")));
        assertThat(config, hasProperty("awsAccessKey", equalTo("${AWS_ACCESS_KEY}")));
        assertThat(config, hasProperty("bucket", equalTo("${BUCKET}")));
    }
}
