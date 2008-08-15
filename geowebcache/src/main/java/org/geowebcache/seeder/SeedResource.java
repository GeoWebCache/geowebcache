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
package org.geowebcache.seeder;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.RESTDispatcher;
import org.geowebcache.layer.SRS;
import org.geowebcache.util.wms.BBOX;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.resource.Representation;
import org.restlet.resource.Resource;
import org.restlet.resource.Variant;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.json.JsonHierarchicalStreamDriver;
import com.thoughtworks.xstream.io.json.JettisonMappedXmlDriver;
import com.thoughtworks.xstream.io.xml.DomDriver;
import java.io.IOException;
import java.util.concurrent.*;


public class SeedResource extends Resource {
    private static int[][] statusArray = new int[getExecutor().getCorePoolSize()][3]; 
    public JSONObject myrequest; 
    private static Log log = LogFactory
            .getLog(org.geowebcache.seeder.SeedResource.class);
    /**
     * Constructor
     * @param context
     * @param request
     * @param response
     */
    public SeedResource(Context context, Request request, Response response) {
        super(context, request, response);
        getVariants().add(new Variant(MediaType.APPLICATION_JSON));
        getVariants().add(new Variant(MediaType.APPLICATION_XML));
    }
    /**
     * Method returns a StringRepresentation with the status of the running threads
     * in the thread pool. 
     */
    public Representation getRepresentation(Variant variant) {
        Representation rep = null;
        
        try {            
           XStream xs = new XStream(new JsonHierarchicalStreamDriver());
           JSONObject obj = null;
           int[][] list = getStatusList();
           synchronized (list) {
               obj = new JSONObject(xs.toXML(list));
           }     
           
           rep = new JsonRepresentation(obj);
        } catch (JSONException jse) {
                jse.printStackTrace();
        }

        return rep; 
    }

    /**
     * Method responsible for handling incoming POSTs. It will parse the xml document and
     * deserialize it into a SeedRequest, then create a SeedTask and forward it to the 
     * thread pool executor. 
     */
    @Override
    public void post(Representation entity) {
        log.info("Received seed request from  "
                + getRequest().getHostRef().getHostIdentifier());

        try {
            String text = entity.getText();
            XStream xs = null;
            if(entity.getMediaType().equals(MediaType.APPLICATION_XML))
                xs = new XStream(new DomDriver());
            else if(entity.getMediaType().equals(MediaType.APPLICATION_JSON)){
                xs = new XStream(new JettisonMappedXmlDriver());
            }
            xs.alias("seedRequest", SeedRequest.class);
            xs.alias("format", String.class);
            xs.alias("projection", SRS.class);
            xs.alias("bounds", BBOX.class);
            xs.alias("zoomstart", Integer.class);
            xs.alias("zoomstop", Integer.class);
            SeedRequest rq = (SeedRequest) xs.fromXML(text);

            XStream xst = new XStream(new JettisonMappedXmlDriver());
            xst.alias("seedRequest", SeedRequest.class);
            xst.alias("format", String.class);
            xst.alias("projection", SRS.class);
            xst.alias("bounds", BBOX.class);
            xst.alias("zoomstart", Integer.class);
            xst.alias("zoomstop", Integer.class);
            
            String json = xst.toXML(rq);
            SeedRequest rq1 = (SeedRequest) xst.fromXML(json);
            System.out.println("json for is " + json);
            
            getExecutor().submit(new MTSeeder(new SeedTask(rq)));

        } catch (IOException ioex) {
            log.error("Exception occured while unmarshalling SeedRequest from XML");
        }
    }

    /**
     * Method returns the thread pool executor that handles seeds
     * @return
     */
    public static ThreadPoolExecutor getExecutor() {
        return RESTDispatcher.getExecutor();
    }
    
    /**
     * Method returns List of Strings representing the status of the currently running threads
     * @return
     */
    public static int[][] getStatusList() {
        return statusArray;
    }

    public boolean allowPost() {
        return true;
    }

}
