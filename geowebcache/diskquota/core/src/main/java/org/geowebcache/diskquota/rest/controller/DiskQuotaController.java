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
 * @author David Vick, Boundless, Copyright 2017
 */
package org.geowebcache.diskquota.rest.controller;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.io.json.JettisonMappedXmlDriver;
import com.thoughtworks.xstream.io.json.JsonHierarchicalStreamDriver;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.logging.Logger;
import org.apache.commons.io.IOUtils;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.geotools.util.logging.Logging;
import org.geowebcache.config.ContextualConfigurationProvider.Context;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.diskquota.ConfigLoader;
import org.geowebcache.diskquota.DiskQuotaConfig;
import org.geowebcache.diskquota.DiskQuotaMonitor;
import org.geowebcache.io.GeoWebCacheXStream;
import org.geowebcache.storage.blobstore.memory.CacheStatistics;
import org.geowebcache.util.ApplicationContextProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

@Component
@RestController
@RequestMapping(path = "${gwc.context.suffix:}/rest")
public class DiskQuotaController {

    private final WebApplicationContext context;

    @Autowired
    public DiskQuotaController(ApplicationContextProvider appCtx) {
        context = appCtx == null ? null : appCtx.getApplicationContext();
    }

    static final Logger LOG = Logging.getLogger(DiskQuotaController.class.getName());

    @Autowired
    DiskQuotaMonitor monitor;

    public void setDiskQuotaMonitor(DiskQuotaMonitor monitor) {
        this.monitor = monitor;
    }

    @RequestMapping(value = "/diskquota", method = RequestMethod.GET)
    public ResponseEntity<?> doGet(HttpServletRequest request) {
        final DiskQuotaConfig config = monitor.getConfig();

        if (request.getPathInfo().contains("json")) {
            try {
                return getJsonRepresentation(config);
            } catch (JSONException e) {
                return new ResponseEntity<>("Caught JSON Execption.", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } else {
            return getXmlRepresentation(config);
        }
    }

    @RequestMapping(value = "/diskquota", method = RequestMethod.PUT)
    public ResponseEntity<?> doPut(HttpServletRequest request) {
        DiskQuotaConfig config = monitor.getConfig();
        DiskQuotaConfig newConfig = null;
        String reqData = "";
        try {
            StringWriter writer = new StringWriter();
            IOUtils.copy(request.getInputStream(), writer, Charset.defaultCharset());
            reqData = writer.toString();
            if (request.getPathInfo().contains("json")) {
                newConfig = fromJSON(reqData);
                applyDiff(config, newConfig);

                return getJsonRepresentation(config);
            } else {
                newConfig = fromXML(reqData);
                applyDiff(config, newConfig);
                return getXmlRepresentation(config);
            }

        } catch (IOException | JSONException e) {
            return new ResponseEntity<>("Error writing input stream to string", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Applies the set values in {@code newConfig} (the non null ones) to {@code config}
     *
     * @throws IllegalArgumentException as per {@link DiskQuotaConfig#setCacheCleanUpFrequency},
     *     {@link DiskQuotaConfig#setDiskBlockSize}, {@link DiskQuotaConfig#setMaxConcurrentCleanUps} ,
     *     {@link DiskQuotaConfig#setCacheCleanUpUnits}
     */
    private void applyDiff(DiskQuotaConfig config, DiskQuotaConfig newConfig) throws IllegalArgumentException {
        // apply diff
        if (newConfig != null) {
            if (null != newConfig.isEnabled()) {
                config.setEnabled(newConfig.isEnabled());
            }
            if (null != newConfig.getCacheCleanUpFrequency()) {
                config.setCacheCleanUpFrequency(newConfig.getCacheCleanUpFrequency());
            }
            if (null != newConfig.getMaxConcurrentCleanUps()) {
                config.setMaxConcurrentCleanUps(newConfig.getMaxConcurrentCleanUps());
            }
            if (null != newConfig.getCacheCleanUpUnits()) {
                config.setCacheCleanUpUnits(newConfig.getCacheCleanUpUnits());
            }
            if (null != newConfig.getGlobalExpirationPolicyName()) {
                config.setGlobalExpirationPolicyName(newConfig.getGlobalExpirationPolicyName());
            }
            if (null != newConfig.getGlobalQuota()) {
                config.setGlobalQuota(newConfig.getGlobalQuota());
            }
            if (null != newConfig.getLayerQuotas()) {
                config.setLayerQuotas(newConfig.getLayerQuotas());
            }
        }
    }

    private DiskQuotaConfig fromJSON(String entity) throws IOException {

        final String text = entity;

        HierarchicalStreamDriver driver = new JettisonMappedXmlDriver();

        XStream xStream = new GeoWebCacheXStream(driver);

        xStream = ConfigLoader.getConfiguredXStream(xStream);

        DiskQuotaConfig configuration;
        try (StringReader reader = new StringReader(text)) {
            configuration = ConfigLoader.loadConfiguration(reader, xStream);
            return configuration;
        }
    }

    private DiskQuotaConfig fromXML(String entity) throws IOException {

        final String text = entity;
        try (StringReader reader = new StringReader(text)) {
            XStream xstream = ConfigLoader.getConfiguredXStream(new GeoWebCacheXStream());
            DiskQuotaConfig diskQuotaConfig = ConfigLoader.loadConfiguration(reader, xstream);
            return diskQuotaConfig;
        }
    }

    /**
     * Private method for retunring a JSON representation of the Statistics
     *
     * @return a {@link ResponseEntity} object
     */
    private ResponseEntity<?> getJsonRepresentation(DiskQuotaConfig config) throws JSONException {
        XStream xs = XMLConfiguration.getConfiguredXStreamWithContext(
                new GeoWebCacheXStream(new JsonHierarchicalStreamDriver()), context, Context.REST);
        JSONObject obj = new JSONObject(xs.toXML(config));
        return new ResponseEntity<>(obj.toString(), HttpStatus.OK);
    }

    /**
     * Private method for retunring an XML representation of the Statistics
     *
     * @return a {@link ResponseEntity} object
     */
    private ResponseEntity<?> getXmlRepresentation(DiskQuotaConfig config) {
        XStream xStream = getConfiguredXStream(new GeoWebCacheXStream());
        String xmlText = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + xStream.toXML(config);

        return new ResponseEntity<>(xmlText, HttpStatus.OK);
    }

    /**
     * This method adds to the input {@link XStream} an alias for the CacheStatistics
     *
     * @return an updated XStream
     */
    public static XStream getConfiguredXStream(XStream xs) {
        xs.setMode(XStream.NO_REFERENCES);
        xs.alias("gwcInMemoryCacheStatistics", CacheStatistics.class);
        return xs;
    }
}
