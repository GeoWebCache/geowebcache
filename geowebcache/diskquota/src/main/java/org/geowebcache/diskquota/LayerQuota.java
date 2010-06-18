package org.geowebcache.diskquota;

public class LayerQuota {

    private String layer;

    private Quota quota;

    private String expirationPolicyName;

    private Quota usedQuota;

    private transient LayerQuotaExpirationPolicy expirationPolicy;

    public String getExpirationPolicyName() {
        return expirationPolicyName;
    }

    public void setExpirationPolicyName(String expirationPolicyName) {
        this.expirationPolicyName = expirationPolicyName;
    }

    public LayerQuotaExpirationPolicy getExpirationPolicy() {
        return expirationPolicy;
    }

    public void setExpirationPolicy(LayerQuotaExpirationPolicy expirationPolicy) {
        this.expirationPolicy = expirationPolicy;
    }

    public void setUsedQuota(Quota usedQuota) {
        this.usedQuota = usedQuota;
    }

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
                .append("Expiration policy: '").append(expirationPolicyName).append("', quota:")
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
        usedQuota.setValue(cacheSize);
        usedQuota.setUnits(units);
        this.usedQuota = usedQuota;
    }
}
