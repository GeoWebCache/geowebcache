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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.demo.Demo;
import org.geowebcache.layer.BadTileException;
import org.geowebcache.layer.OutOfBoundsException;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.service.Service;
import org.geowebcache.tile.Tile;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.AbstractController;

/**
 * This is the main router for requests of all types.
 */
public class GeoWebCacheDispatcher extends AbstractController {
    private static Log log = LogFactory.getLog(org.geowebcache.GeoWebCacheDispatcher.class);

    public static final String TYPE_SERVICE = "service";
    
    public static final String TYPE_TRUNCATE = "truncate";
    
    public static final String TYPE_RPC = "rpc";
    
    public static final String TYPE_DEMO = "demo";

    private TileLayerDispatcher tileLayerDispatcher = null;

    private HashMap<String,Service> services = null;
    
    private byte[] blankPNG8 = null; 
    
    private String servletPrefix = null;

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
     * GeoServer and other solutions that embedded this dispatcher will prepend a
     * path, this is used to remove it.
     * 
     * @param servletPrefix
     */
    public void setServletPrefix(String servletPrefix) {
        if(! servletPrefix.startsWith("/")) {
            this.servletPrefix = "/" + servletPrefix; 
        } else {
            this.servletPrefix = servletPrefix;
        }
        
        log.info("Invoked setServletPrefix("+servletPrefix+")");
    }

    /**
     * Services convert HTTP requests into the internal grid representation and
     * specify what layer the response should come from.
     * 
     * The classpath is scanned for objects extending Service, thereby making it
     * easy to add new services.
     */
    private void loadServices() {
        // Give all service objects direct access to the tileLayerDispatcher
        Service.setTileLayerDispatcher(tileLayerDispatcher);
        WebApplicationContext context = (WebApplicationContext) getApplicationContext();
        
        Map serviceBeans = context.getBeansOfType(Service.class);
        Iterator beanIter = serviceBeans.keySet().iterator();
        services = new HashMap<String,Service>();
        while (beanIter.hasNext()) {
            Service aService = (Service) serviceBeans.get(beanIter.next());
            services.put(aService.getPathName(), aService);
        }
    }

