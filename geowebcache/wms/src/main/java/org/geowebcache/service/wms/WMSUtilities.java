package org.geowebcache.service.wms;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Utility class used for adding the applicationContext to the WMSService class without making it ApplicationContextAware (Which throws an AOP
 * exception).
 * 
 * @author Nicola Lagomarsini
 * 
 */
public class WMSUtilities implements ApplicationContextAware {
    /** Application context to pass*/
    private ApplicationContext applicationContext;

    public WMSUtilities() {

    }

    public void setApplicationContext(ApplicationContext context) throws BeansException {
        this.applicationContext = context;
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

}
