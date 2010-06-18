package org.geowebcache.diskquota;

public enum StorageUnit {

    B(1) {
        @Override
        public double convertTo(double value, StorageUnit target) {
            return 0;
        }
    },
    KB(1024) {
        @Override
        public double convertTo(double value, StorageUnit target) {
            return 0;
        }
    },
    MB(1024 * 1024) {
        @Override
        public double convertTo(double value, StorageUnit target) {
            return 0;
         }
    },
    GB(1024 * 1024 * 1024) {
        @Override
        public double convertTo(double value, StorageUnit target) {
            return 0;
        }
    },
    TB(1024 * 1024 * 1024 * 1024) {
        @Override
        public double convertTo(double value, StorageUnit target) {
            return 0;
        }
    };

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

    public double convertTo(double value, StorageUnit target) {
        return target.fromBytes(toBytes(value));
    }
}