    private void loadBlankPNG8() {
        //WebApplicationContext context = (WebApplicationContext) getApplicationContext();
        
        InputStream is = GeoWebCacheDispatcher.class.getResourceAsStream("blank.png");
        //URL test = GeoWebCacheDispatcher.class.getResource("blank.png");
        blankPNG8 = new byte[129];
        
        try {
            int ret = is.read(blankPNG8);
            log.info("Read " + ret + " from blank PNG8 file (expected 129).");
        } catch (IOException ioe) {
            log.error(ioe.getMessage());
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

        // Break the request into components, {type, service name}
        String[] requestComps = null;
        try {
            String normalizedURI = request.getRequestURI().replaceFirst(request.getContextPath(), "");
            
            if(servletPrefix != null) {
                normalizedURI =  normalizedURI.replaceFirst(servletPrefix, ""); //getRequestURI().replaceFirst(request.getContextPath()+, "");
            }
             requestComps = parseRequest(normalizedURI);
            //requestComps = parseRequest(request.getRequestURI());
        } catch (GeoWebCacheException gwce) {
            writeError(response, 400, gwce.getMessage());
            return null;
        }

        try {
            if(requestComps == null) {
                handleFrontPage(request, response);
            } else if (requestComps[0].equalsIgnoreCase(TYPE_SERVICE)) {
                handleServiceRequest(requestComps[1], request, response);
            } else if (requestComps[0].equalsIgnoreCase(TYPE_DEMO)) {
                handleDemoRequest(requestComps[1],request, response);   
            } else {
                writeError(response, 404, "Unknown path: " + requestComps[0]);
            }
        } catch (Exception e) {
            // e.printStackTrace();
            if(! (e instanceof BadTileException) || log.isDebugEnabled()) {
                log.error(e.getMessage()+ " " + request.getRequestURL().toString());
            }
            
            writeError(response, 400, e.getMessage());
            if(! (e instanceof GeoWebCacheException) 
                    || log.isDebugEnabled()) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * Destroy function, has to be referenced in bean declaration:
     * <bean ... destroy="destroy">...</bean>
     */
    public void destroy() {
        log.info("GeoWebCacheDispatcher.destroy() was invoked, shutting down.");
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
        
        if(splitStr == null || splitStr.length < 2) {
            return null;
        }
        
        retStrs[0] = splitStr[1];
        if(splitStr.length > 2) {
            retStrs[1] = splitStr[2];
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

        Tile tile = null;
        
        try {
        // 1) Figure out what Service should handle this request
        Service service = findService(serviceStr);

        // 2) Find out what layer will be used and how
        tile = service.getTile(request, response);

        // Check where this should be dispatched
        if (tile.reqHandler == Tile.RequestHandler.SERVICE) {            
            // A3 The service object takes it from here
            service.handleRequest(tileLayerDispatcher, tile);
            
        } else {
            // B3) Get the configuration that has to respond to this request
            TileLayer layer = tileLayerDispatcher.getTileLayer(tile.getLayerId());
            
            // Save it for later
            tile.setTileLayer(layer);
            
            // Keep the URI 
            //tile.requestURI = request.getRequestURI();
            
            // A5) Ask the layer to provide the content for the tile
            layer.getTile(tile);

            // A6) Write response
            writeData(tile);
        }
        
        } catch (OutOfBoundsException e) {
            writeEmpty(tile, e.getMessage());
        }
        // Log statistic
    }
    
    private void handleDemoRequest(String action, HttpServletRequest request, 
            HttpServletResponse response) throws GeoWebCacheException {
        Demo.makeMap(this.tileLayerDispatcher, action, request, response);        
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
        if (this.services == null) {
            loadServices();
            loadBlankPNG8();
        }

        // E.g. /wms/test -> /wms
        Service service = (Service) services.get(serviceStr);
        if (service == null) {
            if(serviceStr == null || serviceStr.length() == 0) {
                serviceStr = ", try service/&lt;name of service&gt;";
            } else {
                serviceStr = " \""+ serviceStr + "\"";
            }
            throw new GeoWebCacheException(
                    "Unable to find handler for service" + serviceStr);
        }
        return service;
    }
    
    /**
     * Create a minimalistic frontpage, in case a GeoServer user
     * hit /geoserver/gwc or /geoserver/gwc/
     * 
     * @param request
     * @param response
     */
    private static void handleFrontPage(HttpServletRequest request,
            HttpServletResponse response) {

        String url;
        
        if(request.getRequestURL().toString().endsWith("/")) {
            url = "./demo";
        } else {
            String[] strs = request.getRequestURL().toString().split("/");
            url = strs[strs.length - 1]+ "/demo";
        }
        
        String message = "<html><body>\n" + Demo.GWC_HEADER
                + "<h4>Welcome to GeoWebCache</h4>"
                + "<a href=\""+url+"\">Dynamic list of layers</a>"
                + "</body></html>\n";

        writePage(response, 200, message);
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
    private static void writeError(HttpServletResponse response, int httpCode,
            String errorMsg) {
        
        log.debug(errorMsg);

        errorMsg = 
                "<html><body>\n"
                + Demo.GWC_HEADER
                +"<h4>"+httpCode+": "+errorMsg+"</h4>"
                +"</body></html>\n";

        writePage(response, httpCode, errorMsg);
    }
    
    
    private static void writePage(HttpServletResponse response, int httpCode,
            String message) {
        response.setContentType("text/html");
        response.setStatus(httpCode);

        if (message == null) {
            return;
        }

        try {
            OutputStream os = response.getOutputStream();
            os.write(message.getBytes());
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        
    }

    /**
     * Happy ending, sets the headers and writes the response back to the
     * client.
     * 
     * Note that the expiration header should already have been set by
     * the layer that retrieved the data.
     * 
     * @param response
     *            where to write to
     * @param tileResponse
     *            the response with the data and MIME type
     * @throws IOException
     */
    private void writeData(Tile tile) throws IOException {
        byte[] data = tile.getContent();
        
        HttpServletResponse response = tile.servletResp;
        
        // Did we get anything?
        if (tile.getError() && data != null) {
            // TODO something nice
            log.error("writeData() oops.. no data or tile was null");
        } else {
            response.setStatus(tile.getStatus());
            response.setContentType(tile.getMimeType().getMimeType());
            
            if(data != null) {
                response.setContentLength(data.length);
                
                try {
                    OutputStream os = response.getOutputStream();
                    os.write(data);
                } catch (IOException ioe) {
                    log.debug("Caught IOException: " + ioe.getMessage() + "\n\n" + ioe.toString());
                }
            }
        }
    }
    
    private void writeEmpty(Tile tile, String message) {
        tile.servletResp.setHeader("geowebcache-message", message);
        tile.servletResp.setStatus(200);
        tile.servletResp.setContentType(ImageMime.png.getMimeType());
        TileLayer layer = tile.getLayer();
        if(layer != null) {
            layer.setExpirationHeader(tile.servletResp);
        }
        try { 
            tile.servletResp.getOutputStream().write(this.blankPNG8);
        } catch (IOException ioe) {
            log.debug("Caught IOException: " + ioe.getMessage() + "\n\n" + ioe.toString());
        }
    }
}
