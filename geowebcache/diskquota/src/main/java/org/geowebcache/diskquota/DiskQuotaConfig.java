package org.geowebcache.diskquota;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class DiskQuotaConfig {

    private Quota defaultQuota;

    private List<LayerQuota> layerQuotas;

    private transient Map<String, LayerQuota> layerQuotasMap = new HashMap<String, LayerQuota>();

    public Quota getDefaultQuota() {
        return defaultQuota;
    }

    public void setDefaultQuota(Quota quota) {
        defaultQuota = quota;
    }

    @SuppressWarnings("unchecked")
    public List<LayerQuota> getLayerQuotas() {
        return layerQuotas == null ? Collections.EMPTY_LIST : layerQuotas;
    }

    public void setLayerQuotas(List<LayerQuota> quotas) {
        this.layerQuotas = quotas;
        buildMap(quotas);
    }

    public Quota getLayerQuota(final String layerName) {
        if (layerQuotasMap.isEmpty()) {
            buildMap(layerQuotas);
        }
        LayerQuota quota = layerQuotasMap.get(layerName);
        if (quota != null) {
            return quota.getQuota();
        }
        return defaultQuota;// may be null
    }

    private void buildMap(List<LayerQuota> quotas) {
        layerQuotasMap.clear();
        if (quotas == null) {
            return;
        }
        if (quotas == null || quotas.size() == 0) {
            return;
        }
        for (LayerQuota lq : quotas) {
            layerQuotasMap.put(lq.getLayer(), lq);
        }
    }

    public void remove(LayerQuota lq) {
        for (Iterator<LayerQuota> it = layerQuotas.iterator(); it.hasNext();) {
            LayerQuota quota = it.next();
            if (quota.getLayer().equals(lq.getLayer())) {
                it.remove();
                layerQuotasMap.remove(lq.getLayer());
                break;
            }
        }
    }

    /**
     * @return number of explicitly configured layers (ie, not affected by
     *         {@link #getDefaultQuota() default quota})
     */
    public int getNumLayers() {
        return layerQuotas == null ? 0 : layerQuotas.size();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        sb.append("[default: ").append(defaultQuota);
        for (LayerQuota lq : getLayerQuotas()) {
            sb.append("\n").append(lq);
        }
        sb.append("]");
        return sb.toString();
    }
}
