package org.geowebcache.diskquota;

public final class LayerQuota {

    private String layer;

    private Quota quota;

    private String expirationPolicyName;

    private Quota usedQuota = new Quota();

    private transient LayerQuotaExpirationPolicy expirationPolicy;

    public LayerQuota(final String layer, final String expirationPolicyName) {
        this.layer = layer;
        this.expirationPolicyName = expirationPolicyName;
        readResolve();
    }

    /**
     * Supports initialization of instance variables during XStream deserialization
     * 
     * @return
     */
    private Object readResolve() {
        if (quota == null) {
            quota = new Quota();
        }
        if (usedQuota == null) {
            usedQuota = new Quota();
        }

        return this;
    }

    public String getExpirationPolicyName() {
        return expirationPolicyName;
    }

    public LayerQuotaExpirationPolicy getExpirationPolicy() {
        return expirationPolicy;
    }

    public void setExpirationPolicy(LayerQuotaExpirationPolicy expirationPolicy) {
        this.expirationPolicy = expirationPolicy;
    }

    public String getLayer() {
        return layer;
    }

    /**
     * The layer's disk quota
     * 
     * @return
     */
    public Quota getQuota() {
        return quota;
    }

    /**
     * @return the cache usage for the layer. Non null, but a zero value might mean unknown
     */
    public Quota getUsedQuota() {
        return usedQuota;
    }

    @Override
    public String toString() {
        return new StringBuilder(getClass().getSimpleName()).append("[layer: ").append(layer)
                .append(", Expiration policy: '").append(expirationPolicyName).append("', quota:")
                .append(quota).append("]").toString();
    }
}
