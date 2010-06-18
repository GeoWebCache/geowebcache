package org.geowebcache.diskquota;

import java.util.HashMap;
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

    public List<LayerQuota> getLayerQuotas() {
        return layerQuotas;
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

}
