package org.geowebcache.diskquota;

public class Quota {

    private long limit;

    private StorageUnit units;

    private String expirationPolicy;

    public long getLimit() {
        return limit;
    }

    public void setLimit(long limit) {
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

}
