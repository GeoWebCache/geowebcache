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
package org.geowebcache.diskquota.storage;

import java.io.Serial;
import java.io.Serializable;
import org.geowebcache.diskquota.ExpirationPolicy;

/** @author groldan */
public final class LayerQuota implements Serializable {

    @Serial
    private static final long serialVersionUID = 5726170502452942487L;

    private String layer;

    private ExpirationPolicy expirationPolicyName;

    private Quota quota;

    public LayerQuota(final String layer, final ExpirationPolicy expirationPolicyName) {
        this(layer, expirationPolicyName, null);
    }

    public LayerQuota(final String layer, final ExpirationPolicy expirationPolicyName, Quota quota) {
        this.layer = layer;
        this.expirationPolicyName = expirationPolicyName;
        this.quota = quota;
        readResolve();
    }

    /** Supports initialization of instance variables during XStream deserialization */
    private Object readResolve() {
        return this;
    }

    public ExpirationPolicy getExpirationPolicyName() {
        return expirationPolicyName;
    }

    public String getLayer() {
        return layer;
    }

    /** @return The layer's configured disk quota, or {@code null} if it has no max quota set */
    public Quota getQuota() {
        return quota;
    }

    @Override
    public String toString() {
        return new StringBuilder(getClass().getSimpleName())
                .append("[layer: ")
                .append(layer)
                .append(", Expiration policy: '")
                .append(expirationPolicyName)
                .append("', quota:")
                .append(quota)
                .append("]")
                .toString();
    }
}
