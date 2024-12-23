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
 * @author Gabriel Roldan (OpenGeo) 2010
 */
package org.geowebcache.diskquota.storage;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Enumerates the various storage units according to the power of two based units (instead of the more common commercial
 * prefixes, often used but not entirely correct)
 */
public enum StorageUnit {
    /** Byte */
    B(BigDecimal.ONE), //
    /** Kibibyte (2<sup>10</sup> Bytes) */
    KiB(B.bytes.multiply(BigDecimal.valueOf(1024))), //
    /** Mebibyte (2<sup>20</sup> Bytes) */
    MiB(KiB.bytes.multiply(BigDecimal.valueOf(1024))), //
    /** Gibibyte (2<sup>30</sup> Bytes) */
    GiB(MiB.bytes.multiply(BigDecimal.valueOf(1024))), //
    /** Tebibyte (2<sup>40</sup> Bytes) */
    TiB(GiB.bytes.multiply(BigDecimal.valueOf(1024))), //
    /** Pebibyte (2<sup>50</sup> Bytes) */
    PiB(TiB.bytes.multiply(BigDecimal.valueOf(1024))), //
    /** Exibyte (2<sup>60</sup> Bytes) */
    EiB(PiB.bytes.multiply(BigDecimal.valueOf(1024))), //
    /** Zebibyte (2<sup>70</sup> Bytes) */
    ZiB(EiB.bytes.multiply(BigDecimal.valueOf(1024))),
    /** Yobibyte (2<sup>80</sup> Bytes) */
    YiB(ZiB.bytes.multiply(BigDecimal.valueOf(1024)));

    private final BigDecimal bytes;

    private StorageUnit(final BigDecimal bytes) {
        this.bytes = bytes;
    }

    public final BigInteger toBytes(double value) {
        return toBytes(BigDecimal.valueOf(value));
    }

    public final BigInteger toBytes(BigDecimal value) {
        BigDecimal toBytes = bytes.multiply(value);
        return toBytes.toBigInteger();
    }

    public final BigDecimal fromBytes(BigInteger value) {
        return new BigDecimal(value).divide(bytes);
    }

    public BigDecimal convertTo(double value, StorageUnit target) {
        return convertTo(BigDecimal.valueOf(value), target);
    }

    public final BigDecimal convertTo(BigDecimal value, StorageUnit target) {
        return target.fromBytes(toBytes(value));
    }

    /** Returns the most appropriate storage unit to represent the given amount */
    public static StorageUnit bestFit(double value, StorageUnit units) {
        return bestFit(BigDecimal.valueOf(value), units);
    }

    /** Returns the most appropriate storage unit to represent the given amount */
    public static StorageUnit bestFit(BigDecimal value, StorageUnit units) {
        BigDecimal bytes = new BigDecimal(units.toBytes(value));
        // use compareTo because BigDecimal.equals does not consider 1.0 and 1.00 to be equal, so
        // can't do, for example, bytes.min(TiB.bytes).equals(YiB.bytes)
        if (bytes.compareTo(YiB.bytes) >= 0) {
            return YiB;
        }
        if (bytes.compareTo(ZiB.bytes) >= 0) {
            return ZiB;
        }
        if (bytes.compareTo(EiB.bytes) >= 0) {
            return EiB;
        }
        if (bytes.compareTo(PiB.bytes) >= 0) {
            return PiB;
        }
        if (bytes.compareTo(TiB.bytes) >= 0) {
            return TiB;
        }
        if (bytes.compareTo(GiB.bytes) >= 0) {
            return GiB;
        }
        if (bytes.compareTo(MiB.bytes) >= 0) {
            return MiB;
        }
        if (bytes.compareTo(KiB.bytes) >= 0) {
            return KiB;
        }

        return B;
    }

    public static StorageUnit bestFit(BigInteger bytes) {
        return bestFit(new BigDecimal(bytes), B);
    }

    /** Returns {@code MB} instead of {@code MiB}, {@code GB} instead of {@code GiB}, etc. */
    public String toNiceString() {
        String s = toString();
        if (B == this) {
            return s;
        }

        return new StringBuilder().append(s.charAt(0)).append(s.charAt(2)).toString();
    }
}
