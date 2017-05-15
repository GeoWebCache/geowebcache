package org.geowebcache.s3;

import static org.junit.Assert.*;

import org.junit.Test;

import com.amazonaws.services.s3.model.CannedAccessControlList;

public class S3BlobStoreConfigTest {

    @Test
    public void testACLPublic() {
        S3BlobStoreConfig config = new S3BlobStoreConfig();
        config.setAccess("public");
        assertEquals(CannedAccessControlList.PublicRead, config.getAccessControlList());
    }
    
    @Test
    public void testACLPrivate() {
        S3BlobStoreConfig config = new S3BlobStoreConfig();
        config.setAccess("private");
        assertEquals(CannedAccessControlList.BucketOwnerFullControl, config.getAccessControlList());
    }

}
