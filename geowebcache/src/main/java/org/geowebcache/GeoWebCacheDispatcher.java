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
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 */

package org.geowebcache;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.layer.TileRequest;
import org.geowebcache.layer.TileResponse;
import org.geowebcache.seeder.SeederDispatcher;
import org.geowebcache.service.Service;
import org.geowebcache.service.ServiceRequest;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.AbstractController;

/**
 * This is the main router for requests of all types.
 */
public class GeoWebCacheDispatcher extends AbstractController {
    private static Log log = LogFactory
            .getLog(org.geowebcache.GeoWebCacheDispatcher.class);

    public static final String TYPE_SERVICE = "service";

    public static final String TYPE_SEED = "seed";

    public static final String TYPE_TRUNCATE = "truncate";
    
    WebApplicationContext context = null;

    private String servletPrefix = null;

    private TileLayerDispatcher tileLayerDispatcher = null;

    private SeederDispatcher seederDispatcher = null;

    private HashMap services = null;

    public GeoWebCacheDispatcher() {
        super();
    }

    /**
     * Setter method for Spring. TileLayerDispatcher is a class for looking up
     * TileLayer objects based on the name of the layer.
     * 
     * @param tileLayerDispatcher
     *            a class for looking up TileLayer objects
     */
    public void setTileLayerDispatcher(TileLayerDispatcher tileLayerDispatcher) {
        this.tileLayerDispatcher = tileLayerDispatcher;
        log.info("set TileLayerDispatcher");
    }

    /**
     * Setter method for Spring. SeederDispatcher sets up seeding etc.
     * 
     * @param seederDispatcher
     */
    public void setSeederDispatcher(SeederDispatcher seederDispatcher) {
        this.seederDispatcher = seederDispatcher;
        log.info("set SeederDispatcher");
    }

    /**
     * GeoServer and other solutions that embedd this dispatcher will prepend a
     * path, this is used to remove it.
     * 
     * @param servletPrefix
     */
    public void setServletPrefix(String servletPrefix) {
        this.servletPrefix = servletPrefix;
    }

    /**
     * Services convert HTTP requests into the internal grid representation and
     * specify what layer the response should come from.
     * 
     * The classpath is scanned for objects extending Service, thereby making it
     * easy to add new services.
     */
    private void loadServices() {
        Map serviceBeans = context.getBeansOfType(Service.class);
        Iterator beanIter = serviceBeans.keySet().iterator();
        services = new HashMap();
        while (beanIter.hasNext()) {
            Service aService = (Service) serviceBeans.get(beanIter.next());
            services.put(aService.getPathName(), aService);
        }
    }

    /**
     * Spring function for MVC, this is the entry point for the application.
     * 
     * If a tile is requested the request will be handed off to
     * handleServiceRequest.
     * 
     */
    protected ModelAndView handleRequestInternal(HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        context = (WebApplicationContext) getApplicationContext();

        // Break the request into components, {type, service name}
        String[] requestComps = null;
        try {
            requestComps = parseRequest(request.getRequestURI());
        } catch (GeoWebCacheException gwce) {
            writeError(response, 400, gwce.getMessage());
            return null;
        }

        try {
            if (requestComps[0].equalsIgnoreCase(TYPE_SERVICE)) {

                handleServiceRequest(requestComps[1], request, response);

            } else if (requestComps[0].equalsIgnoreCase(TYPE_SEED)) {
                handleSeedRequest(request, response);
            } else if (requestComps[0].equalsIgnoreCase(TYPE_TRUNCATE)) {
                handleTruncateRequest(request, response);
            } else {
                writeError(response, 404, "Unknow path: " + requestComps[0]);
            }
        } catch (GeoWebCacheException gwce) {
            // e.printStackTrace();
            writeError(response, 400, gwce.getMessage());
        }
        return null;
    }

    /**
     * Essentially this slices away the prefix, leaving type and request
     * 
     * @param servletPath
     * @return {type, service}ervletPrefix
     */
    private String[] parseRequest(String servletPath)
            throws GeoWebCacheException {
        String[] retStrs = new String[2];
        String[] splitStr = servletPath.split("/");
        // This string should start with / , so the first hit will be ""

        if (servletPrefix != null) {
            if (log.isDebugEnabled()) {
                if (splitStr[0].equals(servletPrefix)) {
                    log.info("Matched servlet prefix " + servletPrefix
                            + " to request");
                } else {
                    log.error("Servlet prefix " + servletPrefix
                            + " does not match " + splitStr[0]);
                }
            }
            if (splitStr.length < 4) {
                throw new GeoWebCacheException("Unable to parse " + servletPath
                        + " given prefix " + servletPrefix);
            }
            retStrs[0] = new String(splitStr[3]);
            if(splitStr.length > 4) {
                retStrs[1] = new String(splitStr[4]);
            }
        } else {
            if (splitStr.length < 3) {
                throw new GeoWebCacheException("Unable to parse " + servletPath);
            }
            if (splitStr.length == 3) {
                retStrs[0] = new String(splitStr[2]);
            } else {
                retStrs[0] = new String(splitStr[2]);
                retStrs[1] = new String(splitStr[3]);
            }
        }
        return retStrs;
    }

