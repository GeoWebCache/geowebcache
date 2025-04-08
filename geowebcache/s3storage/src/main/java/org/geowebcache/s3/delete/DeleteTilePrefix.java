package org.geowebcache.s3.delete;

import static com.google.common.base.Preconditions.checkNotNull;

public class DeleteTilePrefix implements DeleteTileRange {
    private final String prefix;
    private final String bucket;
    private final String path;

    public DeleteTilePrefix(String prefix, String bucket, String path) {
        checkNotNull(prefix, "prefix must not be null");
        checkNotNull(bucket, "bucket must not be null");
        checkNotNull(path, "path must not be null");

        this.prefix = prefix;
        this.bucket = bucket;
        this.path = path;
    }

    @Override
    public String path() {
        return path;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getBucket() {
        return bucket;
    }
}
