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

import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.util.Configuration;
import org.geowebcache.util.XMLConfiguration;
import org.geowebcache.util.XMLConfigurationTest;
import org.restlet.resource.Representation;

import junit.framework.TestCase;

/**
 *  Most of the work is done by XMLConfig and XStream, so this is fairly short
 */
public class TileLayerRestletTest extends TestCase {
    // For the gets we'll use a shared one
    TileLayerRestlet tlr = preparedTileLayerRestlet();
        
    protected void setUp() throws Exception {
        super.setUp();
    }
    
    public void testBogus() throws Exception {
	assertTrue(true);
    }
    //public void testGetXml() throws Exception {
    //    Representation rep = tlr.doGetInternal("topp:states", "xml");
    //}

    //public void testGetJson() throws Exception {
    //   Representation rep = tlr.doGetInternal("topp:states2", "json");
    //}
    
    //public void testGetInvalid() throws Exception {
    //    Representation rep = tlr.doGetInternal("topp:states", "jpeg");
    //}
    
    private XMLConfiguration loadXMLConfig() {
        InputStream is = XMLConfiguration.class.getResourceAsStream(XMLConfigurationTest.LATEST_FILENAME);
        XMLConfiguration xmlConfig = null;
        try {
            xmlConfig = new XMLConfiguration(is);
        } catch (Exception e) {
            // Do nothing
        }
        
        return xmlConfig;
    }
    
    TileLayerRestlet preparedTileLayerRestlet() {
        TileLayerDispatcher layerDispatcher = new TileLayerDispatcher();
        XMLConfiguration xmlConfig = loadXMLConfig();
        LinkedList<Configuration> configList = new LinkedList<Configuration>();
        configList.add(xmlConfig);
        layerDispatcher.setConfig(configList);
        
        TileLayerRestlet tlr = new TileLayerRestlet();
        tlr.setXMLConfiguration(xmlConfig);
        tlr.setTileLayerDispatcher(layerDispatcher);
        
        return tlr;
    }
    
}
