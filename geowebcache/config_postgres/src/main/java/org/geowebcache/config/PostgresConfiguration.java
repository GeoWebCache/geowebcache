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
 * @author Emil Zail / T-Kartor 2016 (original code from XMLConfiguration)
 */
package org.geowebcache.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.ContextualConfigurationProvider.Context;
import org.geowebcache.config.meta.ServiceInformation;
import org.geowebcache.diskquota.DiskQuotaMonitor;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.io.GeoWebCacheXStream;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerListener;
import org.geowebcache.layer.wms.WMSHttpHelper;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.util.ApplicationContextProvider;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import org.springframework.web.context.WebApplicationContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomReader;

/**
 * This class will configure gwc-layers (currently only WMSLayer's) into a PostgreSQL database. This
 * class is based on XMLConfiguration class and is configuring everything except layers using
 * 'geowebcache.xml' file.
 * 
 * <p>
 * NOTE {@link #initialize(GridSetBroker)} MUST have been called before any other method is used,
 * otherwise this configuration is in an inconsistent and unpredictable state.
 * </p>
 * 
 * <p>
 * Database connection can be created with jndiName (DataSource) or by providing url/user/password.
 * Optionally schema can be set if table should be created outside of default public
 * </p>
 * 
 * @author Emil Zail - T-Kartor
 * @see XMLConfiguration
 */
