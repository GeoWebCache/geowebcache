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
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 *  
 */
package org.geowebcache.layer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.service.Request;
import org.geowebcache.service.wms.WMSParameters;

public class MetaTile {
	private static Log log = LogFactory.getLog(org.geowebcache.layer.MetaTile.class);

	private LayerProfile profile = null;
	protected int[] metaGrid  = new int[5]; //minx,miny,maxx,maxy,zoomlevel
	protected BBOX metaBbox = null;
	int metaX = -1; // The actual X metatiling factor, after adjusting to bounds
	int metaY = -1; // The actual Y metatiling factor, after adjusting to bounds.
	int[][] gridPositions = null;
	private BufferedImage img = null;
	private BufferedImage[] tiles = null;
	private long expiration = LayerProfile.CACHE_VALUE_UNSET;
	ImageWriter imageWriter = null;
	public boolean failed = false;

	/**
	 * Used for requests by clients
	 * 
	 * @param profile
	 * @param initGridPosition
	 */
	protected MetaTile(LayerProfile profile, int[] initGridPosition) {
		this.profile = profile;
		calcMetaGrid(initGridPosition);
		metaBbox = profile.gridCalc.calcMetaBbox(metaGrid);
		fillGridPositions();
	}

	/**
	 * Used for seeder to distinguish int[]s
	 * 
	 * @param profile
	 * @param metaGrid
	 * @param doesNothing
	 */
	protected MetaTile(LayerProfile profile, int[] metaGrid, boolean doesNothing) {
		this.profile = profile;
		this.metaGrid = metaGrid;
		metaBbox = profile.gridCalc.calcMetaBbox(metaGrid);
		fillGridPositions();
	}

	/**
	 * Calculates the tile positions covered by this metatile
	 * 
	 * @param grid
	 */
	protected void calcMetaGrid(int[] gridLoc) {
		int[] gridBounds = profile.gridCalc.getGridBounds(gridLoc[2]);
		
		if(log.isDebugEnabled())
			log.debug("calcMetaGrid gridBounds: " + Arrays.toString(gridBounds));
		//System.out.println("gridBounds: " + Arrays.toString(gridBounds));
		
		// Naively
		metaGrid[0] = gridLoc[0] - (gridLoc[0] % profile.metaWidth);
		metaGrid[2] = metaGrid[0]+profile.metaWidth;
		metaGrid[1] = gridLoc[1] - (gridLoc[1] % profile.metaHeight);
		metaGrid[3] = Math.min(metaGrid[1]+profile.metaHeight, gridBounds[3]);
		// Zoomlevel
		metaGrid[4] = gridLoc[2];
		
		if(log.isDebugEnabled())
			log.debug("calcMetaGrid naively: " + Arrays.toString(metaGrid));
		//System.out.println("Naively: " + Arrays.toString(metaGrid));
		
		// Adjust for max bounds
		if(metaGrid[2] > gridBounds[2]) {
			metaX = gridBounds[2] - metaGrid[0];
			metaGrid[2] = gridBounds[2];
		} else {
			metaX = profile.metaWidth;
		}
		
		if(metaGrid[3] > gridBounds[3]) {
			metaY = gridBounds[3] - metaGrid[1];
			metaGrid[3] = gridBounds[3];
		} else {
			metaY = profile.metaWidth;
		}
		
		if(log.isDebugEnabled())
			log.debug("calcMetaGrid post adjustment: " + Arrays.toString(metaGrid));
		//System.out.println("Post : " + Arrays.toString(metaGrid));

	}



	/**
	 * The bottom left grid position and zoomlevel for this metatile,
	 * used for locking.
	 * @return
	 */
	protected int[] getMetaGridPos() {
		int[] gridPos = {metaGrid[0], metaGrid[1], metaGrid[4]};
		return gridPos;
	}

	/**
	 * Creates an array with all the grid positions, used for cache keys
	 */
	protected void fillGridPositions() {
		int[] gridBounds = profile.gridCalc.getGridBounds(metaGrid[4]);

		metaX = metaGrid[0] - Math.min(metaGrid[0]+profile.metaWidth, gridBounds[2]);
		metaY = metaGrid[1] - Math.min(metaGrid[1]+profile.metaHeight, gridBounds[3]);

		gridPositions = new int[metaX*metaY][3];

		for(int y=0; y < metaY; y++) {
			for(int x=0; x< metaX; x++) {
				int tile = y*metaX + x;
				gridPositions[tile][0] = metaGrid[0] + x;
				gridPositions[tile][1] = metaGrid[1] + y;
				gridPositions[tile][2] = metaGrid[4];
			}
		}
	}


