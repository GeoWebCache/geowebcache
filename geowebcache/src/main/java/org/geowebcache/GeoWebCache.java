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
 * @author Chris Whitney
 * 
 */
package org.geowebcache;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.service.gmaps.GMapsConverter;
import org.geowebcache.service.kml.KMLService;
import org.geowebcache.service.ve.VEConverter;
import org.geowebcache.service.wms.WMSParameters;
import org.geowebcache.util.Configuration;
import org.geowebcache.util.ServletUtils;
import org.geowebcache.util.wms.BBOX;

public class GeoWebCache extends HttpServlet {
    private static final long serialVersionUID = 4175613925719485006L;

    private static Log log = LogFactory
            .getLog(org.geowebcache.GeoWebCache.class);

    private static final String DEFAULT_REL_CONFIG_DIR = "WEB-INF"
            + File.separator + "classes";

    private Configuration configuration = null;

    private HashMap layers = null;

    /**
     * 1) Find out where the configuration directory is, 2) read all the
     * property files in it 3) create the necessary objects 4) TODO Digester for
     * Etags, if desirable
     */
    @Override
    public void init() throws ServletException {
        // 1) Get the configuration directory
        String configDir = System.getProperty("GEOWEBCACHE_CONFIG_DIR");
        if (configDir != null) {
            log.trace("Using environment variable, GEOWEBACACHE_CONFIG_DIR = "
                    + configDir);
        } else {
            // Try getting it from web.xml
            configDir = getServletContext().getInitParameter(
                    "GEOWEBCACHE_CONFIG_DIR");
            // configDir = getInitParameter("GEOWEBCACHE_CONFIG_DIR");
            if (configDir != null) {
                log.trace("Using web.xml, GEOWEBACACHE_CONFIG_DIR = "
                        + configDir);
            } else {
                log
                        .error("GEOWEBCACHE_CONFIG_DIR not specified! Using default configuration.");
                String dir = getServletContext().getRealPath("");

                // This should work for Tomcat
                configDir = dir + File.separator + DEFAULT_REL_CONFIG_DIR;
                File fh = new File(configDir);

                if (!fh.exists()) {
                    // Probably running Jetty through Eclipse?
                    configDir = dir + "../resources";
                }
            }
        }

        // 2) Check configuration directory can be read
        File configDirH = new File(configDir);
        if (configDirH.isDirectory() && configDirH.canRead()) {
            log.trace("Opened configuration directory: " + configDir);
        } else {
            log.fatal("Unable to read configuration from " + configDir);
            throw new ServletException("Unable to read configuration from "
                    + configDir);
        }
        // 3) Read the configuration files and create corresponding objects in
        // layers.
        configuration = new Configuration(configDirH);
        layers = configuration.getLayers();

        // 4) Digest mechanism
        // TODO
        log.trace("Completed loading configuration");
    }

    /**
     * Loops over all the layers and calls destroy() on their caches.
     */
    @Override
    public void destroy() {
        Iterator iter = layers.entrySet().iterator();
        while (iter.hasNext()) {
            WMSLayer tl = (WMSLayer) iter.next();
            tl.destroy();
        }
    }

    /**
     * And it goes a little something like this 1) Get request 2) Parse Request
     * into WMS parameters 3) Determine whether we serve this layer 4) Ask
     * TileLayer for best fit for the given parameters 5) Return tile
     */
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (log.isDebugEnabled()) {
            log.debug("-------------New Request-----------");
            log.debug(request.getRequestURI());
        }

        // For some reason getPathTranslated returns null
        String contextPath = request.getContextPath();
        String requestURI = request.getRequestURI();
        String subPath = requestURI.substring(contextPath.length(),
                requestURI.length()).toLowerCase();

