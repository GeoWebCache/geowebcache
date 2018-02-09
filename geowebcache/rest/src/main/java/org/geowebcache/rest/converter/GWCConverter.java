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
import org.geowebcache.config.BlobStoreInfo;
import org.geowebcache.config.ContextualConfigurationProvider;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.grid.GridSet;
import org.geowebcache.io.GeoWebCacheXStream;
import org.geowebcache.rest.exception.RestException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
public class GWCConverter<T> extends AbstractHttpMessageConverter<T>
        implements HttpMessageConverter<T> {

    @Value("${gwc.context.suffix:}")
    private String gwcPrefix;

    @Autowired
    private XMLConfiguration xmlConfig;

    public final List<Class> supportedClasses = Collections.unmodifiableList(Arrays.asList(BlobStoreInfo.class, GridSet.class));

    public GWCConverter() {
        super(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_XML);
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
    protected T readInternal(Class<? extends T> clazz, HttpInputMessage httpInputMessage) throws IOException, HttpMessageNotReadableException {
        MediaType contentType = httpInputMessage.getHeaders().getContentType();

        XStream xs = xmlConfig.getConfiguredXStreamWithContext(new GeoWebCacheXStream(new DomDriver()), ContextualConfigurationProvider.Context.REST);

        try {
            if (MediaType.APPLICATION_XML.isCompatibleWith(contentType) || MediaType.TEXT_XML.isCompatibleWith(contentType)) {
                return (T)  xs.fromXML(httpInputMessage.getBody());
            } else if (MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
                HierarchicalStreamDriver driver = new JettisonMappedXmlDriver();
                HierarchicalStreamReader hsr = driver.createReader(httpInputMessage.getBody());
                // See http://jira.codehaus.org/browse/JETTISON-48
                StringWriter writer = new StringWriter();
                new HierarchicalStreamCopier().copy(hsr, new PrettyPrintWriter(writer));
                writer.close();
                return (T) xs.fromXML(writer.toString());
            } else {
                throw new RestException("Unknown or missing format", HttpStatus.UNSUPPORTED_MEDIA_TYPE);
            }
        } catch (ConversionException xstreamExceptionWrapper) {
            Throwable cause = xstreamExceptionWrapper.getCause();
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause!=null){
                throw new RestException(cause.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, cause);
            } else {
                throw new RestException(xstreamExceptionWrapper.getMessage(),
                        HttpStatus.INTERNAL_SERVER_ERROR, xstreamExceptionWrapper);
            }
        }
    }

    @Override
    protected void writeInternal(T object, HttpOutputMessage httpOutputMessage) throws IOException, HttpMessageNotWritableException {
        MediaType contentType = httpOutputMessage.getHeaders().getContentType();

        try (OutputStreamWriter outputWriter = new OutputStreamWriter(httpOutputMessage.getBody())) {
            if (MediaType.APPLICATION_XML.isCompatibleWith(contentType) || MediaType.TEXT_XML.isCompatibleWith(contentType)) {
                XStream xs = new GeoWebCacheXStream();
                Object xsObject = object;


                if (object instanceof XStreamListAliasWrapper) {
                    final XStreamListAliasWrapper wrapper = ((XStreamListAliasWrapper)object);
                    xsObject = wrapper.object;
                    xs.alias(wrapper.alias+"s", wrapper.collectionClass);
                    xs.registerConverter(wrapper.createConverter(gwcPrefix));
                }

                xs = xmlConfig.getConfiguredXStreamWithContext(xs, ContextualConfigurationProvider.Context.REST);
                String xmlText = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + xs.toXML(xsObject);

                outputWriter.write(xmlText);
            } else if (MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
                XStream xs = xmlConfig.getConfiguredXStreamWithContext(new GeoWebCacheXStream(
                        new JsonHierarchicalStreamDriver()), ContextualConfigurationProvider.Context.REST);
                Object jsonObject;
                if (object instanceof XStreamListAliasWrapper) {
                    jsonObject = new JSONArray(((XStreamListAliasWrapper) object).object);
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
