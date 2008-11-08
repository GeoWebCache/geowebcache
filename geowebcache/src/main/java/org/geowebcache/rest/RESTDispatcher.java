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
package org.geowebcache.rest;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.util.XMLConfiguration;

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
    private static Log log = LogFactory.getLog(org.geowebcache.rest.RESTDispatcher.class);
    
    public static final String METHOD_PUT = "PUT";

    public static final String METHOD_DELETE = "DELETE";

    private static final int THREAD_NUMBER = 4;
    
    private static final int THREAD_MAX_NUMBER = 32;
    
    private final XMLConfiguration xmlConfig;
    
    private final TileLayerDispatcher tlDispatcher;
    
    private final ThreadPoolExecutor tpe;

    //private static Map<String, TileLayer> layers = null;
    
    ServletConverter myConverter;

    private Router myRouter;

    private static RESTDispatcher instance;
    
    

    /**
     * RESTDispatcher constructor
     * 
     * @param c - an XMLConfiguration
     */
    public RESTDispatcher(TileLayerDispatcher tlDispatcher, XMLConfiguration xmlConfig) {
        super();
        setSupportedMethods(
                new String[] { METHOD_GET, METHOD_POST, METHOD_DELETE }
                );
        
        this.xmlConfig = xmlConfig;
        this.tlDispatcher = tlDispatcher;
        this.tpe = new ThreadPoolExecutor( 
                THREAD_NUMBER, THREAD_MAX_NUMBER, Long.MAX_VALUE, TimeUnit.SECONDS, 
                new LinkedBlockingQueue<Runnable>());
        this.instance = this;
    }
    
    protected static RESTDispatcher getInstance(){
        return instance;
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
            //layers = (HashMap<String, TileLayer>) tlDispatcher.getLayers();
            myConverter.service(request, response);
        } catch (ServletException se) {
            log.error(se.getMessage());
        } catch (IOException ioe) {
            log.error(ioe.getMessage());
        }
        //TODO WRITE ERROR OR SOMETHING
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
            myRouter.attach("/", RESTIndexResource.class);
            myRouter.attach("/layers", TileLayerResource.class);
            myRouter.attach("/seed/", SeedResource.class);
            myRouter.attach("/rest/seed/", SeedResource.class);
        }

        return myRouter;
    }
    
    /**
     * Method returns the current XML configuration
     * Not at all happy about this
     * @return
     */
    public XMLConfiguration getXMLConfiguration() {
        return xmlConfig;
    }
    
    /**
     * Method returns the tileLayerDispatcher
     * Not at all happy about this
     */
    protected TileLayerDispatcher getTileLayerDispatcher() {
        return tlDispatcher;
    }
    
    /**
     * Method returns the thread pool executor responsible for handling
     * layer seeds
     * @return
     */
    protected ThreadPoolExecutor getExecutor(){
        return tpe;
    }
    
}
