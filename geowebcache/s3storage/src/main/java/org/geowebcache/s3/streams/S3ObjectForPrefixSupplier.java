package org.geowebcache.s3.streams;

import static com.google.common.base.Preconditions.checkNotNull;

import com.amazonaws.services.s3.iterable.S3Objects;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import java.util.Iterator;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * S3ObjectPathsForPrefixSupplier This class will interact with the AmazonS3 connection to retrieve all the objects with
 * prefix and bucket provided <br>
 * It will return these lazily one by one as the get methods is called
 */
public class S3ObjectForPrefixSupplier implements Supplier<S3ObjectSummary> {
    private final String prefix;
    private long count = 0;
    private final Logger logger;
    private final S3Objects s3Objects;

    private Iterator<S3ObjectSummary> iterator;

    public S3ObjectForPrefixSupplier(String prefix, String bucket, S3Objects s3Objects, Logger logger) {
        checkNotNull(prefix, "prefix must not be null");
        checkNotNull(bucket, "bucket must not be null");
        checkNotNull(s3Objects, "s3Objects must not be null");
        checkNotNull(logger, "logger must not be null");

        this.prefix = prefix;
        this.s3Objects = s3Objects;
        this.logger = logger;
    }

    @Override
    public S3ObjectSummary get() {
        return next();
    }

    private synchronized S3ObjectSummary next() {
        if (iterator == null) {
            iterator = s3Objects.iterator();
        }
        if (iterator.hasNext()) {
            count++;
            return iterator.next();
        } else {
            logger.info(String.format("Exhausted objects with prefix: %s supplied %d", prefix, count));
            return null;
        }
    }
}
