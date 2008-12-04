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
 * @author Arne Kepp / The Open Planning Project 2008 
 */
package org.geowebcache.rest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.demo.Demo;
import org.geowebcache.layer.TileLayerDispatcher;
import org.restlet.Context;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.Resource;
import org.restlet.resource.Variant;

public class ReloadPageResource extends Resource {
    private static Log log = LogFactory.getLog(org.geowebcache.rest.ReloadPageResource.class);
    
    public ReloadPageResource(Context context, Request request, Response response) {
        super(context, request, response);
        getVariants().add(new Variant(MediaType.TEXT_HTML));        
    }
    
    public boolean allowPost() {
        return true;
    }
    
    public void handlePost() {
        Request req = super.getRequest();
        
        Form form = req.getEntityAsForm();

        if (form != null && form.getFirst("reload_configuration") != null) {

            StringBuilder doc = new StringBuilder();
            
            doc.append("<html><body>\n" + Demo.GWC_HEADER);
            
            TileLayerDispatcher tlDispatcher = RESTDispatcher.getInstance().getTileLayerDispatcher();
            
            try {
                tlDispatcher.reInit();
                doc.append("<p>Loaded "+tlDispatcher.getLayers().size()
                        +" layers from configuration resources.</p>");

                doc.append("<p>Note that this functionality has not been rigorously tested,"
                        + " please reload the servlet if you run into any problems."
                        + " Also note that you must truncate the tiles of any layers that have changed.</p>");
                
            } catch (GeoWebCacheException e) {
                doc.append("<p>There was a problem reloading the configuration:<br>\n" 
                        + e.getMessage()
                        + "\n<br>"
                        + " If you believe this is a bug, please submit a ticket at "
                        + "<a href=\"http://geowebcache.org\">GeoWebCache.org</a>"
                	+ "</p>");
            }
            
            doc.append("<p><a href=\"../demo\">Go back</a></p>\n");
            
            doc.append("</body></html>");
            
            this.getResponse().setEntity(doc.toString(), MediaType.TEXT_HTML);
            
            
        } else {
            String error = "Unknown or malformed request, POST was to "
                    + req.getResourceRef().getPath();

            this.getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            this.getResponse().setEntity(error, MediaType.TEXT_HTML);
            log.error(error);
        }
    }
}
