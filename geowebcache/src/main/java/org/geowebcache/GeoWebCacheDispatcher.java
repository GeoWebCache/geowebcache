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
import org.geowebcache.service.Service;
import org.geowebcache.service.ServiceRequest;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.AbstractController;

/**
 * This is the main router for requests of all types.
 */
public class GeoWebCacheDispatcher extends AbstractController {
    private static Log log = LogFactory.getLog(org.geowebcache.GeoWebCacheDispatcher.class);
    
    public static final String TYPE_SERVICE = "/service";
    public static final String TYPE_SEED = "/seed";
    
    WebApplicationContext context = null;
    
    private TileLayerDispatcher tileLayerDispatcher = null;
    private HashMap services = null;
    
    public GeoWebCacheDispatcher() {
        super();
    }
    
    /**
     * Setter method for Spring. TileLayerDispatcher is a class for
     * looking up TileLayer objects based on the name of the layer.
     * 
     * @param tileLayerDispatcher a class for looking up TileLayer objects
     */
    public void setTileLayerDispatcher(TileLayerDispatcher tileLayerDispatcher) {
    	this.tileLayerDispatcher = tileLayerDispatcher;
    }
    
    /**
     * Services convert HTTP requests into the internal grid representation
     * and specify what layer the response should come from.
     * 
     * The classpath is scanned for objects extending Service, thereby
     * making it easy to add new services.
     */
    private void loadServices() {
        Map serviceBeans = context.getBeansOfType(Service.class);
        Iterator beanIter = serviceBeans.keySet().iterator();
        services = new HashMap();
        while(beanIter.hasNext()) {
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
    protected ModelAndView handleRequestInternal(
            HttpServletRequest request, HttpServletResponse response
            ) throws Exception {
        // Basics
        context = (WebApplicationContext) getApplicationContext();
        String requestType = new String(request.getServletPath());
        
        if(requestType.equalsIgnoreCase(TYPE_SERVICE)) {
        	try {
        		handleServiceRequest(request, response);
        	} catch (GeoWebCacheException gwce) {
        		//e.printStackTrace();
        		writeError(response, 400, gwce.getMessage());
        	}
        } else if(requestType.equalsIgnoreCase(TYPE_SEED)) {
            //handleSeedRequest(request, response);
            writeError(response, 400, "Seeding is currently not supported");
        } else {
            writeError(response, 404, "Unknow path: " + requestType);
        }
        return null;
    }
    
    
    /** 
     * This is the main method for handling service requests.
     * See comments in the code.
     * 
     * @param request 
     * @param response 
     * @throws Exception 
     */
    private void handleServiceRequest(HttpServletRequest request, 
            HttpServletResponse response) throws Exception {
        
        // 1) Figure out what Service should handle this request
        Service service = findService(request);

        // 2) Find out what layer will be used and how
        ServiceRequest servReq = service.getServiceRequest(request);
        
        // 3) Get the configuration that has to respond to this request
        TileLayer layer = 
        	tileLayerDispatcher.getTileLayer(servReq.getLayerIdent());
        
        // Check where this should be dispatched
        if(servReq.getType() == ServiceRequest.SERVICE_REQUEST_TILE) {
        	// A4) Convert to internal representation, using info from request and layer 
        	TileRequest tileRequest = service.getTileRequest(layer, request);
        
        	// A5) Ask the layer to provide the tile
        	TileResponse tileResponse = layer.getResponse(tileRequest, request.getRequestURI(), response);
        
        	// A6) Write response
        	writeData(response, tileResponse);
        } else {
        	// B4 The service object takes it from here
        	service.handleRequest(layer, request, response);
        }
    }
    
    /**
     * Helper function for looking up the service that should handle
     * the request. 
     * 
     * @param request full HttpServletRequest
     * @return
     */
    private Service findService(HttpServletRequest request) {
        if(this.services == null)
            loadServices();
        
        // E.g. /wms/test -> /wms
        String pathInfo = request.getPathInfo();
        int pathEnd = pathInfo.indexOf('/',1);  
        if(pathEnd < 0) pathEnd = pathInfo.length();
        String serviceType = new String(pathInfo.substring(0, pathEnd));
        
        return (Service) services.get(serviceType);
    }
    
    /**
     * Wrapper method for writing an error back to the client,
     * and logging it at the same time. 
     * 
     * @param response where to write to
     * @param httpCode the HTTP code to provide
     * @param errorMsg the actual error message, human readable
     */
    private void writeError(HttpServletResponse response, 
            int httpCode, String errorMsg) {
        
        response.setContentType("text/plain");
        response.setStatus(httpCode);
        
        if(errorMsg == null) {
        	return;
        }
        
        log.error(errorMsg);
            
        try {
        	OutputStream os = response.getOutputStream();
        	os.write(errorMsg.getBytes());
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
    
    /**
     * Happy ending, sets the headers and writes the response back
     * to the client.
     * 
     * @param response where to write to
     * @param tileResponse the response with the data and MIME type
     * @throws IOException
     */
    private void writeData(HttpServletResponse response, 
            TileResponse tileResponse) throws IOException {

        // Did we get anything?
        if (tileResponse == null || tileResponse.data == null || tileResponse.data.length == 0) {
            log.trace("sendData() had nothing to return");

            // Response: 500 , should not have gotten here
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        log.trace("sendData() Sending data.");
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(tileResponse.mimeType);
        response.setContentLength(tileResponse.data.length);
        try {
        	OutputStream os = response.getOutputStream();
        	os.write(tileResponse.data);
        	os.flush();
        } catch (IOException ioe) {
        	log.debug("Caught IOException"+ioe.getMessage());
        }
    }
}
