package org.geowebcache.diskquota;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Holds the quota configuration for all the registered layers as well as the instance wide settings
 * such as cache disk block size, maximum number of concurrent cache clean ups, etc.
 * 
 * @author groldan
 * 
 */
public class DiskQuotaConfig {

    static final int DEFAULT_DISK_BLOCK_SIZE = 4096;

    static final int DEFAULT_CLEANUP_FREQUENCY = 10 * 60;

    static final TimeUnit DEFAULT_CLEANUP_UNITS = TimeUnit.SECONDS;

    static final int DEFAULT_MAX_CONCURRENT_CLEANUPS = 2;

    static String DEFAULT_GLOBAL_POLICY_NAME = "LFU";

    private int diskBlockSize;

    private int cacheCleanUpFrequency;

    private TimeUnit cacheCleanUpUnits;

    private int maxConcurrentCleanUps;

    private String globalExpirationPolicyName;

    private Quota globalQuota;

    private List<LayerQuota> layerQuotas;

    private transient Map<String, LayerQuota> layerQuotasMap;

    private transient ExpirationPolicy expirationPolicy;

    private transient boolean dirty;

    public DiskQuotaConfig() {
        readResolve();
    }

    /**
     * Supports initialization of instance variables during XStream deserialization
     * 
     * @return
     */
    private Object readResolve() {
        if (diskBlockSize == 0) {
            diskBlockSize = DEFAULT_DISK_BLOCK_SIZE;
        }
        if (cacheCleanUpFrequency == 0) {
            cacheCleanUpFrequency = DEFAULT_CLEANUP_FREQUENCY;
        }
        if (layerQuotas == null) {
            layerQuotas = new ArrayList<LayerQuota>(2);
        }
        if (maxConcurrentCleanUps == 0) {
            maxConcurrentCleanUps = DEFAULT_MAX_CONCURRENT_CLEANUPS;
        }
        if (cacheCleanUpUnits == null) {
            cacheCleanUpUnits = DEFAULT_CLEANUP_UNITS;
        }
        if (globalQuota != null && globalExpirationPolicyName == null) {
            globalExpirationPolicyName = DEFAULT_GLOBAL_POLICY_NAME;
        }
        return this;
    }

    public int getDiskBlockSize() {
        return diskBlockSize;
    }

    public void setDiskBlockSize(int blockSizeBytes) {
        if (blockSizeBytes <= 0) {
            throw new IllegalArgumentException("Block size shall be a positive integer");
        }
        this.diskBlockSize = blockSizeBytes;
    }

    public int getCacheCleanUpFrequency() {
        return cacheCleanUpFrequency;
    }

    public void setCacheCleanUpFrequency(int cacheCleanUpFrequency) {
        if (cacheCleanUpFrequency < 0) {
            throw new IllegalArgumentException("cacheCleanUpFrequency shall be a positive integer");
        }
        this.cacheCleanUpFrequency = cacheCleanUpFrequency;
    }

    public TimeUnit getCacheCleanUpUnits() {
        return cacheCleanUpUnits;
    }

    public void setCacheCleanUpUnits(TimeUnit cacheCleanUpUnit) {
        if (cacheCleanUpUnit == null) {
            throw new IllegalArgumentException("cacheCleanUpUnits can't be null");
        }
        this.cacheCleanUpUnits = cacheCleanUpUnit;
    }

    public List<LayerQuota> getLayerQuotas() {
        return layerQuotas;
    }

    public synchronized void setLayerQuotas(List<LayerQuota> quotas) {
        this.layerQuotas = quotas == null ? new ArrayList<LayerQuota>(2) : quotas;
        this.layerQuotasMap = null;
    }

    public LayerQuota getLayerQuota(final String layerName) {
        LayerQuota quota = getLayerQuotasMap().get(layerName);
        return quota;
    }

    private synchronized Map<String, LayerQuota> getLayerQuotasMap() {
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

    public synchronized void remove(LayerQuota lq) {
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
        return layerQuotas.size();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        sb.append("[");
        for (LayerQuota lq : getLayerQuotas()) {
            sb.append("\n\t").append(lq);
        }
        sb.append("]");
        return sb.toString();
    }

    public int getMaxConcurrentCleanUps() {
        return maxConcurrentCleanUps;
    }

    public void setMaxConcurrentCleanUps(int nThreads) {
        if (nThreads <= 0) {
            throw new IllegalArgumentException("nThreads shall be a positive integer: " + nThreads);
        }
        this.maxConcurrentCleanUps = nThreads;
    }

    public Quota getGlobalQuota() {
        return this.globalQuota;
    }

    public ExpirationPolicy getGlobalExpirationPolicy() {
        return this.expirationPolicy;
    }

    public String getGlobalExpirationPolicyName() {
        return this.globalExpirationPolicyName;
    }

    void setGlobalExpirationPolicy(ExpirationPolicy policy) {
        this.expirationPolicy = policy;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public boolean isDirty() {
        return this.dirty;
    }
}
