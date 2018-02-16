package org.geowebcache;

import org.springframework.beans.factory.InitializingBean;

/**
 * Bean which can be reset to its initial condition.
 * @author smithkm
 *
 */
public interface ReinitializingBean extends InitializingBean {
    /**
     * Reinitialize the configuration from its persistence.
     */
    default void reinitialize() throws Exception {
        afterPropertiesSet();
    }
    
    /**
     * Prepare to be reinitialized.
     */
    void deinitialize() throws Exception;
}
