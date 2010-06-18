package org.geowebcache.diskquota;

import java.math.BigDecimal;

public enum StorageUnit {
    /**
     * Byte
     */
    B(BigDecimal.ONE), //
    /**
     * Kibibyte (2<sup>10</sup> Bytes)
     */
    KiB(B.bytes.multiply(BigDecimal.valueOf(1024))), //
    /**
     * Mebibyte (2<sup>20</sup> Bytes)
     */
    MiB(KiB.bytes.multiply(BigDecimal.valueOf(1024))), //
    /**
     * Gibibyte (2<sup>30</sup> Bytes)
     */
    GiB(MiB.bytes.multiply(BigDecimal.valueOf(1024))), //
    /**
     * Tebibyte (2<sup>40</sup> Bytes)
     */
    TiB(GiB.bytes.multiply(BigDecimal.valueOf(1024))), //
    /**
     * Pebibyte (2<sup>50</sup> Bytes)
     */
    PiB(TiB.bytes.multiply(BigDecimal.valueOf(1024))), //
    /**
     * Exibyte (2<sup>60</sup> Bytes)
     */
    EiB(PiB.bytes.multiply(BigDecimal.valueOf(1024))), //
    /**
     * Zebibyte (2<sup>70</sup> Bytes)
     */
    ZiB(EiB.bytes.multiply(BigDecimal.valueOf(1024))),
    /**
     * Yobibyte (2<sup>80</sup> Bytes)
     */
    YiB(EiB.bytes.multiply(BigDecimal.valueOf(1024)));

    private final BigDecimal bytes;

    private StorageUnit(final BigDecimal bytes) {
        this.bytes = bytes;
    }

    protected final BigDecimal toBytes(BigDecimal value) {
        return bytes.multiply(value);
    }

    protected final BigDecimal fromBytes(BigDecimal value) {
        return value.divide(bytes);
    }

    public BigDecimal convertTo(double value, StorageUnit target) {
        return convertTo(BigDecimal.valueOf(value), target);
    }

    public final BigDecimal convertTo(BigDecimal value, StorageUnit target) {
        return target.fromBytes(toBytes(value));
    }

    /**
     * Returns the most appropriate storage unit to represent the given amount
     * 
     * @param value
     * @param units
     * @return
     */
    public static StorageUnit closest(BigDecimal value, StorageUnit units) {
        BigDecimal bytes = units.convertTo(value, B);
        if (bytes.min(YiB.bytes) == YiB.bytes) {
            return YiB;
        }
        if (bytes.min(ZiB.bytes) == ZiB.bytes) {
            return ZiB;
        }
        if (bytes.min(EiB.bytes) == EiB.bytes) {
            return EiB;
        }
        if (bytes.min(PiB.bytes) == PiB.bytes) {
            return PiB;
        }
        if (bytes.min(TiB.bytes) == TiB.bytes) {
            return TiB;
        }
        if (bytes.min(GiB.bytes) == GiB.bytes) {
            return GiB;
        }
        if (bytes.min(MiB.bytes) == MiB.bytes) {
            return MiB;
        }
        if (bytes.min(KiB.bytes) == KiB.bytes) {
            return KiB;
        }

        return B;
    }

}
