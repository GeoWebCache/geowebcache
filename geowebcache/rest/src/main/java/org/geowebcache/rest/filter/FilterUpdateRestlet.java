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
 * @author Arne Kepp, OpenGeo, Copyright 2009
 */
package org.geowebcache.rest.filter;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;

import org.geowebcache.filter.request.RequestFilter;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.rest.GWCRestlet;
import org.geowebcache.rest.RestletException;
import org.geowebcache.rest.config.XMLConfiguration;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;

public class FilterUpdateRestlet extends GWCRestlet {

    private TileLayerDispatcher tld;

    public FilterUpdateRestlet(TileLayerDispatcher tld) {
        this.tld = tld;
    }

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
        } catch (IOException ioe) {
            response.setEntity("Encountered IO error " + ioe.getMessage(), MediaType.TEXT_PLAIN);
            response.setStatus(Status.SERVER_ERROR_INTERNAL);
        }
    }

    public void doPost(Request req, Response resp) throws RestletException, IOException {
        String filterName = (String) req.getAttributes().get("filterName");
        String updateType = (String) req.getAttributes().get("updateType");

        Iterator<TileLayer> lIter = tld.getLayerList().iterator();

        RequestFilter filter = null;

        TileLayer tl = null;

        while (lIter.hasNext() && filter == null) {
            tl = lIter.next();
            List<RequestFilter> filters = tl.getRequestFilters();
            if(filters!=null){
                Iterator<RequestFilter> fIter = filters.iterator();
                while (fIter.hasNext() && filter == null) {
                    RequestFilter cFilter = fIter.next();
                    if (cFilter.getName().equals(filterName)) {
                        filter = cFilter;
                    }
                }
            }
        }

        // Check that we have found a filter and that it's the correct type
        if (filter == null) {
            throw new RestletException("No filter by the name " + filterName + " was found.",
                    Status.CLIENT_ERROR_BAD_REQUEST);
        }

        if (updateType.equalsIgnoreCase("xml")) {
            // Parse the input using XStream
            InputStream input = req.getEntity().getStream();
            XmlFilterUpdate fu = XMLConfiguration.parseXMLFilterUpdate(input);

            fu.runUpdate(filter, tl);

        } else if (updateType.equalsIgnoreCase("zip")) {
            ZipFilterUpdate fu = new ZipFilterUpdate(req.getEntity().getStream());

            fu.runUpdate(filter, tl);
        } else {
            throw new RestletException("Unknow update type " + updateType + "\n",
                    Status.CLIENT_ERROR_BAD_REQUEST);
        }

        resp.setEntity("Filter update completed, no problems encountered.\n", MediaType.TEXT_PLAIN);
        resp.setStatus(Status.SUCCESS_OK);
    }
}
