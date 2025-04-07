package org.geowebcache.s3.streams;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.geowebcache.s3.S3ObjectsWrapper;

import java.util.Iterator;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * S3ObjectPathsForPrefixSupplier This class will interact with the AmazonS3 connection to retrieve all the objects with
 * prefix and bucket provided <br>
 * It will return these lazily one by one as the get methods is called
 */
public class S3ObjectPathsForPrefixSupplier implements Supplier<S3ObjectSummary> {
    private final String prefix;
    private final String bucket;
    private final S3ObjectsWrapper wrapper;
    private long count = 0;
    private final Logger logger;

    private Iterator<S3ObjectSummary> iterator;

    public S3ObjectPathsForPrefixSupplier(String prefix, String bucket, S3ObjectsWrapper wrapper, Logger logger) {
        checkNotNull(prefix, "prefix must not be null");
        checkNotNull(bucket, "bucket must not be null");
        checkNotNull(wrapper, "wrapper must not be null");
        checkNotNull(logger, "logger must not be null");

        this.prefix = prefix;
        this.bucket = bucket;
        this.wrapper = wrapper;
        this.logger = logger;
    }

    @Override
    public S3ObjectSummary get() {
        return next();
    }

    private synchronized S3ObjectSummary next() {
        if (iterator == null) {
            logger
                    .info(String.format(
                            "Creating an iterator for objects in bucket: %s with prefix: %s", bucket, prefix));
            iterator = wrapper.iterator();
        }
        if (iterator.hasNext()) {
            count++;
            return iterator.next();
        } else {
            logger
                    .info(String.format(
                            "No more objects in bucket: %s with prefix: %s supplied %d", bucket, prefix, count));
            return null;
        }
    }
}
