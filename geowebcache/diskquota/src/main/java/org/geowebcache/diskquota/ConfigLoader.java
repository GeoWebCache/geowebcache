package org.geowebcache.diskquota;

import java.io.InputStream;
import java.util.List;

import com.thoughtworks.xstream.XStream;

class ConfigLoader {

    private ConfigLoader() {
        // nothing to do
    }

    public static DiskQuotaConfig loadConfiguration(final InputStream configStream) {
        XStream xstream = getConfiguredXStram();
        DiskQuotaConfig fromXML = (DiskQuotaConfig) xstream.fromXML(configStream);
        return fromXML;
    }

    private static XStream getConfiguredXStram() {
        XStream xs = new XStream();
        xs.setMode(XStream.NO_REFERENCES);

        xs.alias("gwcQuotaConfiguration", DiskQuotaConfig.class);
        xs.alias("layerQuotas", List.class);
        xs.alias("LayerQuota", LayerQuota.class);
        xs.alias("Quota", Quota.class);
        return xs;
    }
}
