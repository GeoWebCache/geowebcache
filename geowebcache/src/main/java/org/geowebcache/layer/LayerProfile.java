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
 * @author Arne Kepp, The Open Planning Project, Copyright 2007
 *  
 */
package org.geowebcache.layer;

import java.io.IOException;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.service.wms.WMSParameters;

public class LayerProfile {
	private static Log log = LogFactory.getLog(org.geowebcache.layer.LayerProfile.class);

	public static final int CACHE_NEVER = 0;
	public static final int CACHE_VALUE_UNSET = -1;
	public static final int CACHE_NEVER_EXPIRE = -2;
	public static final int CACHE_USE_WMS_BACKEND_VALUE = -4;
	
	// This assumes image output
	protected String srs = "EPSG:4326";
	protected BBOX bbox = null;
	protected BBOX gridBase = null;
	protected GridCalculator gridCalc = null;
	protected int width = 256;
	protected int height = 256; 
	protected int metaWidth = 1;
	protected int metaHeight = 1;
	protected double maxTileWidth = -1.0;
	protected double maxTileHeight = -1.0;
	protected int zoomStart = 0;
	protected int zoomStop = 25;
	protected String request = "map";
	protected String version = "1.1.1";
	protected String errorMime = "application/vnd.ogc.se_inimage";
	protected String transparent = null;
	protected String tiled = null;
	protected String bgcolor = null;
	protected String palette = null;
	protected String wmsURL[] = {"http://localhost:8080/geoserver/wms"};
	protected int curWmsURL = 0;
	protected String wmsLayers = "topp:states";
	protected String wmsStyles = null;
	
	protected WMSParameters wmsparams = null;
	protected long expireClients = CACHE_USE_WMS_BACKEND_VALUE; 
	protected long expireCache =  CACHE_NEVER_EXPIRE;
	
	/**
	 * Only for testin purposes
	 */
	public LayerProfile() {		

	}
	
	public LayerProfile(Properties props) {		
		setParametersFromProperties(props);
		
		if(log.isTraceEnabled()) {
			log.trace("Created a new layer: " + this.toString());
		}
	}
	
