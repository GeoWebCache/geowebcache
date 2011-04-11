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
package org.geowebcache.rest.layers;

import java.io.InputStream;
import java.util.LinkedList;

import junit.framework.TestCase;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.Configuration;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.config.XMLConfigurationTest;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.rest.RestletException;
import org.restlet.resource.Representation;

/**
 * Most of the work is done by XMLConfig and XStream, so this is fairly short
 */
public class TileLayerRestletTest extends TestCase {
    // For the gets we'll use a shared one
    TileLayerRestlet tlr;

    protected void setUp() throws Exception {
        tlr = preparedTileLayerRestlet();
    }

    // public void testBogus() throws Exception {
    // assertTrue(true);
    // }

    public void testGetXml() throws Exception {
        Representation rep = tlr.doGetInternal("topp:states", "xml");

        String str = rep.getText();

        assertTrue(str.indexOf("<name>topp:states</name>") > 0);
        // TODO This needs to get back in
        // assertTrue(str.indexOf("<double>49.371735</double>") > 0);
        // assertTrue(str.indexOf("<wmsStyles>population</wmsStyles>") > 0);
        assertTrue(str.indexOf("</wmsLayer>") > 0);
        assertTrue(str.indexOf("states2") == -1);
    }

    public void testGetJson() throws Exception {
        Representation rep = tlr.doGetInternal("topp:states2", "json");

        String str = rep.getText();

        assertTrue(str.indexOf(",\"name\":\"topp:states2\",") > 0);
        // TODO this needs to go back in
        // assertTrue(str.indexOf("959189.3312465074]},") > 0);
        assertTrue(str.indexOf("[\"image/png\",\"image/jpeg\"]") > 0);
        assertTrue(str.indexOf("}}") > 0);
    }

    public void testGetInvalid() throws Exception {
        Representation rep = null;
        try {
            rep = tlr.doGetInternal("topp:states", "jpeg");
        } catch (RestletException re) {
            // Format should be invalid
            assertTrue(re.getRepresentation().getText().indexOf("format") > 0);
        }
        assertTrue(rep == null);
    }

    private XMLConfiguration loadXMLConfig() {
        InputStream is = XMLConfiguration.class
                .getResourceAsStream(XMLConfigurationTest.LATEST_FILENAME);
        XMLConfiguration xmlConfig = null;
        try {
            xmlConfig = new XMLConfiguration(is);
        } catch (Exception e) {
            // Do nothing
        }

        return xmlConfig;
    }

    TileLayerRestlet preparedTileLayerRestlet() throws GeoWebCacheException {

        GridSetBroker gridSetBroker = new GridSetBroker(false, false);
        XMLConfiguration xmlConfig = loadXMLConfig();
        xmlConfig.initialize(gridSetBroker);
        LinkedList<Configuration> configList = new LinkedList<Configuration>();
        configList.add(xmlConfig);

        TileLayerDispatcher layerDispatcher = new TileLayerDispatcher(gridSetBroker, configList);

        TileLayerRestlet tlr = new TileLayerRestlet();
        tlr.setXMLConfiguration(xmlConfig);
        tlr.setTileLayerDispatcher(layerDispatcher);

        return tlr;
    }

}
