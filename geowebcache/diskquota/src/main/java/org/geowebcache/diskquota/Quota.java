package org.geowebcache.diskquota;

public class Quota {

    private double value;

    private StorageUnit units;

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
        if (this.units != StorageUnit.TB && value / 1024 > 1) {
            this.value = this.units.convertTo(this.value, StorageUnit.GB);
            this.units = StorageUnit.GB;
        }
    }

    public void substract(final double amount, final StorageUnit units) {
        this.value -= units.convertTo(amount, this.units);
    }
}
