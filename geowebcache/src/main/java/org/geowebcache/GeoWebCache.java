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
package org.geowebcache;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.layer.BBOX;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.service.gmaps.GMapsConverter;
import org.geowebcache.service.ve.VEConverter;
import org.geowebcache.service.wms.WMSParameters;
import org.geowebcache.util.Configuration;
import org.geowebcache.util.ServletUtils;

public class GeoWebCache extends HttpServlet {	
	private static final long serialVersionUID = 4175613925719485006L;
	private static Log log = LogFactory.getLog(org.geowebcache.GeoWebCache.class);
	private static final String DEFAULT_REL_CONFIG_DIR = "WEB-INF" + File.separator + "classes";
	private Configuration configuration = null;
	private HashMap layers = null;

	/**
	 * 1) Find out where the configuration directory is,
	 * 2) read all the property files in it
	 * 3) create the necessary objects
	 * 4) TODO Digester for Etags, if desirable
	 */
	@Override
	public void init() throws ServletException {
		// 1) Get the configuration directory
		String configDir = System.getProperty("GEOWEBCACHE_CONFIG_DIR");
		if(configDir != null) {
			log.trace("Using environment variable, GEOWEBACACHE_CONFIG_DIR = " + configDir);
		} else {
			// Try getting it from web.xml
			configDir = getServletContext().getInitParameter("GEOWEBCACHE_CONFIG_DIR");
			//configDir = getInitParameter("GEOWEBCACHE_CONFIG_DIR");
			if(configDir != null) {
				log.trace("Using web.xml, GEOWEBACACHE_CONFIG_DIR = " + configDir);
			} else {
				log.error("GEOWEBCACHE_CONFIG_DIR not specified! Using default configuration.");
				String dir = getServletContext().getRealPath("");
				
				// This should work for Tomcat
				configDir = dir + File.separator + DEFAULT_REL_CONFIG_DIR;
				File fh = new File(configDir);

				if(! fh.exists()) {
					// Probably running Jetty through Eclipse?
					configDir = dir + "../resources";
				}
			}
		}
		
		// 2) Check configuration directory can be read
		File configDirH = new File(configDir);
		if(configDirH.isDirectory() && configDirH.canRead()) {
			log.trace("Opened configuration directory: " + configDir);
		} else {
			log.fatal("Unable to read configuration from " + configDir);
			throw new ServletException("Unable to read configuration from " + configDir);
		}
		// 3) Read the configuration files and create corresponding objects in layers.
		configuration = new Configuration(configDirH);
		layers = configuration.getLayers();
		
		// 4) Digest mechanism
		//TODO
		log.trace("Completed loading configuration");
	}
	
	/**
	 * Loops over all the layers and calls destroy() on their caches.
	 */
	@Override
	public void destroy() {
		Iterator iter = layers.entrySet().iterator();
		while(iter.hasNext()) {
			TileLayer tl = (TileLayer) iter.next();
			tl.destroy();
		}
	}

	/**
	 * And it goes a little something like this
	 * 1) Get request
	 * 2) Parse Request into WMS parameters
	 * 3) Determine whether we serve this layer
	 * 4) Ask TileLayer for best fit for the given parameters
	 * 5) Return tile
	 */
	@Override
	public void doGet(HttpServletRequest request,
						HttpServletResponse response)
						throws ServletException, IOException {

		if(log.isDebugEnabled()) {
			log.debug("-------------New Request-----------");
			log.debug(request.getRequestURI());
		}

		// For some reason getPathTranslated returns null
		String contextPath = request.getContextPath();
		String requestURI = request.getRequestURI();
		String subPath = requestURI.substring(contextPath.length(), requestURI.length()).toLowerCase();
		
		if(subPath.equals("/wms")) {
			doGetWMS(request, response);
		} else if(subPath.equals("/ve")) {
			doGetVE(request, response);
		} else if(subPath.equals("/gmaps")) {
			doGetGmaps(request, response);
		} else if(subPath.equals("/seed")) {
			doGetSeed(request, response);
		}
	}
	
	public void doGetWMS(HttpServletRequest request,
			HttpServletResponse response)
	throws ServletException, IOException {
		// Parse request
		WMSParameters wmsparams = new WMSParameters(request);

		// Check whether we serve this layer
		TileLayer cachedLayer = findLayer(wmsparams.getLayer(), response);

		if(cachedLayer != null) {
			cachedLayer = checkLayer(cachedLayer, wmsparams, response);
		}
				
		// Get data (from backend, if necessary)
		wmsGetData(cachedLayer, wmsparams, response);
	}
	
