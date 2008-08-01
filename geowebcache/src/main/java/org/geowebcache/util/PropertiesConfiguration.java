/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Chris Whitney
 *  
 */
package org.geowebcache.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.cache.CacheFactory;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.wms.WMSLayer;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.web.context.WebApplicationContext;

public class PropertiesConfiguration implements Configuration,
        ApplicationContextAware {
    private static Log log = LogFactory
            .getLog(org.geowebcache.util.PropertiesConfiguration.class);

    private String absPath = null;

    private String relPath = null;

    private File configDirH = null;

    private WebApplicationContext context;

    private CacheFactory cacheFactory = null;

    public PropertiesConfiguration(CacheFactory cacheFactory) {
        this.cacheFactory = cacheFactory;
    }

    public Map<String,TileLayer> getTileLayers() throws GeoWebCacheException {
        if (configDirH == null) {
            determineConfigDirH();
        }

        // Find all the property files and process each one into a TileLayer
        File[] propFiles = findPropFiles(configDirH);

        if (propFiles != null && propFiles.length > 0) {
            log.trace("Found " + propFiles.length + " property files in "
                    + configDirH.getAbsolutePath());
        } else {
            log.error("Found no property files in "
                    + configDirH.getAbsolutePath());
            return null;
        }

        HashMap<String,TileLayer> layers = new HashMap<String,TileLayer>();

        // Loop over the property files, create TileLayers
        for (int i = 0; i < propFiles.length; i++) {
            Properties props = readProperties(propFiles[i]);
            if (props == null) {
                continue;
            }

            String fileName = propFiles[i].getName();
            String layerName = props.getProperty("layername");
            
            if (layerName == null) {
                String[] nameComps = fileName.split("\\.");
                layerName = new String(nameComps[0].substring(6));
            } else {
                layerName = new String(layerName);
            }

            log.info("Adding layer " + layerName + " from  "
                    + propFiles[i].getAbsolutePath());

            // TODO need support for other types of layers
            //temporary hack
            WMSLayer layer = null;//new WMSLayer(layerName, props, cacheFactory);

            if (layer != null) {
                layers.put(layerName, layer);
            }
        }
        return layers;
    }

    public void determineConfigDirH() {
        if (absPath != null) {
            configDirH = new File(absPath);
            return;
        }

        /* Only keep going for relative directory */
        if (relPath == null) {
            log.warn("No configuration directory was specified,"
                    +" reverting to default: ");
            relPath = "";
        } else {
            if(File.separator.equals("\\") && relPath.equals("/WEB-INF/classes")) {
                log.warn("You seem to be running on windows, changing search path to \\WEB-INF\\classes");
                relPath = "\\WEB-INF\\classes";
            }
        }
        
        String baseDir = context.getServletContext().getRealPath("");
        
        configDirH = new File(baseDir + relPath);

        log.info("Configuration directory set to: "
                + configDirH.getAbsolutePath());

        if (!configDirH.exists() || !configDirH.canRead()) {
            log.error(configDirH.getAbsoluteFile()
                    + " cannot be read or does not exist!");
        }
    }

    /**
     * Find all the .properties files in the configuration file directory.
     * 
     * @param configDirH
     */
    private File[] findPropFiles(File configDirH) {
        FilenameFilter select = new ExtensionFileLister("layer_","properties");
        return configDirH.listFiles(select);
    }

    private Properties readProperties(File propFile) {
        log.debug("Found " + propFile.getAbsolutePath() + " file.");
        Properties props = null;
        try {
            FileInputStream in = new FileInputStream(propFile);
            props = new Properties();
            props.load(in);
            in.close();
        } catch (IOException ioe) {
            log.error("Could not read " + propFile.getAbsolutePath()
                    + " file: ", ioe);
        }
        return props;
    }

    public String getIdentifier() {
        return configDirH.getAbsolutePath();
    }

    public void setRelativePath(String relPath) {
        this.relPath = relPath;
    }

    public void setAbsolutePath(String absPath) {
        this.absPath = absPath;
    }

    public void setApplicationContext(ApplicationContext arg0)
            throws BeansException {
        context = (WebApplicationContext) arg0;
    }
}

