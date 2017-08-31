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
 * @author David Vick, Boundless, Copyright 2017
 *
 * Original file
 * TileLayerRestlet.java
 *
 */

package org.geowebcache.rest.controller;

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
import org.geowebcache.GeoWebCacheDispatcher;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.Configuration;
import org.geowebcache.config.ContextualConfigurationProvider.Context;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.io.GeoWebCacheXStream;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.rest.exception.RestException;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;
import org.geowebcache.util.NullURLMangler;
import org.geowebcache.util.ServletUtils;
import org.geowebcache.util.URLMangler;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@RestController
@RequestMapping(path="${gwc.context.suffix:}/rest")
public class TileLayerController extends GWCController {

    @Autowired
    TileLayerDispatcher layerDispatcher;

    private URLMangler urlMangler = new NullURLMangler();

    private GeoWebCacheDispatcher controller = null;

    @Autowired
    private StorageBroker storageBroker;

    @Autowired
    private XMLConfiguration xmlConfig;

    @ExceptionHandler(RestException.class)
    public ResponseEntity<?> handleRestException(RestException ex) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        return new ResponseEntity<Object>(ex.toString(), headers, ex.getStatus());
    }

    // set by spring
    public void setUrlMangler(URLMangler urlMangler) {
        this.urlMangler = urlMangler;
    }

    // set by spring
    public void setController(GeoWebCacheDispatcher controller) {
        this.controller = controller;
    }

    // set by spring
    public void setStorageBroker(StorageBroker storageBroker) {
        this.storageBroker = storageBroker;
    }

    /*
    DO GET
     */

    /**
     * Get List of layers as xml
     * @param request
     * @return
     */
    @RequestMapping(value = "/layers", method = RequestMethod.GET)
    public ResponseEntity<?> doGet(HttpServletRequest request) {
        if (request.getPathInfo().contains("json")) {
            return listLayers("json", request.getScheme() + "://" + request.getServerName()
                    + ":" + request.getServerPort(), request.getContextPath() + request.getServletPath());
        } else {
            return listLayers(null, request.getScheme() + "://" + request.getServerName()
                    + ":" + request.getServerPort(), request.getContextPath() + request.getServletPath());
        }
    }

    /**
     * Get List of layers as {xml, json}
     * @param request
     * @param response
     * @param extension
     * @return
     */
