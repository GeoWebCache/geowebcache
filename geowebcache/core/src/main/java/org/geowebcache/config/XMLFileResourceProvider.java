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
 * @author Arne Kepp, Marius Suta, The Open Planning Project, Copyright 2008 - 2015
 * @author Niels Charlier
 */
package org.geowebcache.config;

import jakarta.servlet.ServletContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.geotools.util.logging.Logging;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.util.ApplicationContextProvider;
import org.geowebcache.util.GWCVars;
import org.springframework.context.ApplicationContext;
import org.springframework.lang.NonNull;
import org.springframework.web.context.WebApplicationContext;

/** Default implementation of ConfigurationResourceProvider that uses the file system. */
public class XMLFileResourceProvider implements ConfigurationResourceProvider {

    private static Logger log = Logging.getLogger(XMLFileResourceProvider.class.getName());

    public static final String GWC_CONFIG_DIR_VAR = "GEOWEBCACHE_CONFIG_DIR";

    /**
     * Web app context, used to look up {@link XMLConfigurationProvider}s. Will be null if used the
     * {@link XMLConfigurationProvider(File)} constructor
     */
    private final WebApplicationContext context;

    /** Location of the configuration file */
    @NonNull
    private final File configDirectory;

    /** Name of the configuration file */
    @NonNull
    private final String configFileName;

    private String templateLocation;

    public XMLFileResourceProvider(
            final String configFileName,
            final WebApplicationContext appCtx,
            final String configFileDirectory,
            final DefaultStorageFinder storageDirFinder)
            throws ConfigurationException {

        if (configFileDirectory == null && storageDirFinder == null) {
            throw new NullPointerException("At least one of configFileDirectory or storageDirFinder must not be null");
        }

        this.context = appCtx;
        this.configFileName = configFileName;

        if (configFileDirectory != null) {
            // Use the given path
            if (new File(configFileDirectory).isAbsolute()) {

                log.config("Provided configuration directory as absolute path '" + configFileDirectory + "'");
                this.configDirectory = new File(configFileDirectory);
            } else {
                ServletContext servletContext = context.getServletContext();
                if (servletContext != null) {
                    String baseDir = servletContext.getRealPath("");
                    log.config("Provided configuration directory relative to servlet context '"
                            + baseDir
                            + "': "
                            + configFileDirectory);
                    this.configDirectory = new File(baseDir, configFileDirectory);
                } else {
                    throw new IllegalStateException("Unexpected, cannot locate the config directory");
                }
            }
        } else {
            // Otherwise use the storage directory
            this.configDirectory = new File(storageDirFinder.getDefaultPath());
        }
        log.config("Will look for " + configFileName + " in '" + configDirectory + "'");
    }

    public XMLFileResourceProvider(
            final String configFileName,
            final ApplicationContextProvider appCtx,
            final String configFileDirectory,
            final DefaultStorageFinder storageDirFinder)
            throws ConfigurationException {
        this(
                configFileName,
                appCtx == null ? null : appCtx.getApplicationContext(),
                configFileDirectory,
                storageDirFinder);
    }

    /**
     * Constructor that will look for {@code geowebcache.xml} at the directory defined by {@code storageDirFinder}
     *
     * @param appCtx use to lookup {@link XMLConfigurationProvider} extenions, may be {@code null}
     */
    public XMLFileResourceProvider(
            final String configFileName,
            final ApplicationContextProvider appCtx,
            final DefaultStorageFinder storageDirFinder)
            throws ConfigurationException {
        this(configFileName, appCtx, getConfigDirVar(appCtx.getApplicationContext()), storageDirFinder);
    }

    /**
     * Constructor that will look for {@code geowebcache.xml} at the directory defined by {@code storageDirFinder}
     *
     * @param appCtx use to lookup {@link XMLConfigurationProvider} extenions, may be {@code null}
     */
    public XMLFileResourceProvider(
            final String configFileName,
            final WebApplicationContext appCtx,
            final DefaultStorageFinder storageDirFinder)
            throws ConfigurationException {
        this(configFileName, appCtx, getConfigDirVar(appCtx), storageDirFinder);
    }

    /**
     * {@inheritDoc}
     *
     * <p>If the configuration file doesn't exist and {@link #hasInput() == true}, the file will be first created from
     * the {@link #setTemplate(String) template}
     *
     * @throws IOException if the file can't be created or copied from the template
     */
    @Override
    public InputStream in() throws IOException {
        return new FileInputStream(findOrCreateConfFile());
    }