	private void wmsGetData(TileLayer cachedLayer, WMSParameters wmsparams, HttpServletResponse response) 
	throws IOException {
		if(cachedLayer != null) { 
			byte[] data = cachedLayer.getData(wmsparams, response);

			// Did we get anything?
			if(data == null || data.length == 0) {
				// Response: 404
				response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				return;
			}
			response.setStatus(HttpServletResponse.SC_OK);
			response.setContentType(wmsparams.getImagemime().getMime());
			response.setContentLength(data.length);
			sendData(response, data);
		} else {
			// Response: 404
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
		}
	}
	
	
	public void doGetSeed(HttpServletRequest request,
			HttpServletResponse response)
	throws ServletException, IOException {		
		
		Map params = request.getParameterMap();
		String strLayers = ServletUtils.stringFromMap(params, "layers");
		TileLayer layer = (TileLayer) layers.get(strLayers);
		
		if(layer == null) {
			response.setContentType("text/plain");
			response.sendError(400, "No layers or unknown layer "+strLayers);
			// complain loudly and quit
			log.error("No layers?");
		}
		
		response.setContentType("text/html");
		response.setBufferSize(12);
		
		BBOX reqBounds = null;
		if(params.containsKey("bbox")) {
			reqBounds = new BBOX(((String[]) params.get("bbox"))[0]);
		}

		String strStart = ServletUtils.stringFromMap(params, "start");
		String strStop = ServletUtils.stringFromMap(params, "stop");
		String strFormat = ServletUtils.stringFromMap(params, "format");
		
		int start = -1; 
		if(strStart != null) {
			start = Integer.valueOf(strStart);
		}

		int stop = -1;
		if(strStop != null) {
			stop = Integer.valueOf(strStop);
		}
		
		layer.seed(start, stop, strFormat, reqBounds, response);
		response.flushBuffer();
	}

	public void doGetVE(HttpServletRequest request,
			HttpServletResponse response)
	throws ServletException, IOException {
		
		Map params = request.getParameterMap();
		String strLayer = ServletUtils.stringFromMap(params, "layers");
		String strQuadKey = ServletUtils.stringFromMap(params, "quadkey");
		
		BBOX bbox = VEConverter.convertQuadKey(strQuadKey);
		
		TileLayer cachedLayer = findLayer(strLayer, response);
		WMSParameters wmsparams = cachedLayer.getWMSParamTemplate();
		wmsparams.setBBOX(bbox);
		
		// A late sanity check
		if(cachedLayer != null) {
			cachedLayer = checkLayer(cachedLayer, wmsparams, response);
		}
		
		wmsGetData(cachedLayer, wmsparams, response);
	}
	
	public void doGetGmaps(HttpServletRequest request,
			HttpServletResponse response)
	throws ServletException, IOException {
		Map params = request.getParameterMap();
		String strLayer = ServletUtils.stringFromMap(params, "layers");
		String strZoom = ServletUtils.stringFromMap(params, "zoom");
		String strX = ServletUtils.stringFromMap(params, "x");
		String strY = ServletUtils.stringFromMap(params, "y");
		
		BBOX bbox = GMapsConverter.convert(Integer.parseInt(strZoom), Integer.parseInt(strX), Integer.parseInt(strY));
		
		TileLayer cachedLayer = findLayer(strLayer, response);
		WMSParameters wmsparams = cachedLayer.getWMSParamTemplate();
		wmsparams.setBBOX(bbox);
		
		// A late sanity check
		if(cachedLayer != null) {
			cachedLayer = checkLayer(cachedLayer, wmsparams, response);
		}
		
		wmsGetData(cachedLayer, wmsparams, response);
	}
	
	/**
	 * Finds the requested layer, provided request is within bounds
	 * 
	 * @param wmsparams
	 * @param response
	 * @return
	 */
	private TileLayer findLayer(String strLayer, HttpServletResponse response) throws IOException{
		TileLayer cachedLayer = (TileLayer) layers.get(strLayer);
		
		if(cachedLayer == null) {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			response.setContentType("text/plain");
			response.getOutputStream().print("Unknown layer: " + strLayer);
		}
		
		return cachedLayer;
	}
	
	private TileLayer checkLayer(TileLayer cachedLayer, WMSParameters wmsparams, HttpServletResponse response)
	 throws IOException{
		
		String errorMsg = cachedLayer.covers(wmsparams);
			
		if(errorMsg != null) {
			response.setContentType("text/plain");
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			response.getOutputStream().print(errorMsg);
			return null;
		} else {
			return cachedLayer;
		}
	}

	private void sendData(HttpServletResponse response, byte[] data) throws IOException {
		log.trace("Sending data.");		
		OutputStream os = response.getOutputStream();
		os.write(data);
		os.flush();
	}
	

	
}
