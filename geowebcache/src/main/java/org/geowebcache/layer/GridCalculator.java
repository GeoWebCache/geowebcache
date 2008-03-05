package org.geowebcache.layer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class GridCalculator {
	private static Log log = LogFactory.getLog(org.geowebcache.layer.GridCalculator.class);
	private BBOX base = null;
	private double baseWidth;
	private double baseHeight;
	private int[][] gridLevels = null;
	private LayerProfile profile = null;
	
	//TODO this code does not handle coordinate systems where the base height is bigger than the width
	//private double layerHeight;
	
	protected GridCalculator(LayerProfile profile, BBOX gridBase) {
		this.base = gridBase;
		// Calculate
		this.baseWidth = base.coords[2] - base.coords[0];
		this.baseHeight = base.coords[3] - base.coords[1];
		this.profile = profile;
	}
	
	protected void calculateGridBounds(BBOX layerBounds, int zoomStart, int zoomStop) {
		gridLevels = new int[zoomStop + 1][4]; // We'll just waste a few bytes, for cheap lookups

		double tileWidth = profile.maxTileWidth;
		double tileHeight = profile.maxTileHeight;
		
		int tileCountX = (int) Math.round(baseWidth/tileWidth);
		int tileCountY = (int) Math.round(baseHeight/tileHeight);
		
		int metaLarger = (profile.metaHeight > profile.metaWidth) ? profile.metaHeight : profile.metaWidth;
		
		for(int level = 0; level <= zoomStop; level++) {
			// Min X
			gridLevels[level][0] = (int) Math.floor((layerBounds.coords[0] - base.coords[0])/tileWidth);
			// Min Y
			gridLevels[level][1] = (int) Math.floor((layerBounds.coords[1] - base.coords[1])/tileHeight);
			// Max X
			gridLevels[level][2] = (int) Math.ceil((layerBounds.coords[2] - base.coords[0])/tileWidth) - 1;
			// Max Y
			gridLevels[level][3] = (int) Math.ceil((layerBounds.coords[3] - base.coords[1])/tileHeight) - 1;
			
			// Adjust for metatiling if appropriate

			
			if(tileCountX > metaLarger || tileCountY > metaLarger) {
				// Round down
				gridLevels[level][0] = gridLevels[level][0] - (gridLevels[level][0] % profile.metaWidth);
				// Round down
				gridLevels[level][1] = gridLevels[level][1] - (gridLevels[level][1] % profile.metaHeight);
				// Naive round up
				gridLevels[level][2] = gridLevels[level][2] - (gridLevels[level][2] % profile.metaWidth) + (profile.metaWidth -1);
				// Naive round up
				gridLevels[level][3] = gridLevels[level][3] - (gridLevels[level][3] % profile.metaHeight) + (profile.metaHeight -1);
				
				// Fix for naive round ups, imagine applying a 3x3 metatile to a 4x4 grid
				if(gridLevels[level][2] >= tileCountX) {
					gridLevels[level][2] = tileCountX - 1;
				}
				if(gridLevels[level][3] >= tileCountY) {
					gridLevels[level][3] = tileCountY - 1;
				}
			}
			
			// For the next round
			tileWidth = tileWidth / 2;
			tileHeight = tileHeight / 2;
			
			tileCountX = tileCountX * 2;
			tileCountY = tileCountY * 2;
		}
	}
	
	protected int[] getGridBounds(int zoomLevel) {
		return this.gridLevels[zoomLevel].clone();
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
	 * @return [0] = x coordinate , [1] y coordinate, [2] = zoomLevel
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
	
	public int inRange(int[] location) {
		// Check Z
		if(location[2] < profile.zoomStart) {
			return -1;
		}
		if(location[2] >= gridLevels.length) {
			return -2;
		}
		
		int[] bounds = gridLevels[location[2]];

		// Check X
		if(location[0] < bounds[0] || location[0] > bounds[2]) {
			return -3;
		}
		
		// Check Y
		if(location[1] < bounds[1] || location[1] > bounds[3]) {
			return -4;
		}
		
		return 0;
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
		retVals[2] = (int) Math.round((bounds.coords[2] - base.coords[0])/tileWidth) - 1;
		// max Y
		retVals[3] = (int) Math.round((bounds.coords[3] - base.coords[1])/tileWidth) - 1;
		
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
	
	/**
	 * Used for seeding, returns gridExtent but adjusts for meta tile size
	 * 
	 * @return
	 */
	protected int[] metaGridExtent(int zoomLevel, BBOX bounds) {
		int[] retVals = this.gridExtent(zoomLevel, bounds);
		retVals[0] = retVals[0] - (retVals[0] % profile.metaWidth);
		retVals[1] = retVals[1] - (retVals[1] % profile.metaHeight);
		retVals[2] = retVals[2] + (retVals[2] % profile.metaWidth);
		retVals[3] = retVals[3] + (retVals[3] % profile.metaHeight);	
		return retVals;
	}

}
