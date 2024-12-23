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
 * @author Gabriel Roldan, Boundless Spatial Inc, Copyright 2015
 */
package org.geowebcache.s3;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;

/**
 * Loads the configuration from a properties file {@code $HOME/.gwc_s3_tests.properties}, which must exist and contain
 * entries for {@code bucket}, {@code accessKey}, and {@code secretKey}.
 *
 * <p>If the file doesn't exist, the returned {@link #getProperties()} will be empty.
 *
 * <p>If the file does exist and doesn't contain one of the required keys, the constructor fails with an
 * {@link IllegalArgumentException}.
 */
public class PropertiesLoader {

    private static Logger log = Logging.getLogger(PropertiesLoader.class.getName());

    private Properties properties = new Properties();

    public PropertiesLoader() {
        String home = System.getProperty("user.home");
        File configFile = new File(home, ".gwc_s3_tests.properties");
        log.info("Loading S3 tests config. File must have keys 'bucket', 'accessKey', and 'secretKey'");
        if (configFile.exists()) {
            try (InputStream in = new FileInputStream(configFile)) {
                properties.load(in);
                checkArgument(
                        null != properties.getProperty("bucket"),
                        "bucket not provided in config file " + configFile.getAbsolutePath());
                checkArgument(
                        null != properties.getProperty("accessKey"),
                        "accessKey not provided in config file " + configFile.getAbsolutePath());
                checkArgument(
                        null != properties.getProperty("secretKey"),
                        "secretKey not provided in config file " + configFile.getAbsolutePath());
            } catch (IOException e) {
                log.log(Level.SEVERE, "Error loading S3 tests config: " + configFile.getAbsolutePath(), e);
            }
        } else {
            log.warning(
                    "S3 storage config file not found. GWC S3 tests will be ignored. " + configFile.getAbsolutePath());
        }
    }

    public Properties getProperties() {
        return properties;
    }
}
