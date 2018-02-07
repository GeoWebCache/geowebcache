package org.geowebcache.s3;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.amazonaws.services.s3.model.CannedAccessControlList;

public class S3BlobStoreConfigTest {

    @Test
    public void testACLPublic() {
        S3BlobStoreInfo config = new S3BlobStoreInfo();
        config.setAccess(Access.PUBLIC);
        assertEquals(CannedAccessControlList.PublicRead, config.getAccessControlList());
    }
    
    @Test
    public void testACLPrivate() {
        S3BlobStoreInfo config = new S3BlobStoreInfo();
        config.setAccess(Access.PRIVATE);
        assertEquals(CannedAccessControlList.BucketOwnerFullControl, config.getAccessControlList());
    }
}
