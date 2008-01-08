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

package org.geowebcache.cache.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileLock;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.geowebcache.cache.Cache;
import org.geowebcache.cache.CacheException;
import org.geowebcache.tile.RawTile;


public class FileCache implements Cache {

	private static Log log = LogFactory.getLog(org.geowebcache.cache.file.FileCache.class);
	
	// The path where this FileCache stores files
	private String pathPrefix = null;
	
	public FileCache() {
		// nothing, for factory
	}
	
	/**
	 * Check props , run setUp() on given directory
	 */
	public void init(Properties props) throws org.geowebcache.cache.CacheException {
		String propPathPrefix = props.getProperty("filecachepath");
		if(propPathPrefix != null) {
			this.pathPrefix = propPathPrefix;
		} else {
			// Try to use %TEMP%
			this.pathPrefix = System.getenv("TEMP");
		}
	}

	/**
	 * 1) Test whether pathPrefix exists and
	 * 2) is writeable
	 */
	public void setUp() throws org.geowebcache.cache.CacheException {
		File path = new File(this.pathPrefix);
		if(path.exists() && path.isDirectory() && path.canWrite()) {
			log.trace("Succesfully opened " + pathPrefix + " for writing");
		} else {
			throw new CacheException("Can not write to " + this.pathPrefix + ", the path is not an existing directory or not writeable");
		}
	}

	/** 
	 * See if file exists, read file
	 */
	public Object get(Object key) throws org.geowebcache.cache.CacheException {
		String filePath =  pathPrefix + File.separator + (String) key;
		File fh = new File(filePath);
		
		if(! fh.canRead() ) 
			return null;
		
		long length = fh.length();
		
		if(length < 1)
			return null;
		
		byte[] data = new byte[(int) length];
		
		try {
			InputStream is = new FileInputStream(fh);
			is.read(data);
		} catch (FileNotFoundException fnfe) {
			log.trace("Did not find " + filePath);
			return null;
		} catch (IOException ioe) {
			throw new CacheException(ioe);
		}
		
		return new RawTile(data);
	}

	public void remove(Object key) throws org.geowebcache.cache.CacheException {
		// Do nothing for now
	}

	public void removeAll() throws org.geowebcache.cache.CacheException {
		// Do nothing for now
	}

	/**
	 * 
	 */
	public void set(Object key, Object obj) throws org.geowebcache.cache.CacheException {
		String filePath =  pathPrefix + File.separator + (String) key;
		File fh = new File(filePath);
		File pfh = new File(fh.getParent());
		
		RawTile tile = (RawTile) obj;
		
		if(pfh.mkdirs() || (pfh.exists() && pfh.isDirectory() && pfh.canWrite() )) {
			try {
				FileOutputStream os = new FileOutputStream(fh);
				os.write(tile.getData());
				os.flush();
				os.close();
			} catch (FileNotFoundException fnfe) {
				throw new CacheException(fnfe);
			} catch (IOException ioe) {
				throw new CacheException(ioe);
			}
		} else {
			throw new CacheException("Unable to create directories: " + pfh.getAbsolutePath());
		}
	}

	public String getDefaultCacheKeyName() {
		return "org.geowebcache.cachekey.FilePathKey";
	}


}
