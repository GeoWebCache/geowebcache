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
	protected BBOX bbox = new BBOX(-180.0, -90.0, 180.0, 90.0);
	double layerWidth = -1;
	double layerHeight = -1;
	protected int width = 256;
	protected int height = 256; 
	protected int metaWidth = 1;
	protected int metaHeight = 1;
	protected String request = "map";
	protected String version = "1.1.1";
	protected String errorMime = "application/vnd.ogc.se_inimage";
	protected String transparent = null;
	protected String tiled = null;
	protected String wmsURL = "http://localhost:8080/geoserver/wms";
	protected String wmsLayers = "topp:states";
	protected String wmsStyles = null;
	protected WMSParameters wmsparams = null;
	protected long expireClients = CACHE_USE_WMS_BACKEND_VALUE; 
	protected long expireCache =  CACHE_NEVER_EXPIRE;
	
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
		
		String propBbox = props.getProperty("bbox");
		if(propBbox != null)
			this.bbox = new BBOX(propBbox);

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
		
		String propUrl = props.getProperty("wmsurl");
		if(propUrl != null)
			this.wmsURL = propUrl;

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
		
		this.layerWidth = bbox.coords[2] - bbox.coords[0];
		this.layerHeight = bbox.coords[3] - bbox.coords[1];
	}
	
	/**
	 * Determines the location in a three dimensional grid based on
	 * WMS recommendations.
	 * 
	 * It creates a grid of (2^zoomLevel x 2^zoomLevel) tiles. 0,0 denotes the bottom
	 * left corner. The tile's location in this grid is determined as
	 * follows:
	 * 
	 * <ol><li>Based on the width of the requested tile the desired zoomlevel
	 * is determined.</li>
	 * <li>The rounded zoomLevel is used to divide the width into 2^zoomLevel segments</li>
	 * <li>The min X value is used to determine the X position on this grid</li>
	 * <li>The min Y value is used to determine the Y position on this grid</li>
	 * </ol>
	 * 
	 * @param tileBounds the bounds of the requested tile
	 * @return [0] = x coordinate , [1] y coordinate, [2] = zoomLevel, 
	 */
	public int[] gridLocation(BBOX tileBounds) {
		int[] retVals = new int[3];
		
		double reqTileWidth = tileBounds.coords[2] - tileBounds.coords[0];
		// (Z) Zoom level 
		retVals[2] = (int) Math.round(Math.log(this.layerWidth / reqTileWidth) / Math.log(2));
		
		double tileWidth = layerWidth / (Math.pow(2, retVals[2]));
		// X
		retVals[0] = (int) Math.round((tileBounds.coords[0] - bbox.coords[0])/tileWidth);
		// Y
		retVals[1] = (int) Math.round((tileBounds.coords[1] - bbox.coords[1])/tileWidth);
		
		if(log.isTraceEnabled()) {
			log.trace("zoomLevel: " + retVals[0] + " x:" + retVals[1]+ " y:" + retVals[2]);
		}
		return retVals;
	}
	
	/**
	 * Calculates bottom left and top right grid positions for a particular zoomlevel 
	 * 
	 * @param bounds
	 * @return
	 */
	private int[] gridExtent(int zoomLevel, BBOX bounds) {
		int[] retVals = new int[4];
		
		double tileWidth = layerWidth / (Math.pow(2, zoomLevel));
		// min X
		retVals[0] = (int) Math.round((bounds.coords[0] - bbox.coords[0])/tileWidth - 0.49);
		// min Y
		retVals[1] = (int) Math.round((bounds.coords[1] - bbox.coords[1])/tileWidth - 0.49);
		// max X
		retVals[2] = (int) Math.round((bounds.coords[2] - bbox.coords[0])/tileWidth + 0.49);
		// max Y
		retVals[3] = (int) Math.round((bounds.coords[3] - bbox.coords[1])/tileWidth + 0.49);
		
		return retVals;
	}
	
	/**
	 * Used for seeding, returns gridExtent but adjusts for meta tile size
	 * 
	 * @return
	 */
	protected int[] metaGridExtent(int zoomLevel, BBOX bounds) {
		int[] retVals = gridExtent(zoomLevel, bounds);
		
		retVals[0] = retVals[0] - (retVals[0] % this.metaWidth);
		retVals[1] = retVals[1] - (retVals[1] % this.metaHeight);
		retVals[2] = retVals[2] + (retVals[2] % this.metaWidth);
		retVals[3] = retVals[3] + (retVals[3] % this.metaHeight);
		
		return retVals;
	}
	
	/**
	 * Uses the location on the grid to determine 
	 * 
	 * @param gridLoc
	 * @return
	 */
	public BBOX recreateBbox(int[] gridLoc) {
		double tileWidth = this.layerWidth / Math.pow(2, gridLoc[2]);
		
		return new BBOX(bbox.coords[0] + tileWidth*gridLoc[0],
						bbox.coords[1] + tileWidth*gridLoc[1],
						bbox.coords[0] + tileWidth*(gridLoc[0] + 1),
						bbox.coords[1] + tileWidth*(gridLoc[1] + 1));
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
		if(wmsStyles != null)
			wmsparams.setStyles(wmsStyles);
		
		return wmsparams;
	}
}