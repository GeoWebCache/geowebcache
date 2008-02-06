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
    	
package org.geowebcache.tile;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantLock;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.cache.Cache;
import org.geowebcache.cache.CacheException;
import org.geowebcache.cache.CacheFactory;
import org.geowebcache.cachekey.CacheKey;
import org.geowebcache.cachekey.CacheKeyFactory;
import org.geowebcache.mime.ImageMimeType;
import org.geowebcache.service.Connection;
import org.geowebcache.service.Parameters;
import org.geowebcache.service.Request;
import org.geowebcache.service.Response;
import org.geowebcache.service.wms.WMSParameters;


public class TileLayer {
	private static Log log = LogFactory.getLog(org.geowebcache.tile.TileLayer.class);
	String name;
	WMSLayer wmsLayer;
	TileProfile tileProfile;
	Cache cache;
	CacheKey cacheKey;
	//ImageFormats[] formats = null;
	HashMap cacheQueue = new HashMap();
	// Temporary!
	String imageExtension = ".png";
	String imageMIME = "image/png";
	boolean debugHeaders = false;
	
	public TileLayer(String layerName, Properties props) throws CacheException {
		this.name = layerName;
		setParametersFromProperties(props);
	}
	
	private void setParametersFromProperties(Properties props) throws CacheException {
		wmsLayer = new WMSLayer(props);
		tileProfile = new TileProfile(props);
		
		// Image formats
		//String propImageFormats = props.getProperty("imageFormats");
		//if(propImageFormats != null)
		//mimeTypes = new String[4];
		//mimeTypes[0] = set
		
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
		
		String propImageExtension = props.getProperty("imageextension");
		if(propImageExtension != null)
			imageExtension = propImageExtension;
			
		String propImageMIME = props.getProperty("imagemime");
		if(propImageMIME != null)
			imageMIME = propImageMIME;

		String propDebugHeaders = props.getProperty("debugHeaders");
		if(propDebugHeaders != null)
			debugHeaders = Boolean.valueOf(propDebugHeaders);
	}
	
	private boolean supports(String format) {
		return true;
	}
	
	/**
	 * Rough checks to see whether the request is within bounds
	 * and whether we support the format
	 * 
	 * @param wmsparams
	 * @return
	 */
	public String covers(WMSParameters wmsparams) {
		if( ! wmsparams.getSrs().equalsIgnoreCase(this.tileProfile.srs)) {
			return "Unexpected SRS: "+wmsparams.getSrs()+" , expected "+this.tileProfile.srs;
		}
		
		if( ! this.supports(wmsparams.getImagemime().getMime())) {
			return "Unsupported MIME type requested: " + wmsparams.getImagemime().getMime();
		}
		
		BBOX reqbox = wmsparams.getBBOX();
		if(! reqbox.isSane()) {
			return "The requested bounding box "+reqbox.getReadableString()+" is not sane";
		}
		
		if( this.tileProfile.bbox.contains(reqbox) ) {
			return "The layers bounding box "+this.tileProfile.bbox.getReadableString()
			+" does not cover the requested bounding box " + reqbox.getReadableString();
		}
		// All good
		return null;
	}
	
