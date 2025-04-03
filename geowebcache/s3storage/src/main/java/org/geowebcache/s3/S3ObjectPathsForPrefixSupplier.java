package org.geowebcache.s3;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * S3ObjectPathsForPrefixSupplier
 * This class will interact with the AmazonS3 connection to retrieve all the objects
 * with prefix and bucket provided
 * <br/>
 * It will return these lazily one by one as the get methods is called
 */
public class S3ObjectPathsForPrefixSupplier implements Supplier<S3ObjectSummary> {
    private final String prefix;
    private final String bucket;
    private final S3ObjectsWrapper wrapper;
    private long count = 0;

    private Iterator<S3ObjectSummary> iterator;
    private S3ObjectPathsForPrefixSupplier(String prefix, String bucket, S3ObjectsWrapper wrapper){
        this.prefix = prefix;
        this.bucket = bucket;
        this.wrapper = wrapper;
    }


    @Override
    public S3ObjectSummary get() {
        return next();
    }

    private synchronized S3ObjectSummary next() {
        if (iterator == null) {
            S3BlobStore.log.info(String.format("Creating an iterator for objects in bucket: %s with prefix: %s", bucket, prefix));
            iterator = wrapper.iterator();
        }
        if (iterator.hasNext()) {
            count++;
            return iterator.next();
        } else {
            S3BlobStore.log.info(String.format("No more objects in bucket: %s with prefix: %s supplied %d", bucket, prefix, count));
            return null;
        }
    }

    static Builder newBuilder(){
        return new Builder();
    }

    static class Builder {
        private String prefix;
        private String bucket;
        private S3ObjectsWrapper s3ObjectsWrapper;

        public Builder withPrefix(String prefix) {
            this.prefix = prefix;
            return this;
        }
        public Builder withBucket(String bucket) {
            this.bucket = bucket;
            return this;
        }
        public Builder withWrapper(S3ObjectsWrapper wrapper) {
            this.s3ObjectsWrapper = wrapper;
            return this;
        }

        public S3ObjectPathsForPrefixSupplier build() {
            checkNotNull(prefix);
            checkNotNull(bucket);
            checkNotNull(s3ObjectsWrapper);

            return new S3ObjectPathsForPrefixSupplier(prefix, bucket, s3ObjectsWrapper);
        }
    }
}
