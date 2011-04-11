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
package org.geowebcache.rest.reload;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.rest.GWCRestlet;
import org.geowebcache.rest.RestletException;
import org.geowebcache.util.ServletUtils;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;

public class ReloadRestlet extends GWCRestlet {
    private static Log log = LogFactory.getLog(org.geowebcache.rest.reload.ReloadRestlet.class);

    TileLayerDispatcher layerDispatcher;

    public void handle(Request request, Response response) {
        Method met = request.getMethod();
        try {
            if (met.equals(Method.POST)) {
                doPost(request, response);
            } else {
                throw new RestletException("Method not allowed",
                        Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
            }
        } catch (RestletException re) {
            response.setEntity(re.getRepresentation());
            response.setStatus(re.getStatus());
        }
    }

    public void doPost(Request req, Response resp) throws RestletException {
        Form form = req.getEntityAsForm();

        if (form == null || form.getFirst("reload_configuration") == null) {
            throw new RestletException(
                    "Unknown or malformed request. Please try again, somtimes the form "
                    +"is not properly received. This frequently happens on the first POST "
                    +"after a restart. The POST was to " + req.getResourceRef().getPath(), 
                    Status.CLIENT_ERROR_BAD_REQUEST );
        }

        StringBuilder doc = new StringBuilder();

        doc.append("<html>\n"+ServletUtils.gwcHtmlHeader("GWC Reload") +"<body>\n" + ServletUtils.gwcHtmlLogoLink("../"));

        try {
            layerDispatcher.reInit();
            String info = "Configuration reloaded. Read "
                + layerDispatcher.getLayerCount() 
                + " layers from configuration resources.";
            
            log.info(info);
            doc.append("<p>"+info+"</p>");
            
            doc.append("<p>Note that this functionality has not been rigorously tested,"
                    + " please reload the servlet if you run into any problems."
                    + " Also note that you must truncate the tiles of any layers that have changed.</p>");

        } catch (Exception e) {
            doc.append("<p>There was a problem reloading the configuration:<br>\n"
                    + e.getMessage()
                    + "\n<br>"
                    + " If you believe this is a bug, please submit a ticket at "
                    + "<a href=\"http://geowebcache.org\">GeoWebCache.org</a>"
                    + "</p>");
        }

        doc.append("<p><a href=\"../demo\">Go back</a></p>\n");
        doc.append("</body></html>");

        resp.setEntity(doc.toString(), MediaType.TEXT_HTML);
    }

    public void setTileLayerDispatcher(TileLayerDispatcher tileLayerDispatcher) {
        layerDispatcher = tileLayerDispatcher;
    }
}
