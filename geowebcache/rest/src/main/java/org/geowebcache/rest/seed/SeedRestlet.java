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
package org.geowebcache.rest.seed;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.ContextualConfigurationProvider.Context;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.io.GeoWebCacheXStream;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.rest.GWCRestlet;
import org.geowebcache.rest.RestletException;
import org.geowebcache.seed.SeedRequest;
import org.geowebcache.seed.TileBreeder;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.resource.Representation;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.json.JsonHierarchicalStreamDriver;

public class SeedRestlet extends GWCSeedingRestlet {
    @SuppressWarnings("unused")
    private static Log log = LogFactory.getLog(SeedFormRestlet.class);

    private TileBreeder seeder;

    /**
     * Returns a StringRepresentation with the status of the running threads in the thread pool.
     */
    public void doGet(Request req, Response resp) throws RestletException {
        Representation rep = null;

        final String layerName;
        if (req.getAttributes().containsKey("layer")) {
            try {
                layerName = URLDecoder.decode((String) req.getAttributes().get("layer"), "UTF-8");
            } catch (UnsupportedEncodingException uee) {
                throw new RuntimeException(uee);
            }
        } else {
            layerName = null;
        }
        try {
            XStream xs = new GeoWebCacheXStream(new JsonHierarchicalStreamDriver());
            JSONObject obj = null;
            long[][] list;
            if (null == layerName) {
                list = seeder.getStatusList();
            } else {
                try {
                    seeder.findTileLayer(layerName);
                } catch (GeoWebCacheException e) {
                    throw new RestletException(e.getMessage(), Status.CLIENT_ERROR_BAD_REQUEST);
                }
                list = seeder.getStatusList(layerName);
            }
            obj = new JSONObject(xs.toXML(list));

            rep = new JsonRepresentation(obj);
        } catch (JSONException jse) {
            jse.printStackTrace();
        }

        resp.setEntity(rep);
    }

    protected void handleRequest(Request req, Response resp, Object obj) {
        final SeedRequest sr = (SeedRequest) obj;
        String layerName = null;
        try {
            layerName = URLDecoder.decode((String) req.getAttributes().get("layer"), "UTF-8");
        } catch (UnsupportedEncodingException uee) {
        }

        try {
            seeder.seed(layerName, sr);
        } catch (IllegalArgumentException e) {
            throw new RestletException(e.getMessage(), Status.CLIENT_ERROR_BAD_REQUEST);
        } catch (GeoWebCacheException e) {
            throw new RestletException(e.getMessage(), Status.SERVER_ERROR_INTERNAL);
        }

    }

    public void setTileBreeder(TileBreeder seeder) {
        this.seeder = seeder;
    }
}
