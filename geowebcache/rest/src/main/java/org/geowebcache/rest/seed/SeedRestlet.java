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

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.rest.GWCRestlet;
import org.geowebcache.rest.RestletException;
import org.geowebcache.seed.SeedRequest;
import org.geowebcache.seed.TileBreeder;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.resource.Representation;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.copy.HierarchicalStreamCopier;
import com.thoughtworks.xstream.io.json.JettisonMappedXmlDriver;
import com.thoughtworks.xstream.io.json.JsonHierarchicalStreamDriver;
import com.thoughtworks.xstream.io.xml.DomDriver;
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;

public class SeedRestlet extends GWCRestlet {
    private static Log log = LogFactory.getLog(SeedFormRestlet.class);

    private TileBreeder seeder;

    public JSONObject myrequest;

    private XMLConfiguration xmlConfig;

    public void handle(Request request, Response response) {
        Method met = request.getMethod();
        try {
            if (met.equals(Method.GET)) {
                doGet(request, response);
            } else if (met.equals(Method.POST)) {
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
            XStream xs = new XStream(new JsonHierarchicalStreamDriver());
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

    /**
     * Method responsible for handling incoming POSTs. It will parse the XML document and
     * deserialize it into a SeedRequest, then create a SeedTask and forward it to the thread pool
     * executor.
     */
    public void doPost(Request req, Response resp) throws RestletException, IOException {
        String formatExtension = (String) req.getAttributes().get("extension");

        SeedRequest sr = null;

        XStream xs = xmlConfig.getConfiguredXStream(new XStream(new DomDriver()));

        if (formatExtension.equalsIgnoreCase("xml")) {
            sr = (SeedRequest) xs.fromXML(req.getEntity().getStream());
        } else if (formatExtension.equalsIgnoreCase("json")) {
            sr = (SeedRequest) xs.fromXML(convertJson(req.getEntity().getText()));
        } else {
            throw new RestletException("Format extension unknown or not specified: "
                    + formatExtension, Status.CLIENT_ERROR_BAD_REQUEST);
        }

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

    /**
     * Deserializing a json string is more complicated.
     * 
     * XStream does not natively support it. Rather, it uses a JettisonMappedXmlDriver to convert to
     * intermediate xml and then deserializes that into the desired object. At this time, there is a
     * known issue with the Jettison driver involving elements that come after an array in the json
     * string.
     * 
     * http://jira.codehaus.org/browse/JETTISON-48
     * 
     * The code below is a hack: it treats the json string as text, then converts it to the
     * intermediate xml and then deserializes that into the SeedRequest object.
     */
    private String convertJson(String entityText) throws IOException {
        HierarchicalStreamDriver driver = new JettisonMappedXmlDriver();
        StringReader reader = new StringReader(entityText);
        HierarchicalStreamReader hsr = driver.createReader(reader);
        StringWriter writer = new StringWriter();
        new HierarchicalStreamCopier().copy(hsr, new PrettyPrintWriter(writer));
        writer.close();
        return writer.toString();
    }

    public void setXmlConfig(XMLConfiguration xmlConfig) {
        this.xmlConfig = xmlConfig;
    }

    public void setTileBreeder(TileBreeder seeder) {
        this.seeder = seeder;
    }
}
