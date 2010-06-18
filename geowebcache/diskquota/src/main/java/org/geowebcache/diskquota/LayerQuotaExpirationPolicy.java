package org.geowebcache.diskquota;

public interface LayerQuotaExpirationPolicy {

    /**
     * Returns the unique name for this expiration policy
     * <p>
     * This name is an identifier for the policy implemented by this strategy, not for each instance
     * of the concrete subclass. It can be used to match a policy in a collection.
     * </p>
     * 
     * @return an identifier for this quota expiration policy strategy
     */
    String getName();

}
