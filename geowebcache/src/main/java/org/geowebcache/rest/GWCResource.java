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
import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.Resource;

public class GWCResource extends Resource {
    private static Log log = LogFactory.getLog(org.geowebcache.rest.GWCResource.class);
    
    public GWCResource(Context context, Request request, Response response) {
        super(context, request, response);
    }
    
    void checkPosMediaType(Representation entity) throws GeoWebCacheException {
        String remoteAdr = getRequest().getClientInfo().getAddress();

        if (entity == null
                || ((!entity.getMediaType().includes(MediaType.APPLICATION_XML)) 
                &&  (!entity.getMediaType().includes(MediaType.APPLICATION_JSON)))) {

            String message = "Request from "+ remoteAdr + " did not specify MIME type"
                    + " of the document posted. Please specify application/xml "
                    + " or application/json";
            throw new GeoWebCacheException(message);
        } else {
            log.info("Received seed request from  " + remoteAdr);
        }
    }
    
    void writeError(Status status, String message) {
        log.error(message);
        this.getResponse().setStatus(status, message);
    }
    
}
