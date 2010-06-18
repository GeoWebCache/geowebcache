package org.geowebcache.diskquota;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class DiskQuotaConfig {

    private Quota defaultQuota;

    private List<LayerQuota> layerQuotas;

    private transient Map<String, LayerQuota> layerQuotasMap;

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
        this.layerQuotasMap = null;
    }

    public void setLayerQuota(String layerName, LayerQuota lq) {
        getLayerQuotasMap().put(layerName, lq);
    }

    public LayerQuota getLayerQuota(final String layerName) {
        LayerQuota quota = getLayerQuotasMap().get(layerName);
        return quota;
    }

    private Map<String, LayerQuota> getLayerQuotasMap() {
        if (layerQuotasMap == null) {
            layerQuotasMap = new HashMap<String, LayerQuota>();

            if (layerQuotas != null) {
                for (LayerQuota lq : layerQuotas) {
                    layerQuotasMap.put(lq.getLayer(), lq);
                }
            }
        }
        return layerQuotasMap;
    }

    public void remove(LayerQuota lq) {
        for (Iterator<LayerQuota> it = layerQuotas.iterator(); it.hasNext();) {
            LayerQuota quota = it.next();
            if (quota.getLayer().equals(lq.getLayer())) {
                it.remove();
                getLayerQuotasMap().remove(lq.getLayer());
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
