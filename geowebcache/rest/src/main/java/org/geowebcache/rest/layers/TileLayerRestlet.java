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
 * @author Marius Suta / The Open Planning Project 2008 
 * @author Arne Kepp / The Open Planning Project 2009 
 */
package org.geowebcache.rest.layers;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.Configuration;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.rest.GWCRestlet;
import org.geowebcache.rest.RestletException;
import org.geowebcache.rest.XstreamRepresentation;
import org.geowebcache.service.HttpErrorCodeException;
import org.geowebcache.util.ServletUtils;
import org.geowebcache.util.URLMangler;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.data.CharacterSet;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.resource.Representation;
import org.restlet.resource.StringRepresentation;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.copy.HierarchicalStreamCopier;
import com.thoughtworks.xstream.io.json.JettisonMappedXmlDriver;
import com.thoughtworks.xstream.io.json.JsonHierarchicalStreamDriver;
import com.thoughtworks.xstream.io.xml.DomDriver;
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;

/**
 * This is the TileLayer resource class required by the REST interface
 */
public class TileLayerRestlet extends GWCRestlet {

    private XMLConfiguration xmlConfig;

    private TileLayerDispatcher layerDispatcher;

    private URLMangler urlMangler;

    // set by spring
    public void setUrlMangler(URLMangler urlMangler) {
        this.urlMangler = urlMangler;
    }

