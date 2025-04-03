package org.geowebcache.s3;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.iterable.S3Objects;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import java.util.Iterator;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;

public class S3ObjectsWrapper {
    private S3Objects s3Objects;

    public S3ObjectsWrapper(S3Objects s3Object) {
        checkNotNull(s3Object);
        this.s3Objects = s3Object;
    }

    public static S3ObjectsWrapper withPrefix(AmazonS3 s3, String bucketName, String prefix) {
        return new S3ObjectsWrapper(S3Objects.withPrefix(s3, bucketName, prefix));
    }

    public Iterator<S3ObjectSummary> iterator() {
        return s3Objects.iterator();
    }

}
