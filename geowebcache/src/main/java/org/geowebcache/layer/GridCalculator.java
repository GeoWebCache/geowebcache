package org.geowebcache.layer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class GridCalculator {
	private static Log log = LogFactory.getLog(org.geowebcache.layer.GridCalculator.class);
	private BBOX base = null;
	private double baseWidth;
	//private double layerHeight;
	
	protected GridCalculator(BBOX base) {
		this.base = base;
		// Calculate
		this.baseWidth = base.coords[2] - base.coords[0];
		//this.layerHeight = base.coords[3] - base.coords[1];
	}
	

	/**
	 * Expands the given bounds to the closest bounds supported by the grid
	 *  
	 * @param bounds
	 * @return
	 */
	public BBOX expandedBbox(BBOX bounds) {		
		// Find the closest zoomlevel, zoom out if necessary
		double reqTileWidth = bounds.coords[2] - bounds.coords[0];
		double reqTileHeight = bounds.coords[3] - bounds.coords[1];
		
		if(reqTileHeight > reqTileWidth) {
			// We only care about the larger one anyway
			reqTileWidth = reqTileHeight;
		}
		
		// Calculate the approximate zoomLevel, may have to zoom out later
		double zoomDouble = Math.log((baseWidth / reqTileWidth) / Math.log(2));
		long zoomLong = (long) Math.floor(zoomDouble) + 2; // TODO This is a bit too much black magic, why 2?
		double tileWidth = baseWidth / (Math.pow(2, zoomLong));
		
		// Tolerate up to 10% error here
		if(tileWidth*1.01 > reqTileWidth) {
			//This may be okay
		} else {
			zoomLong = zoomLong - 1;
			// Caculate tileWidth again, which we have forced to be larger than reqTileWidth
			tileWidth = baseWidth / (Math.pow(2, zoomLong));
		}
		
		if(zoomLong < 0){
			log.error("expandedBbox("+bounds.toString()+") - You really can't zoom that far out.");
		}
		
		// TODO still missing some rounding here
		// Min X
		int mingridx = (int) Math.floor((bounds.coords[0] - base.coords[0])/tileWidth);
		// Min Y
		int mingridy = (int) Math.floor((bounds.coords[1] - base.coords[1])/tileWidth);
		// Max X
		int maxgridx = (int) Math.ceil((bounds.coords[2] - base.coords[0])/tileWidth);
		// Max Y
		int maxgridy = (int) Math.ceil((bounds.coords[3] - base.coords[1])/tileWidth);
		
		if(maxgridy - mingridy != 1) {
			log.error("maxgridy: " + maxgridy + "  mingridy: "+mingridy);
		} 
		if(maxgridx - mingridx != 1) {
			log.error("maxgridx: " + maxgridx + "  mingridx: "+mingridx);
		}
		
		return new BBOX(
					base.coords[0] + tileWidth*mingridx,
					base.coords[1] + tileWidth*mingridy,
					base.coords[0] + tileWidth*maxgridx,
					base.coords[1] + tileWidth*maxgridy );
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
		retVals[2] = (int) Math.round(Math.log(baseWidth / reqTileWidth) / Math.log(2));
		
		double tileWidth = baseWidth / (Math.pow(2, retVals[2]));
		// X
		retVals[0] = (int) Math.round((tileBounds.coords[0] - base.coords[0])/tileWidth);
		// Y
		retVals[1] = (int) Math.round((tileBounds.coords[1] - base.coords[1])/tileWidth);
		
		if(log.isTraceEnabled()) {
			log.trace("x: " + retVals[0] + " y:" + retVals[1]+ " z:" + retVals[2]);
		}
		return retVals;
	}
	
	
	/**
	 * Calculates bottom left and top right grid positions for a particular zoomlevel 
	 * 
	 * @param bounds
	 * @return
	 */
	protected int[] gridExtent(int zoomLevel, BBOX bounds) {
		int[] retVals = new int[4];
		
		double tileWidth = baseWidth / (Math.pow(2, zoomLevel));
		// min X
		retVals[0] = (int) Math.round((bounds.coords[0] - base.coords[0])/tileWidth);
		// min Y
		retVals[1] = (int) Math.round((bounds.coords[1] - base.coords[1])/tileWidth);
		// max X
		retVals[2] = (int) Math.round((bounds.coords[2] - base.coords[0])/tileWidth);
		// max Y
		retVals[3] = (int) Math.round((bounds.coords[3] - base.coords[1])/tileWidth);
		
		return retVals;
	}
	
	/**
	 * Uses the location on the grid to determine 
	 * 
	 * @param gridLoc
	 * @return
	 */
	public BBOX recreateBbox(int[] gridLoc) {
		double tileWidth = baseWidth / Math.pow(2, gridLoc[2]);
		
		return new BBOX(base.coords[0] + tileWidth*gridLoc[0],
						base.coords[1] + tileWidth*gridLoc[1],
						base.coords[0] + tileWidth*(gridLoc[0] + 1),
						base.coords[1] + tileWidth*(gridLoc[1] + 1));
	}
	
	/**
	 * Calculates he bounding box for the meta tile based on the 
	 * grid calculated above.
	 * 
	 * TODO verify this is the right bbox
	 */
	protected BBOX calcMetaBbox(int[] metaGrid) {		
		double tileWidth = baseWidth / Math.pow(2, metaGrid[4]);
		
		BBOX metaBbox = new BBOX(
						base.coords[0] + tileWidth*metaGrid[0],
						base.coords[1] + tileWidth*metaGrid[1],
						base.coords[0] + tileWidth*metaGrid[2],
						base.coords[1] + tileWidth*metaGrid[3] );
		
		return metaBbox;
	}
	

}
