/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Gabriel Roldan (OpenGeo) 2010
 */
package org.geowebcache.diskquota.storage;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.NumberFormat;

/**
 * A <b>Mutable</b> representation of the disk usage of a given cache tile set, given by a value and
 * a {@link StorageUnit storage unit}.
 *
 * <p>Instances of this class are <b>not</b> thread safe.
 *
 * @author groldan
 */
public class Quota implements Cloneable, Comparable<Quota>, Serializable {

    private static final long serialVersionUID = -3817255124248938529L;

    private static final NumberFormat NICE_FORMATTER = NumberFormat.getNumberInstance();

    static {
        NICE_FORMATTER.setMinimumFractionDigits(1);
        NICE_FORMATTER.setMaximumFractionDigits(2);
    }

    private int id;

    private String tileSetId;

    private BigInteger bytes;

    public Quota() {
        this(BigInteger.ZERO);
    }

    public Quota(BigInteger bytes) {
        this.bytes = bytes;
    }

    public Quota(Quota quota) {
        id = quota.id;
        tileSetId = quota.tileSetId;
        bytes = quota.getBytes();
    }

    public int getId() {
        return id;
    }

    public String getTileSetId() {
        return tileSetId;
    }

    public void setTileSetId(String tileSetId) {
        this.tileSetId = tileSetId;
    }

    public BigInteger getBytes() {
        return bytes;
    }

    public void setBytes(BigInteger bytes) {
        this.bytes = bytes;
    }

    public void setBytes(long bytes) {
        setBytes(BigInteger.valueOf(bytes));
    }

    public Quota(double value, StorageUnit units) {
        this(BigDecimal.valueOf(value), units);
    }

    public Quota(BigDecimal value, StorageUnit units) {
        this.bytes = units.toBytes(value);
    }

    /** Supports initialization of instance variables during XStream deserialization */
    private Object readResolve() {
        if (this.bytes == null) {
            this.bytes = BigInteger.ZERO;
        }

        return this;
    }

    @Override
    public String toString() {
        StorageUnit bestFit = StorageUnit.bestFit(bytes);
        BigDecimal value = StorageUnit.B.convertTo(new BigDecimal(bytes), bestFit);
        return new StringBuilder(NICE_FORMATTER.format(value))
                .append(bestFit.toString())
                .toString();
    }

    /** Adds {@code bytes} bytes to this quota */
    public void add(BigInteger bytes) {
        this.bytes = this.bytes.add(bytes);
    }

    /** Shorthand for {@link #add(BigInteger) add(BigInteger.valueOf(bytes))} */
    public void addBytes(long bytes) {
        this.bytes = this.bytes.add(BigInteger.valueOf(bytes));
    }

    /** Shorthand for {@link #add(BigInteger) add(units.toBytes(amount))} */
    public void add(double amount, StorageUnit units) {
        this.bytes = this.bytes.add(units.toBytes(amount));
    }

    /** Shorthand for {@link #add(BigInteger) add(quota.getBytes())} */
    public void add(final Quota quota) {
        this.bytes = this.bytes.add(quota.getBytes());
    }

    /** Subtracts {@code bytes} bytes from this quota */
    public void subtract(final BigInteger bytes) {
        this.bytes = this.bytes.subtract(bytes);
    }

    /** Shorthand for {@link #subtract(BigInteger) subtract(quota.getBytes())} */
    public void subtract(final Quota quota) {
        subtract(quota.getBytes());
    }

    /** Shorthand for {@link #subtract(BigInteger) subtract(units.toBytes(amount))} */
    public void subtract(final double amount, final StorageUnit units) {
        subtract(units.toBytes(amount));
    }

    /** Returns the difference between this quota and the argument one, in this quota's units */
    public Quota difference(Quota quota) {
        BigInteger difference = this.bytes.subtract(quota.getBytes());
        return new Quota(difference);
    }

    /**
     * Returns a more user friendly string representation of this quota, like in 1.1GB, 0.75MB, etc.
     */
    public String toNiceString() {
        StorageUnit bestFit = StorageUnit.bestFit(bytes);
        BigDecimal value = StorageUnit.B.convertTo(new BigDecimal(bytes), bestFit);
        return new StringBuilder(NICE_FORMATTER.format(value))
                .append(' ')
                .append(bestFit.toNiceString())
                .toString();
    }

    /**
     * @param quota quota to be compared against this one
     * @return {@code this} or {@code quota}, the one that represents a lower amount
     */
    public Quota min(Quota quota) {
        BigInteger min = this.bytes.min(quota.getBytes());
        return this.bytes.equals(min) ? this : quota;
    }

    /** @see java.lang.Comparable#compareTo(java.lang.Object) */
    @Override
    public int compareTo(Quota o) {
        if (o == null) {
            throw new NullPointerException("Can't compare against null");
        }
        return bytes.compareTo(o.getBytes());
    }

    /** Shorthand for {@code setBytes(unit.convertTo(value, StorageUnit.B).toBigInteger())} */
    public void setValue(double value, StorageUnit unit) {
        setBytes(unit.convertTo(value, StorageUnit.B).toBigInteger());
    }

    @Override
    public Quota clone() {
        return new Quota(this);
    }

    public void setId(int id) {
        // TODO Auto-generated method stub

    }
}
