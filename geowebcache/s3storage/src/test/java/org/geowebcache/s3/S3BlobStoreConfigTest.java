package org.geowebcache.s3;

import static org.junit.Assert.assertEquals;

import com.amazonaws.services.s3.model.CannedAccessControlList;
import org.junit.Test;

public class S3BlobStoreConfigTest {

    @Test
    public void testACLPublic() {
        S3BlobStoreConfig config = new S3BlobStoreConfig();
        config.setAccess(Access.PUBLIC);
        assertEquals(CannedAccessControlList.PublicRead, config.getAccessControlList());
    }

    @Test
    public void testACLPrivate() {
        S3BlobStoreConfig config = new S3BlobStoreConfig();
        config.setAccess(Access.PRIVATE);
        assertEquals(CannedAccessControlList.BucketOwnerFullControl, config.getAccessControlList());
    }
}
