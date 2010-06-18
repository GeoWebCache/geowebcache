package org.geowebcache.diskquota;

public class ExpirationPolicyLRU implements LayerQuotaExpirationPolicy {

    private static final String POLICY_NAME = "LRU";

    public String getName() {
        return POLICY_NAME;
    }

}
