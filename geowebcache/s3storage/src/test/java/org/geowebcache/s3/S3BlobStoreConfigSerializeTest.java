/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Kevin Smith, Boundless, Copyright 2017
 */

package org.geowebcache.s3;

import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.geowebcache.s3.S3BlobStoreInfo;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.XStreamException;

public class S3BlobStoreConfigSerializeTest {
    
    @Test
    public void testNoAccess() throws Exception {
        S3BlobStoreConfigProvider provider = new S3BlobStoreConfigProvider();
        XStream xs = provider.getConfiguredXStream(new XStream());
        S3BlobStoreInfo config = (S3BlobStoreInfo) xs.fromXML("<S3BlobStore><id>test</id><enabled>false</enabled><useHTTPS>true</useHTTPS></S3BlobStore>");
        assertThat(config, hasProperty("accessControlList", is(CannedAccessControlList.PublicRead)));
    }
    
    @Test
    public void testPublicAccess() throws Exception {
        S3BlobStoreConfigProvider provider = new S3BlobStoreConfigProvider();
        XStream xs = provider.getConfiguredXStream(new XStream());
        S3BlobStoreInfo config = (S3BlobStoreInfo) xs.fromXML("<S3BlobStore><id>test</id><enabled>false</enabled><access>PUBLIC</access><useHTTPS>true</useHTTPS></S3BlobStore>");
        assertThat(config, hasProperty("accessControlList", is(CannedAccessControlList.PublicRead)));
    }
    
    @Test
    public void testPrivateAccess() throws Exception {
        S3BlobStoreConfigProvider provider = new S3BlobStoreConfigProvider();
        XStream xs = provider.getConfiguredXStream(new XStream());
        S3BlobStoreInfo config = (S3BlobStoreInfo) xs.fromXML("<S3BlobStore><id>test</id><enabled>false</enabled><access>PRIVATE</access><useHTTPS>true</useHTTPS></S3BlobStore>");
        assertThat(config, hasProperty("accessControlList", is(CannedAccessControlList.BucketOwnerFullControl)));
    }
    
    @Test
    public void testPrivateAccessLowerCase() throws Exception {
        S3BlobStoreConfigProvider provider = new S3BlobStoreConfigProvider();
        XStream xs = provider.getConfiguredXStream(new XStream());
        S3BlobStoreInfo config = (S3BlobStoreInfo) xs.fromXML("<S3BlobStore><id>test</id><enabled>false</enabled><access>private</access><useHTTPS>true</useHTTPS></S3BlobStore>");
        assertThat(config, hasProperty("accessControlList", is(CannedAccessControlList.BucketOwnerFullControl)));
    }
    @Test
    public void testPublicAccessLowerCase() throws Exception {
        S3BlobStoreConfigProvider provider = new S3BlobStoreConfigProvider();
        XStream xs = provider.getConfiguredXStream(new XStream());
        S3BlobStoreInfo config = (S3BlobStoreInfo) xs.fromXML("<S3BlobStore><id>test</id><enabled>false</enabled><access>public</access><useHTTPS>true</useHTTPS></S3BlobStore>");
        assertThat(config, hasProperty("accessControlList", is(CannedAccessControlList.PublicRead)));
    }
    
    @Rule
    public ExpectedException exception = ExpectedException.none();
    
    @Test
    public void testInvalidAccess() throws Exception {
        S3BlobStoreConfigProvider provider = new S3BlobStoreConfigProvider();
        XStream xs = provider.getConfiguredXStream(new XStream());
        exception.expect(XStreamException.class);
        S3BlobStoreInfo config = (S3BlobStoreInfo) xs.fromXML("<S3BlobStore><id>test</id><enabled>false</enabled><access>NOT_A_REAL_ACCESS_TYPE</access><useHTTPS>true</useHTTPS></S3BlobStore>");
    }
    
}
