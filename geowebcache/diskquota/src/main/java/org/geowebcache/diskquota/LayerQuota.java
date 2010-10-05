package org.geowebcache.diskquota;

public final class LayerQuota {

    private String layer;

    private Quota quota;

    private String expirationPolicyName;

    private Quota usedQuota = new Quota();

    private transient ExpirationPolicy expirationPolicy;

    private transient boolean dirty;

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
        if (usedQuota == null) {
            usedQuota = new Quota();
        }

        return this;
    }

    public String getExpirationPolicyName() {
        return expirationPolicyName;
    }

    public ExpirationPolicy getExpirationPolicy() {
        return expirationPolicy;
    }

    public void setExpirationPolicy(ExpirationPolicy expirationPolicy) {
        this.expirationPolicy = expirationPolicy;
    }

    public String getLayer() {
        return layer;
    }

    /**
     * @return The layer's configured disk quota, or {@code null} if it has no max quota set
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

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public boolean isDirty() {
        return this.dirty;
    }
}
