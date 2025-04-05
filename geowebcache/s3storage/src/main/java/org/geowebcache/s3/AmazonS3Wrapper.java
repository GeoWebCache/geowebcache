package org.geowebcache.s3;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.DeleteObjectsResult;

public class AmazonS3Wrapper {
    private final AmazonS3 conn;

    public AmazonS3Wrapper(AmazonS3 conn) {
        this.conn = conn;
    }

    public DeleteObjectsResult deleteObjects(DeleteObjectsRequest deleteObjectsRequest) throws SdkClientException {
        return conn.deleteObjects(deleteObjectsRequest);
    }
}