    /**
     * {@inheritDoc}
     *
     * <p>If the configuration file doesn't exist and {@link #hasOutput() == true}, the file will be first created from
     * the {@link #setTemplate(String) template}
     *
     * @throws IOException if the file can't be created or copied from the template
     */
    @Override
    public OutputStream out() throws IOException {
        return new FileOutputStream(findOrCreateConfFile());
    }

    @Override
    public void backup() throws IOException {
        backUpConfig(findOrCreateConfFile());
    }

    @Override
    public String getId() {
        return configDirectory.getAbsolutePath();
    }

    private static String getConfigDirVar(ApplicationContext ctxt) {
        return GWCVars.findEnvVar(ctxt, GWC_CONFIG_DIR_VAR);
    }

    @Override
    public void setTemplate(final String templateLocation) {
        this.templateLocation = templateLocation;
    }

    private File findConfigFile() throws IOException {

        if (!configDirectory.exists() && !configDirectory.mkdirs()) {
            throw new IOException("TileLayerConfiguration directory does not exist and cannot be created: '"
                    + configDirectory.getAbsolutePath()
                    + "'");
        }
        return new File(configDirectory, configFileName);
    }

    @Override
    public String getLocation() throws IOException {
        File f = findConfigFile();
        try {
            return f.getCanonicalPath();
        } catch (IOException ex) {
            log.log(Level.SEVERE, "Could not canonize config path", ex);
            return f.getPath();
        }
    }

    private File findOrCreateConfFile() throws IOException {
        File xmlFile = findConfigFile();

        if (xmlFile.exists()) {
            log.config("Found configuration file in " + configDirectory.getAbsolutePath());
        } else if (templateLocation != null) {
            if (!configDirectory.canWrite()) {
                throw new IOException("TileLayerConfiguration directory is not writable: '"
                        + configDirectory.getAbsolutePath() + "'");
            }

            log.warning("Found no configuration file in config directory, will create one at '"
                    + xmlFile.getAbsolutePath()
                    + "' from template "
                    + getClass().getResource(templateLocation).toExternalForm());
            // grab template from classpath
            try {
                try (InputStream templateStream = getClass().getResourceAsStream(templateLocation);
                        OutputStream output = new FileOutputStream(xmlFile)) {
                    IOUtils.copy(templateStream, output);
                    output.flush();
                }
            } catch (IOException e) {
                throw new IOException("Error copying template config to " + xmlFile.getAbsolutePath(), e);
            }
        }

        return xmlFile;
    }

    private void backUpConfig(final File xmlFile) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd'T'HHmmss").format(new Date());
        String backUpFileName = "geowebcache_" + timeStamp + ".bak";
        File parentFile = xmlFile.getParentFile();

        log.fine("Backing up config file " + xmlFile.getName() + " to " + backUpFileName);

        String[] previousBackUps = parentFile.list((dir, name) -> {
            if (configFileName.equals(name)) {
                return false;
            }
            if (name.startsWith(configFileName) && name.endsWith(".bak")) {
                return true;
            }
            return false;
        });

        final int maxBackups = 10;
        if (previousBackUps != null && previousBackUps.length > maxBackups) {
            Arrays.sort(previousBackUps);
            String oldest = previousBackUps[0];
            log.fine("Deleting oldest config backup " + oldest + " to keep a maximum of " + maxBackups + " backups.");
            new File(parentFile, oldest).delete();
        }

        File backUpFile = new File(parentFile, backUpFileName);
        FileUtils.copyFile(xmlFile, backUpFile);
        log.fine("Config backup done");
    }

    /**
     * Determines if the config file exists and is readable, or doesn't exist but can be created.
     *
     * <p>Calling this method has no side effects. The target file either exists, or can be created throught the
     * {@link #setTemplate(String) template}, if a template has been set. In such case, it'll be created by either
     * {@link #in()} or {@link #out()}.
     */
    @Override
    public boolean hasInput() {
        try {
            File file = findConfigFile();
            return file.exists() || (templateLocation != null && configDirectory.canWrite());
        } catch (IOException e) {
            log.log(Level.WARNING, "Error obtaining config file", e);
            return false;
        }
    }

    /**
     * Determines if the configuration can be {@link #out() written} to the {@link #getLocation() output file}.
     *
     * <p>Calling this method has no side effects. The target file may or may not exist. The target directory must be
     * writable, and so must the target file in case it does exist.
     *
     * @return {@code true} if the {@link #getLocation() configuration file} can be written to.
     */
    @Override
    public boolean hasOutput() {
        try {
            File file = findConfigFile();
            return configDirectory.canWrite() && (!file.exists() || file.canWrite());
        } catch (IOException e) {
            log.log(Level.WARNING, "Error obtaining config file", e);
            return false;
        }
    }
}
