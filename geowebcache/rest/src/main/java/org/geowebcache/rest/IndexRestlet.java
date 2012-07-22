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

import org.restlet.Router;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Reference;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.StringRepresentation;

/**
 * This class just provides links to the actual resources
 */
public class IndexRestlet extends GWCRestlet {
    Router router;

    public IndexRestlet(Router restletRouter) {
        super();
        router = restletRouter;
    }

    public void handle(Request request, Response response) {
        if (request.getMethod().equals(Method.GET)) {
            doGet(request, response);
        } else {
            response.setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
        }
    }

    private void doGet(Request request, Response response) {
        Reference resourceRef = request.getResourceRef();
        String baseUrl = resourceRef.toString();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        Representation result = new StringRepresentation(
                "<html><body>\n"
                        + "<a id=\"logo\" href=\""
                        + resourceRef.getParentRef()
                        + "\">"
                        + "<img src=\""
                        + baseUrl
                        + "/web/geowebcache_logo.png\" alt=\"\" height=\"100\" width=\"353\" border=\"0\"/></a>\n"
                        + "<h3>Resources available from here:</h3>"
                        + "<ul>"
                        + "<li><h4><a href=\""
                        + baseUrl
                        + "/layers/\">layers</a></h4>"
                        + "Lets you see the configured layers. You can also view a specific layer "
                        + " by appending the name of the layer to the URL, DELETE an existing layer "
                        + " or POST a new one. Note that the latter operations only make sense when GeoWebCache"
                        + " has been configured through geowebcache.xml. You can POST either XML or JSON."
                        + "</li>\n" + "<li><h4>seed</h4>" + "" + "</li>\n" + "</ul>"
                        + "</body></html>",

                MediaType.TEXT_HTML);
        response.setEntity(result);
    }
}
