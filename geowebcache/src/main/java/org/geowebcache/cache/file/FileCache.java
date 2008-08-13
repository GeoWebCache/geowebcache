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

import javax.servlet.ServletContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.cache.Cache;
import org.geowebcache.cache.CacheException;
import org.geowebcache.cache.CacheKey;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.tile.Tile;
import org.geowebcache.util.GWCVars;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;

public class FileCache implements Cache {
    public final static String GWC_CACHE_DIR = "GEOWEBCACHE_CACHE_DIR";

    public final static String GS_DATA_DIR = "GEOSERVER_DATA_DIR";

    private static Log log = LogFactory
            .getLog(org.geowebcache.cache.file.FileCache.class);

    private String defaultKeyBeanId = null;

    private String defaultCachePrefix = null;

    private WebApplicationContext context = null;

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
            log.info("Succesfully opened " + path.getAbsolutePath()
                    + " for writing");
            return;
        } else {
            if (!path.exists()) {
                if (path.mkdirs()) {
                    log.info(cachePrefix
                            + " did not exist, has been created recursively.");
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
    public boolean get(CacheKey keyProto, Tile tile, long ttl)
            throws CacheException, GeoWebCacheException {

        String filePath = (String) keyProto.createKey(tile);
        log.trace("Attempting to read" + filePath);

        File fh = new File(filePath);

        try {
            InputStream is = new FileInputStream(fh);
            tile.read(is);
            is.close();
        } catch (FileNotFoundException fnfe) {
            log.trace("Did not find " + filePath);
            return false;
        } catch (IOException ioe) {
            log.error("IOException reading from " + filePath + ": "
                    + ioe.getMessage());
            throw new CacheException(ioe);
        }

        return true;
    }

    public boolean remove(CacheKey keyProto, Tile tile)
            throws org.geowebcache.cache.CacheException {
        String filePath = (String) keyProto.createKey(tile);

        File fh = new File(filePath);
        return fh.delete();
    }

    public void removeAll() throws org.geowebcache.cache.CacheException {
        // Do nothing for now
    }

    public void set(CacheKey keyProto, Tile tile, long ttl)
            throws org.geowebcache.cache.CacheException {
        if (ttl == GWCVars.CACHE_NEVER) {
            return;
        }

        if (tile.getError()) {
            Thread.dumpStack();
            throw new CacheException("Cache cannot store tile with error!");
        }

        String filePath = (String) keyProto.createKey(tile);
        File fh = new File(filePath);
        File pfh = new File(fh.getParent());

        if (pfh.mkdirs()
                || (pfh.exists() && pfh.isDirectory() && pfh.canWrite())) {
            try {
                FileOutputStream os = new FileOutputStream(fh);
                tile.write(os);
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

    public String getDefaultPrefix(String param) throws CacheException {
        if (this.defaultCachePrefix == null) {
            determineDefaultPrefix();
            if (this.defaultCachePrefix == null) {
                throw new CacheException(
                        "Unable to find writable path for cache.");
            }
        }

        return this.defaultCachePrefix + File.separator + param;
    }

    /**
     * Looks for <br>
     * 1) GEOWEBCACHE_CACHE_DIR<br>
     * 2) GEOSERVER_DATA_DIR<br>
     * 3) %TEMP%, $TEMP<br>
     * <br>
     * Using<br>
     * A) Java environment variable<br>
     * B) Servlet context parameter<br>
     * C) System environment variable<br>
     * 
     */
    public void determineDefaultPrefix() {

        ServletContext serlvCtx = context.getServletContext();

        final String[] typeStrs = { "Java environment variable ",
                "Servlet context parameter ", "System environment variable " };

        final String[] varStrs = { GWC_CACHE_DIR, GS_DATA_DIR, "TEMP", "TMP" };

        String msgPrefix = null;
        int iVar = 0;
        for (int i = 0; i < varStrs.length && defaultCachePrefix == null; i++) {
            for (int j = 0; j < typeStrs.length && defaultCachePrefix == null; j++) {
                String value = null;
                String varStr = new String(varStrs[i]);
                String typeStr = typeStrs[j];

                switch (j) {
                case 1:
                    value = System.getProperty(varStr);
                    break;
                case 2:
                    value = serlvCtx.getInitParameter(varStr);
                    break;
                case 3:
                    value = System.getenv(varStr);
                    break;
                }

                if (value == null || value.equalsIgnoreCase("")) {
                    if (log.isDebugEnabled()) {
                        log.debug("Found " + typeStr + varStr + " to be unset");
                    }
                    continue;
                }

                File fh = new File(value);

                // Being a bit pessimistic here
                msgPrefix = "Found " + typeStr + varStr + " set to " + value;

                if (!fh.exists()) {
                    log.error(msgPrefix + " , but this path does not exist");
                    continue;
                }
                if (!fh.isDirectory()) {
                    log.error(msgPrefix + " , which is not a directory");
                    continue;
                }
                if (!fh.canWrite()) {
                    log.error(msgPrefix + " , which is not writeable");
                    continue;
                }

                // Sweet, we can work with this
                this.defaultCachePrefix = value;
                iVar = i;
            }
        }
        if (this.defaultCachePrefix == null) {
            log.error("Found no usable default cache prefixes !!! "
                    + "Please set " + GWC_CACHE_DIR);

            String tmpDir = System.getProperty("java.io.tmpdir");
            if (tmpDir != null) {
                this.defaultCachePrefix = tmpDir + File.separator
                        + "geowebcache";
                log.warn("Reverting to java.io.tmpdir: "
                        + this.defaultCachePrefix);
            }
        } else {
            switch (iVar) {
            case 0: // GEOWEBCACHE_CACHE_DIR, do nothing
                break;

            case 1: // GEOSERVER_DATA_DIR, prefix
                this.defaultCachePrefix = this.defaultCachePrefix
                        + File.separator + "gwc";
                break;

            case 2: // TEMP directories
            case 3:
                this.defaultCachePrefix = this.defaultCachePrefix
                        + File.separator + "geowebcache";
            }
            log.info(msgPrefix + ", using it as the default prefix.");
        }
    }

    public void setDefaultKeyBeanId(String defaultKeyBeanId) {
        this.defaultKeyBeanId = defaultKeyBeanId;
    }

    public String getDefaultKeyBeanId() {
        return this.defaultKeyBeanId;
    }

    public void setApplicationContext(ApplicationContext arg0)
            throws BeansException {
        context = (WebApplicationContext) arg0;
    }
}
