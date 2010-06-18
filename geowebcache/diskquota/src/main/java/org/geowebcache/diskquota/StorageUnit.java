package org.geowebcache.diskquota;

public enum StorageUnit {

    B(1), //
    KB(1024), //
    MB(1024 * 1024), //
    GB(1024 * 1024 * 1024), //
    TB(1024 * 1024 * 1024 * 1024);

    protected final long bytes;

    private StorageUnit(final long bytes) {
        this.bytes = bytes;
    }

    protected final long toBytes(double value) {
        return (long) (bytes * value);
    }

    protected final double fromBytes(long value) {
        return value / (double) bytes;
    }

    public final double convertTo(double value, StorageUnit target) {
        return target.fromBytes(toBytes(value));
    }
}
