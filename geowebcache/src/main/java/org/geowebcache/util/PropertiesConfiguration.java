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
import org.geowebcache.cache.CacheException;
import org.geowebcache.cache.CacheFactory;
import org.geowebcache.layer.wms.WMSLayer;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class PropertiesConfiguration implements Configuration, ApplicationContextAware {
    private static Log log = LogFactory
            .getLog(org.geowebcache.util.PropertiesConfiguration.class);

    private String configDir = null;
    
    private ApplicationContext context;

    private CacheFactory cacheFactory = null;
    
    public PropertiesConfiguration (String configDir, CacheFactory cacheFactory) {
    	this.configDir = configDir;
    	this.cacheFactory = cacheFactory;
    }
    

	public Map getTileLayers() {
		// Find all the property files and process each one into a TileLayer
		//Resource resource = context.getResource(this.configDir);
		//Resource resource = context.getResource("classpath:./../classes");
		File[] propFiles = null; //
		//String test = null;
		File pfh = null;
		//File pfh = new File("");
		//try {
			//pfh = resource.getFile();
			pfh = new File(this.configDir);
			propFiles = findPropFiles(pfh);
        	//test = propConfigDir.getAbsolutePath();
        //} catch (IOException ioe) {
        //	ioe.printStackTrace();
        //}
        
        if (propFiles != null && propFiles.length > 0) {
            log.trace("Found " + propFiles.length + " property files.");
        } else {
            log.error("Found no property files in "+this.configDir+", absolute directory "+pfh.getAbsolutePath());
        }

        HashMap layers = new HashMap();
        
        // Loop over the property files, create TileLayers
        for (int i = 0; i < propFiles.length; i++) {
            Properties props = readProperties(propFiles[i]);
            if (props == null) {
                continue;
            }
            
            String layerName = propFiles[i].getName();
            String[] nameComps = layerName.split("\\.");
            layerName = new String(nameComps[0].substring(6));

            log.trace("Adding layer " + layerName);

            // TODO need support for other types of layers
            WMSLayer layer = null;
            try {
                layer = new WMSLayer(layerName, props, cacheFactory);
            } catch (CacheException ce) {
                log.trace("CacheException, failed to add layer " + layerName);
                ce.printStackTrace();
            }

            if (layer != null) {
                layers.put(layerName, layer);
            }
        }
        return layers;
    }
	
	public String getIdentifier() {
		return configDir;
	}

    /**
     * Find all the .properties files in the configuration file directory.
     * 
     * @param configDirH
     */
    private File[] findPropFiles(File configDirH) {
        FilenameFilter select = new ExtensionFileListFilter("layer_",
                "properties");
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


	public void setApplicationContext(ApplicationContext arg0) throws BeansException {
		context = arg0;
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
