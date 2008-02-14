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
 */
    	
package org.geowebcache.layer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.cache.Cache;
import org.geowebcache.cache.CacheException;
import org.geowebcache.cache.CacheFactory;
import org.geowebcache.cachekey.CacheKey;
import org.geowebcache.cachekey.CacheKeyFactory;
import org.geowebcache.service.wms.WMSParameters;


public class TileLayer {
	private static Log log = LogFactory.getLog(org.geowebcache.layer.TileLayer.class);
	String name;
	LayerProfile profile;
	Cache cache;
	CacheKey cacheKey;
	ImageFormat[] formats = null;
	HashMap cacheQueue = new HashMap();
	boolean debugHeaders = false;
	Integer cacheLockWait = -1;

	
	public TileLayer(String layerName, Properties props) throws CacheException {
		this.name = layerName;
		setParametersFromProperties(props);
	}
	
	private void setParametersFromProperties(Properties props) throws CacheException {
		profile = new LayerProfile(props);
				
		// Cache and CacheKey
		String propCachetype = props.getProperty("cachetype");
		if(propCachetype != null) {
			cache = CacheFactory.getCache(propCachetype, props);
		} else {
			cache = CacheFactory.getCache("org.geowebcache.cache.jcs.JCSCache", null);
		}
		
		String propCacheKeytype = props.getProperty("cachekeytype");
		if(propCacheKeytype == null) {
			cacheKey = CacheKeyFactory.getCacheKey(cache.getDefaultCacheKeyName(), name);
		} else {
			cacheKey = CacheKeyFactory.getCacheKey(propCacheKeytype, name);
		}
			
		String propImageMIME = props.getProperty("imagemimes");
		if(propImageMIME != null) {
			String[] mimes = propImageMIME.split(",");
			formats = new ImageFormat[mimes.length];
			for(int i=0;i<mimes.length;i++) {
				formats[i] = ImageFormat.createFromMimeType(mimes[i]);
			}
		}
		String propDebugHeaders = props.getProperty("debugheaders");
		if(propDebugHeaders != null)
			debugHeaders = Boolean.valueOf(propDebugHeaders);
		
		String propCacheLockWait = props.getProperty("cachelockwait");
		if(propCacheLockWait != null)
			cacheLockWait = Integer.valueOf(propCacheLockWait);
	}
	
	private boolean supports(String imageMime) {
		for(int i=0; i<formats.length; i++) {
			if(formats[i].getMimeType().equalsIgnoreCase(imageMime))
				return true;
		}
		return false;
	}
	
	/**
	 * Rough checks to see whether the request is within bounds
	 * and whether we support the format
	 * 
	 * @param wmsparams
	 * @return
	 */
	public String covers(WMSParameters wmsparams) {
		if( ! wmsparams.getSrs().equalsIgnoreCase(this.profile.srs)) {
			return "Unexpected SRS: "+wmsparams.getSrs()+" , expected "+this.profile.srs;
		}
		
		if( ! this.supports(wmsparams.getImagemime().getMime())) {
			return "Unsupported MIME type requested: " + wmsparams.getImagemime().getMime();
		}
		
		BBOX reqbox = wmsparams.getBBOX();
		if(! reqbox.isSane()) {
			return "The requested bounding box "+reqbox.getReadableString()+" is not sane";
		}
		
		if(! this.profile.bbox.contains(reqbox) ) {
			return "The layers bounding box "+this.profile.bbox.getReadableString()
			+" does not cover the requested bounding box " + reqbox.getReadableString();
		}
		// All good
		return null;
	}
	
