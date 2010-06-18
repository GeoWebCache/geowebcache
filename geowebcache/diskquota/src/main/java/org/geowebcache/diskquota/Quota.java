package org.geowebcache.diskquota;

public class Quota {

    private double limit;

    private StorageUnit units;

    private String expirationPolicy;

    public double getLimit() {
        return limit;
    }

    public void setLimit(double limit) {
        this.limit = limit;
    }

    public StorageUnit getUnits() {
        return units;
    }

    public void setUnits(StorageUnit units) {
        this.units = units;
    }

    public String getExpirationPolicy() {
        return expirationPolicy;
    }

    public void setExpirationPolicy(String expirationPolicy) {
        this.expirationPolicy = expirationPolicy;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        sb.append("[limit: ").append(limit);
        sb.append(", units: ").append(units);
        sb.append(", expirationPolicy: ").append(expirationPolicy).append(']');
        return sb.toString();
    }

    public synchronized void add(double amount, StorageUnit units) {
        this.limit += units.convertTo(amount, this.units);
        if (this.units != StorageUnit.TB && limit / 1024 > 1) {
            this.limit = this.units.convertTo(this.limit, StorageUnit.GB);
            this.units = StorageUnit.GB;
        }
    }

    public void substract(final double amount, final StorageUnit units) {
        this.limit -= units.convertTo(amount, this.units);
    }
}