	protected void doRequest(String imageMime) {

		WMSParameters wmsparams = profile.getWMSParamTemplate();

		// Fill in the blanks
		try { wmsparams.setImagemime(imageMime); } 
		catch (IOException ioe) { ioe.printStackTrace(); }

		wmsparams.setBBOX(metaBbox);

		// Ask the WMS server, saves returned image into metaTile
		// TODO add exception for configurations that do not use metatiling

		int backendTries = 0; // keep track of how many backends we have tried
		while(img == null && backendTries < profile.wmsURL.length) {
			String backendURL = profile.nextWmsURL();

			try {
				forwardRequest(wmsparams,backendURL);
			} catch (ConnectException ce) {
				log.error("Error forwarding request, "
						+backendURL+wmsparams.toString()+" "+ce.getMessage());
			} catch (IOException ioe) {
				log.error("Error forwarding request, "
						+backendURL+wmsparams.toString()+" "+ioe.getMessage());
				ioe.printStackTrace();
			}
			backendTries++;
		}

		if(img == null) {
			failed = true;
		}
	}

	private void forwardRequest(WMSParameters wmsparams, String backendURL) throws IOException, ConnectException {
		if(log.isTraceEnabled()) {
			log.trace("Forwarding request to " + profile.wmsURL);
		}

		// Create an outgoing WMS request to the server
		Request wmsrequest = new Request(backendURL, wmsparams);
		URL wmsBackendUrl = new URL(wmsrequest.toString());
		URLConnection wmsBackendCon = wmsBackendUrl.openConnection();

		// Do we need to keep track of expiration headers?
		if(profile.expireCache == LayerProfile.CACHE_USE_WMS_BACKEND_VALUE 
				|| profile.expireClients == LayerProfile.CACHE_USE_WMS_BACKEND_VALUE) {

			String cacheControlHeader = wmsBackendCon.getHeaderField("Cache-Control");
			Long wmsBackendMaxAge = extractHeaderMaxAge(cacheControlHeader);

			if(wmsBackendMaxAge != null) {
				log.info("Saved Cache-Control MaxAge from backend: " + wmsBackendMaxAge.toString());
				expiration = wmsBackendMaxAge.longValue() * 1000;
			} else {
				log.error("Layer profile wants MaxAge from backend, but backend does not provide this.");
			}
		}

		img = ImageIO.read(wmsBackendCon.getInputStream());

		if(img == null) {
			//System.out.println("Failed fetching "+  wmsrequest.toString());
			log.error("Failed fetching: " + wmsrequest.toString());
		} else if(log.isDebugEnabled()) {
			//System.out.println("Fetched "+  wmsrequest.toString());
			log.debug("Requested and got: " + wmsrequest.toString());
		}

		if(log.isTraceEnabled()) {
			log.trace("Got image from backend, height: " + img.getHeight());
		}
	}

	protected void createTiles() {
		tiles = new BufferedImage[metaX*metaY];

		if(tiles.length > 1) {
			//final int tileSize = key.getTileSize();
			//final RenderingHints no_cache = new RenderingHints(JAI.KEY_TILE_CACHE, null);
			int yfix = metaX*metaY;

			for(int y=0; y < metaY; y++) {	
				for(int x=0; x< metaX; x++) {
					int tile = y*metaX + x;

					int i = x * metaX;
					int j = (y+1) * metaY;

					tiles[tile] = img.getSubimage(i,yfix-j, profile.width, profile.height);
				}
			}
		} else {
			tiles[0] = img;
		}
	}

	protected boolean writeTileToStream(int tileIdx, String format, OutputStream os) throws IOException {
		if(tiles == null) {
			return false;
		} else {
			//if(this.imageWriter == null)
			//	initImageWriter(format);
			javax.imageio.ImageIO.write(tiles[tileIdx], format, os);
			return true;
		}
	}

	//private void initImageWriter(String format) {
	//	imageWriter = javax.imageio.ImageIO.getImageWritersByFormatName(format).next();
	//	
	//	if(imageWriter == null) {
	//		log.error("Unable to find ImageWriter for format" + format);
	//	}
	//}

	private static Long extractHeaderMaxAge(String cacheControlHeader) {
		if(cacheControlHeader == null) {
			return null;
		}

		String expression = "max-age=([0-9]*)[ ,]";
		Pattern p = Pattern.compile(expression);
		Matcher m = p.matcher(cacheControlHeader.toLowerCase());

		if(m.find()){
			return new Long(Long.parseLong(m.group(1)));
		}else{
			return null;
		}		
	}

	protected int[][] getGridPositions() {
		return gridPositions;
	}

	protected BufferedImage getRawImage() {
		return img;
	}

	protected long getExpiration(){
		return expiration;
	}

	public String debugString() {
		if(metaBbox == null) {
			System.out.println("metaBbox is null");
		}

		return "metaBbox: "+metaBbox.toString()+ " metaX: "+metaX+" metaY: "+metaY+" metaGrid: "+Arrays.toString(metaGrid);
	}
}