	/**
	 * 1) Lock metatile
	 * 2) Check whether tile is in cache -> If so, unlock metatile and return tile
	 * 3) Create metatile
	 * 4) Use metatile to forward request
	 * 5) Get tiles, save them to cache
	 * 6) Unlock metatile
	 * 6) Return tile
	 * 
	 * @param wmsparams
	 * @return
	 */
	public byte[] getData(WMSParameters wmsparams,HttpServletResponse response) {
		String debugHeadersStr = null;
		
		int[] gridLoc = profile.gridLocation(wmsparams.getBBOX());
		
		MetaTile metaTile = new MetaTile(this.profile, gridLoc);
		int[] metaGridLoc = metaTile.getMetaGridPos();
		
		// Acquire lock for this metatile
		boolean wait = cacheQueue.containsKey(metaGridLoc); 
		while(wait) {
			if(this.cacheLockWait > 0) {
				try {
					Thread.sleep(cacheLockWait);
				} catch (InterruptedException ie) {
					log.error("Thread got interrupted... how come?");
					ie.printStackTrace();
					// No big deal, though we should quit if anyone prefers that.
				}
			} else {
				Thread.yield();
			}
			Thread.yield();
			wait = cacheQueue.containsKey(metaGridLoc);
		}
		
		ImageFormat imageFormat = ImageFormat.createFromMimeType(wmsparams.getImagemime().getMime());
		Object ck = cacheKey.createKey(gridLoc[0], gridLoc[1], gridLoc[2], imageFormat.getExtension());
		
		if(debugHeaders) {
			debugHeadersStr = 
			"grid-location:"+gridLoc[0]+","+gridLoc[1]+","+gridLoc[2]+";"
			+"cachekey:"+ck.toString()+";";
		}
		
		/********************  Check cache  ********************/
		RawTile tile = null;
		if(profile.expireCache != LayerProfile.CACHE_NEVER) {
			try {
				tile = (RawTile) cache.get(ck,this.profile.expireCache);
				if(tile != null) {

					// Return lock
					removeFromCacheQueue(metaGridLoc);

					if(debugHeaders) {
						response.addHeader("geowebcache-debug",
								debugHeadersStr
								+"from-cache:true");
					}
					return tile.getData();
				}
			} catch (CacheException ce) {
				log.error("Failed to get " + wmsparams.toString() + " from cache");
				ce.printStackTrace();
			}
		}
		/********************  Request metatile  ********************/
		metaTile.doRequest(imageFormat.getMimeType());
		metaTile.createTiles();
		int[][] gridPositions = metaTile.getGridPositions();
		
		byte[] data = null;
		// Mostly for completeness, don't laugh
		if(profile.expireCache == LayerProfile.CACHE_NEVER) {
			data = getTile(gridLoc, gridPositions, metaTile, imageFormat);
		} else {
			saveTiles(gridPositions, metaTile, imageFormat);
						
			// Try the cache again
			try {
				tile = (RawTile) cache.get(ck, this.profile.expireCache);
			} catch (CacheException ce) {
				log.error("Failed to get " + wmsparams.toString() + " from cache, after first seeding cache.");
				ce.printStackTrace();
			}
			data = tile.getData();
			
			if(profile.expireClients == LayerProfile.CACHE_USE_WMS_BACKEND_VALUE) {
				profile.expireClients = metaTile.getExpiration();
				log.info("Setting expireClients based on metaTile: " + profile.expireClients);
			}
		}
		
		// Return lock
		removeFromCacheQueue(metaGridLoc);
		
		if(debugHeaders) {
			response.addHeader("geowebcache-debug",
					debugHeadersStr + "from-cache:false;wmsUrl:"+wmsparams.toString());
		}
		return data;
	}
	
	public void setExpirationHeader(HttpServletResponse response){
		if(profile.expireClients == LayerProfile.CACHE_VALUE_UNSET)
			return;
		
		if(profile.expireClients > 0) {
			response.setDateHeader("Expires", System.currentTimeMillis() + profile.expireClients);
		} else if(profile.expireClients == LayerProfile.CACHE_NEVER_EXPIRE) {
			long oneYear = 3600*24*365*1000;
			response.setDateHeader("Expires", System.currentTimeMillis() + oneYear);
		} else if(profile.expireClients == LayerProfile.CACHE_NEVER) {
			response.setDateHeader("Expires", 1);
		}
	}
	
	private void saveTiles(int[][] gridPositions, MetaTile metaTile, ImageFormat imageFormat) {
		// Loop over the gridPositions, generate cache keys and save to cache

		for(int i=0; i < gridPositions.length; i++) {
			int[] gridPos = gridPositions[i];
			
			Object ck = (Object) cacheKey.createKey(gridPos[0], gridPos[1], gridPos[2], imageFormat.getExtension());
			
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try {
				metaTile.writeTileToStream(i, imageFormat.getJavaName(), out);
			} catch(IOException ioe) {
				log.error("Unable to write image tile to ByteArrayOutputStream: " + ioe.getMessage());
				ioe.printStackTrace();
			}
			
			RawTile tile = new RawTile(out.toByteArray());
			
			if(profile.expireCache == LayerProfile.CACHE_USE_WMS_BACKEND_VALUE)
				profile.expireCache = metaTile.getExpiration();
			
				log.info("Setting expireCache based on metaTile for layer "
						+this.name+": "+profile.expireCache);
			try {
					cache.set(ck, tile, profile.expireCache);
			} catch (CacheException ce) {
				log.error("Unable to save data to cache, stack trace follows: " + ce.getMessage());
				ce.printStackTrace();
			}
		}		
	}
	
	private byte[] getTile(int[] gridPos, int[][] gridPositions, MetaTile metaTile, ImageFormat imageFormat) {		
		for(int i=0; i < gridPositions.length; i++) {
			int[] curPos = gridPositions[i];
			
			if(curPos.equals(gridPos)) {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				try {
					metaTile.writeTileToStream(i, imageFormat.getJavaName(), out);
				} catch(IOException ioe) {
					log.error("Unable to write image tile to ByteArrayOutputStream: " + ioe.getMessage());
					ioe.printStackTrace();
				}
				
				return out.toByteArray();
			}
		}
		return null;
	}
			
	private synchronized boolean addToCacheQueue(int[] metaGridLoc) {
		if(cacheQueue.containsKey(metaGridLoc)) {
			return false;
		} else {
			cacheQueue.put(metaGridLoc, new Boolean(true));
			return true;
		}
	}
	
	private synchronized boolean removeFromCacheQueue(int[] metaGridLoc) {
		if(cacheQueue.containsKey(metaGridLoc)) {
			cacheQueue.remove(metaGridLoc);
			return true;
		}
		return false;
	}
}
