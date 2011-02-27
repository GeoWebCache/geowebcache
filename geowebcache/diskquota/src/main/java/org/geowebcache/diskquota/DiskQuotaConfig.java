/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Gabriel Roldan (OpenGeo) 2010
 *  
 */
package org.geowebcache.diskquota;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.geowebcache.diskquota.storage.LayerQuota;
import org.geowebcache.diskquota.storage.Quota;
import org.springframework.util.Assert;

/**
 * Holds the quota configuration for all the registered layers as well as the instance wide settings
 * such as cache disk block size, maximum number of concurrent cache clean ups, etc.
 * 
 * @author groldan
 * 
 */
public class DiskQuotaConfig {

    static final int DEFAULT_DISK_BLOCK_SIZE = 4096;

    static final int DEFAULT_CLEANUP_FREQUENCY = 10;

    static final TimeUnit DEFAULT_CLEANUP_UNITS = TimeUnit.SECONDS;

    static final int DEFAULT_MAX_CONCURRENT_CLEANUPS = 2;

    static ExpirationPolicy DEFAULT_GLOBAL_POLICY_NAME = ExpirationPolicy.LFU;

    private Boolean enabled;

    private int diskBlockSize;

    private int cacheCleanUpFrequency;

    private TimeUnit cacheCleanUpUnits;

    private int maxConcurrentCleanUps;

    private ExpirationPolicy globalExpirationPolicyName;

    private Quota globalQuota;

    private transient Date lastCleanUpTime;

    private List<LayerQuota> layerQuotas;

    public DiskQuotaConfig() {
        readResolve();
    }

    /**
     * Supports initialization of instance variables during XStream deserialization
     * 
     * @return
     */
    private Object readResolve() {
        if (enabled == null) {
            enabled = Boolean.FALSE;
        }
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
        if (globalExpirationPolicyName == null) {
            globalExpirationPolicyName = DEFAULT_GLOBAL_POLICY_NAME;
        }
        return this;
    }

    public boolean isEnabled() {
        return enabled.booleanValue();
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
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

    /**
     * @return the configured layer quotas
     */
    public List<LayerQuota> getLayerQuotas() {
        return new ArrayList<LayerQuota>(layerQuotas);
    }

    public void addLayerQuota(LayerQuota quota) {
        Assert.notNull(quota);
        Assert.notNull(quota.getQuota());
        this.layerQuotas.add(quota);
    }

    /**
     * @return The layer quota for the given layer or {@code null} if no quota is being tracked for
     *         that layer
     */
    public LayerQuota getLayerQuota(final String layerName) {
        for (LayerQuota lq : layerQuotas) {
            if (lq.getLayer().equals(layerName)) {
                return lq;
            }
        }

        return null;
    }

    public void remove(final LayerQuota lq) {
        for (Iterator<LayerQuota> it = layerQuotas.iterator(); it.hasNext();) {
            if (it.next().getLayer().equals(lq.getLayer())) {
                it.remove();
            }
        }
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

    /**
     * @return the global quota, or {@code null} if not set
     */
    public Quota getGlobalQuota() {
        return this.globalQuota;
    }

    /**
     * @param newQuota
     *            the new global quota, or {@code null} to unset
     */
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

    public Set<String> getLayerNames() {
        Set<String> names = new HashSet<String>();
        for (LayerQuota lq : getLayerQuotas()) {
            names.add(lq.getLayer());
        }
        return names;
    }
}
