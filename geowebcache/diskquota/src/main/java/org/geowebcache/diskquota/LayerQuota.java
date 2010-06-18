package org.geowebcache.diskquota;

public class LayerQuota {

    private String layer;

    private Quota quota;

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
}