	private void setParametersFromProperties(Properties props) {
		String propSrs = props.getProperty("srs");
		if(propSrs != null)
			this.srs = propSrs;
		
		if(srs.equalsIgnoreCase("EPSG:4326")) {
			this.maxTileWidth = 180.0;
			this.maxTileHeight = 180.0;
		} else if(srs.equalsIgnoreCase("EPSG:900912")){
			this.maxTileWidth = 20037508.34*2;
			this.maxTileHeight = 20037508.34*2;
		} else {
			log.error("May not interpret SRS " + srs + " correctly.");
		}
		
		String propZoomStart = props.getProperty("zoomStart");
		if(propZoomStart != null)
			zoomStart = Integer.parseInt(propZoomStart);
		
		String propZoomStop = props.getProperty("zoomStop");
		if(propZoomStop != null)
			zoomStop = Integer.parseInt(propZoomStop);
		
		String propMetatiling = props.getProperty("metatiling");
		if(propMetatiling != null) {
			String[] metatiling = propMetatiling.split("x");
			if(metatiling != null && metatiling.length == 2) {
				metaWidth = Integer.parseInt(metatiling[0]);
				metaHeight = Integer.parseInt(metatiling[1]);
			} else {
				log.error("Unable to interpret metatiling="+propMetatiling+", expected something like 3x3");
			}
		}
		
		String propGrid = props.getProperty("grid");
		if(propGrid != null) {
			gridBase = new BBOX(propGrid);
			if(! gridBase.isSane()) {
				log.error("The grid "+propGrid+" intepreted as " + gridBase.toString() + " is not sane.");
			}
		} else {
			gridBase = new BBOX(-180.0, -90.0, 180.0, 90.0);
		}
		gridCalc = new GridCalculator(this);

		
		// The following depends on metatiling and grid
		String propBbox = props.getProperty("bbox");
		if(propBbox != null) {
			BBOX layerBounds = new BBOX(propBbox);
			log.info("Specified bbox " + layerBounds.toString() + ".");
			
			if(! layerBounds.isSane()) {
				log.error("The bbox "+propBbox+" intepreted as " 
						+ layerBounds.toString() + " is not sane.");
			} else if(! gridBase.contains(layerBounds)){
				log.error("The bbox "+propBbox+" intepreted as " 
						+ layerBounds.toString() + " is not contained by the grid: " + gridBase.toString());
			} else {
				bbox = layerBounds; // adjust to the closest grid ?
				//log.info("Recreated bbox: " + bbox.toString());
			}
		} else {
			bbox = new BBOX(-180.0, -90.0, 180.0, 90.0);
		}


		String propWidth = props.getProperty("width");
		if(propWidth != null)
			this.width = Integer.parseInt(propWidth);
		
		String propHeight = props.getProperty("height");
		if(propHeight != null)
			this.height = Integer.parseInt(propHeight);
		
		String propVersion = props.getProperty("version");
		if(propVersion != null)
			this.version = propVersion;

		String propErrorMime = props.getProperty("errormime");
		if(propErrorMime != null)
			this.errorMime = propErrorMime;
		
		String propTiled = props.getProperty("tiled");
		if(propTiled != null)
			this.tiled = propTiled;
	
		String propTransparent = props.getProperty("transparent");
		if(propTransparent != null)
			this.transparent = propTransparent;
		
		String propBgcolor = props.getProperty("bgcolor");
		if(propBgcolor != null)
			this.bgcolor = propBgcolor;
	
		String propPalette = props.getProperty("palette");
		if(propPalette != null)
			this.palette = propPalette;

		
		String propUrl = props.getProperty("wmsurl");
		if(propUrl != null) {
			this.wmsURL = propUrl.split(",");
		}
		
		String propLayers = props.getProperty("wmslayers");
		if(propLayers != null)
			this.wmsLayers = propLayers;

		String propStyles = props.getProperty("wmsstyles");
		if(propStyles != null)
			this.wmsStyles = propStyles;
		
		String propExpireClients = props.getProperty("expireclients");
		if(propExpireClients != null)
			expireClients = Integer.parseInt(propExpireClients) * 1000;

		String propExpireCache = props.getProperty("expireCache");
		if(propExpireCache != null)
			expireCache = Integer.parseInt(propExpireCache) * 1000;
		
	}
	
	/**
	 * Get the WMS backend URL that should be used next according to the round robin.
	 * 
	 * @return the next URL
	 */
	protected String nextWmsURL() {
		curWmsURL = (curWmsURL + 1) % this.wmsURL.length;
		return wmsURL[curWmsURL];
	}
		
	/**
	 * Gets the template for a WMS request for this profile,
	 * missing the response mimetype and the boundingbox.
	 * 
	 * This is just painful 
	 * 	- clone?
	 *  - IOException on mimetype?
	 * 
	 * -> Simplify WMSParameters?
	 */
	protected WMSParameters getWMSParamTemplate() {
		wmsparams = new WMSParameters();
		wmsparams.setRequest(request);
		wmsparams.setVersion(version);
		wmsparams.setLayer(wmsLayers);
		wmsparams.setSrs(srs);
		try { wmsparams.setErrormime(errorMime); } 
		catch (IOException ioe) { ioe.printStackTrace(); }
		wmsparams.setWidth(metaWidth*width);
		wmsparams.setHeight(metaHeight*height);
		if(transparent != null)
			wmsparams.setIsTransparent(transparent);
		if(tiled != null || metaHeight > 1 || metaWidth > 1)
			wmsparams.setIsTiled(tiled);
		if(bgcolor != null)
			wmsparams.setBgColor(bgcolor);
		if(palette != null)
			wmsparams.setPalette(palette);
		if(wmsStyles != null)
			wmsparams.setStyles(wmsStyles);
		
		return wmsparams;
	}
}