public class PostgresConfiguration extends AbsConfigurationDispatcher
        implements ConfigurationDispatcher, InitializingBean {

    static final String DEFAULT_CONFIGURATION_FILE_NAME = "geowebcache.xml";

    private static final Log LOGGER = LogFactory
            .getLog(org.geowebcache.config.PostgresConfiguration.class);

    private static final String POSTGRESQL_DRIVER = "org.postgresql.Driver";

    /**
     * Available to parse GTWMSLayer with special deserializer
     * 
     * @see WMSLayerDeserializer
     */
    public final static Gson GSON;

    static {
        GsonBuilder gsonBuilder = new GsonBuilder();
        // specific handling of ParameterFilters
        gsonBuilder.registerTypeAdapter(GsonWMSLayer.class, new WMSLayerDeserializer());
        // handle null..
        gsonBuilder.serializeSpecialFloatingPointValues();

        GSON = gsonBuilder.create();
    }

    /**
     * Web app context, used to look up {@link XMLConfigurationProvider}s. Will be null if used the
     * {@link #PostgresConfiguration(File)} constructor
     */
    private final WebApplicationContext context;

    private final ConfigurationResourceProvider resourceProvider;

    private GeoWebCacheConfiguration gwcConfig;

    private transient Map<String, WMSLayer> addedLayers = new HashMap<String, WMSLayer>();

    private transient Set<String> removeLayers = new HashSet<String>();

    private DataSource dataSource;

    private String jndiName;

    private String url;

    private String user;

    private String password;

    private String schema;

    private String registerDriver;

    private Integer maxBackendRequests;

    private GridSetBroker gridSetBroker;

    private EhCacheManager cacheManager;

    private boolean gridsetsUpdated = false;

    /**
     * A flag for whether the config needs to be loaded at {@link #initialize(GridSetBroker)}. If
     * the constructor loads the configuration, will set it to false, then each call to initialize()
     * will reset this flag to true
     */
    private boolean reloadConfigOnInit = true;

    private DiskQuotaMonitor monitor;

    // sql queries

    private static final String PUBLIC_SCHEMA = "public";

    private static final String SELECT_ALL_FROM_GWC_LAYERS = "SELECT * FROM %s.gwc_layers;";

    private static final String SELECT_COUNT_FROM_GWC_LAYERS = "SELECT count(*) FROM %s.gwc_layers;";

    private static final String SELECT_FROM_GWC_LAYERS_BY_NAME = "SELECT * FROM %s.gwc_layers WHERE name = '%s'";

    private static final String CREATE_TABLE_IF_NOT_EXISTS_GWC_LAYERS = "CREATE TABLE IF NOT EXISTS %s.gwc_layers ( name text,  layer text,  PRIMARY KEY (name) );";

    private static final String DELETE_FROM_GWC_LAYER = "DELETE FROM %s.gwc_layers WHERE name = ?;";

    private static final String UPDATE_GWC_LAYERS = "UPDATE %s.gwc_layers SET layer = ? WHERE name = ?;";

    private static final String INSERT_INTO_GWC_LAYERS = "INSERT INTO %s.gwc_layers (name, layer) VALUES (?, ?);";

    private static final String SELECT_EXISTS_BY_NAME = "SELECT CASE WHEN count(name) = 1 THEN true ELSE false END FROM %s.gwc_layers WHERE name = '%s'";

    private static final String SELECT_NAME_FROM_GWC_LAYERS = "SELECT name FROM %s.gwc_layers;";

    /**
     * Base Constructor with custom ConfiguratioNResourceProvider
     * 
     * @param appCtx
     *            use to lookup {@link XMLConfigurationProvider} extensions, may be {@code null}
     * @param inFac
     */
    public PostgresConfiguration(final ApplicationContextProvider appCtx,
            final ConfigurationResourceProvider inFac) {
        this.context = appCtx == null ? null : appCtx.getApplicationContext();
        this.resourceProvider = inFac;

        this.cacheManager = new EhCacheManager();
    }

    /**
     * File System based Constructor
     * 
     * @param appCtx
     *            use to lookup {@link XMLConfigurationProvider} extensions, may be {@code null}
     * @param configFileDirectory
     * @param storageDirFinder
     * @throws ConfigurationException
     */
    public PostgresConfiguration(final ApplicationContextProvider appCtx,
            final String configFileDirectory, final DefaultStorageFinder storageDirFinder)
                    throws ConfigurationException {
        this(appCtx, new XMLFileResourceProvider(DEFAULT_CONFIGURATION_FILE_NAME, appCtx,
                configFileDirectory, storageDirFinder));
        resourceProvider.setTemplate("/" + DEFAULT_CONFIGURATION_FILE_NAME);
    }

    /**
     * Constructor that will look for {@code geowebcache.xml} at the directory defined by
     * {@code storageDirFinder}
     * 
     * @param appCtx
     *            use to lookup {@link XMLConfigurationProvider} extenions, may be {@code null}
     * @param defaultStorage
     * @throws ConfigurationException
     */
    public PostgresConfiguration(final ApplicationContextProvider appCtx,
            final DefaultStorageFinder storageDirFinder) throws ConfigurationException {
        this(appCtx, new XMLFileResourceProvider(DEFAULT_CONFIGURATION_FILE_NAME, appCtx,
                storageDirFinder));
        resourceProvider.setTemplate("/" + DEFAULT_CONFIGURATION_FILE_NAME);
    }

    /**
     * Constructor that will accept an absolute or relative path for finding {@code geowebcache.xml}
     * 
     * @param appCtx
     * @param configFileDirectory
     * @throws ConfigurationException
     */
    public PostgresConfiguration(final ApplicationContextProvider appCtx,
            final String configFileDirectory) throws ConfigurationException {
        this(appCtx, configFileDirectory, null);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (resourceProvider.hasInput()) {
            this.gwcConfig = loadConfiguration();
        }
        this.reloadConfigOnInit = false;

        // if a manual registration of driver should be done (e.g. Tomcat)
        if (registerDriver != null && registerDriver.equals("true")) {
            LOGGER.info(String.format("Registering driver: driver='%s'", POSTGRESQL_DRIVER));

            try {
                Class.forName(POSTGRESQL_DRIVER);
            } catch (Exception e) {
                LOGGER.error(e);

                throw new RuntimeException("Failed to load PostgreSQL driver", e);
            }
        }

        // creates data source and controls properties
        try {
            if (jndiName != null) {
                createDataSource();
            } else if (url == null || user == null) {
                throw new Exception("url and user must be provided if no jndiName");
            }
        } catch (Throwable e) {
            LOGGER.error(
                    "Failed to create data source or mandatory properties have not been defined",
                    e);

            throw new RuntimeException(e);
        }

        // will get instance of DiskQuotaMonitor bean
        if (this.monitor == null) {
            try {
                LOGGER.info("Getting bean: class=" + DiskQuotaMonitor.class.getName());

                setMonitor(context.getBean(DiskQuotaMonitor.class));
            } catch (Exception e) {
                LOGGER.error("Failed to get disk quota bean", e);
            }
        }

        if (this.maxBackendRequests == null) {
            LOGGER.info("Defaulting maxBackendRequests to 64");

            this.maxBackendRequests = 64;
        }

        // creates layers table
        prepareLayersTable();
    }

    /**
     * Constructor with input stream (only for testing)
     * 
     * @throws ConfigurationException
     */
    public PostgresConfiguration(final InputStream is) throws ConfigurationException {
        this(null, new ConfigurationResourceProvider() {

            @Override
            public InputStream in() {
                throw new UnsupportedOperationException();
            }

            @Override
            public OutputStream out() throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public void backup() throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public void setTemplate(String template) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getLocation() throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getId() {
                return "mockConfig";
            }

            @Override
            public boolean hasInput() {
                return false;
            }

            @Override
            public boolean hasOutput() {
                return false;
            }

        });
        try {
            gwcConfig = loadConfiguration(is);
        } catch (IOException e) {
            throw new ConfigurationException(e.getMessage(), e);
        }
    }

    public void setTemplate(String template) {
        resourceProvider.setTemplate(template);
    }

    public void setJndiName(String jndiName) {
        this.jndiName = jndiName;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public void setRegisterDriver(String registerDriver) {
        this.registerDriver = registerDriver;
    }

    public void setMaxBackendRequests(Integer maxBackendRequests) {
        this.maxBackendRequests = maxBackendRequests;
    }

    public void setMonitor(final DiskQuotaMonitor monitor) {
        this.monitor = monitor;
    }

    /**
     * @return bean for monitoring
     */
    public DiskQuotaMonitor getMonitor() {
        return this.monitor;
    }

    public String getConfigLocation() throws ConfigurationException {
        try {
            return resourceProvider.getLocation();
        } catch (IOException e) {
            throw new ConfigurationException(e.getMessage(), e);
        }
    }

    @Override
    public boolean isRuntimeStatsEnabled() {
        if (gwcConfig == null || gwcConfig.getRuntimeStats() == null) {
            return true;
        } else {
            return gwcConfig.getRuntimeStats();
        }
    }

    @Override
    public synchronized ServiceInformation getServiceInformation() {
        return gwcConfig.getServiceInformation();
    }

    /**
     * Configuration objects lacking their own defaults can delegate to this
     * 
     * @param layer
     */
    public void setDefaultValues(TileLayer layer) {
        // Additional values that can have defaults set
        if (layer.isCacheBypassAllowed() == null) {
            if (gwcConfig.getCacheBypassAllowed() != null) {
                layer.setCacheBypassAllowed(gwcConfig.getCacheBypassAllowed());
            } else {
                layer.setCacheBypassAllowed(false);
            }
        }

        if (layer.getBackendTimeout() == null) {
            if (gwcConfig.getBackendTimeout() != null) {
                layer.setBackendTimeout(gwcConfig.getBackendTimeout());
            } else {
                layer.setBackendTimeout(120);
            }
        }

        if (layer.getFormatModifiers() == null) {
            if (gwcConfig.getFormatModifiers() != null) {
                layer.setFormatModifiers(gwcConfig.getFormatModifiers());
            }
        }

        if (layer instanceof WMSLayer) {
            WMSLayer wl = (WMSLayer) layer;

            URL proxyUrl = null;
            try {
                if (gwcConfig.getProxyUrl() != null) {
                    proxyUrl = new URL(gwcConfig.getProxyUrl());
                    LOGGER.debug("Using proxy " + proxyUrl.getHost() + ":" + proxyUrl.getPort());
                } else if (wl.getProxyUrl() != null) {
                    proxyUrl = new URL(wl.getProxyUrl());
                    LOGGER.debug("Using proxy " + proxyUrl.getHost() + ":" + proxyUrl.getPort());
                }
            } catch (MalformedURLException e) {
                LOGGER.error("could not parse proxy URL " + wl.getProxyUrl()
                        + " ! continuing WITHOUT proxy!", e);
            }

            final WMSHttpHelper sourceHelper;

            if (wl.getHttpUsername() != null) {
                sourceHelper = new WMSHttpHelper(wl.getHttpUsername(), wl.getHttpPassword(),
                        proxyUrl);
                LOGGER.debug("Using per-layer HTTP credentials for " + wl.getName() + ", "
                        + "username " + wl.getHttpUsername());
            } else if (gwcConfig.getHttpUsername() != null) {
                sourceHelper = new WMSHttpHelper(gwcConfig.getHttpUsername(),
                        gwcConfig.getHttpPassword(), proxyUrl);
                LOGGER.debug("Using global HTTP credentials for " + wl.getName());
            } else {
                sourceHelper = new WMSHttpHelper(null, null, proxyUrl);
                LOGGER.debug("Not using HTTP credentials for " + wl.getName());
            }

            wl.setSourceHelper(sourceHelper);
            wl.setLockProvider(gwcConfig.getLockProvider());
        }
    }

    private GeoWebCacheConfiguration loadConfiguration() throws ConfigurationException {
        Assert.isTrue(resourceProvider.hasInput());
        InputStream in;
        try {
            in = resourceProvider.in();
            try {
                return loadConfiguration(in);
            } finally {
                in.close();
            }
        } catch (IOException e) {
            throw new ConfigurationException(
                    "Error parsing config file " + resourceProvider.getId(), e);
        }
    }

    private GeoWebCacheConfiguration loadConfiguration(InputStream xmlFile)
            throws IOException, ConfigurationException {
        Node rootNode = loadDocument(xmlFile);
        XStream xs = getConfiguredXStreamWithContext(new GeoWebCacheXStream(), Context.PERSIST);

        GeoWebCacheConfiguration config;
        config = (GeoWebCacheConfiguration) xs.unmarshal(new DomReader((Element) rootNode));
        return config;
    }

    /**
     * @see org.geowebcache.config.Configuration#save()
     */
    public synchronized void save() throws IOException {
        if (!resourceProvider.hasOutput()) {
            return;
        }

        try {
            // CRUD for WMS layers
            createDeleteOrUpdateLayers();
        } catch (SQLException e) {
            LOGGER.error(e);

            throw new IOException(e);
        }

        // gridsets
        if (gridsetsUpdated) {
            try {
                resourceProvider.backup();
            } catch (Exception e) {
                LOGGER.warn(
                        "Error creating backup of configuration file " + resourceProvider.getId(),
                        e);
            }

            persistToFile();
        }
    }

    public XStream getConfiguredXStreamWithContext(XStream xs,
            ContextualConfigurationProvider.Context providerContext) {
        return getConfiguredXStreamWithContext(xs, this.context, providerContext);
    }

    /**
     * Method responsible for writing out the entire GeoWebCacheConfiguration object
     * 
     * throws an exception if it does not succeed
     */
    private void persistToFile() throws IOException {
        Assert.isTrue(resourceProvider.hasOutput());
        // create the XStream for serializing the configuration
        XStream xs = getConfiguredXStreamWithContext(new GeoWebCacheXStream(), Context.PERSIST);

        try (OutputStreamWriter writer = new OutputStreamWriter(resourceProvider.out(), "UTF-8")) {
            // set version to latest
            String currentSchemaVersion = getCurrentSchemaVersion();
            gwcConfig.setVersion(currentSchemaVersion);

            writer.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
            xs.toXML(gwcConfig, writer);
        } catch (UnsupportedEncodingException uee) {
            uee.printStackTrace();
            throw new IOException(uee.getMessage());
        } catch (FileNotFoundException fnfe) {
            throw fnfe;
        } catch (IOException e) {
            throw (IOException) new IOException(
                    "Error writing to " + resourceProvider.getId() + ": " + e.getMessage())
                            .initCause(e);
        }

        LOGGER.info("Wrote configuration to " + resourceProvider.getId());
    }

    /**
     * @return {@code true} only if {@code tl instanceof WMSLayer}
     * @see org.geowebcache.config.Configuration#canSave(org.geowebcache.layer.TileLayer)
     */
    public boolean canSave(TileLayer tl) {
        return tl instanceof WMSLayer && !tl.isTransientLayer();
    }

    /**
     * @param tl
     *            the layer to add to this configuration (instanceof WMSLayer)
     * @return
     * @throws IllegalArgumentException
     *             if a layer named the same than {@code tl} already exists
     * @see org.geowebcache.config.Configuration#addLayer(org.geowebcache.layer.TileLayer)
     */
    public synchronized void addLayer(TileLayer tl) throws IllegalArgumentException {
        if (tl == null) {
            throw new NullPointerException();
        }
        if (!(tl instanceof WMSLayer)) {
            throw new IllegalArgumentException(
                    "Can't add layers of type " + tl.getClass().getName());
        }
        if (containsLayer(tl.getName())) {
            throw new IllegalArgumentException("Layer '" + tl.getName() + "' already exists");
        }

        initialize(tl);

        addedLayers.put(tl.getName(), (WMSLayer) tl);
    }

    /**
     * Method responsible for modifying an existing layer (WMSLayer).
     * 
     * @param tl
     *            the new layer to overwrite the existing layer
     * @throws NoSuchElementException
     * @see org.geowebcache.config.Configuration#modifyLayer(org.geowebcache.layer.TileLayer)
     */
    public synchronized void modifyLayer(TileLayer tl) throws NoSuchElementException {
        if (!containsLayer(tl.getName())) {
            throw new NoSuchElementException("Layer " + tl.getName() + " does not exist");
        }

        initialize(tl);

        addedLayers.put(tl.getName(), (WMSLayer) tl);
    }

    /**
     * @return {@code true} if the layer was removed, {@code false} if no such layer exists
     * @see org.geowebcache.config.Configuration#removeLayer(java.lang.String)
     */
    public synchronized boolean removeLayer(final String layerName) {
        final boolean contains = containsLayer(layerName);

        if (contains) {
            removeLayers.add(layerName);
        } else {
            // possibly removes from cache
            cacheManager.removeLayer(layerName);
        }

        // in case it was added but not saved before remove
        addedLayers.remove(layerName);

        return contains;
    }

    /**
     * @param gridSet
     * @throws GeoWebCacheException
     */
    public synchronized void addOrReplaceGridSet(final XMLGridSet gridSet)
            throws IllegalArgumentException {
        final String gridsetName = gridSet.getName();

        // should store new xml file
        gridsetsUpdated = true;

        List<XMLGridSet> gridSets = gwcConfig.getGridSets();

        for (Iterator<XMLGridSet> it = gridSets.iterator(); it.hasNext();) {
            XMLGridSet gset = it.next();
            if (gridsetName.equals(gset.getName())) {
                it.remove();
            }
        }
        gridSets.add(gridSet);
    }

    /**
     * Removes and returns the gridset configuration named {@code gridsetName}.
     * 
     * @param gridsetName
     *            the name of the gridset to remove
     * @return the removed griset, or {@code null} if no such gridset exists
     */
    public synchronized XMLGridSet removeGridset(final String gridsetName) {
        List<XMLGridSet> gridSets = gwcConfig.getGridSets();

        // should store new xml file
        gridsetsUpdated = true;

        for (Iterator<XMLGridSet> it = gridSets.iterator(); it.hasNext();) {
            XMLGridSet gset = it.next();
            if (gridsetName.equals(gset.getName())) {
                it.remove();
                return gset;
            }
        }
        return null;
    }

    /**
     * Method responsible for loading xml configuration file and parsing it into a W3C DOM Document
     * 
     * @param file
     *            the file contaning the layer configurations
     * @return W3C DOM Document
     */
    static Node loadDocument(InputStream xmlFile) throws ConfigurationException, IOException {
        Node topNode = null;
        try {
            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
            docBuilderFactory.setNamespaceAware(true);
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            topNode = checkAndTransform(docBuilder.parse(xmlFile));
        } catch (Exception e) {
            throw (IOException) new IOException(e.getMessage()).initCause(e);
        }

        return topNode;
    }

    private static Node checkAndTransform(Document doc) throws ConfigurationException {
        Node rootNode = doc.getDocumentElement();

        // debugPrint(rootNode);

        if (!rootNode.getNodeName().equals("gwcConfiguration")) {
            LOGGER.info("The configuration file is of the pre 1.0 type, trying to convert.");
            rootNode = applyTransform(rootNode, "geowebcache_pre10.xsl").getFirstChild();
        }

        // debugPrint(rootNode);

        if (rootNode.getNamespaceURI().equals("http://geowebcache.org/schema/1.0.0")) {
            LOGGER.info("Updating configuration from 1.0.0 to 1.0.1");
            rootNode = applyTransform(rootNode, "geowebcache_100.xsl").getFirstChild();
        }

        // debugPrint(rootNode);

        if (rootNode.getNamespaceURI().equals("http://geowebcache.org/schema/1.0.1")) {
            LOGGER.info("Updating configuration from 1.0.1 to 1.0.2");
            rootNode = applyTransform(rootNode, "geowebcache_101.xsl").getFirstChild();
        }

        // debugPrint(rootNode);

        if (rootNode.getNamespaceURI().equals("http://geowebcache.org/schema/1.0.2")) {
            LOGGER.info("Updating configuration from 1.0.2 to 1.1.0");
            rootNode = applyTransform(rootNode, "geowebcache_102.xsl").getFirstChild();
        }

        if (rootNode.getNamespaceURI().equals("http://geowebcache.org/schema/1.1.0")) {
            LOGGER.info("Updating configuration from 1.1.0 to 1.1.3");
            rootNode = applyTransform(rootNode, "geowebcache_110.xsl").getFirstChild();
        }

        if (rootNode.getNamespaceURI().equals("http://geowebcache.org/schema/1.1.3")) {
            LOGGER.info("Updating configuration from 1.1.3 to 1.1.4");
            rootNode = applyTransform(rootNode, "geowebcache_113.xsl").getFirstChild();
        }

        if (rootNode.getNamespaceURI().equals("http://geowebcache.org/schema/1.1.4")) {
            LOGGER.info("Updating configuration from 1.1.4 to 1.1.5");
            rootNode = applyTransform(rootNode, "geowebcache_114.xsl").getFirstChild();
        }

        if (rootNode.getNamespaceURI().equals("http://geowebcache.org/schema/1.1.5")) {
            LOGGER.info("Updating configuration from 1.1.5 to 1.2.0");
            rootNode = applyTransform(rootNode, "geowebcache_115.xsl").getFirstChild();
        }

        if (rootNode.getNamespaceURI().equals("http://geowebcache.org/schema/1.2.0")) {
            LOGGER.info("Updating configuration from 1.2.0 to 1.2.1");
            rootNode = applyTransform(rootNode, "geowebcache_120.xsl").getFirstChild();
        }

        if (rootNode.getNamespaceURI().equals("http://geowebcache.org/schema/1.2.1")) {
            LOGGER.info("Updating configuration from 1.2.1 to 1.2.2");
            rootNode = applyTransform(rootNode, "geowebcache_121.xsl").getFirstChild();
        }

        if (rootNode.getNamespaceURI().equals("http://geowebcache.org/schema/1.2.2")) {
            LOGGER.info("Updating configuration from 1.2.2 to 1.2.4");
            rootNode = applyTransform(rootNode, "geowebcache_122.xsl").getFirstChild();
        }

        if (rootNode.getNamespaceURI().equals("http://geowebcache.org/schema/1.2.4")) {
            LOGGER.info("Updating configuration from 1.2.4 to 1.2.5");
            rootNode = applyTransform(rootNode, "geowebcache_124.xsl").getFirstChild();
        }

        if (rootNode.getNamespaceURI().equals("http://geowebcache.org/schema/1.2.5")) {
            LOGGER.info("Updating configuration from 1.2.5 to 1.2.6");
            rootNode = applyTransform(rootNode, "geowebcache_125.xsl").getFirstChild();
        }

        if (rootNode.getNamespaceURI().equals("http://geowebcache.org/schema/1.2.6")) {
            LOGGER.info("Updating configuration from 1.2.6 to 1.5.0");
            rootNode = applyTransform(rootNode, "geowebcache_126.xsl").getFirstChild();
        }

        if (rootNode.getNamespaceURI().equals("http://geowebcache.org/schema/1.5.0")) {
            LOGGER.info("Updating configuration from 1.5.0 to 1.5.1");
            rootNode = applyTransform(rootNode, "geowebcache_150.xsl").getFirstChild();
        }

        if (rootNode.getNamespaceURI().equals("http://geowebcache.org/schema/1.5.1")) {
            LOGGER.info("Updating configuration from 1.5.1 to 1.6.0");
            rootNode = applyTransform(rootNode, "geowebcache_151.xsl").getFirstChild();
        }

        // Check again after transform
        if (!rootNode.getNodeName().equals("gwcConfiguration")) {
            LOGGER.error(
                    "Unable to parse file, expected gwcConfiguration at root after transform.");
            throw new ConfigurationException("Unable to parse after transform.");
        } else {
            // Parsing the schema file
            try {
                validate(rootNode);
                LOGGER.info("Configuration file validated fine.");
            } catch (SAXException e) {
                String msg = "*** GWC configuration validation error: " + e.getMessage();
                char[] c = new char[4 + msg.length()];
                Arrays.fill(c, '*');
                String warndecoration = new String(c).substring(0, 80);
                LOGGER.warn(warndecoration);
                LOGGER.warn(msg);
                LOGGER.warn(
                        "*** Will try to use configuration anyway. Please check the order of declared elements against the schema.");
                LOGGER.warn(warndecoration);
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        return rootNode;
    }

    static void validate(Node rootNode) throws SAXException, IOException {
        // Perform validation
        // TODO dont know why this one suddenly failed to look up, revert to
        // XMLConstants.W3C_XML_SCHEMA_NS_URI
        SchemaFactory factory = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");
        InputStream is = PostgresConfiguration.class.getResourceAsStream("geowebcache.xsd");

        Schema schema = factory.newSchema(new StreamSource(is));
        Validator validator = schema.newValidator();

        // debugPrint(rootNode);

        DOMSource domSrc = new DOMSource(rootNode);
        validator.validate(domSrc);
    }

    static String getCurrentSchemaVersion() {
        InputStream is = PostgresConfiguration.class.getResourceAsStream("geowebcache.xsd");
        Document dom;
        try {
            dom = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        String version = dom.getDocumentElement().getAttribute("version");
        if (null == version || version.trim().length() == 0) {
            throw new IllegalStateException("Schema doesn't define version");
        }
        return version.trim();
    }

    private static Node applyTransform(Node oldRootNode, String xslFilename) {
        DOMResult result = new DOMResult();
        Transformer transformer;

        InputStream is = PostgresConfiguration.class.getResourceAsStream(xslFilename);

        try {
            transformer = TransformerFactory.newInstance().newTransformer(new StreamSource(is));
            transformer.transform(new DOMSource(oldRootNode), result);
        } catch (TransformerConfigurationException e) {
            e.printStackTrace();
        } catch (TransformerFactoryConfigurationError e) {
            e.printStackTrace();
        } catch (TransformerException e) {
            e.printStackTrace();
        }

        return result.getNode();
    }

    /**
     * @see org.geowebcache.config.Configuration#initialize(org.geowebcache.grid.GridSetBroker)
     */
    @Override
    public int initialize(final GridSetBroker gridSetBroker) throws GeoWebCacheException {

        this.gridSetBroker = gridSetBroker;

        if (this.reloadConfigOnInit && resourceProvider.hasInput()) {
            this.gwcConfig = loadConfiguration();
        }

        contributeGridSets(gridSetBroker);

        // clear cache on reload
        cacheManager.clearAll();

        this.reloadConfigOnInit = true;

        return getTileLayerCount();
    }

    /**
     * Will add/update/remove from db and cache
     * 
     * @throws SQLException
     */
    private void createDeleteOrUpdateLayers() throws SQLException {
        // create layers
        runCommand(new PostgresCommand() {

            private boolean exists(Connection con, String layerName) throws SQLException {
                Statement s = null;
                ResultSet rs = null;
                try {
                    s = con.createStatement();
                    rs = s.executeQuery(
                            String.format(SELECT_EXISTS_BY_NAME, getSchema(), layerName));

                    if (rs.next()) {
                        return rs.getBoolean(1);
                    }

                    return false;
                } finally {
                    SQLUtils.closeQuietly(rs);
                    SQLUtils.closeQuietly(s);
                }
            }

            @Override
            public void execute(Connection con) throws SQLException {
                PreparedStatement insert = null;
                PreparedStatement update = null;
                PreparedStatement delete = null;
                try {
                    insert = con
                            .prepareStatement(String.format(INSERT_INTO_GWC_LAYERS, getSchema()));
                    update = con.prepareStatement(String.format(UPDATE_GWC_LAYERS, getSchema()));
                    delete = con
                            .prepareStatement(String.format(DELETE_FROM_GWC_LAYER, getSchema()));

                    for (Entry<String, WMSLayer> layer : addedLayers.entrySet()) {
                        String layerName = layer.getKey();

                        // removes from cache if exists
                        cacheManager.removeLayer(layerName);

                        if (exists(con, layerName)) {
                            LOGGER.debug("Updating layer: name=" + layerName);
                            update.setString(1, JSONUtils.stringify(layer.getValue()));
                            update.setString(2, layerName);
                            update.executeUpdate();
                        } else {
                            LOGGER.debug("Creating layer: name=" + layerName);
                            insert.setString(1, layerName);
                            insert.setString(2, JSONUtils.stringify(layer.getValue()));
                            insert.executeUpdate();
                        }
                    }

                    for (String layerName : removeLayers) {
                        LOGGER.debug("Deleting layer: name=" + layerName);
                        delete.setString(1, layerName);
                        delete.executeUpdate();

                        // removes from cache if exists
                        cacheManager.removeLayer(layerName);
                    }
                } catch (SQLException e) {
                    LOGGER.error("Failed to get connection", e);
                } finally {
                    SQLUtils.closeQuietly(insert);
                    SQLUtils.closeQuietly(update);
                    SQLUtils.closeQuietly(delete);
                }

            }
        });

        // clears internal lists
        addedLayers.clear();
        removeLayers.clear();
    }

    private void prepareLayersTable() throws GeoWebCacheException {
        LOGGER.info("Preparing layers table with: " + (jndiName == null ? url : jndiName));

        // create schema/table
        try {
            runCommand(new PostgresCommand() {

                @Override
                public void execute(Connection con) throws SQLException {
                    PreparedStatement create = null;
                    try {
                        String createTable = String.format(CREATE_TABLE_IF_NOT_EXISTS_GWC_LAYERS,
                                getSchema());

                        LOGGER.info(createTable);

                        create = con.prepareStatement(createTable);
                        create.executeUpdate();
                    } catch (SQLException e) {
                        LOGGER.error("Failed to get connection", e);
                    } finally {
                        SQLUtils.closeQuietly(create);
                    }

                }
            });
        } catch (SQLException e) {
            LOGGER.error(e.getMessage());

            throw new GeoWebCacheException(e);
        }
    }

    private Connection getConnection() throws SQLException {
        return dataSource == null ? DriverManager.getConnection(url, user, password)
                : dataSource.getConnection();
    }

    private synchronized void createDataSource() throws NamingException {
        if (dataSource == null) {
            LOGGER.info("Creating data source from jndiName: value=" + jndiName);

            dataSource = (DataSource) new InitialContext().lookup(jndiName);
        }
    }

    /**
     * @see #schema
     * 
     * @return schema (public if {@code schema==null})
     */
    private String getSchema() {
        return schema == null ? PUBLIC_SCHEMA : schema;
    }

    private void contributeGridSets(final GridSetBroker gridSetBroker) {
        LOGGER.info("Initializing GridSets from " + getIdentifier());

        if (gwcConfig.getGridSets() != null) {
            Iterator<XMLGridSet> iter = gwcConfig.getGridSets().iterator();
            while (iter.hasNext()) {
                XMLGridSet xmlGridSet = iter.next();

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Reading " + xmlGridSet.getName());
                }

                GridSet gridSet = xmlGridSet.makeGridSet();

                LOGGER.info("Read GridSet " + gridSet.getName());

                gridSetBroker.put(gridSet);
            }
        }
    }

    private void initialize(final TileLayer layer) {
        LOGGER.info("Initializing TileLayer: name='" + layer.getName() + "'");

        setDefaultValues(layer);
        layer.initialize(gridSetBroker);
    }

    /**
     * @see org.geowebcache.config.Configuration#getIdentifier()
     */
    public String getIdentifier() {
        return resourceProvider.getId();
    }

    /**
     * @see org.geowebcache.config.Configuration#getTileLayers()
     */
    public List<TileLayer> getTileLayers() {
        final String sql = String.format(SELECT_ALL_FROM_GWC_LAYERS, getSchema());

        List<TileLayer> layers = null;
        try {
            layers = new ResultSetHandler<List<TileLayer>>() {

                @Override
                public List<TileLayer> executeImpl(ResultSet rs) throws SQLException {
                    List<TileLayer> iterable = new ArrayList<TileLayer>();
                    while (rs.next()) {
                        iterable.add(parseLayer(rs.getString(2)));
                    }

                    return iterable;

                }
            }.execute(sql);
        } catch (SQLException e) {
            LOGGER.error(e);
        }

        return layers;
    }

    /**
     * @see #getTileLayers()
     * 
     * @see org.geowebcache.config.Configuration#getLayers()
     */
    public Iterable<TileLayer> getLayers() {
        return getTileLayers();
    }

    /**
     * id and name is the same for this implementation
     * 
     * @see #getTileLayerById(String)
     * 
     * @see org.geowebcache.config.Configuration#getTileLayer(java.lang.String)
     */
    public TileLayer getTileLayer(String layerName) {
        return getTileLayerById(layerName);
    }

    /**
     * @see #getTileLayer(String)
     * 
     * @see org.geowebcache.config.Configuration#getTileLayerById(String)
     */
    public TileLayer getTileLayerById(String layerId) {
        WMSLayer layer = cacheManager.getLayer(layerId);

        if (layer != null) {
            return layer;
        }

        final String sql = String.format(SELECT_FROM_GWC_LAYERS_BY_NAME, getSchema(), layerId);

        try {
            layer = new ResultSetHandler<WMSLayer>() {

                @Override
                public WMSLayer executeImpl(ResultSet rs) throws SQLException {
                    if (rs.next()) {
                        return parseLayer(rs.getString(2));
                    }

                    return null;

                }

            }.execute(sql);

            if (layer != null) {
                cacheManager.addLayer(layer);

                // get monitor class
                DiskQuotaMonitor monitor = getMonitor();
                if (monitor != null) {
                    try {
                        LOGGER.debug("Adding to quota monitor");
                        monitor.addLayerToQuotaMonitor(layer);
                    } catch (InterruptedException e) {
                        LOGGER.warn("Failed to add to quota monitor", e);
                    }

                    TileLayerListener listener = monitor.getLayerListener();
                    if (listener != null) {
                        LOGGER.debug(
                                "Adding listener: class='" + listener.getClass().getName() + "'");
                        layer.addLayerListener(listener);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error(e);
        }

        return layer;
    }

    /**
     * @see org.geowebcache.config.Configuration#containsLayer(java.lang.String)
     */
    public boolean containsLayer(String layerId) {
        TileLayer layer = cacheManager.getLayer(layerId);

        if (layer != null) {
            return true;
        }

        final String sql = String.format(SELECT_EXISTS_BY_NAME, getSchema(), layerId);

        boolean exists = false;
        try {
            exists = new ResultSetHandler<Boolean>() {

                @Override
                public Boolean executeImpl(ResultSet rs) throws SQLException {
                    if (rs.next()) {
                        return rs.getBoolean(1);
                    }

                    return false;

                }
            }.execute(sql);
        } catch (SQLException e) {
            LOGGER.error(e);
        }

        return exists;
    }

    /**
     * @see org.geowebcache.config.Configuration#getTileLayerCount()
     */
    public int getTileLayerCount() {
        final String sql = String.format(SELECT_COUNT_FROM_GWC_LAYERS, getSchema());

        int count = -1;
        try {
            count = new ResultSetHandler<Integer>() {

                @Override
                public Integer executeImpl(ResultSet rs) throws SQLException {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }

                    return -1;

                }
            }.execute(sql);
        } catch (SQLException e) {
            LOGGER.error(e);
        }

        return count;
    }

    /**
     * @see org.geowebcache.config.Configuration#getTileLayerNames()
     */
    public Set<String> getTileLayerNames() {
        final String sql = String.format(SELECT_NAME_FROM_GWC_LAYERS, getSchema());

        Set<String> list = new HashSet<String>();
        try {
            list = new ResultSetHandler<Set<String>>() {

                @Override
                public Set<String> executeImpl(ResultSet rs) throws SQLException {
                    Set<String> list = new HashSet<String>();
                    while (rs.next()) {
                        list.add(rs.getString(1));
                    }

                    return list;

                }
            }.execute(sql);
        } catch (SQLException e) {
            LOGGER.error(e);
        }

        return list;
    }

    public String getVersion() {
        return gwcConfig.getVersion();
    }

    /**
     * Used for getting the "fullWMS" parameter from GeoWebCacheConfigration
     * 
     * @return
     */
    public Boolean getfullWMS() {
        return gwcConfig != null ? gwcConfig.getFullWMS() : null;
    }

    public List<BlobStoreConfig> getBlobStores() {
        return gwcConfig.getBlobStores();
    }

    public LockProvider getLockProvider() {
        return gwcConfig.getLockProvider();
    }

    // db handlers

    private void runCommand(final PostgresCommand command) throws SQLException {
        Connection con = null;

        try {
            // get connection
            con = getConnection();

            // Will execute db-work
            command.execute(con);
        } catch (SQLException ex) {
            // will possibly rollback
            try {
                if (con != null && !con.getAutoCommit()) {
                    con.rollback();
                }
            } catch (Exception er) {
                LOGGER.error("Failed to rollback");
            }

            throw ex;
        } catch (Exception e) {
            // rollback
            try {
                if (con != null && !con.getAutoCommit()) {
                    con.rollback();
                }
            } catch (Exception er) {
                LOGGER.error("Failed to rollback");
            }

            LOGGER.error(e.getMessage(), e);

            throw new SQLException(e);
        } finally {
            // Will close connection
            if (con != null) {
                SQLUtils.closeQuietly(con);

                LOGGER.trace("Closed connection");
            }
        }
    }

    private abstract class ResultSetHandler<E> {

        public E execute(final String query) throws SQLException {
            final PostgresResultSet<E> res = new PostgresResultSet<E>();

            runCommand(new PostgresCommand() {

                @Override
                public void execute(Connection con) throws SQLException {
                    Statement s = null;
                    ResultSet rs = null;
                    try {
                        s = con.createStatement();
                        rs = s.executeQuery(query);

                        res.setObj(executeImpl(rs));
                    } finally {
                        SQLUtils.closeQuietly(rs);
                        SQLUtils.closeQuietly(s);
                    }
                }
            });

            return res.getObj();
        }

        public abstract E executeImpl(ResultSet rs) throws SQLException;

    }

    private WMSLayer parseLayer(String json) {
        WMSLayer layer = GSON.fromJson(json, GsonWMSLayer.class);
        
        // will set default and transient values
        initialize(layer);

        return layer;
    }

}