        if (subPath.equals("/wms")) {
            doGetWMS(request, response);
        } else if (subPath.equals("/ve")) {
            doGetVE(request, response);
        } else if (subPath.equals("/gmaps")) {
            doGetGmaps(request, response);
        } else if (subPath.equals("/kml")) {
            doGetKML(request, response);
        }
//        } else if (subPath.equals("/seed")) {
//            doGetSeed(request, response);
//        }
    }

    public void doGetWMS(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        
        // Parse request
        WMSParameters wmsparams = new WMSParameters(request);

        // Check whether we serve this layer
        WMSLayer cachedLayer = findAndCheckLayer(wmsparams, request, response);

        if (cachedLayer != null) {
            // Get data
            byte[] data = cachedLayer.getData(wmsparams, response);
            
            // Will also return error message, if appropriate
            sendData(response, wmsparams.getImageMime(), data);
        } else {
            // finAndCheckLayer() has already set error message
        }
    }


    public void doGetVE(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        Map params = request.getParameterMap();
        String strLayer = ServletUtils.stringFromMap(params, "layers");
        String strQuadKey = ServletUtils.stringFromMap(params, "quadkey");
        String strFormat = ServletUtils.stringFromMap(params, "format");
        
        int[] gridLoc = VEConverter.convert(strQuadKey);

        forwardToBackend(
                strLayer, strFormat, "EPSG:900913", gridLoc, request, response);
    }

    public void doGetGmaps(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        Map params = request.getParameterMap();
        String strLayer = ServletUtils.stringFromMap(params, "layers");
        String strZoom = ServletUtils.stringFromMap(params, "zoom");
        String strX = ServletUtils.stringFromMap(params, "x");
        String strY = ServletUtils.stringFromMap(params, "y");
        String strFormat = ServletUtils.stringFromMap(params, "format");
        
        int[] gridLoc = GMapsConverter.convert(Integer.parseInt(strZoom), Integer
                .parseInt(strX), Integer.parseInt(strY));

        forwardToBackend(
                strLayer, strFormat, "EPSG:900913", gridLoc, request, response);
    }
    
    public void doGetKML(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        Map params = request.getParameterMap();
        
        String strLayer = ServletUtils.stringFromMap(params, "layers");
        String strZoom = ServletUtils.stringFromMap(params, "z");
        String strX = ServletUtils.stringFromMap(params, "x");
        String strY = ServletUtils.stringFromMap(params, "y");
        String strFormat = ServletUtils.stringFromMap(params, "format");
        
        int[] gridLoc = { 
                    Integer.parseInt(strX),
                    Integer.parseInt(strY),
                    Integer.parseInt(strZoom) };
        
        if(strFormat.equals("application/vnd.google-earth.kml+xml")
                || strFormat.equals("application/vnd.google-earth.kmz+xml")) {
            // We need to make a nice little XML document
            WMSLayer cachedLayer = findAndCheckLayer(
                    strLayer, "EPSG:4326", strFormat, request, response);
            if(cachedLayer != null) {
                KMLService.getOverlayKML(cachedLayer, response);
            }
        } else {
            // Client wants an image
            forwardToBackend(
                    strLayer, strFormat, "EPSG:4326", gridLoc, request, response);
        }
            
    }
    