    @Override
    public void handle(Request request, Response response) {
        Method met = request.getMethod();
        try {
            if (met.equals(Method.GET)) {
                doGet(request, response);
            } else {
                // These modify layers, so we reload afterwards
                if (met.equals(Method.POST)) {
                    doPost(request, response);
                } else if (met.equals(Method.PUT)) {
                    doPut(request, response);
                } else if (met.equals(Method.DELETE)) {
                    doDelete(request, response);
                } else {
                    throw new RestletException("Method not allowed",
                            Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
                }
            }
        } catch (RestletException re) {
            response.setEntity(re.getRepresentation());
            response.setStatus(re.getStatus());
        } catch (HttpErrorCodeException httpException) {
            int errorCode = httpException.getErrorCode();
            Status status = Status.valueOf(errorCode);
            response.setStatus(status);
            response.setEntity(httpException.getMessage(), MediaType.TEXT_PLAIN);
        } catch (Exception e) {
            // Either GeoWebCacheException or IOException
            response.setEntity(e.getMessage() + " " + e.toString(), MediaType.TEXT_PLAIN);
            response.setStatus(Status.SERVER_ERROR_INTERNAL);
            e.printStackTrace();
        }
    }

    /**
     * GET outputs an existing layer
     * 
     * @param req
     * @param resp
     * @throws RestletException
     * @throws
     */
    protected void doGet(Request req, Response resp) throws RestletException {
        String layerName = (String) req.getAttributes().get("layer");
        final String formatExtension = (String) req.getAttributes().get("extension");

        Representation representation;
        if (layerName == null) {
            String restRoot = req.getRootRef().toString();
            String contextPath = req.getRootRef().getPath();
            String baseURL = restRoot.substring(0, restRoot.length() - contextPath.length());
            String prefix = req.getResourceRef().getParentRef().getPath();
            representation = listLayers(formatExtension, baseURL, prefix);
        } else {
            try {
                layerName = URLDecoder.decode(layerName, "UTF-8");
            } catch (UnsupportedEncodingException uee) {
            }
            representation = doGetInternal(layerName, formatExtension);
        }
        resp.setEntity(representation);
    }

    /**
     * @param extension
     * @param rootPath
     * @param contextPath
     * @return
     */
    Representation listLayers(String extension, final String rootPath, final String contextPath) {

        if (null == extension) {
            extension = "xml";
        }
        List<String> layerNames = new ArrayList<String>(layerDispatcher.getLayerNames());
        Collections.sort(layerNames);

        Representation representation;
        if (extension.equalsIgnoreCase("xml")) {

            representation = new XstreamRepresentation(layerNames);
            representation.setCharacterSet(CharacterSet.UTF_8);

            final XStream xStream = ((XstreamRepresentation) representation).getXStream();
            xStream.alias("layers", List.class);

            xmlConfig.getConfiguredXStream(xStream);

            xStream.registerConverter(new Converter() {

                /**
                 * @see com.thoughtworks.xstream.converters.ConverterMatcher#canConvert(java.lang.Class)
                 */
                public boolean canConvert(@SuppressWarnings("rawtypes") Class type) {
                    return List.class.isAssignableFrom(type);
                }

                /**
                 * @see com.thoughtworks.xstream.converters.Converter#marshal(java.lang.Object,
                 *      com.thoughtworks.xstream.io.HierarchicalStreamWriter,
                 *      com.thoughtworks.xstream.converters.MarshallingContext)
                 */
                public void marshal(Object source, HierarchicalStreamWriter writer,
                        MarshallingContext context) {

                    @SuppressWarnings("unchecked")
                    List<String> layers = (List<String>) source;

                    for (String name : layers) {

                        writer.startNode("layer");

                        writer.startNode("name");
                        writer.setValue(name);
                        writer.endNode(); // name

                        writer.startNode("atom:link");
                        writer.addAttribute("xmlns:atom", "http://www.w3.org/2005/Atom");
                        writer.addAttribute("rel", "alternate");
                        String href = urlMangler.buildURL(rootPath, contextPath, "/layers/"
                                + ServletUtils.URLEncode(name) + ".xml");
                        writer.addAttribute("href", href);
                        writer.addAttribute("type", MediaType.TEXT_XML.toString());

                        writer.endNode();

                        writer.endNode();// layer
                    }
                }

                /**
                 * @see com.thoughtworks.xstream.converters.Converter#unmarshal(com.thoughtworks.xstream.io.HierarchicalStreamReader,
                 *      com.thoughtworks.xstream.converters.UnmarshallingContext)
                 */
                public Object unmarshal(HierarchicalStreamReader reader,
                        UnmarshallingContext context) {
                    throw new UnsupportedOperationException();
                }
            });
        } else if (extension.equalsIgnoreCase("html")) {
            throw new RestletException("Unknown or missing format extension : " + extension,
                    Status.CLIENT_ERROR_BAD_REQUEST);
        } else {
            throw new RestletException("Unknown or missing format extension : " + extension,
                    Status.CLIENT_ERROR_BAD_REQUEST);
        }

        return representation;
    }

    /**
     * We separate out the internal to make unit testing easier
     * 
     * @param layerName
     * @param formatExtension
     * @return
     * @throws RestletException
     */
    protected Representation doGetInternal(String layerName, String formatExtension)
            throws RestletException {
        TileLayer tl = findTileLayer(layerName, layerDispatcher);

        if (formatExtension.equalsIgnoreCase("xml")) {
            return getXMLRepresentation(tl);
        } else if (formatExtension.equalsIgnoreCase("json")) {
            return getJsonRepresentation(tl);
        } else {
            throw new RestletException("Unknown or missing format extension : " + formatExtension,
                    Status.CLIENT_ERROR_BAD_REQUEST);
        }
    }

    /**
     * POST overwrites an existing layer
     * 
     * @param req
     * @param resp
     * @throws RestletException
     */
    private void doPost(Request req, Response resp) throws RestletException, IOException,
            GeoWebCacheException {
        TileLayer tl = deserializeAndCheckLayer(req, resp, false);

        try {
            Configuration configuration = layerDispatcher.modify(tl);
            configuration.save();
        } catch (IllegalArgumentException e) {
            throw new RestletException("Layer " + tl.getName()
                    + " is not known by the configuration."
                    + "Maybe it was loaded from another source, or you're trying to add a new "
                    + "layer and need to do an HTTP PUT ?", Status.CLIENT_ERROR_BAD_REQUEST);
        }
    }

    /**
     * PUT creates a new layer
     * 
     * @param req
     * @param resp
     * @throws RestletException
     */
    private void doPut(Request req, Response resp) throws RestletException, IOException,
            GeoWebCacheException {
        TileLayer tl = deserializeAndCheckLayer(req, resp, true);

        TileLayer testtl = null;
        try {
            testtl = findTileLayer(tl.getName(), layerDispatcher);
        } catch (RestletException re) {
            // This is the expected behavior, it should not exist
        }

        if (testtl == null) {
            Configuration config = layerDispatcher.addLayer(tl);
            config.save();
        } else {
            throw new RestletException("Layer with name " + tl.getName() + " already exists, "
                    + "use POST if you want to replace it.", Status.CLIENT_ERROR_BAD_REQUEST);
        }
    }

    /**
     * DELETE removes an existing layer
     * 
     * @param req
     * @param resp
     * @throws RestletException
     */
    private void doDelete(Request req, Response resp) throws RestletException, GeoWebCacheException {
        String layerName = (String) req.getAttributes().get("layer");
        findTileLayer(layerName, layerDispatcher);
        try {
            Configuration configuration = layerDispatcher.removeLayer(layerName);
            if (configuration == null) {
                throw new RestletException("Configuration to remove layer not found",
                        Status.SERVER_ERROR_INTERNAL);
            }
            configuration.save();
        } catch (IOException e) {
            throw new RestletException(e.getMessage(), Status.SERVER_ERROR_INTERNAL, e);
        }
    }

    protected TileLayer deserializeAndCheckLayer(Request req, Response resp, boolean isPut)
            throws RestletException, IOException {
        // TODO UTF-8 may not always be right here
        String layerName = ServletUtils.URLDecode((String) req.getAttributes().get("layer"),
                "UTF-8");
        String formatExtension = (String) req.getAttributes().get("extension");
        InputStream is = req.getEntity().getStream();

        // If appropriate, check whether this layer exists
        if (!isPut) {
            findTileLayer(layerName, layerDispatcher);
        }

        return deserializeAndCheckLayerInternal(layerName, formatExtension, is);
    }

    /**
     * We separate out the internal to make unit testing easier
     * 
     * @param layerName
     * @param formatExtension
     * @param is
     * @return
     * @throws RestletException
     * @throws IOException
     */
    protected TileLayer deserializeAndCheckLayerInternal(String layerName, String formatExtension,
            InputStream is) throws RestletException, IOException {

        XStream xs = xmlConfig.getConfiguredXStream(new XStream(new DomDriver()));

        TileLayer newLayer;

        try {
            if (formatExtension.equalsIgnoreCase("xml")) {
                newLayer = (TileLayer) xs.fromXML(is);
            } else if (formatExtension.equalsIgnoreCase("json")) {
                HierarchicalStreamDriver driver = new JettisonMappedXmlDriver();
                HierarchicalStreamReader hsr = driver.createReader(is);
                // See http://jira.codehaus.org/browse/JETTISON-48
                StringWriter writer = new StringWriter();
                new HierarchicalStreamCopier().copy(hsr, new PrettyPrintWriter(writer));
                writer.close();
                newLayer = (TileLayer) xs.fromXML(writer.toString());
            } else {
                throw new RestletException("Unknown or missing format extension: "
                        + formatExtension, Status.CLIENT_ERROR_BAD_REQUEST);
            }
        } catch (ConversionException xstreamExceptionWrapper) {
            Throwable cause = xstreamExceptionWrapper.getCause();
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RestletException(cause.getMessage(), Status.SERVER_ERROR_INTERNAL,
                    (Exception) cause);
        }

        if (!newLayer.getName().equals(layerName)) {
            throw new RestletException("There is a mismatch between the name of the "
                    + " layer in the submission and the URL you specified.",
                    Status.CLIENT_ERROR_BAD_REQUEST);
        }

        return newLayer;
    }

    /**
     * Returns a XMLRepresentation of the layer
     * 
     * @param layer
     * @return
     */
    public Representation getXMLRepresentation(TileLayer layer) {
        XStream xs = xmlConfig.getConfiguredXStream(new XStream());
        String xmlText = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + xs.toXML(layer);

        return new StringRepresentation(xmlText, MediaType.TEXT_XML);
    }

    /**
     * Returns a JsonRepresentation of the layer
     * 
     * @param layer
     * @return
     */
    public JsonRepresentation getJsonRepresentation(TileLayer layer) {
        JsonRepresentation rep = null;
        try {
            XStream xs = xmlConfig.getConfiguredXStream(new XStream(
                    new JsonHierarchicalStreamDriver()));
            JSONObject obj = new JSONObject(xs.toXML(layer));
            rep = new JsonRepresentation(obj);
        } catch (JSONException jse) {
            jse.printStackTrace();
        }
        return rep;
    }

    public void setTileLayerDispatcher(TileLayerDispatcher tileLayerDispatcher) {
        layerDispatcher = tileLayerDispatcher;
    }

    public void setXMLConfiguration(XMLConfiguration xmlConfig) {
        this.xmlConfig = xmlConfig;
    }
}
