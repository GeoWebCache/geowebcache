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
	
	LayerProfile profile = null;
	int[] metaGrid  = new int[5]; //minx,miny,maxx,maxy,zoomlevel
	BBOX metaBbox = null;
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
		this.calcMetaGrid(initGridPosition);
	}
	
	/**
	 * Used for seeder
	 * 
	 * @param profile
	 * @param metaGrid
	 * @param doesNothing
	 */
	protected MetaTile(LayerProfile profile, int[] metaGrid, boolean doesNothing) {
		this.profile = profile;
		this.metaGrid = metaGrid;
	}
	
	/**
	 * Calculates the tile positions covered by this metatile
	 * @param grid
	 */
	protected void calcMetaGrid(int[] grid) {
		metaGrid[0] = grid[0] - (grid[0] % profile.metaWidth);
		metaGrid[2] = metaGrid[0] + profile.metaWidth;
		
		metaGrid[1] = grid[1] - (grid[1] % profile.metaHeight);
		metaGrid[3] = metaGrid[1] + profile.metaHeight;
		metaGrid[4] = grid[2];
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
		int tileCount = profile.metaWidth*profile.metaHeight;
		this.gridPositions = new int[tileCount][4];
		
		for(int y=0; y < profile.metaHeight; y++) {	
			for(int x=0; x< profile.metaWidth; x++) {
				int tile = y*profile.metaWidth + x;
				
				gridPositions[tile][0] = metaGrid[0] + x;
				gridPositions[tile][1] = metaGrid[1] + y;
				gridPositions[tile][2] = metaGrid[4];
			}
		}
	}
	
	
	protected void doRequest(String imageMime) {
		this.metaBbox = profile.gridCalc.calcMetaBbox(metaGrid);
		WMSParameters wmsparams = profile.getWMSParamTemplate();
		
		// Fill in the blanks
		try { wmsparams.setImagemime(imageMime); } 
		catch (IOException ioe) { ioe.printStackTrace(); }
		
		wmsparams.setBBOX(this.metaBbox);
		
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
			this.failed = true;
		}
	}
	
	private void forwardRequest(WMSParameters wmsparams, String backendURL) throws IOException, ConnectException {
		if(log.isTraceEnabled())
			log.trace("Forwarding request to " + this.profile.wmsURL);
		
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
				this.expiration = wmsBackendMaxAge.longValue() * 1000;
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
		
		if(log.isTraceEnabled())
			log.trace("Got image from backend, height: " + this.img.getHeight());
	}
	
	// TODO verify we dont cross the date boundary
	protected void createTiles() {
		tiles = new BufferedImage[profile.metaWidth*profile.metaHeight];
		
		if(tiles.length > 1) {
			//final int tileSize = key.getTileSize();
			//final RenderingHints no_cache = new RenderingHints(JAI.KEY_TILE_CACHE, null);
			int yfix = profile.height*profile.metaHeight;
		
			for(int y=0; y < profile.metaHeight; y++) {	
				for(int x=0; x< profile.metaWidth; x++) {
					int tile = y*profile.metaWidth + x;
				
					int i = x * profile.width;
					int j = (y+1) * profile.height;

					tiles[tile] = img.getSubimage(i,yfix-j, profile.width, profile.height);
				}
			}
		} else {
			tiles[0] = this.img;
		}
	}
	
	protected boolean writeTileToStream(int tileIdx, String format, OutputStream os) throws IOException {
		if(this.tiles == null) {
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
		if(cacheControlHeader == null)
			return null;
		
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
		if(this.gridPositions == null)
			fillGridPositions();
		
		return this.gridPositions;
	}
	
	protected BufferedImage getRawImage() {
		return this.img;
	}
	
	protected long getExpiration(){
		return this.expiration;
	}
}
