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
 * @author Torben Barsballe (Boundless), 2018
 */
package org.geowebcache.rest.converter;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.copy.HierarchicalStreamCopier;
import com.thoughtworks.xstream.io.json.JettisonMappedXmlDriver;
import com.thoughtworks.xstream.io.json.JsonHierarchicalStreamDriver;
import com.thoughtworks.xstream.io.xml.DomDriver;
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.geowebcache.config.BlobStoreInfo;
import org.geowebcache.config.ContextualConfigurationProvider;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.config.XMLGridSet;
import org.geowebcache.grid.GridSet;
import org.geowebcache.io.GeoWebCacheXStream;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.rest.exception.RestException;
import org.geowebcache.util.ApplicationContextProvider;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.context.WebApplicationContext;

/**
 * Spring MVC Converter for GeoWebCache
 *
 * <p>Supports conversion of {@link BlobStoreInfo}, {@link GridSet}, {@link TileLayer}, {@link ServerConfigurationPOJO},
 * and {@link XStreamListAliasWrapper} containing lists of those classes to and from JSON and XML via XStream
 *
 * @param <T>
 */
public class GWCConverter<T> extends AbstractHttpMessageConverter<T> implements HttpMessageConverter<T> {

    private final WebApplicationContext context;

    public final List<Class> supportedClasses = Collections.unmodifiableList(
            Arrays.asList(BlobStoreInfo.class, GridSet.class, TileLayer.class, ServerConfigurationPOJO.class));

    public GWCConverter(ApplicationContextProvider appCtx) {
        super(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_XML);
        this.context = appCtx.getApplicationContext();
    }

    /**
     * Apply additional global XStream configuration unique to this converter
     *
     * @param xs XStream to configure
     * @return Configured XStream
     */
    private XStream configureXStream(XStream xs) {
        xs.alias("global", ServerConfigurationPOJO.class);
        return xs;
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        for (Class<?> supportedClass : supportedClasses) {
            if (supportedClass.isAssignableFrom(clazz)) {
                return true;
            }
        }
        return XStreamListAliasWrapper.class.isAssignableFrom(clazz);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected T readInternal(Class<? extends T> clazz, HttpInputMessage httpInputMessage)
            throws IOException, HttpMessageNotReadableException {
        MediaType contentType = httpInputMessage.getHeaders().getContentType();

        XStream xs = configureXStream(XMLConfiguration.getConfiguredXStreamWithContext(
                new GeoWebCacheXStream(new DomDriver()), context, ContextualConfigurationProvider.Context.REST));

        T object;
        try {
            if (MediaType.APPLICATION_XML.isCompatibleWith(contentType)
                    || MediaType.TEXT_XML.isCompatibleWith(contentType)) {
                object = (T) xs.fromXML(httpInputMessage.getBody());
            } else if (MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
                HierarchicalStreamDriver driver = new JettisonMappedXmlDriver();
                HierarchicalStreamReader hsr = driver.createReader(httpInputMessage.getBody());
                // See http://jira.codehaus.org/browse/JETTISON-48
                StringWriter writer = new StringWriter();
                new HierarchicalStreamCopier().copy(hsr, new PrettyPrintWriter(writer));
                writer.close();
                object = (T) xs.fromXML(writer.toString());
            } else {
                throw new RestException("Unknown or missing format", HttpStatus.UNSUPPORTED_MEDIA_TYPE);
            }
            if (object instanceof XMLGridSet set) {
                return (T) set.makeGridSet();
            }
            return object;
        } catch (ConversionException xstreamExceptionWrapper) {
            Throwable cause = xstreamExceptionWrapper.getCause();
            if (cause instanceof Error error) {
                throw error;
            }
            if (cause instanceof RuntimeException exception) {
                throw exception;
            }
            if (cause != null) {
                throw new RestException(cause.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, cause);
            } else {
                throw new RestException(
                        xstreamExceptionWrapper.getMessage(),
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        xstreamExceptionWrapper);
            }
        }
    }

    @Override
    protected void writeInternal(T object, HttpOutputMessage httpOutputMessage)
            throws IOException, HttpMessageNotWritableException {
        MediaType contentType = httpOutputMessage.getHeaders().getContentType();

        try (OutputStreamWriter outputWriter = new OutputStreamWriter(httpOutputMessage.getBody())) {
            if (MediaType.APPLICATION_XML.isCompatibleWith(contentType)
                    || MediaType.TEXT_XML.isCompatibleWith(contentType)) {
                XStream xs = new GeoWebCacheXStream();
                Object xsObject = object;

                if (object instanceof XStreamListAliasWrapper aliasWrapper) {
                    final XStreamListAliasWrapper wrapper = aliasWrapper;
                    xsObject = wrapper.object;
                    xs.alias(wrapper.alias + "s", wrapper.collectionClass);
                    xs.registerConverter(wrapper.createConverter());
                } else if (object instanceof GridSet set) {
                    xsObject = new XMLGridSet(set);
                }

                xs = configureXStream(XMLConfiguration.getConfiguredXStreamWithContext(
                        xs, context, ContextualConfigurationProvider.Context.REST));
                String xmlText = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + xs.toXML(xsObject);

                outputWriter.write(xmlText);
            } else if (MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
                XStream xs = configureXStream(XMLConfiguration.getConfiguredXStreamWithContext(
                        new GeoWebCacheXStream(new JsonHierarchicalStreamDriver()),
                        context,
                        ContextualConfigurationProvider.Context.REST));
                Object jsonObject;
                if (object instanceof XStreamListAliasWrapper wrapper) {
                    jsonObject = new JSONArray(wrapper.object);
                } else if (object instanceof GridSet set) {
                    jsonObject = new JSONObject(xs.toXML(new XMLGridSet(set)));
                } else {
                    jsonObject = new JSONObject(xs.toXML(object));
                }

                outputWriter.write(jsonObject.toString());
            } else {
                throw new RestException("Unknown or missing format", HttpStatus.UNSUPPORTED_MEDIA_TYPE);
            }
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }
}