//    @RequestMapping(value = "/layers.{extension}", method = RequestMethod.GET)
//    public ResponseEntity<?> doGet(HttpServletRequest request, HttpServletResponse response,
//                                   @PathVariable String extension) {
//        return listLayers(extension, request.getServerName(), request.getContextPath());
//    }

    /**
     * Get layer by name and requested output {xml, json}
     * @param request
     * @param response
     * @param layer
     * @param extension
     * @return
     */
    @RequestMapping(value = "/layers/{layer}.{extension}", method = RequestMethod.GET)
    public ResponseEntity<?> doGet(HttpServletRequest request, HttpServletResponse response,
                                   @PathVariable String layer,
                                   @PathVariable String extension) {
        return doGetInternal(layer, extension);
    }

    /*
    DO POST
     */
    @RequestMapping(value = "/layers/{layer}.{extension}", method = RequestMethod.POST)
    public ResponseEntity<?> doPost(HttpServletRequest req, HttpServletResponse resp,
                                    @PathVariable String layer,
                                    @PathVariable String extension) throws GeoWebCacheException,
            RestException, IOException {
        TileLayer tl = deserializeAndCheckLayer(req, layer, extension, false);

        try {
            Configuration configuration = layerDispatcher.modify(tl);
            configuration.save();
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<Object>("Layer " + tl.getName()
                    + " is not known by the configuration."
                    + "Maybe it was loaded from another source, or you're trying to add a new "
                    + "layer and need to do an HTTP PUT ?", HttpStatus.BAD_REQUEST);
        }
        return null;
    }

    /*
    DO PUT
     */
    @RequestMapping(value = "/layers/{layer}.{extension}", method = RequestMethod.PUT)
    public ResponseEntity<?> doPut(HttpServletRequest req, HttpServletResponse resp,
                                   @PathVariable String layer,
                                   @PathVariable String extension) throws GeoWebCacheException,
            RestException, IOException {
        TileLayer tl = deserializeAndCheckLayer(req, layer, extension, true);

        TileLayer testtl = null;
        try {
            testtl = findTileLayer(tl.getName(), layerDispatcher);
        } catch (RestException re) {
            // This is the expected behavior, it should not exist
        }

        if (testtl == null) {
            Configuration config = layerDispatcher.addLayer(tl);
            config.save();
        } else {
            throw new RestException("Layer with name " + tl.getName() + " already exists, "
                    + "use POST if you want to replace it.", HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<Object>("layer saved", HttpStatus.OK);
    }

    /*
    DO DELETE
     */
    @RequestMapping(value = "/layers/{layer}", method = RequestMethod.DELETE)
    public ResponseEntity<?> doDelete(HttpServletRequest req,
                                      @PathVariable String layer) throws GeoWebCacheException,
            RestException, IOException {
        String layerName = layer;
        findTileLayer(layerName, layerDispatcher);
        // TODO: refactor storage management to use a comprehensive event system;
        // centralise duplicate functionality from GeoServer gs-gwc GWC.layerRemoved
        // and CatalogConfiguration.removeLayer into GeoWebCache and use event system
        // to ensure removal and rename operations are atomic and consistent. Until this
        // is done, the following is a temporary workaround:
        //
        // delete cached tiles first in case a blob store
        // uses the configuration to perform the deletion
        StorageException storageBrokerDeleteException = null;
        try {
            storageBroker.delete(layerName);
        } catch (StorageException se) {
            // save exception for later so failure to delete
            // cached tiles does not prevent layer removal
            storageBrokerDeleteException = se;
        }
        try {
            Configuration configuration = layerDispatcher.removeLayer(layerName);
            if (configuration == null) {
                throw new RestException("Configuration to remove layer not found",
                        HttpStatus.INTERNAL_SERVER_ERROR);
            }
            configuration.save();
        } catch (IOException e) {
            throw new RestException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
        if (storageBrokerDeleteException != null) {
            // layer removal worked, so report failure to delete cached tiles
            throw new RestException(
                    "Removal of layer " + layerName
                            + " was successful but deletion of cached tiles failed: "
                            + storageBrokerDeleteException.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR, storageBrokerDeleteException);
        }
        return new ResponseEntity<Object>(layerName + " deleted", HttpStatus.OK);
    }

    protected TileLayer deserializeAndCheckLayer(HttpServletRequest req, String layer, String extension, boolean isPut)
            throws RestException, IOException {

        // TODO UTF-8 may not always be right here
        String layerName = layer;
        String formatExtension = extension;
        InputStream is = req.getInputStream();

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
     * @return
     * @throws RestException
     */
    public ResponseEntity<? extends Object> doGetInternal(String layerName, String formatExtension)
            throws RestException {
        TileLayer tl = findTileLayer(layerName, layerDispatcher);

        if (formatExtension.equalsIgnoreCase("xml")) {
            return getXMLRepresentation(tl);
        } else if (formatExtension.equalsIgnoreCase("json")) {
            return getJsonRepresentation(tl);
        } else {
            throw new RestException("Unknown or missing format extension : " + formatExtension,
                    HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * We separate out the internal to make unit testing easier
     *
     * @param layerName
     * @param formatExtension
     * @param is
     * @return
     * @throws RestException
     * @throws IOException
     */
    protected TileLayer deserializeAndCheckLayerInternal(String layerName, String formatExtension,
                                                         InputStream is) throws RestException, IOException {

        XStream xs = xmlConfig.getConfiguredXStreamWithContext(new GeoWebCacheXStream(new DomDriver()), Context.REST);

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
                throw new RestException("Unknown or missing format extension: "
                        + formatExtension, HttpStatus.BAD_REQUEST);
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
                throw new RestException(cause.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                        (Exception) cause);
            } else {
                throw new RestException(xstreamExceptionWrapper.getMessage(),
                        HttpStatus.INTERNAL_SERVER_ERROR, xstreamExceptionWrapper);
            }
        }

        if (!newLayer.getName().equals(layerName)) {
            throw new RestException("There is a mismatch between the name of the "
                    + " layer in the submission and the URL you specified.",
                    HttpStatus.BAD_REQUEST);
        }

        // Check that the parameter filters deserialized correctly
        if(newLayer.getParameterFilters()!=null) {
            try {
                for(@SuppressWarnings("unused")
                        ParameterFilter filter: newLayer.getParameterFilters()){
                    // Don't actually need to do anything here.  Just iterate over the elements
                    // casting them into ParameterFilter
                }
            } catch (ClassCastException ex) {
                // By this point it has already been turned into a POJO, so the XML is no longer
                // available.  Otherwise it would be helpful to include in the error message.
                throw new RestException("parameterFilters contains an element that is not "+
                        "a known ParameterFilter", HttpStatus.BAD_REQUEST);
            }
        }
        return newLayer;
    }

    /**
     * @param extension
     * @param rootPath
     * @param contextPath
     * @return
     */
    public ResponseEntity<?> listLayers(String extension, final String rootPath, final String contextPath) {

        XStream xStream = new XStream();
        if (null == extension) {
            extension = "xml";
        }
        List<String> layerNames = new ArrayList<String>(layerDispatcher.getLayerNames());
        Collections.sort(layerNames);

        HttpHeaders headers = new HttpHeaders();
        if (extension.equalsIgnoreCase("xml")) {
            headers.setContentType(MediaType.APPLICATION_XML);
        } else {
            headers.setContentType(MediaType.APPLICATION_JSON_UTF8);
        }

        if (extension.equalsIgnoreCase("xml")) {

            xStream.alias("layers", List.class);
            xStream.alias("name", String.class);

            xmlConfig.getConfiguredXStreamWithContext(xStream, Context.REST);

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
            return new ResponseEntity<Object>(xStream.toXML(layerNames), headers, HttpStatus.OK);
        } else if (extension.equalsIgnoreCase("html")) {
            throw new RestException("Unknown or missing format extension : " + extension,
                    HttpStatus.BAD_REQUEST);
        } else if (!extension.equalsIgnoreCase("json")) {
            throw new RestException("Unknown or missing format extension : " + extension,
                    HttpStatus.BAD_REQUEST);
        }

        return new ResponseEntity<Object>(layerNames, headers, HttpStatus.OK);
    }

    /**
     * Returns a XMLRepresentation of the layer
     *
     * @param layer
     * @return
     */
    public ResponseEntity<?> getXMLRepresentation(TileLayer layer) {
        XStream xs = xmlConfig.getConfiguredXStreamWithContext(new GeoWebCacheXStream(), Context.REST);
        String xmlText = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + xs.toXML(layer);

        return new ResponseEntity(xmlText, HttpStatus.OK);
    }

    /**
     * Returns a JsonRepresentation of the layer
     *
     * @param layer
     * @return
     */
    public ResponseEntity<?> getJsonRepresentation(TileLayer layer) {
        JSONObject rep = null;
        try {
            XStream xs = xmlConfig.getConfiguredXStreamWithContext(new GeoWebCacheXStream(
                    new JsonHierarchicalStreamDriver()), Context.REST);
            JSONObject obj = new JSONObject(xs.toXML(layer));
            rep = obj;
        } catch (JSONException jse) {
            return new ResponseEntity<Object>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity(rep, HttpStatus.OK);
    }

    public void setTileLayerDispatcher(TileLayerDispatcher tileLayerDispatcher) {
        layerDispatcher = tileLayerDispatcher;
    }

    public void setXMLConfiguration(XMLConfiguration xmlConfig) {
        this.xmlConfig = xmlConfig;
    }

}
