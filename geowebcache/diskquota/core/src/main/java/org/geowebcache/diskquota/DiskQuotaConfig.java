/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Gabriel Roldan (OpenGeo) 2010
 */
package org.geowebcache.diskquota;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.geowebcache.diskquota.storage.LayerQuota;
import org.geowebcache.diskquota.storage.Quota;
import org.geowebcache.diskquota.storage.StorageUnit;
import org.springframework.util.Assert;

/**
 * Holds the quota configuration for all the registered layers as well as the instance wide settings such as cache disk
 * block size, maximum number of concurrent cache clean ups, etc.
 *
 * @author groldan
 */
public class DiskQuotaConfig implements Cloneable, Serializable {

    @Serial
    private static final long serialVersionUID = 4376471696761297546L;

    @Deprecated
    static final int DEFAULT_DISK_BLOCK_SIZE = 4096;

    static final int DEFAULT_CLEANUP_FREQUENCY = 10;

    static final TimeUnit DEFAULT_CLEANUP_UNITS = TimeUnit.SECONDS;

    static final int DEFAULT_MAX_CONCURRENT_CLEANUPS = 2;

    static ExpirationPolicy DEFAULT_GLOBAL_POLICY_NAME = ExpirationPolicy.LFU;

    private Boolean enabled;

    private Integer cacheCleanUpFrequency;

    private TimeUnit cacheCleanUpUnits;

    private Integer maxConcurrentCleanUps;

    private ExpirationPolicy globalExpirationPolicyName;

    private Quota globalQuota;

    private transient Date lastCleanUpTime;

    private List<LayerQuota> layerQuotas;

    private String quotaStore;

    public void setDefaults() {
        if (enabled == null) {
            enabled = Boolean.FALSE;
        }
        if (cacheCleanUpFrequency == null) {
            cacheCleanUpFrequency = DEFAULT_CLEANUP_FREQUENCY;
        }

        if (maxConcurrentCleanUps == null) {
            maxConcurrentCleanUps = DEFAULT_MAX_CONCURRENT_CLEANUPS;
        }
        if (cacheCleanUpUnits == null) {
            cacheCleanUpUnits = DEFAULT_CLEANUP_UNITS;
        }
        if (globalExpirationPolicyName == null) {
            globalExpirationPolicyName = DEFAULT_GLOBAL_POLICY_NAME;
        }
        if (globalQuota == null) {
            globalQuota = new Quota(500, StorageUnit.MiB);
        }
    }

    void setFrom(DiskQuotaConfig other) {
        this.cacheCleanUpFrequency = other.cacheCleanUpFrequency;
        this.cacheCleanUpUnits = other.cacheCleanUpUnits;
        this.enabled = other.enabled;
        this.globalExpirationPolicyName = other.globalExpirationPolicyName;
        this.globalQuota = other.globalQuota;
        this.layerQuotas = other.layerQuotas == null ? null : new ArrayList<>(other.layerQuotas);
        this.maxConcurrentCleanUps = other.maxConcurrentCleanUps;
        this.quotaStore = other.quotaStore;
    }

    public Boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getCacheCleanUpFrequency() {
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

    /** @return the configured layer quotas, or {@code null} if not set */
    public List<LayerQuota> getLayerQuotas() {
        return layerQuotas == null ? null : new ArrayList<>(layerQuotas);
    }

    public void setLayerQuotas(List<LayerQuota> layerQuotas) {
        this.layerQuotas = layerQuotas == null ? null : new ArrayList<>(layerQuotas);
    }

    public void addLayerQuota(LayerQuota quota) {
        Assert.notNull(quota, "Quota must be non null");
        Assert.notNull(quota.getQuota(), "Quota must be non null");
        if (layerQuotas == null) {
            layerQuotas = new ArrayList<>();
        }
        this.layerQuotas.add(quota);
    }

    /** @return The layer quota for the given layer or {@code null} if no quota is being tracked for that layer */
    public LayerQuota layerQuota(final String layerName) {
        if (layerQuotas != null) {
            for (LayerQuota lq : layerQuotas) {
                if (lq.getLayer().equals(layerName)) {
                    return lq;
                }
            }
        }

        return null;
    }

    public void remove(final LayerQuota lq) {
        if (layerQuotas != null) {
            for (Iterator<LayerQuota> it = layerQuotas.iterator(); it.hasNext(); ) {
                if (it.next().getLayer().equals(lq.getLayer())) {
                    it.remove();
                }
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        sb.append("[");
        if (null != getLayerQuotas()) {
            for (LayerQuota lq : getLayerQuotas()) {
                sb.append("\n\t").append(lq);
            }
        }
        sb.append("]");
        return sb.toString();
    }

    public Integer getMaxConcurrentCleanUps() {
        return maxConcurrentCleanUps;
    }

    public void setMaxConcurrentCleanUps(int nThreads) {
        if (nThreads <= 0) {
            throw new IllegalArgumentException("maxConcurrentCleanUps shall be a positive integer: " + nThreads);
        }
        this.maxConcurrentCleanUps = nThreads;
    }

    /** @return the global quota, or {@code null} if not set */
    public Quota getGlobalQuota() {
        return this.globalQuota;
    }

    /** @param newQuota the new global quota, or {@code null} to unset */
    public void setGlobalQuota(final Quota newQuota) {
        if (newQuota == null) {
            this.globalQuota = null;
        } else {
            this.globalQuota = new Quota(newQuota);
        }
    }

    public ExpirationPolicy getGlobalExpirationPolicyName() {
        return this.globalExpirationPolicyName;
    }

    public void setGlobalExpirationPolicyName(ExpirationPolicy policy) {
        this.globalExpirationPolicyName = policy;
    }

    public void setLastCleanUpTime(Date date) {
        this.lastCleanUpTime = date;
    }

    public Date getLastCleanUpTime() {
        return this.lastCleanUpTime;
    }

    public Set<String> layerNames() {
        Set<String> names = new HashSet<>();
        if (null != getLayerQuotas()) {
            for (LayerQuota lq : getLayerQuotas()) {
                names.add(lq.getLayer());
            }
        }
        return names;
    }

    @Override
    public DiskQuotaConfig clone() {
        DiskQuotaConfig clone;
        try {
            clone = (DiskQuotaConfig) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
        clone.lastCleanUpTime = lastCleanUpTime;
        clone.globalQuota = globalQuota == null ? null : new Quota(globalQuota);
        clone.layerQuotas = layerQuotas == null ? null : new ArrayList<>(layerQuotas);
        return clone;
    }

    /** Returns the quota store name */
    public String getQuotaStore() {
        return quotaStore;
    }

    /** Sets the quota store name */
    public void setQuotaStore(String quotaStore) {
        this.quotaStore = quotaStore;
    }
}
