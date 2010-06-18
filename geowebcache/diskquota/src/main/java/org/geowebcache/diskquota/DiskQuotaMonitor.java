package org.geowebcache.diskquota;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.storage.StorageException;
import org.geowebcache.util.ApplicationContextProvider;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.web.context.WebApplicationContext;

public class DiskQuotaMonitor implements DisposableBean {

    private static final Log log = LogFactory.getLog(DiskQuotaMonitor.class);

    private static final String CONFIGURATION_FILE_NAME = "geowebcache-diskquota.xml";

    private static final String[] CONFIGURATION_REL_PATHS = { "/WEB-INF/classes", "/../resources" };

    private WebApplicationContext context;

    private URL configFile;

    private DiskQuotaConfig quotaConfig;

    public DiskQuotaMonitor(DefaultStorageFinder storageFinder,
            ApplicationContextProvider contextProvider) throws IOException {

        this.context = contextProvider.getApplicationContext();
        String cachePath;
        try {
            cachePath = storageFinder.getDefaultPath();
        } catch (StorageException e) {
            throw new RuntimeException(e);
        }
        try {
            this.configFile = findConfFile(cachePath);
        } catch (GeoWebCacheException e) {
            throw new RuntimeException(e);
        }

        if (configFile == null) {
            log.info("Found no " + CONFIGURATION_FILE_NAME + " file. Disk Quota is disabled.");
            return;
        }

        InputStream configIn = configFile.openStream();
        try {
            this.quotaConfig = ConfigLoader.loadConfiguration(configIn);
        } finally {
            configIn.close();
        }
    }

    /**
     * @see org.springframework.beans.factory.DisposableBean#destroy()
     */
    public void destroy() throws Exception {
    }

    private URL findConfFile(String cachePath) throws GeoWebCacheException {
        File configDir = determineConfigDirH(cachePath);

        URL resource = null;

        if (configDir == null) {

            resource = getClass().getResource("/" + CONFIGURATION_FILE_NAME);

        } else {
            File xmlFile = null;
            xmlFile = new File(configDir.getAbsolutePath() + File.separator
                    + CONFIGURATION_FILE_NAME);
            log.info("Found configuration file in " + configDir.getAbsolutePath());
            try {
                resource = xmlFile.toURI().toURL();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }

        if (resource == null) {
            log.debug("Unable to determine location of " + CONFIGURATION_FILE_NAME + ".");
        }

        return resource;
    }

    private File determineConfigDirH(String defaultPath) {
        String baseDir = context.getServletContext().getRealPath("");

        File configH = null;
        /*
         * Try 1) environment variables 2) standard paths 3) class path /geowebcache-diskquota.xml
         */
        if (defaultPath != null) {
            File tmpPath = new File(defaultPath + File.separator + CONFIGURATION_FILE_NAME);
            if (tmpPath.exists()) {
                configH = new File(tmpPath.getParent());
            }
        }

        // Finally, try "standard" paths if we have to.
        if (configH == null) {
            for (int i = 0; i < CONFIGURATION_REL_PATHS.length; i++) {
                String relPath = CONFIGURATION_REL_PATHS[i];
                if (File.separator.equals("\\")) {
                    relPath = relPath.replace("/", "\\");
                }

                File tmpPath = new File(baseDir + relPath + File.separator
                        + CONFIGURATION_FILE_NAME);

                if (tmpPath.exists() && tmpPath.canRead()) {
                    log.info("No configuration directory was specified, using "
                            + tmpPath.getAbsolutePath());
                    configH = new File(baseDir + relPath);
                }
            }
        }
        if (configH == null) {
            log.info("Failed to find " + CONFIGURATION_FILE_NAME
                    + ". This is not a problem unless you are trying "
                    + "to use a custom XML configuration file.");
        } else {
            log.debug("Configuration directory set to: " + configH.getAbsolutePath());

            if (!configH.exists() || !configH.canRead()) {
                log.error("Configuration file cannot be read or does not exist!");
            }
        }

        return configH;
    }
}
