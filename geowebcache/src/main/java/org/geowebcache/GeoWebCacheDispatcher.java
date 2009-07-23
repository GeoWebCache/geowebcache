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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
import org.geowebcache.conveyor.Conveyor;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.demo.Demo;
import org.geowebcache.filter.request.RequestFilterException;
import org.geowebcache.layer.BadTileException;
import org.geowebcache.layer.OutOfBoundsException;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.service.Service;
import org.geowebcache.storage.DefaultStorageFinder;
import org.geowebcache.storage.StorageBroker;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.AbstractController;

/**
 * This is the main router for requests of all types.
 */
public class GeoWebCacheDispatcher extends AbstractController {
    private static Log log = LogFactory.getLog(org.geowebcache.GeoWebCacheDispatcher.class);

    public static final String TYPE_SERVICE = "service";
    
    public static final String TYPE_DEMO = "demo";

    private TileLayerDispatcher tileLayerDispatcher = null;
    
    private StorageBroker storageBroker = null;
    
    private DefaultStorageFinder defaultStorageFinder = null;

    private HashMap<String,Service> services = null;
    
    private byte[] blankTile = null; 
    
    private String servletPrefix = null;

    public GeoWebCacheDispatcher() {
        super();
    }

    public void setStorageBroker(StorageBroker sb) {
       this.storageBroker = sb;
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

    public void setDefaultStorageFinder(DefaultStorageFinder defaultStorageFinder) {
        this.defaultStorageFinder = defaultStorageFinder;
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
        WebApplicationContext context = (WebApplicationContext) getApplicationContext();
        
        Map serviceBeans = context.getBeansOfType(Service.class);
        Iterator beanIter = serviceBeans.keySet().iterator();
        services = new HashMap<String,Service>();
        while (beanIter.hasNext()) {
            Service aService = (Service) serviceBeans.get(beanIter.next());
            services.put(aService.getPathName(), aService);
        }
    }

    private void loadBlankTile() {        
        String blankTilePath = defaultStorageFinder.findEnvVar(DefaultStorageFinder.GWC_BLANK_TILE_PATH);
        
        if(blankTilePath != null) {
            File fh = new File(blankTilePath);
            if(fh.exists() && fh.canRead() && fh.isFile()) {
                long fileSize = fh.length();
                blankTile = new byte[(int) fileSize];
                
                int total = 0;
                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(fh);
                    int read = 0;
                    while(read != -1) {
                        read = fis.read(blankTile, total, blankTile.length - total);
                        
                        if(read != -1)
                            total += read;
                    }
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        fis.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                
                if(total == blankTile.length && total > 0) {
                    log.info("Loaded blank tile from " + blankTilePath);
                } else {
                    log.error("Failed to load blank tile from " + blankTilePath);
                }

                return;
            } else {
                log.error("" + blankTilePath + " does not exist or is not readable.");
            }
        }
        
        // Use the built-in one: 
        InputStream is = null;   
        try {
            is = GeoWebCacheDispatcher.class.getResourceAsStream("blank.png");
            blankTile = new byte[425];
            int ret = is.read(blankTile);
            log.info("Read " + ret + " from blank PNG file (expected 425).");
        } catch (IOException ioe) {
            log.error(ioe.getMessage());
        } finally {
            try {
                if(is != null) 
                    is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
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
            } else if (requestComps[0].equalsIgnoreCase(TYPE_DEMO) 
                    || requestComps[0].equalsIgnoreCase(TYPE_DEMO + "s")) {
                handleDemoRequest(requestComps[1],request, response);   
            } else {
                writeError(response, 404, "Unknown path: " + requestComps[0]);
            }
        } catch (Exception e) {
            // e.printStackTrace();
            if(e instanceof RequestFilterException) {
                
                RequestFilterException reqE = (RequestFilterException) e;
                reqE.setHttpInfoHeader(response);
                writeFixedResponse(response, reqE.getResponseCode(), reqE.getContenType(), reqE.getResponse());
                
            } else {
                if(! (e instanceof BadTileException) || log.isDebugEnabled()) {
                    log.error(e.getMessage()+ " " + request.getRequestURL().toString());
                }
                
                writeError(response, 400, e.getMessage());
                
                if(! (e instanceof GeoWebCacheException) || log.isDebugEnabled()) {
                    e.printStackTrace();
                }
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

        Conveyor conv = null;

        // 1) Figure out what Service should handle this request
        Service service = findService(serviceStr);

        // 2) Find out what layer will be used and how
        conv = service.getConveyor(request, response);

        // Check where this should be dispatched
        if (conv.reqHandler == Conveyor.RequestHandler.SERVICE) {
            // A3 The service object takes it from here
            service.handleRequest(conv);

        } else {
            ConveyorTile convTile = (ConveyorTile) conv;

            // B3) Get the configuration that has to respond to this request
            TileLayer layer = tileLayerDispatcher.getTileLayer(convTile.getLayerId());
            
            // Save it for later
            convTile.setTileLayer(layer);
            
            // Apply the filters
            layer.applyRequestFilters(convTile);

            // Keep the URI
            // tile.requestURI = request.getRequestURI();

            try {
                // A5) Ask the layer to provide the content for the tile
                layer.getTile(convTile);
                
                // A6) Write response
                writeData(convTile);
                
                // Alternatively: 
            } catch (OutOfBoundsException e) {
                writeEmpty(convTile, e.getMessage());
            }
        }

        // Log statistic?
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
            loadBlankTile();
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
    private static void writeError(HttpServletResponse response, int httpCode, String errorMsg) {
        log.debug(errorMsg);
        errorMsg =  "<html><body>\n" + Demo.GWC_HEADER + "<h4>"+httpCode+": "+errorMsg+"</h4>" + "</body></html>\n";
        writePage(response, httpCode, errorMsg);
    }
        
    private static void writePage(HttpServletResponse response, int httpCode, String message) {
        writeFixedResponse(response, httpCode, "text/html", message.getBytes());        
    }

    /**
     * Happy ending, sets the headers and writes the response back to the
     * client.
     */
    private void writeData(ConveyorTile tile) throws IOException {  
        writeFixedResponse(tile.servletResp, 200, tile.getMimeType().getMimeType(), tile.getContent());
    }
    
    /**
     * Writes a transparent, 8 bit PNG to avoid having clients like OpenLayers
     * showing lots of pink tiles
     */
    private void writeEmpty(ConveyorTile tile, String message) {
        tile.servletResp.setHeader("geowebcache-message", message);
        TileLayer layer = tile.getLayer();
        if(layer != null) {
            layer.setExpirationHeader(tile.servletResp);
        }

        writeFixedResponse(tile.servletResp, 200, ImageMime.png.getMimeType(), this.blankTile);
    }
    
    private static void writeFixedResponse(HttpServletResponse response, int httpCode, String contentType, byte[] data) {
        response.setStatus(httpCode);
        response.setContentType(contentType);
        
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
