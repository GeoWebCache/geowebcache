package org.geowebcache.diskquota;

public class LayerQuota {

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

    private String layer;

    private Quota quota;
}
