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
 *  @author David Winslow / The Open Planning Project 2008 
 */
package org.geowebcache.rest;

import java.util.Iterator;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.restlet.Restlet;
import org.restlet.Router;
import org.restlet.resource.StringRepresentation;
import org.springframework.beans.BeansException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.AbstractController;

import com.noelios.restlet.ext.servlet.ServletConverter;

/**
 * Simple AbstractController implementation that does the translation between
 * Spring requests and Restlet requests.
 */
public class RESTDispatcher extends AbstractController {
    public static String METHOD_PUT = "PUT";
    public static String METHOD_DELETE = "DELETE";
    
    ServletConverter myConverter;
    
    private Router myRouter = new Router();

    public RESTDispatcher(Map map) {
        super();
        setSupportedMethods(new String[] {
                METHOD_GET, METHOD_POST, METHOD_PUT, METHOD_DELETE, METHOD_HEAD
            });
        addRoutes(map);
        
        myRouter.attach("", new IndexRestlet(myRouter));
    }

    protected void initApplicationContext() throws BeansException {
        super.initApplicationContext();

        myConverter = new ServletConverter(getServletContext());
        myConverter.setTarget(myRouter);
    }
    
    protected ModelAndView handleRequestInternal(HttpServletRequest req, HttpServletResponse resp)
        throws Exception {
        
        try {
            myConverter.service(req, resp);
        }
        catch( Exception e ) {
            RestletException re = null;
            if ( e instanceof RestletException ) {
                re = (RestletException) e;
            }
            if ( re == null && e.getCause() instanceof RestletException ) {
                re = (RestletException) e.getCause();
            }
            
            if ( re != null ) {
                resp.setStatus( re.getStatus().getCode() );
                // This does not actually write anything?
                //re.getRepresentation().write(resp.getOutputStream());
                
                String reStr = re.getRepresentation().getText();
                resp.getOutputStream().write(reStr.getBytes());
                
                resp.getOutputStream().flush();
            }
            else {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                new StringRepresentation( e.getMessage() ).write( resp.getOutputStream() );
                resp.getOutputStream().flush();
            }
        }
            
        return null;
    }

    private void addRoutes(Map m){
        Iterator it = m.entrySet().iterator();

        while (it.hasNext()){
            Map.Entry entry = (Map.Entry) it.next();

            if (entry.getValue() instanceof GWCResource){
                myRouter.attach(entry.getKey().toString(), ((GWCResource) entry.getValue()).getClass());
            } else if (entry.getValue() instanceof Restlet){
                myRouter.attach(entry.getKey().toString(), (Restlet) entry.getValue());
            } else {
                
            }
        }
    }
}
