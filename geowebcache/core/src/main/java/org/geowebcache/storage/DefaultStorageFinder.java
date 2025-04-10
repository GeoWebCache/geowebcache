/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Arne Kepp / The Open Planning Project 2009
 */
package org.geowebcache.storage;

import java.io.File;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.config.ConfigurationException;
import org.geowebcache.util.ApplicationContextProvider;
import org.geowebcache.util.GWCVars;
import org.geowebcache.util.GWCVars.Variable;
import org.springframework.web.context.WebApplicationContext;

/**
 * Locates the position of the GeoWebCache file cache inspecting system variables, the servlet context and environment
 * variables
 */
public class DefaultStorageFinder {
    public static final String GWC_CACHE_DIR = "GEOWEBCACHE_CACHE_DIR";

    public static final String GS_DATA_DIR = "GEOSERVER_DATA_DIR";

    public static final String GWC_METASTORE_DISABLED = "GWC_METASTORE_DISABLED";

    public static final String GWC_METASTORE_JDBC_URL = "GWC_METASTORE_JDBC_URL";

    public static final String GWC_METASTORE_USERNAME = "GWC_METASTORE_USERNAME";

    public static final String GWC_METASTORE_PASSWORD = "GWC_METASTORE_PASSWORD";

    public static final String GWC_METASTORE_DRIVER_CLASS = "GWC_METASTORE_DRIVER_CLASS";

    public static final String GWC_BLANK_TILE_PATH = "GWC_BLANK_TILE_PATH";

    private static Logger log = Logging.getLogger(DefaultStorageFinder.class.getName());

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
     */
    private void determineDefaultPrefix() {
        final String[] varStrs = {GWC_CACHE_DIR, GS_DATA_DIR, "TEMP", "TMP"};

        String msgPrefix = null;
        int iVar = 0;
        for (int i = 0; i < varStrs.length && defaultPrefix == null; i++) {
            String varStr = varStrs[i];
            List<Variable> found = GWCVars.findVariable(context, varStr);
            for (Variable v : found) {
                String typeStr = v.getType().getSource();
                String value = v.getValue();
                if (value == null || value.isEmpty()) {
                    if (log.isLoggable(Level.FINE)) {
                        log.fine(typeStr + " " + varStr + " is unset");
                    }
                    continue;
                }

                File fh = new File(value);

                // Being a bit pessimistic here
                msgPrefix = "Found " + typeStr + " " + varStr + " set to " + value;

                if (!fh.exists()) {
                    log.log(Level.SEVERE, msgPrefix + ", but this path does not exist");
                    continue;
                }
                if (!fh.isDirectory()) {
                    log.log(Level.SEVERE, msgPrefix + ", which is not a directory");
                    continue;
                }
                if (!fh.canWrite()) {
                    log.log(Level.SEVERE, msgPrefix + ", which is not writeable");
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
                logMsg = "Reverting to java.io.tmpdir '"
                        + temp.getAbsolutePath()
                        + "' for storage. "
                        + "Please set "
                        + GWC_CACHE_DIR
                        + ".";
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
        log.config(logMsg);
    }
}
