package org.geowebcache.s3;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Loads the configuration from a properties file
 * {@code $HOME/.gwc_s3_tests.properties}, which must exist and contain entries
 * for {@code bucket}, {@code accessKey}, and {@code secretKey}.
 * <p>
 * If the file doesn't exist, the returned {@link #getProperties()} will be
 * empty.
 * <p>
 * If the file does exist and doesn't contain one of the required keys, the
 * constructor fails with an {@link IllegalArgumentException}.
 */
public class PropertiesLoader {

	private static Log log = LogFactory.getLog(PropertiesLoader.class);

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
						"bucket not provided in config file "
								+ configFile.getAbsolutePath());
				checkArgument(
						null != properties.getProperty("accessKey"),
						"accessKey not provided in config file "
								+ configFile.getAbsolutePath());
				checkArgument(
						null != properties.getProperty("secretKey"),
						"secretKey not provided in config file "
								+ configFile.getAbsolutePath());
			} catch (IOException e) {
				log.fatal(
						"Error loading S3 tests config: "
								+ configFile.getAbsolutePath(), e);
			}
		} else {
			log.warn("S3 storage config file not found. GWC S3 tests will be ignored. "
					+ configFile.getAbsolutePath());
		}
	}

	public Properties getProperties() {
		return properties;
	}

}