//    /**
//     * Function for setting up seeding
//     * 
//     * TODO replace
//     * 
//     * @param request
//     * @param response
//     * @throws ServletException
//     * @throws IOException
//     */
//    public void doGetSeed(HttpServletRequest request,
//            HttpServletResponse response) throws ServletException, IOException {
//
//        Map params = request.getParameterMap();
//        String strLayers = ServletUtils.stringFromMap(params, "layers");
//        WMSLayer layer = (WMSLayer) layers.get(strLayers);
//
//        if (layer == null) {
//            response.setContentType("text/plain");
//            response.sendError(400, "No layers or unknown layer " + strLayers);
//            // complain loudly and quit
//            log.error("No layers?");
//        }
//
//        response.setContentType("text/html");
//        response.setBufferSize(12);
//
//        BBOX reqBounds = null;
//        if (params.containsKey("bbox")) {
//            reqBounds = new BBOX(((String[]) params.get("bbox"))[0]);
//        }
//
//        String strStart = ServletUtils.stringFromMap(params, "start");
//        String strStop = ServletUtils.stringFromMap(params, "stop");
//        String strFormat = ServletUtils.stringFromMap(params, "format");
//
//        int start = -1;
//        if (strStart != null) {
//            start = Integer.valueOf(strStart);
//        }
//
//        int stop = -1;
//        if (strStop != null) {
//            stop = Integer.valueOf(strStop);
//        }
//
//        layer.seed(start, stop, strFormat, reqBounds, response);
//        response.flushBuffer();
//    }
    
    
    /**
     * Wrapper function that picks layer, SRS and MIME type out of
     * the WMS parameters to pass on to the findAndCheckLayer
     * function below.
     * 
     * @param wmsParams
     * @param request
     * @param response
     * @return
     * @throws IOException
     */
    private WMSLayer findAndCheckLayer(WMSParameters wmsParams,
            HttpServletRequest request, HttpServletResponse response)
    throws IOException {
        
        return findAndCheckLayer(wmsParams.getLayer(), wmsParams.getSrs(), 
                wmsParams.getImageMime(), request, response);
    }
    

    /**
     * Looks up the layer and does basic checks, 
     * whether it supports the projection and the MIME type
     * 
     * It returns a null and writes error message to the
     * response object if any of these are amiss.
     * 
     * @param strLayer external name of layer(s)
     * @param srs requested projection
     * @param mimeType requested MIME type for response
     * @param request the original request object
     * @param response the response object
     * @return the matching tile layer
     * @throws IOException
     */
    private WMSLayer findAndCheckLayer(String strLayer, String srs, 
            String mimeType, HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        String errorMsg = null;
        
        // Check whether we actually know this layer
        WMSLayer cachedLayer = (WMSLayer) layers.get(strLayer);
        if(cachedLayer == null) {
            errorMsg = "Unknown layer: " + strLayer;
        }
        // Check projection support
        if(errorMsg == null)
            errorMsg = cachedLayer.supportsProjection(srs);
        
        // Check MIME support
        if(errorMsg == null)
            errorMsg = cachedLayer.supportsMime(mimeType);
        
        if (errorMsg != null) {
            response.setContentType("text/plain");
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            errorMsg = errorMsg + "\n\n" + " Raw query string: " + request.getQueryString();
            response.getOutputStream().print(errorMsg);
            log.debug(errorMsg);
            return null;
        } else {
            return cachedLayer;
        }
    }
    
    /**
     * Actually send the data back to the client, or an error if there is none.
     * 
     * @param response Where to write the response
     * @param mimeType MIME type to set for the response
     * @param data the data
     * @throws IOException
     */
    private void sendData(HttpServletResponse response, String mimeType, byte[] data)
            throws IOException {
        
        // Did we get anything?
        if (data == null || data.length == 0) {
            log.trace("sendData() had nothing to return");
            
            // Response: 500 , should not have gotten here
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        
        log.trace("sendData() Sending data.");
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(mimeType);
        response.setContentLength(data.length);
        OutputStream os = response.getOutputStream();
        os.write(data);
        os.flush();
    }
    
    /**
     * Checks whether the requested layer is supported and forwards
     * the request to the appropriate layer object, after running a few
     * tests on the request. 
     * 
     * @param strLayer
     * @param strFormat
     * @param SRS
     * @param gridLoc
     * @param request
     * @param response
     * @throws IOException
     */
    private void forwardToBackend(String strLayer, String strFormat, String SRS, int[] gridLoc,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        
        WMSLayer cachedLayer = findAndCheckLayer(
                strLayer, SRS, strFormat, request, response);

        if (cachedLayer != null) {
            
            byte[] data = cachedLayer.getData(
                    gridLoc, strFormat, request.getQueryString(), response);
            
            // Will also return error message, if appropriate
            sendData(response, strFormat, data);
        } else {
            // finAndCheckLayer() has already set error message
        }
    }

}
