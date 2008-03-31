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
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.cache.Cache;
import org.geowebcache.cache.CacheException;
import org.geowebcache.layer.RawTile;
import org.geowebcache.layer.wms.WMSLayerProfile;

public class FileCache implements Cache {

	private static Log log = LogFactory
			.getLog(org.geowebcache.cache.file.FileCache.class);

	private String defaultKeyBeanId = null;

	/**
	 * Check props , run setUp() on given directory
	 */
	public void init(Properties props) {
	}

	public void destroy() {
		// nothing to do
	}

	/**
	 * 1) Test whether pathPrefix exists and 2) is writeable
	 */
	public void setUp(String cachePrefix)
			throws org.geowebcache.cache.CacheException {
		File path = new File(cachePrefix);
		if (path.exists() && path.isDirectory() && path.canWrite()) {
			log.info("Succesfully opened " + cachePrefix + " for writing");
			return;
		} else {
			if (!path.exists()) {
				if (path.mkdirs()) {
					log
							.info(cachePrefix
									+ " did not exist, have been created.");
					return;
				}
			}
		}

		throw new CacheException("Can not write to " + cachePrefix
				+ ", the path is not an existing directory or not writeable");

	}

	/**
	 * See if file exists, read file
	 */
	public Object get(Object key, long ttl)
			throws org.geowebcache.cache.CacheException {

		String filePath = (String) key;
		log.trace("Attempting to read" + filePath);

		File fh = new File(filePath);

		if (ttl > 0 && fh.lastModified() > System.currentTimeMillis() - ttl) {
			log.debug(filePath + " had expired, last modified "
					+ fh.lastModified());
			return null;
		}

		if (!fh.canRead()) {
			log.debug("Unable to read " + filePath);
			return null;
		}

		long length = fh.length();

		if (length < 1) {
			return null;
		}

		byte[] data = new byte[(int) length];

		try {
			InputStream is = new FileInputStream(fh);
			is.read(data);
			is.close();
		} catch (FileNotFoundException fnfe) {
			log.trace("Did not find " + filePath);
			return null;
		} catch (IOException ioe) {
			log.error("IOException reading from " + filePath + ": "
					+ ioe.getMessage());
			throw new CacheException(ioe);
		}

		return new RawTile(data);
	}

	public boolean remove(Object key)
			throws org.geowebcache.cache.CacheException {
		String filePath = (String) key;
		File fh = new File(filePath);
		return fh.delete();
	}

	public void removeAll() throws org.geowebcache.cache.CacheException {
		// Do nothing for now
	}

	public void set(Object key, Object obj, long ttl)
			throws org.geowebcache.cache.CacheException {
		if (ttl == WMSLayerProfile.CACHE_NEVER) {
			return;
		}

		String filePath = (String) key;
		log.trace("Attempting write to " + filePath);
		File fh = new File(filePath);
		File pfh = new File(fh.getParent());

		RawTile tile = (RawTile) obj;

		if (pfh.mkdirs()
				|| (pfh.exists() && pfh.isDirectory() && pfh.canWrite())) {
			try {
				FileOutputStream os = new FileOutputStream(fh);
				os.write(tile.getData());
				os.flush();
				os.close();
			} catch (FileNotFoundException fnfe) {
				throw new CacheException(fnfe);
			} catch (IOException ioe) {
				log.error("IOException writing to " + filePath + ": "
						+ ioe.getMessage());
				throw new CacheException(ioe);
			}
		} else {
			throw new CacheException("Unable to create directories: "
					+ pfh.getAbsolutePath());
		}
	}

	public void setDefaultKeyBeanId(String defaultKeyBeanId) {
		this.defaultKeyBeanId = defaultKeyBeanId;
	}

	public String getDefaultKeyBeanId() {
		return this.defaultKeyBeanId;
	}
}
