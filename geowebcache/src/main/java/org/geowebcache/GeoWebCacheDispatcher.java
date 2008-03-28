package org.geowebcache;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.service.Service;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.AbstractController;

public class GeoWebCacheDispatcher extends AbstractController {
    private static Log log = LogFactory.getLog(org.geowebcache.GeoWebCacheDispatcher.class);
    
    public static final String TYPE_SERVICE = "/service";
    public static final String TYPE_SEED = "/seed";
    
    ApplicationContext context = null;
    //ApplicationContext context = null;
    
    private HashMap layers = null;
    private HashMap services = null;
    
    public GeoWebCacheDispatcher() {
        super();
    }
        
    private void loadServices() {
        Map serviceBeans = context.getBeansOfType(Service.class);
        Iterator beanIter = serviceBeans.keySet().iterator();
        services = new HashMap();
        while(beanIter.hasNext()) {
            Service aService = (Service) serviceBeans.get(beanIter.next());
            services.put(aService.getPathName(), aService);
        }
    }
    
    private void addLayerHandlers() {
        //layers = context.getBeansOfType(TileLayer.class);
    }
    
    protected ModelAndView handleRequestInternal(
            HttpServletRequest request, HttpServletResponse response
            ) throws Exception {
        // Basics
        context = getApplicationContext();
        String requestType = new String(request.getServletPath());
        
        if(requestType.equalsIgnoreCase(TYPE_SERVICE)) {
            handleServiceRequest(request, response);
        } else if(requestType.equalsIgnoreCase(TYPE_SEED)) {
            //handleSeedRequest(request, response);
            writeError(response, 400, "Seeding is currently not supported");
        } else {
            writeError(response, 404, "Unknow path: " + requestType);
        }
        return null;
    }
    
    private void handleServiceRequest(HttpServletRequest request, 
            HttpServletResponse response) throws Exception {
        
        // 1) Figure out what Service should handle this request
        Service service = findService(request);

        // 2) Find out what layer will be used
        System.out.println("Hells yeah!");
        
        // 3) Get the backend that has to respond to this request
        
        // 4) Convert to internal representation, using info from request and layer 
        
        // 5) Ask the layer to provide the tile
        
        // 6) Write response
    }
    
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
    
    private TileLayer findLayer(String layerIdentifier) {
        if(this.layers == null)
            addLayerHandlers();
        
        return (TileLayer) layers.get(layerIdentifier);
    }
    
    private void writeError(HttpServletResponse response, 
            int httpCode, String errorMsg) {
        
        response.setContentType("text/plain");
        response.setStatus(httpCode);
        
        try {
            Writer errorWriter = response.getWriter();
            errorWriter.write(errorMsg);
            errorWriter.close();
        } catch (IOException ioe) {
            // Do nothing at this point
        }
        log.error(errorMsg);
    }
}
