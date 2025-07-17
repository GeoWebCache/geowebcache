/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * <p>Copyright 2025
 */
package org.geowebcache.s3.streams;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.iterable.S3Objects;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import java.util.Iterator;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.geowebcache.s3.S3BlobStore.Bounds;

/**
 * Similar to the UnboundedS3KeySupplier it retrieves keys from S3. It is slightly more optimised as it respects the x
 * bounds only fetching objects that are from the range of x bounds S3ObjectPathsForPrefixSupplier This class will
 * interact with the AmazonS3 connection to retrieve all the objects with prefix and bucket provided <br>
 * It will return these lazily one by one as the get methods is called
 */
public class BoundedS3KeySupplier implements Supplier<S3ObjectSummary> {
    private final String prefixWithoutBounds;
    private final Logger logger;
    private final AmazonS3 conn;
    private final Bounds bounds;
    private final String bucket;
    private final int batch;

    public BoundedS3KeySupplier(
            String prefixWithoutBounds, Logger logger, AmazonS3 conn, Bounds bounds, String bucket, int batch) {
        this.prefixWithoutBounds = prefixWithoutBounds;
        this.logger = logger;
        this.conn = conn;
        this.bounds = bounds;
        this.nextX = bounds.getMinX();
        this.bucket = bucket;
        this.batch = batch;
    }

    private Iterator<S3ObjectSummary> iterator;
    private long nextX;
    private long count = 0;

    @Override
    public S3ObjectSummary get() {
        return next();
    }

    private synchronized S3ObjectSummary next() {
        boolean hasNext = false;
        do {
            hasNext = iterator != null && iterator.hasNext();
            if (!hasNext) {
                iterator = null;
            }

            if (iterator == null && nextX <= bounds.getMaxX()) {
                String prefixWithX = "%s%d/".formatted(prefixWithoutBounds, nextX);
                S3Objects s3Objects =
                        S3Objects.withPrefix(conn, bucket, prefixWithX).withBatchSize(batch);
                iterator = s3Objects.iterator();
                hasNext = iterator.hasNext();
                nextX++;
            }
        } while (!hasNext && nextX <= bounds.getMaxX()); // It is exhausted if

        if (hasNext) {
            count++;
            S3ObjectSummary summary = iterator.next();
            logger.fine("%s: %s".formatted(summary.getKey(), bounds));
            return summary;
        } else {
            logger.info("Exhausted objects with prefix: %s supplied %d".formatted(prefixWithoutBounds + bounds, count));
            return null;
        }
    }
}
