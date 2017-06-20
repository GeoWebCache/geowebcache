package org.geowebcache.diskquota.rest;

import java.io.IOException;
import java.io.StringReader;

import org.geowebcache.diskquota.ConfigLoader;
import org.geowebcache.diskquota.DiskQuotaConfig;
import org.geowebcache.diskquota.DiskQuotaMonitor;
import org.geowebcache.io.GeoWebCacheXStream;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.resource.Representation;
import org.restlet.resource.Resource;
import org.restlet.resource.StringRepresentation;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.io.json.JettisonMappedXmlDriver;
import com.thoughtworks.xstream.io.json.JsonHierarchicalStreamDriver;

/**
 * REST resource mapping the DiskQuota {@link DiskQuotaConfig configuration}.
 * <p>
 * Allows GET and PUT methods for JSON and XML formats.
 * <p>
 * 
 * @author groldan
 * 
 */
public class DiskQuotaConfigurationResource extends Resource {

    private DiskQuotaMonitor monitor;

    /**
     * Set by {@link DiskQuotaFinder}
     */
    public void setMonitor(DiskQuotaMonitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public boolean allowGet() {
        return true;
    }

    @Override
    public boolean allowPut() {
        return true;
    }

    @Override
    public void handleGet() {
        final Request request = getRequest();
        final Response response = getResponse();
        final String formatExtension = (String) request.getAttributes().get("extension");
        final DiskQuotaConfig config = monitor.getConfig();

        Representation representation;
        if ("json".equals(formatExtension)) {
            try {
                representation = getJsonRepresentation(config);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        } else if ("xml".equals(formatExtension)) {
            representation = getXmlRepresentation(config);
        } else {
            response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST,
                    "Unknown or missing format extension : " + formatExtension);
            return;
        }

        response.setEntity(representation);
        response.setStatus(Status.SUCCESS_OK);
    }

    @Override
    public void put(final Representation entity) {
        final Request request = getRequest();
        final Response response = getResponse();

        final String formatExtension = (String) request.getAttributes().get("extension");
        DiskQuotaConfig config = monitor.getConfig();
        DiskQuotaConfig newConfig = null;

        try {
            if ("json".equals(formatExtension)) {

                newConfig = fromJSON(entity);
                applyDiff(config, newConfig);
                response.setEntity(getJsonRepresentation(config));

            } else if ("xml".equals(formatExtension)) {

                newConfig = fromXML(entity);
                applyDiff(config, newConfig);
                response.setEntity(getXmlRepresentation(config));

            } else {
                response.setStatus(Status.CLIENT_ERROR_UNSUPPORTED_MEDIA_TYPE);
                return;
            }
        } catch (Exception e) {
            response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST, e.getMessage());
            response.setEntity(e.getMessage(), MediaType.TEXT_PLAIN);
            return;
        }

        monitor.saveConfig();
        response.setStatus(Status.SUCCESS_OK);
    }

    /**
     * Applies the set values in {@code newConfig} (the non null ones) to {@code config}
     * 
     * @param config
     * @param newConfig
     * @throws IllegalArgumentException
     *             as per {@link DiskQuotaConfig#setCacheCleanUpFrequency},
     *             {@link DiskQuotaConfig#setDiskBlockSize},
     *             {@link DiskQuotaConfig#setMaxConcurrentCleanUps} ,
     *             {@link DiskQuotaConfig#setCacheCleanUpUnits}
     */
    private void applyDiff(DiskQuotaConfig config, DiskQuotaConfig newConfig)
            throws IllegalArgumentException {
        // apply diff
        if (newConfig != null) {
            if (null != newConfig.isEnabled()) {
                config.setEnabled(newConfig.isEnabled());
            }
            if (null != newConfig.getCacheCleanUpFrequency()) {
                config.setCacheCleanUpFrequency(newConfig.getCacheCleanUpFrequency());
            }
            if (null != newConfig.getDiskBlockSize()) {
                config.setDiskBlockSize(newConfig.getDiskBlockSize());
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

    @Override
    public Representation getPreferredRepresentation() {
        DiskQuotaConfig config = monitor.getConfig();
        JsonRepresentation jsonRepresentation;
        try {
            jsonRepresentation = getJsonRepresentation(config);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return jsonRepresentation;
    }

    private JsonRepresentation getJsonRepresentation(DiskQuotaConfig config) throws JSONException {
        JsonRepresentation rep = null;
        XStream xs = ConfigLoader.getConfiguredXStream(new GeoWebCacheXStream(
                new JsonHierarchicalStreamDriver()));
        JSONObject obj = new JSONObject(xs.toXML(config));
        rep = new JsonRepresentation(obj);
        return rep;
    }

    private Representation getXmlRepresentation(DiskQuotaConfig config) {
        XStream xStream = ConfigLoader.getConfiguredXStream(new GeoWebCacheXStream());
        String xml = xStream.toXML(config);
        return new StringRepresentation(xml, MediaType.TEXT_XML);
    }

    private DiskQuotaConfig fromXML(Representation entity) throws IOException {

        final String text = entity.getText();
        StringReader reader = new StringReader(text);
        XStream xstream = ConfigLoader.getConfiguredXStream(new GeoWebCacheXStream());
        DiskQuotaConfig diskQuotaConfig = ConfigLoader.loadConfiguration(reader, xstream);
        return diskQuotaConfig;
    }

    private DiskQuotaConfig fromJSON(Representation entity) throws IOException {

        final String text = entity.getText();

        HierarchicalStreamDriver driver = new JettisonMappedXmlDriver();

        XStream xStream = new GeoWebCacheXStream(driver);

        xStream = ConfigLoader.getConfiguredXStream(xStream);

        DiskQuotaConfig configuration;
        StringReader reader = new StringReader(text);
        configuration = ConfigLoader.loadConfiguration(reader, xStream);
        return configuration;
    }
}
