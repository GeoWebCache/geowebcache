package org.geowebcache.rest.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

/**
 * Configure various aspects of Spring MVC
 */
@Configuration
@EnableWebMvc
public class GWCRestConfig extends WebMvcConfigurationSupport {

    @Autowired
    private ApplicationContext applicationContext;

}
