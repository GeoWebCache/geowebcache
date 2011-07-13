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
 * @author Arne Kepp / The Open Planning Project 2009 
 */
package org.geowebcache.rest;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.restlet.Restlet;
import org.restlet.data.Status;

import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.copy.HierarchicalStreamCopier;
import com.thoughtworks.xstream.io.json.JettisonMappedXmlDriver;
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;

public class GWCRestlet extends Restlet {
    
    protected static TileLayer findTileLayer(String layerName, TileLayerDispatcher layerDispatcher) {
        if(layerName == null || layerName.length() == 0) {
            throw new RestletException("Layer not specified",
                    Status.CLIENT_ERROR_BAD_REQUEST);
        }
        
        TileLayer layer = null;
        try {
            layer = layerDispatcher.getTileLayer(layerName);
        } catch (GeoWebCacheException gwce) {
            throw new RestletException("Encountered error: " + gwce.getMessage(), 
                    Status.SERVER_ERROR_INTERNAL);
        }
        
        if(layer == null) {
            throw new RestletException("Uknown layer: " + layerName, 
                    Status.CLIENT_ERROR_NOT_FOUND);
        }
        
        return layer;
    }

    /**
     * Deserializing a json string is more complicated. 
     * 
     * XStream does not natively support it. Rather, it uses a 
     * JettisonMappedXmlDriver to convert to intermediate xml and 
     * then deserializes that into the desired object. At this time, 
     * there is a known issue with the Jettison driver involving 
     * elements that come after an array in the json string.
     * 
     * http://jira.codehaus.org/browse/JETTISON-48
     * 
     * The code below is a hack: it treats the json string as text, then
     * converts it to the intermediate xml and then deserializes that
     * into the SeedRequest object.
     */
    protected String convertJson(String entityText) throws IOException {
        HierarchicalStreamDriver driver = new JettisonMappedXmlDriver();
        StringReader reader = new StringReader(entityText);
        HierarchicalStreamReader hsr = driver.createReader(reader);
        StringWriter writer = new StringWriter();
        new HierarchicalStreamCopier().copy(hsr, new PrettyPrintWriter(
                writer));
        writer.close();
        return writer.toString();
    }
}
