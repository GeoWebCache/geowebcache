package org.geowebcache.diskquota;

import java.text.NumberFormat;

public class Quota {

    private static final NumberFormat NICE_FORMATTER = NumberFormat.getNumberInstance();
    static {
        NICE_FORMATTER.setMinimumFractionDigits(1);
        NICE_FORMATTER.setMaximumFractionDigits(3);
    }

    private double value;

    private StorageUnit units;

    public Quota() {
        value = 0;
        units = StorageUnit.B;
    }

    public Quota(Quota quota) {
        value = quota.getValue();
        units = quota.getUnits();
    }

    public Quota(double value, StorageUnit units) {
        this.value = value;
        this.units = units;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double limit) {
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

        this.value += units.convertTo(amount, this.units);

        if (value > 1024) {
            switch (this.units) {
            case B:
                this.value /= 1024;
                this.units = StorageUnit.KB;
                break;
            case KB:
                this.value /= 1024;
                this.units = StorageUnit.MB;
                break;
            case MB:
                this.value /= 1024;
                this.units = StorageUnit.GB;
                break;
            case GB:
                this.value /= 1024;
                this.units = StorageUnit.TB;
                break;
            case TB:
                // nothing to do, so far StorageUnit handles no units larger than TB?
                break;
            default:
                throw new IllegalStateException();
            }
        }
    }

    public void substract(final double amount, final StorageUnit units) {
        this.value -= units.convertTo(amount, this.units);
    }

    public double getValue(final StorageUnit targetUnits) {
        return this.units.convertTo(value, targetUnits);
    }

    /**
     * Returns the difference between this quota and the argument one, in this quota's units
     * 
     * @param quota
     * @return
     */
    public Quota difference(Quota quota) {
        StorageUnit thisUnits = getUnits();
        double thisValue = getValue();

        double value = quota.getUnits().convertTo(quota.getValue(), thisUnits);
        double difference = thisValue - value;
        return new Quota(difference, thisUnits);
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
