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
 * @author Arne Kepp / The Open Planning Project 2009
 *  
 */

package org.geowebcache.storage;

import java.io.File;

import javax.servlet.ServletContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.util.ApplicationContextProvider;
import org.geowebcache.util.GWCVars;
import org.springframework.web.context.WebApplicationContext;

/**
 * Locates the position of the GeoWebCache file cache inspecting system variables, the servlet
 * context and environment variables
 */
public class DefaultStorageFinder {
    public final static String GWC_CACHE_DIR = "GEOWEBCACHE_CACHE_DIR";

    public final static String GS_DATA_DIR = "GEOSERVER_DATA_DIR";

    public final static String GWC_METASTORE_DISABLED = "GWC_METASTORE_DISABLED";

    public final static String GWC_METASTORE_JDBC_URL = "GWC_METASTORE_JDBC_URL";

    public final static String GWC_METASTORE_USERNAME = "GWC_METASTORE_USERNAME";

    public final static String GWC_METASTORE_PASSWORD = "GWC_METASTORE_PASSWORD";

    public final static String GWC_METASTORE_DRIVER_CLASS = "GWC_METASTORE_DRIVER_CLASS";

    public final static String GWC_BLANK_TILE_PATH = "GWC_BLANK_TILE_PATH";

    private static Log log = LogFactory.getLog(org.geowebcache.storage.DefaultStorageFinder.class);

    private String defaultPrefix = null;

    private WebApplicationContext context = null;

    public DefaultStorageFinder(ApplicationContextProvider provider) {
        context = provider.getApplicationContext();
    }

    public synchronized String getDefaultPath() throws ConfigurationException {
        if (this.defaultPrefix == null) {
            determineDefaultPrefix();
            if (this.defaultPrefix == null) {
                throw new ConfigurationException("Unable to find writable path for cache.");
            }
        }

        return this.defaultPrefix;
    }

    public String findEnvVar(String varStr) {
        return GWCVars.findEnvVar(context, varStr);
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
    private void determineDefaultPrefix() {
        ServletContext serlvCtx = context.getServletContext();

        final String[] typeStrs = { "Java environment variable ", "Servlet context parameter ",
                "System environment variable " };

        final String[] varStrs = { GWC_CACHE_DIR, GS_DATA_DIR, "TEMP", "TMP" };

        String msgPrefix = null;
        int iVar = 0;
        for (int i = 0; i < varStrs.length && defaultPrefix == null; i++) {
            for (int j = 0; j < typeStrs.length && defaultPrefix == null; j++) {
                String value = null;
                String varStr = varStrs[i];
                String typeStr = typeStrs[j];

                switch (j) {
                case 0:
                    value = System.getProperty(varStr);
                    break;
                case 1:
                    value = serlvCtx.getInitParameter(varStr);
                    break;
                case 2:
                    value = System.getenv(varStr);
                    break;
                }

                if (value == null || value.equalsIgnoreCase("")) {
                    if (log.isDebugEnabled()) {
                        log.debug(typeStr + varStr + " is unset");
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
                this.defaultPrefix = value;
                iVar = i;
            }
        }
        String logMsg;

        if (this.defaultPrefix == null) {
            String tmpDir = System.getProperty("java.io.tmpdir");
            if (tmpDir != null) {
                File temp = new File(tmpDir, "geowebcache");
                logMsg = "Reverting to java.io.tmpdir " + this.defaultPrefix + " for storage. "
                        + "Please set " + GWC_CACHE_DIR + ".";
                if (!temp.exists() && !temp.mkdirs()) {
                    throw new RuntimeException("Can't create " + temp.getAbsolutePath());
                }
                this.defaultPrefix = temp.getAbsolutePath();
            } else {
                logMsg = "Unable to determine temp directory. Proceeding with undefined results.";
            }
        } else {
            switch (iVar) {
            case 0: // GEOWEBCACHE_CACHE_DIR, do nothing
                break;

            case 1: // GEOSERVER_DATA_DIR, prefix
                this.defaultPrefix = this.defaultPrefix + File.separator + "gwc";
                break;

            case 2: // TEMP directories
            case 3:
                this.defaultPrefix = this.defaultPrefix + File.separator + "geowebcache";
            }

            logMsg = msgPrefix + ", using it as the default prefix.";
        }

        String warnStr = "*** " + logMsg + " ***";
        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < warnStr.length(); i++) {
            stars.append("*");
        }

        log.info(stars.toString());
        log.info(warnStr);
        log.info(stars.toString());
    }
}
