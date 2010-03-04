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
 * @author Arne Kepp / The Open Planning Project 2009 
 */
package org.geowebcache.rest.bounds;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.rest.GWCRestlet;
import org.geowebcache.rest.RestletException;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.StringRepresentation;

/**
 * Used to get the bounds of layers for benchmarking purposes
 */
public class BoundsRestlet extends GWCRestlet {
    
    private TileLayerDispatcher layerDispatcher;
    
    public void handle(Request request, Response response) {
        Method met = request.getMethod();
        try {
            if (met.equals(Method.GET)) {
                doGet(request, response);
            } else {

                throw new RestletException("Method not allowed",
                        Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
            }

        } catch (RestletException re) {
            response.setEntity(re.getRepresentation());
            response.setStatus(re.getStatus());
        } catch (Exception e) {
            // Either GeoWebCacheException or IOException
            response.setEntity(e.getMessage() + " " + e.toString(), MediaType.TEXT_PLAIN);
            response.setStatus(Status.SERVER_ERROR_INTERNAL);
            e.printStackTrace();
        }
    }
    
    /**
     * GET outputs an existing layer
     * 
     * @param req
     * @param resp
     * @throws RestletException
     * @throws  
     */
    protected void doGet(Request req, Response resp) throws RestletException {
        String layerName = null;
        String srsStr = null;
        try {
            layerName = URLDecoder.decode((String) req.getAttributes().get("layer"), "UTF-8");
        } catch (UnsupportedEncodingException uee) { }
        try {
            srsStr = URLDecoder.decode((String) req.getAttributes().get("srs"), "UTF-8");
        } catch (UnsupportedEncodingException uee) { }
        
        String type = (String) req.getAttributes().get("type");
        resp.setEntity(doGetInternal(layerName, srsStr, type));
    }
    
    /** 
     * We separate out the internal to make unit testing easier
     * 
     * @param layerName
     * @param formatExtension
     * @return
     * @throws RestletException
     */
    protected Representation doGetInternal(String layerName, String gridSetId, String type) 
    throws RestletException {
        TileLayer tl = findTileLayer(layerName, layerDispatcher);
   
        if(tl == null) {
            throw new RestletException(layerName + " is not known", Status.CLIENT_ERROR_NOT_FOUND);
        }
        
        GridSubset grid = tl.getGridSubset(gridSetId);
        
        if(grid == null) {
            throw new RestletException(layerName + " does not support " + gridSetId, Status.CLIENT_ERROR_NOT_FOUND);
        }
        
        StringBuilder str = new StringBuilder();
        long[][] bounds = grid.getCoverages();
        
        if(type.equalsIgnoreCase("java")) {
            str.append("{");
            for(int i=0; i<bounds.length; i++) {
                str.append("{");
                
                for(int j=0; j<bounds[i].length; j++) {
                    str.append(bounds[i][j]);
                    
                    if(j+1 < bounds[i].length) {
                        str.append(", ");
                    }
                }
                
                str.append("}");
                
                if(i+1 < bounds.length) {
                    str.append(", ");
                }       
            }
            str.append("}");
            
            return new StringRepresentation(str.toString(), MediaType.TEXT_PLAIN);
        } else {
            throw new RestletException("Unknown or missing format extension : " + type, 
                    Status.CLIENT_ERROR_BAD_REQUEST);
        }
    }

    
    public void setTileLayerDispatcher(TileLayerDispatcher tileLayerDispatcher) {
        layerDispatcher = tileLayerDispatcher;
    }
}
