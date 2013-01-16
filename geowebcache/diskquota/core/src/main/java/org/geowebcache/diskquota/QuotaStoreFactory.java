package org.geowebcache.diskquota;

import java.io.IOException;
import java.util.List;

import org.geowebcache.config.ConfigurationException;
import org.springframework.context.ApplicationContext;

/**
 * Builds and returns a quota store factory.  
 *  
 * @author Andrea Aime - GeoSolutions
 *
 */
public interface QuotaStoreFactory {

    /**
     * Builds a quota store based on a provided name, or returns null if the
     * specified quota store cannot be handled
     * 
     * @param the application context, should the store depend on other beans 
     * @param quotaStoreName
     * @return
     * @throws ConfigurationException 
     * @throws Exception 
     */
    public QuotaStore getQuotaStore(ApplicationContext ctx, String quotaStoreName) throws IOException, ConfigurationException;

    /**
     * Lists the quota store names supported by this factory
     * @return
     */
    List<String> getSupportedStoreNames();

}