    /**
     * This is the main method for handling service requests. See comments in
     * the code.
     * 
     * @param request
     * @param response
     * @throws Exception
     */
    private void handleServiceRequest(String serviceStr,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {

        // 1) Figure out what Service should handle this request
        Service service = findService(serviceStr);

        // 2) Find out what layer will be used and how
        ServiceRequest servReq = service.getServiceRequest(request);

        // 3) Get the configuration that has to respond to this request
        TileLayer layer = tileLayerDispatcher.getTileLayer(servReq
                .getLayerIdent());
        
        if(layer == null) {
            throw new GeoWebCacheException("Unknown layer "+servReq.getLayerIdent());
        }

        // Check where this should be dispatched
        if (servReq.getFlag(ServiceRequest.SERVICE_REQUEST_DIRECT)) {
            // B4 The service object takes it from here
            service.handleRequest(layer, request, servReq, response);
        } else {
            // A4) Convert to internal representation, using info from request
            // and layer
            TileRequest tileRequest = service.getTileRequest(layer, servReq, request);

            // Kepp the URI 
            tileRequest.requestURI = request.getRequestURI();
            
            // A5) Ask the layer to provide the tile
            TileResponse tileResponse = layer.getResponse(tileRequest, servReq, response);

            // A6) Write response
            writeData(response, tileResponse);
        }
    }

    /**
     * This is the main method for handling seeding requests.
     * 
     * @param request
     * @param response
     * @throws Exception
     */
    private void handleSeedRequest(HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        String layerIdent = seederDispatcher.getLayerIdent(request);

        TileLayer layer = tileLayerDispatcher.getTileLayer(layerIdent);

        if (layer == null) {
            throw new GeoWebCacheException("Layer "+layerIdent+" is not known");
        }

        seederDispatcher.handleSeed(layer, request, response);
    }
    
    /**
     * This is the main method for handling truncate requests.
     * 
     * @param request
     * @param response
     * @throws Exception
     */
    private void handleTruncateRequest(HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        String layerIdent = seederDispatcher.getLayerIdent(request);

        TileLayer layer = tileLayerDispatcher.getTileLayer(layerIdent);

        if (layer == null) {
            throw new GeoWebCacheException("Layer " + layerIdent
                    + " is not known.");
        }

        seederDispatcher.handleTruncate(layer, request, response);
    }

    /**
     * Helper function for looking up the service that should handle the
     * request.
     * 
     * @param request
     *            full HttpServletRequest
     * @return
     */
    private Service findService(String serviceStr) throws GeoWebCacheException {
        if (this.services == null)
            loadServices();

        // E.g. /wms/test -> /wms
        Service service = (Service) services.get(serviceStr);
        if (service == null) {
            throw new GeoWebCacheException(
                    "Unable to find handler for service " + serviceStr);
        }
        return service;
    }

    /**
     * Wrapper method for writing an error back to the client, and logging it at
     * the same time.
     * 
     * @param response
     *            where to write to
     * @param httpCode
     *            the HTTP code to provide
     * @param errorMsg
     *            the actual error message, human readable
     */
    private void writeError(HttpServletResponse response, int httpCode,
            String errorMsg) {

        response.setContentType("text/plain");
        response.setStatus(httpCode);

        if (errorMsg == null) {
            return;
        }

        log.debug(errorMsg);

        try {
            OutputStream os = response.getOutputStream();
            os.write(errorMsg.getBytes());
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    /**
     * Happy ending, sets the headers and writes the response back to the
     * client.
     * 
     * @param response
     *            where to write to
     * @param tileResponse
     *            the response with the data and MIME type
     * @throws IOException
     */
    private void writeData(HttpServletResponse response,
            TileResponse tileResponse) throws IOException {

        // Did we get anything?
        if (tileResponse == null || tileResponse.data == null
                || tileResponse.data.length == 0) {
            log.trace("sendData() had nothing to return");

            // Response: 500 , should not have gotten here
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        log.trace("sendData() Sending data.");
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(tileResponse.mimeType.getMimeType());
        response.setContentLength(tileResponse.data.length);
        try {
            OutputStream os = response.getOutputStream();
            os.write(tileResponse.data);
            os.flush();
        } catch (IOException ioe) {
            log.debug("Caught IOException" + ioe.getMessage());
        }
    }
}