	/**
	 * 1) Check that there is no lock on the same bbox you are requesting
	 * 
	 * @param wmsparams
	 * @return
	 */
	public byte[] getData(WMSParameters wmsparams,HttpServletResponse response) 
	throws IOException {
		String debugHeadersStr = null;
		
		int[] gridLoc = tileProfile.gridLocation(wmsparams.getBBOX());
		
		// This is a bit clumsy, but will have to be revisited
		// when doing metatiling anyway
		BBOX adjustedbbox = tileProfile.recreateBbox(gridLoc);
		
		boolean wait = cacheQueue.containsKey(adjustedbbox); 
		while(wait) {
			Thread.yield();
			wait = cacheQueue.containsKey(adjustedbbox);
		}
		
		Object ck = (Object) cacheKey.createKey(gridLoc[0], gridLoc[1], gridLoc[2], imageExtension);
		
		if(debugHeaders) {
			debugHeadersStr = 
			"grid-location:"+gridLoc[0]+","+gridLoc[1]+","+gridLoc[2]+"--"
			+"adjusted-bbox:"+adjustedbbox.getReadableString()+"--"
			+"cachekey:"+ck.toString()+"--";
		}
		
		RawTile tile = null;
		try {
			tile = (RawTile) cache.get(ck);

			if(tile != null) {
				//System.out.println(" GOT "+ (String) ck + " from cache.");
				if(debugHeaders) {
					response.addHeader("GEOWEBCACHE-DEBUG-HEADERS",
							debugHeadersStr+"from-cache:true");
				}
				return tile.getData();
			}
		} catch (CacheException ce) {
			log.error("Failed to get " + wmsparams.toString() + " from cache");
			ce.printStackTrace();
		}
		
		// We only get here if the cache did not have anything sensible to say
		//System.out.println(" FAILED to get "+ (String) ck + " from cache.");
		if(this.tileProfile.transparent != null)
			wmsparams.setIsTransparent(this.tileProfile.transparent);
		if(this.tileProfile.tiled != null)
			wmsparams.setIsTiled(this.tileProfile.tiled );			
		wmsparams.setBBOX(adjustedbbox);
		
		// Set the actual layers we want to request
		wmsparams.setLayer(this.wmsLayer.layers);
		
		// Force the mimetype
		wmsparams.setImagemime(imageMIME);
		tile = forwardRequest(wmsparams);
		
		try {
			cache.set(ck, tile);
		} catch (CacheException ce) {
			log.error("Unable to save data to cache, stack trace follows: " + ce.getMessage());
			ce.printStackTrace();
		}
		// Unlock the bbox
		removeFromCacheQueue(adjustedbbox);
		
		if(debugHeaders) {
			response.addHeader("GEOWEBCACHE-DEBUG-HEADERS",
					debugHeadersStr + "from-cache:false");
		}
		
		return tile.getData();
	}
	
	/**
	 * Ask the cache
	 * 
	 * @param wmsparams
	 * @return
	 */
	private RawTile forwardRequest(WMSParameters wmsparams) throws IOException {
		// Otherwise, ask the WMS server for the image
		
		log.trace("Forwarding request to " + this.wmsLayer.url);

		// Create an outgoing WMS request to the server
		Request wmsrequest = new Request(this.wmsLayer.url, wmsparams);

		Connection connection = new Connection(wmsrequest);

		try {
			connection.connect();
		} catch(IOException ioe) {
			log.error("Could not connect to WMS: ", ioe);
			throw ioe;
		}

		// Should have some timeout functionality here
		Response wmsresponse = connection.getResponse();

		// If we have a proper response
		InputStream is = wmsresponse.getInputStream();
		
		// This is sort of silly.. if you know of Library functions that do
		// the same thing efficiently, please let me know (ak AT openplans org)
		byte[] buffer = new byte[1024];
		byte[] tmpBuffer = new byte[512];
		int totalCount = 0;
		
		for(int c = 0; c != -1; c = is.read(tmpBuffer)) {
			// Expand buffer if needed
			if(totalCount + c >= buffer.length) {
				int newLength = buffer.length * 2;
				if(newLength < totalCount)
					newLength = totalCount;
				
				byte[] newBuffer = new byte[newLength];
				System.arraycopy(buffer, 0, newBuffer, 0, totalCount);
				buffer = newBuffer;
			}
			System.arraycopy(tmpBuffer, 0, buffer, totalCount, c);
			totalCount += c;		
		}
		is.close();
		
		// Compact buffer
		byte[] newBuffer = new byte[totalCount];
		System.arraycopy(buffer, 0, newBuffer, 0, totalCount);
		
		return new RawTile(newBuffer);		
	}
		
	private synchronized boolean addToCacheQueue(BBOX boundingbox) {
		if(cacheQueue.containsKey(boundingbox)) {
			return false;
		} else {
			cacheQueue.put(boundingbox, boundingbox);
			return true;
		}
	}
	
	private synchronized boolean removeFromCacheQueue(BBOX boundingbox) {
		if(cacheQueue.containsKey(boundingbox)) {
			cacheQueue.remove(boundingbox);
			return true;
		}
		return false;
	}
}
