package org.geowebcache.diskquota;

public class LayerQuota {

    private String layer;

    private Quota quota;

    private Quota usedQuota;

    public String getLayer() {
        return layer;
    }

    public void setLayer(String layer) {
        this.layer = layer;
    }

    public Quota getQuota() {
        return quota;
    }

    public void setQuota(Quota quota) {
        this.quota = quota;
    }

    @Override
    public String toString() {
        return new StringBuilder(getClass().getSimpleName()).append("[layer: ").append(layer)
                .append(quota).append("]").toString();
    }

    /**
     * @return the cache usage for the layer,or {@code null} if unknown
     */
    public Quota getUsedQuota() {
        return usedQuota;
    }

    public void setUsedQuota(double cacheSize, StorageUnit units) {
        Quota usedQuota = new Quota();
        usedQuota.setLimit(cacheSize);
        usedQuota.setUnits(units);
        this.usedQuota = usedQuota;
    }
}
