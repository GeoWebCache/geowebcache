package org.geowebcache.diskquota;

import java.math.BigDecimal;
import java.text.NumberFormat;

public class Quota {

    private static final NumberFormat NICE_FORMATTER = NumberFormat.getNumberInstance();
    static {
        NICE_FORMATTER.setMinimumFractionDigits(1);
        NICE_FORMATTER.setMaximumFractionDigits(3);
    }

    private BigDecimal value;

    private StorageUnit units;

    public Quota() {
        this(BigDecimal.ZERO, StorageUnit.B);
    }

    public Quota(Quota quota) {
        value = quota.getValue();
        units = quota.getUnits();
    }

    public Quota(double value, StorageUnit units) {
        this(BigDecimal.valueOf(value), units);
    }

    public Quota(BigDecimal value, StorageUnit units) {
        this.value = value;
        this.units = units;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(double limit) {
        setValue(BigDecimal.valueOf(limit));
    }

    public void setValue(BigDecimal limit) {
        this.value = limit;
    }

    public StorageUnit getUnits() {
        return units;
    }

    public void setUnits(StorageUnit units) {
        this.units = units;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        sb.append('[').append(value).append(units).append(']');
        return sb.toString();
    }

    public synchronized void add(double amount, StorageUnit units) {

        BigDecimal sum = units.convertTo(amount, this.units).add(this.value);

        if (sum.compareTo(BigDecimal.valueOf(1024)) > 0) {
            // i.e. if added > 1024
            StorageUnit newUnit = StorageUnit.closest(sum, this.units);
            sum = this.units.convertTo(sum, newUnit);
            this.units = newUnit;
        }
        this.value = sum;
    }

    public synchronized void subtract(final double amount, final StorageUnit units) {
        subtract(BigDecimal.valueOf(amount), units);
    }

    public synchronized void subtract(final BigDecimal amount, final StorageUnit units) {
        this.value = this.value.subtract(units.convertTo(amount, this.units));
        BigDecimal abs = this.value.abs();
        if (abs.compareTo(BigDecimal.ONE) < 0 || abs.compareTo(BigDecimal.valueOf(1024)) > 0) {
            StorageUnit newUnit = StorageUnit.closest(abs, this.units);
            this.value = this.units.convertTo(this.value, newUnit);
            this.units = newUnit;
        }
    }

    /**
     * Returns the difference between this quota and the argument one, in this quota's units
     * 
     * @param quota
     * @return
     */
    public Quota difference(Quota quota) {

        Quota difference = new Quota(this);
        difference.subtract(quota.getValue(), quota.getUnits());

        return difference;
    }

    /**
     * Returns a more user friendly string representation of this quota, like in 1.1GB, 0.75MB, etc.
     * 
     * @return
     */
    public String toNiceString() {
        return NICE_FORMATTER.format(value) + units;
    }
}
