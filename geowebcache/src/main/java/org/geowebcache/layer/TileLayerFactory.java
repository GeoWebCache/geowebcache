package org.geowebcache.layer;

import java.io.File;
import java.util.HashMap;

import javax.servlet.ServletException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.util.Configuration;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class TileLayerFactory implements ApplicationContextAware {
	private static Log log = LogFactory.getLog(org.geowebcache.layer.TileLayerFactory.class);
	private HashMap layers = null;
	private ApplicationContext context = null;
	
	public TileLayerFactory(String configDir) {
		init(configDir);
	}
	
    /**
     * 1) Find out where the configuration directory is, 2) read all the
     * property files in it 3) create the necessary objects 4) TODO Digester for
     * Etags, if desirable
     */
    public void init() {
        // 1) Get the configuration directory
        String configDir = System.getProperty("GEOWEBCACHE_CONFIG_DIR");
        if (configDir != null) {
            log.trace("Using environment variable, GEOWEBACACHE_CONFIG_DIR = "
                    + configDir);
        } else {
            // Try getting it from web.xml
            configDir = context.getResource(arg0); .getInitParameter("GEOWEBCACHE_CONFIG_DIR");
            // configDir = getInitParameter("GEOWEBCACHE_CONFIG_DIR");
            if (configDir != null) {
                log.trace("Using web.xml, GEOWEBACACHE_CONFIG_DIR = "
                        + configDir);
            } else {
                log
                        .error("GEOWEBCACHE_CONFIG_DIR not specified! Using default configuration.");
                String dir = getServletContext().getRealPath("");

                // This should work for Tomcat
                configDir = dir + File.separator + DEFAULT_REL_CONFIG_DIR;
                File fh = new File(configDir);

                if (!fh.exists()) {
                    // Probably running Jetty through Eclipse?
                    configDir = dir + "../resources";
                }
            }
        }

        // 2) Check configuration directory can be read
        File configDirH = new File(configDir);
        if (configDirH.isDirectory() && configDirH.canRead()) {
            log.trace("Opened configuration directory: " + configDir);
        } else {
            log.fatal("Unable to read configuration from " + configDir);
            throw new ServletException("Unable to read configuration from "
                    + configDir);
        }
        // 3) Read the configuration files and create corresponding objects in
        // layers.
        configuration = new Configuration(configDirH);
        layers = configuration.getLayers();

        // 4) Digest mechanism
        // TODO
        log.trace("Completed loading configuration");
    }

	public void setApplicationContext(ApplicationContext arg0) throws BeansException {
		// TODO Auto-generated method stub
		context = arg0;
	}
}
