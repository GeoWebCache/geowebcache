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
 */
package org.geowebcache;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerResource;
import org.geowebcache.util.XMLConfiguration;
import org.geowebcache.seeder.SeedResource;

import org.restlet.Restlet;
import org.restlet.Router;
import com.noelios.restlet.ext.servlet.ServletConverter;

import org.springframework.beans.BeansException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.AbstractController;

import java.util.concurrent.*;

/**
 * The RESTDispatcher is responsible for routing all incoming requests made
 * through the rest interface
 */
public class RESTDispatcher extends AbstractController {
    public static final String METHOD_PUT = "PUT";

    public static final String METHOD_DELETE = "DELETE";

    private static final int THREAD_NUMBER = 10;
    
    private static XMLConfiguration config;
    
    private static ThreadPoolExecutor tpe = 
        new ThreadPoolExecutor(THREAD_NUMBER, 20, 500000L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

    private static Map<String, TileLayer> layers = null;
    

    ServletConverter myConverter;

    private Router myRouter;

    private static Log log = LogFactory
            .getLog(org.geowebcache.RESTDispatcher.class);

    /**
     * RESTDispatcher constructor
     * 
     * @param c - an XMLConfiguration
     */
    public RESTDispatcher(XMLConfiguration c) {
        super();
        setSupportedMethods(new String[] { 
                METHOD_GET, METHOD_POST, METHOD_DELETE });
        
        config = c;
        
        // constructor arguments(in order) int corePoolSize, int maximumPoolSize,
        // long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue
        // MOVED tpe init up
        //log.info("created thread pool executor");
        //log.info("created RESTDispatcher.");
    }
    /**
     * Method returns the core thread pool size
     * @return
     */
    public static int getNumThreads(){
        return THREAD_NUMBER; 
    }

    protected void initApplicationContext() throws BeansException {
        super.initApplicationContext();
        myConverter = new ServletConverter(getServletContext());
        myConverter.setTarget(createRoot());
    }

    /**
     * Method is responsible for handling HTTP requests
     * 
     * 
     */

    protected ModelAndView handleRequestInternal(HttpServletRequest request,
            HttpServletResponse response) {

        try {
            layers = (HashMap<String, TileLayer>) config.getTileLayers();
            myConverter.service(request, response);
        }

        catch (GeoWebCacheException gwce) {
            System.out.println(gwce.getMessage());
        } catch (ServletException se) {
            System.out.println(se.getMessage());
        } catch (IOException ioe) {
            System.out.println(ioe.getMessage());
        }
        return null;
    }

    /**
     * Method instantiates the restlet router
     * 
     * @return router
     */

    public Restlet createRoot() {
        if (myRouter == null) {
            myRouter = new Router();
            myRouter.attach("/layers/", TileLayerResource.class);
            myRouter.attach("/layers", TileLayerResource.class);
            myRouter.attach("/seed/", SeedResource.class);
        }

        return myRouter;
    }
    
    /**
     * Method returns a Map of (name,tileLayer) pairs 
     * @return
     */
    public static Map<String, TileLayer> getAllLayers() {
        return layers;
    }
    
    /**
     * Method returns the current XML configuration
     * @return
     */
    public static XMLConfiguration getConfig() {
        return config;
    }
    
    /**
     * Method returns the thread pool executor responsible for handling
     * layer seeds
     * @return
     */
    public static ThreadPoolExecutor getExecutor(){
        return tpe;
    }
    
}
