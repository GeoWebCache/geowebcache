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

import static com.google.common.base.Preconditions.checkNotNull;

import com.amazonaws.services.s3.iterable.S3Objects;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import java.util.Iterator;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * UnboundedS3KeySupplier This class will interact with the AmazonS3 connection to retrieve all the objects with prefix
 * and bucket provided <br>
 * It will return these lazily one by one as the get methods is called
 */
public class UnboundedS3KeySupplier implements Supplier<S3ObjectSummary> {
    private final String prefix;
    private long count = 0;
    private final Logger logger;
    private final S3Objects s3Objects;

    private Iterator<S3ObjectSummary> iterator;

    public UnboundedS3KeySupplier(String prefix, String bucket, S3Objects s3Objects, Logger logger) {
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
            logger.info("Exhausted objects with prefix: %s supplied %d".formatted(prefix, count));
            return null;
        }
    }
}
