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
 */
package org.geowebcache.layer;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.cache.Cache;
import org.geowebcache.cache.CacheFactory;
import org.geowebcache.cache.CacheKey;
import org.geowebcache.mime.MimeType;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.util.wms.BBOX;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TileLayer {
	private static Log log = LogFactory.getLog(org.geowebcache.layer.TileLayer.class);
	
	public volatile Boolean isInitialized = null;
	
	protected Condition[] gridLocConds = null;
	
	protected Lock layerLock;
	
	CacheFactory initCacheFactory = null;
	
	protected Cache cache;
    
	protected CacheKey cacheKey;
    
	protected String cachePrefix = null;
	
	protected String name;

	protected List<String> mimeFormats;
	
	protected List<MimeType> formats;

	protected List<Grid> grids;

	
	
	public TileLayer(String name, CacheFactory cacheFactory) {
		this.name = name;
		this.initCacheFactory = cacheFactory;
		this.grids = new ArrayList<Grid>();
		
	}

	public void setInitCacheFactory(CacheFactory cacheFactory) {
		this.initCacheFactory = cacheFactory;
	}
	
	public void setName(String name) {
		this.name = name;
	}

	public void addGrid(Grid grid) {
		this.grids.add(grid);
	}

	public List<Grid> getGrids() {
		return this.grids;
	}

	public void addFormat(String format) {
		this.mimeFormats.add(format);
	}

	public Boolean isInitialized() {
		Boolean result = isInitialized;
		if (result == null) {
			synchronized (this) {
				result = isInitialized;
				if (result == null) {
					isInitialized = result = this.initialize();
				}
			}
		}
		return result;
	}

	/**
	 * Initializes the layer, creating internal structures for calculating grid
	 * location and so forth.
	 */
	public Boolean initialize() {
		try {
			this.setupParameters(initCacheFactory);
		} catch (GeoWebCacheException gwce) {
			log.error(gwce.getMessage());
			gwce.printStackTrace();
		}

		// Create conditions for tile locking
		this.gridLocConds = new Condition[17];
		for (int i = 0; i < gridLocConds.length; i++) {
			if(layerLock== null){
				layerLock = new ReentrantLock();
			}	
			gridLocConds[i] = layerLock.newCondition();
		}

		// Unset variables for garbage collection
		initCacheFactory = null;

		return new Boolean(true);
	}
	
	public void setupParameters(CacheFactory cacheFactory)throws GeoWebCacheException{
		this.formats = new ArrayList<MimeType>();
		for(String fmt : mimeFormats){
			formats.add(MimeType.createFromFormat(fmt));
		}
		if(formats.get(0) == null)
			formats.add(0,ImageMime.createFromFormat("image/png"));
		
		cache = cacheFactory.getDefaultCache();
        if(cache == null) {
              log.error("Unable to get default cache.");
         }

      cacheKey = cacheFactory.getCacheKeyFactory().getCacheKey(
                  cache.getDefaultKeyBeanId());
      String sanitizedName = name.replace(':', '_');
      cachePrefix = cache.getDefaultPrefix(sanitizedName);
      log.warn("cachePrefix not defined for layer " + name
              + ", using default prefifx and name instead: "
              + cachePrefix);

      // Initialize the cache
      cache.setUp(cachePrefix);

	}

	/**
	 * Rough checks to see whether the layers supports the requested projection,
	 * returns error message otherwise
	 * 
	 * @param srs
	 *            Name of projection, for example "EPSG:4326"
	 * @return null if okay, error message otherwise.
	 */
	public boolean supportsProjection(SRS srs) throws GeoWebCacheException {
		for (Grid g : grids)
			if (srs.equals(g.getProjection()))
				return true;
		throw new GeoWebCacheException("SRS " + srs.toString()
				+ " is not supported by " + this.getName());
	}

	/**
	 * Rough checks to see whether the layers supports the requested mimeType.
	 * Null assumes the default format and is supported.
	 * 
	 * Returns error message otherwise
	 * 
	 * @param mimeType
	 *            MIME type or null, example "image/png"
	 * @return null if okay, error message otherwise.
	 */
	public boolean supportsFormat(String strFormat) throws GeoWebCacheException {
		if (strFormat == null) {
			log.trace("Format was null");
			return true;
		}

		for (MimeType mime : formats) {
			if (strFormat.equalsIgnoreCase(mime.getFormat())) {
				return true;
			}
		}

		throw new GeoWebCacheException("Format " + strFormat
				+ " is not supported by " + this.getName());
	}

	/**
	 * Rough checks to see whether the specified bounding box is supported by
	 * the current layer.
	 * 
	 * Returns error message if not.
	 * 
	 * @param srs
	 *            the string representation
	 * @param reqBounds
	 *            the requested bounds
	 * @return null if okay, error message otherwise.
	 */
	public String supportsBbox(SRS srs, BBOX reqBounds)
			throws GeoWebCacheException {
		this.supportsProjection(srs);

		if (!reqBounds.isSane()) {
			return "The requested bounding box "
					+ reqBounds.getReadableString() + " is not sane";
		}

		if (!(grids.get(getSRSIndex(srs)).getGridBounds()).contains(reqBounds)) {
			return "The layers grid box "
					+ (grids.get(getSRSIndex(srs)).getGridBounds()).getReadableString()
					+ " does not cover the requested bounding box "
					+ reqBounds.getReadableString();
		}

		// All ok
		return null;
	}

	/**
	 * 
	 * @return the array of supported projections
	 */
	public SRS[] getProjections(){
		SRS[] projections = new SRS[grids.size()];
		for(Grid g : grids)
			projections[grids.indexOf(g)] = g.getProjection();
		return projections;
	}

	/**
	 * 
	 * @param reqSRS
	 * @return the internal index of the provided spatial reference system
	 */
	public int getSRSIndex(SRS reqSRS){
		for(Grid g : grids){
			if (reqSRS.equals(g.getProjection()))
				return grids.indexOf(g);
		}
		return -1;
	}

	/**
	 * 
	 * @param srsIdx
	 * @return the bounds of the layer for the given spatial reference system
	 */
	public BBOX getBounds(int srsIdx){
		return grids.get(srsIdx).getBounds();
	}


	/**
	 * 
	 * @return list with supported MIME types
	 */
	public List<MimeType> getMimeTypes(){
		return this.formats;
	}

	/**
	 * The default MIME type is the first one in the configuration
	 * 
	 * @return
	 */
	public MimeType getDefaultMimeType() {
		return formats.get(0);
	}

	/**
	 * The name of the layer.
	 * 
	 * @return
	 */
	public String getName() {
		return this.name;
	}
	
    public Cache getCache() {
        return this.cache;
    }
    
    public CacheKey getCacheKey() {
        return this.cacheKey;
    }

    public String getCachePrefix() {
        return new String(this.cachePrefix);
    }

}
