package org.geowebcache.service.wms;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class WMSUtilities implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    public WMSUtilities(){
        
    }
    
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        this.applicationContext=context;
    }
    
    public ApplicationContext getApplicationContext(){
        return applicationContext;
    }

}
