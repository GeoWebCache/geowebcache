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
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.cache.CacheException;
import org.geowebcache.layer.wms.WMSLayer;

public class PropertiesConfiguration {
    private static Log log = LogFactory
            .getLog(org.geowebcache.util.PropertiesConfiguration.class);

    private Properties props = null;
    
    private File[] propFiles = null;

    private HashMap layers = new HashMap();

    public PropertiesConfiguration(File configDirH) {
        // Find all the property files and process each one into a TileLayer
        findPropFiles(configDirH);
        if (propFiles != null) {
            log.trace("Found " + propFiles.length + " property files.");
        } else {
            log.error("Found no property files!");
        }

        // Loop over the property files, create TileLayers
        for (int i = 0; i < propFiles.length; i++) {
            Properties props = readProperties(propFiles[i]);
            if (props == null) {
                continue;
            }

            String layerName = propFiles[i].getName();
            String[] nameComps = layerName.split("\\.");
            layerName = nameComps[0].substring(6);

            log.trace("Adding layer " + layerName);

            WMSLayer layer = null;
            try {
                layer = new WMSLayer(layerName, props);
            } catch (CacheException ce) {
                log.trace("CacheException, failed to add layer " + layerName);
                ce.printStackTrace();
            }

            if (layer != null) {
                layers.put(layerName, layer);
            }
        }
    }

    /**
     * Find all the .properties files in the configuration file directory.
     * 
     * @param configDirH
     */
    private void findPropFiles(File configDirH) {
        FilenameFilter select = new ExtensionFileListFilter("layer_",
                "properties");
        File[] propFiles = configDirH.listFiles(select);
        this.propFiles = propFiles;
    }

    private Properties readProperties(File propFile) {
        props = null;

        log.debug("Found " + propFile.getAbsolutePath() + " file.");
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

    public HashMap getLayers() {
        return layers;
    }
}

class ExtensionFileListFilter implements FilenameFilter {
    private String prefix;

    private String extension;

    public ExtensionFileListFilter(String prefix, String extension) {
        this.prefix = prefix;
        this.extension = extension;
    }

    public boolean accept(File directory, String filename) {
        if (prefix != null && extension != null) {
            System.out.println(" properties: " + directory.getAbsolutePath()
                    + " " + filename);
            boolean ret = filename.endsWith('.' + extension)
                    && filename.startsWith(prefix);
            return ret;
        } else {
            return false;
        }
    }
}
