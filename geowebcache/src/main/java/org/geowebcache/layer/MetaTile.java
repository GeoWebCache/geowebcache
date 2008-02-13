package org.geowebcache.layer;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;

import javax.imageio.ImageIO;
import javax.media.jai.JAI;
import javax.media.jai.operator.CropDescriptor;

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
	
	
	protected MetaTile(LayerProfile profile, int[] initGridPosition) {
		this.profile = profile;
		this.calcMetaGrid(initGridPosition);
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
	 * Calculates he bounding box for the meta tile based on the 
	 * grid calculated above.
	 */
	protected void calcMetaBbox() {		
		double tileWidth = profile.layerWidth / Math.pow(2, metaGrid[4]);
		metaBbox = new BBOX(
						profile.bbox.coords[0] + tileWidth*metaGrid[0],
						profile.bbox.coords[1] + tileWidth*metaGrid[1],
						profile.bbox.coords[0] + tileWidth*metaGrid[2],
						profile.bbox.coords[1] + tileWidth*metaGrid[3] );
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
		this.calcMetaBbox();
		WMSParameters wmsparams = profile.getWMSParamTemplate();
		
		// Fill in the blanks
		try { wmsparams.setImagemime(imageMime); } 
		catch (IOException ioe) { ioe.printStackTrace(); }
		
		wmsparams.setBBOX(this.metaBbox);
		
		// Ask the WMS server, saves returned image into metaTile
		// TODO add exception for configurations that do not use metatiling
		try {
			forwardRequest(wmsparams);
		} catch (IOException ioe) {
			log.error("Error forwarding request, "+wmsparams.toString()+" "+ioe.getMessage());
			ioe.printStackTrace();
		}
	}
	
	private void forwardRequest(WMSParameters wmsparams) throws IOException {
		log.trace("Forwarding request to " + this.profile.wmsURL);
		
		// Create an outgoing WMS request to the server
		Request wmsrequest = new Request(this.profile.wmsURL, wmsparams);

		this.img = ImageIO.read(new URL(wmsrequest.toString()));
		if(img == null) {
			System.out.println("Failed fetching "+  wmsrequest.toString());
			log.equals("Failed fetching: " + wmsrequest.toString());
		} else {
			System.out.println("Fetched "+  wmsrequest.toString());
			log.debug("Requested and got: " + wmsrequest.toString());
		}
		System.out.println("Fetched image height: " + this.img.getHeight());
	}
	
	protected void createTiles() {
		tiles = new BufferedImage[profile.metaWidth*profile.metaHeight];
		
		if(tiles.length > 1) {
			//final int tileSize = key.getTileSize();
			final RenderingHints no_cache = new RenderingHints(JAI.KEY_TILE_CACHE, null);
			int yfix = profile.height*profile.metaHeight;
		
			for(int y=0; y < profile.metaHeight; y++) {	
				for(int x=0; x< profile.metaWidth; x++) {
					int tile = y*profile.metaWidth + x;
				
					int i = x * profile.width;
					int j = (y+1) * profile.height;

					tiles[tile] = img.getSubimage(i,profile.height*profile.metaHeight-j, profile.width, profile.height);
					//System.out.println("CropDescriptor.create("+img.toString()+","+new Float(i)+","+new Float(j)+","+new Float(profile.width)+","+new Float(profile.width));
					//tiles[tile] = CropDescriptor.create(img, new Float(i), new Float(j), new Float(profile.width), new Float(profile.width), no_cache);
					//tiles[tile] = CropDescriptor.create(img, new Float(x), new Float(y), new Float(profile.width), new Float(profile.height), null);
					//System.out.println("Produced: " + tiles[tile].toString());
				}
			}
		} else {
			System.out.println("Taking a shortcut here.... ");
			tiles[0] = this.img;
		}
	}
	
	protected boolean writeTileToStream(int tileIdx, String format, OutputStream os) throws IOException {
		if(this.tiles == null) {
			return false;
		} else {
			javax.imageio.ImageIO.write(tiles[tileIdx], format, os);
			return true;
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
}
