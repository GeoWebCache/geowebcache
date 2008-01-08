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
package org.geowebcache.tile;

import java.util.Properties;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.service.Parameters;
import org.geowebcache.service.wms.WMSParameters;
import org.geowebcache.util.Configuration;

public class TileProfile {
	private static Log log = LogFactory.getLog(org.geowebcache.tile.TileProfile.class);
	
	// This assumes image output
	protected String srs = "EPSG:4326";
	protected BBOX bbox = new BBOX(-180.0, -90.0, 180.0, 90.0);
	double layerWidth = 360.0;
	double layerHeight = 180.0;
	protected int width = 256;
	protected int height = 256; 
	protected String version = "1.1.1";
	protected String errorMime = "";
	protected String transparent = null;
	protected String tiled = null;
	
	public TileProfile(Properties props) {		
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
		
		this.layerWidth = bbox.coords[2] - bbox.coords[0];
		this.layerHeight = bbox.coords[3] - bbox.coords[1];
	}
	
//	public boolean isInResolutions(BBOX bbox) {
//		Configuration config = Configuration.getInstance();
//		Set resset = config.getResolutionConstraints();
//
//		// If the set is null, then all resolutions are allowed
//		if(resset == null) {
//			return true;
//		}
//
//		Double minres = new Double(calculateMinimumResolution(bbox));
//		Double maxres = new Double(calculateMaximumResolution(bbox));
//
//		// Check each resolution in the set
//		for(Object obj : this.resolutions) {
//			Double res = (Double)obj;
//
//			if(log.isTraceEnabled()) {
//				log.trace("Min res: " + minres + " Max res: " + maxres +
//						" comparing to set res: " + res);
//			}
//
//			if(res > minres && res < maxres) {
//				if(log.isDebugEnabled()) {
//					log.debug("Resolution is within resolution constraints: " + (Double)res);
//				}
//				return true;
//			}
//		}
//		// Resolution not found
//		return false;
//	}

	/**
	 * Returns the minimum resolution bound for the given BBOX
	 * (i.e. the smallest resolution at which pixel change would occur)
	 * 
	 * @param box
	 * @return
	 */
//	private double calculateMinimumResolution(BBOX box) {
//		// Add one pixel to each image dimension
//		double xres = Math.abs((box.getMaximumX()- box.getMinimumX()) / ((double)(this.imgFormat.width + 1)));
//		double yres = Math.abs((box.getMinimumY() - box.getMinimumY()) / ((double)(this.imgFormat.height + 1)));
//
//		// Return the larger of the two dimensions
//		return Math.max(xres, yres);
//	}

	/**
	 * Returns the maximum resolution bound for the given BBOX
	 * (i.e. the largest resolution at which pixel change would occur)
	 * @param box
	 * @return
	 */
//	private double calculateMaximumResolution(BBOX box) {
//		// Subtract one pixel to each image dimension
//		double xres = Math.abs((box.getMaximumX()- box.getMinimumX()) / ((double)(this.imgFormat.width - 1)));
//		double yres = Math.abs((box.getMaximumY() - box.getMinimumY()) / ((double)(this.imgFormat.height - 1)));
//
//		// Return the smaller of the two dimensions
//		return Math.min(xres, yres);
//	}
	
//	private void setResolutionsFromConfiguration() {
//		Configuration config = Configuration.getInstance();
//		this.resolutions = config.getResolutionConstraints();
//	}

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
	
}
