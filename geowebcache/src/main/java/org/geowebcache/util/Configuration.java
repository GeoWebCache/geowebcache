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
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.geowebcache.cache.CacheException;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.service.wms.WMSParameters;


public class Configuration {
	private static Log log = LogFactory.getLog(org.geowebcache.util.Configuration.class);
	//private static final String NAMESPACE = "org.geowebcache";
	//private static final String DEFAULT_SERVER = "http://localhost:8080/geoserver/wms";
	//private static final String DEFAULT_SERVICE = "wms";
	//private static final String DEFAULT_CACHE = NAMESPACE + ".cache.JCS";
	//private static final String DEFAULT_RESOLUTION_CONSTRAINT = "NO";
	private static final String DEFAULT_FORWARD_ON_ERROR = "NO";

	private Properties props = null;

	private WMSParameters wmsparams = null;
	private String server = null;
	private String service = null;
	private String cache = null;
	private Set resolutions = null;
	private boolean forward_errors;

	private File[] propFiles = null;
	private HashMap layers = new HashMap();
	
	// Make Configuration a singleton
	//private static final Configuration singleton_inst = new Configuration();

	public Configuration(File configDirH) {		
		//Find all the property files and process each one into a TileLayer
		findPropFiles(configDirH);
		if(this.propFiles != null) {
			log.trace("Found " + propFiles.length + " property files.");
		} else {
			log.error("Found no property files!");
		}
		
		//Loop over the property files, create TileLayers
		for(int i=0; i<this.propFiles.length; i++) {
			Properties props = readProperties(this.propFiles[i]);
			if(props == null)
				continue;
			
			String layerName = this.propFiles[i].getName();
			String[] nameComps = layerName.split("\\.");
			layerName = nameComps[0].substring(6);
			
			log.trace("Adding layer " + layerName);
			
			TileLayer layer = null;
			try {	
				layer = new TileLayer(layerName, props);
			} catch (CacheException ce) {
				log.trace("CacheException, failed to add layer " + layerName);
				ce.printStackTrace();
			}
			
			if(layer != null)
				this.layers.put(layerName, layer);
		}
	}
	
	/**
	 * Find all the .properties files in the configuration file directory.
	 * @param configDirH
	 */
	private void findPropFiles(File configDirH) {
		FilenameFilter select = new ExtensionFileListFilter("layer_","properties");
		File[] propFiles = configDirH.listFiles(select);
		this.propFiles = propFiles;
	}
	
	
	//public static Configuration getInstance() {
	//	return singleton_inst;
	//}
	
	private Properties readProperties(File propFile) {
		props = null;
		
		log.debug("Found " + propFile.getAbsolutePath() + " file.");
		try {
			FileInputStream in = new FileInputStream(propFile);
			props = new Properties();
			props.load(in);
			in.close();
		} catch(IOException ioe) {
			log.error("Could not read " + propFile.getAbsolutePath() + " file: ", ioe);
		}
		return props;
	}
	
	public HashMap getLayers() {
		return this.layers;
	}
//
//
//	// TODO: configuration should be layer-based: specify a list of layers,
//	// and then configure them
//
//	private WMSParameters extractWMSParameters(Properties props) {
//
//		wmsparams = new WMSParameters();
//
//		if(this.service == null) {
//			setService();
//		}
//
////		wmsparams.setAllFromString(
////				props.getProperty(WMSParameters.REQUEST_PARAM.toLowerCase()),
////				props.getProperty(WMSParameters.VERSION_PARAM.toLowerCase()),
////				props.getProperty(WMSParameters.TILED_PARAM.toLowerCase()),
//				//props.getProperty(WMSParameters.SRS_PARAM.toLowerCase()),
////				props.getProperty(WMSParameters.LAYER_PARAM.toLowerCase()),
////				props.getProperty(WMSParameters.STYLES_PARAM.toLowerCase()),
////				props.getProperty(WMSParameters.BBOX_PARAM.toLowerCase()),
//				//props.getProperty(WMSParameters.ORIGIN_PARAM.toLowerCase()),
////				props.getProperty(WMSParameters.HEIGHT_PARAM.toLowerCase()),
////				props.getProperty(WMSParameters.WIDTH_PARAM.toLowerCase()),
//				//props.getProperty(WMSParameters.IMAGE_TYPE_PARAM.toLowerCase()),
//				//props.getProperty(WMSParameters.ERROR_TYPE_PARAM.toLowerCase())
////		);
//
//		log.info("Default WMS Parameters set to: " + wmsparams.toString());
//		
//		return wmsparams;
//	}
//
////	private void setServer() {
////		this.server = props.getProperty(NAMESPACE + ".server", DEFAULT_SERVER);
////		log.info("Server set to: " + this.server);
////	}
//
////	private void setService() {
////		this.service = props.getProperty( NAMESPACE + ".service", DEFAULT_SERVICE).toLowerCase();
//		log.info("Service set to: " + this.service);
//	}
//
//	private void setCacheType() {
//		this.cache = props.getProperty(NAMESPACE + ".cache", DEFAULT_CACHE);
//		log.info("Cache type set to: " + this.cache);
//	}
//
//	@SuppressWarnings("unchecked")
//	private void setResolutionConstraints() {
//		String res_str = props.getProperty(NAMESPACE + ".resolution_constraint", DEFAULT_RESOLUTION_CONSTRAINT);
//		log.info("Resolution constraint set to: " + res_str);
//
//		if(res_str.equalsIgnoreCase("NO") || res_str.equalsIgnoreCase("NONE")) {
//			this.resolutions = null;
//		} else {
//			this.resolutions = new HashSet();
//			String[] res_array = res_str.split(",");
//
//			for(int i = 0; i < res_array.length; ++i) {
//				this.resolutions.add(Double.valueOf(res_array[i]));
//			}
//
//			if(log.isTraceEnabled()) {
//				log.trace("Parsed resolutions as: " + this.resolutions.toString());
//			}
//		}
//	}
//
//	private void setForwardOnError() {
//		String forward = props.getProperty( NAMESPACE + ".forward_on_error", DEFAULT_FORWARD_ON_ERROR);
//		this.forward_errors = (forward.equalsIgnoreCase("YES") || forward.equalsIgnoreCase("TRUE"));
//
//		if(this.forward_errors) {
//			log.info("Forwarding on error enabled.");
//		} else {
//			log.info("Forwarding on error disabled.");
//		}
//	}
//
//	// TODO: make generic service instead of wms
//	public WMSParameters getParameters() {
//		return this.wmsparams;
//	}
//
//	public String getServer() {
//		return this.server;
//	}
//
//	public String getService() {
//		return this.service;
//	}
//
//	public String getCacheType() {
//		return this.cache;
//	}
//
//	public Set getResolutionConstraints() {
//		return this.resolutions;
//	}
//
//	public boolean getForwardOnError() {
//		return this.forward_errors;
//	}
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
			System.out.println(" properties: " + directory.getAbsolutePath() + " " + filename);
			boolean ret = filename.endsWith('.' + extension) && filename.startsWith(prefix);
			return ret;
		} else {
			return false;
		}
	}
}
