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
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 * 
 */
package org.geowebcache.service.wms;

import java.util.Arrays;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;

import junit.framework.TestCase;

import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.wms.WMSLayer;

public class WMSTileFuserTest extends TestCase {
    GridSetBroker gridSetBroker = new GridSetBroker(false, false);
    
    public void testTileFuserResolution() throws Exception {
        TileLayer layer = createWMSLayer();
        
        // request fits inside -30.0,15.0,45.0,30
        BoundingBox bounds = new BoundingBox(-25.0,17.0,40.0,22);
        
        // One in between
        int width = (int) bounds.getWidth() * 10;
        int height= (int) bounds.getHeight() * 10;
        GridSubset gridSubset = layer.getGridSubset(layer.getGridSubsets().iterator().next());
        WMSTileFuser tileFuser = new WMSTileFuser(layer, gridSubset, bounds, width, height);
        tileFuser.determineSourceResolution();
        assertEquals(0.087890625, tileFuser.srcResolution, 0.087890625*0.001);
        
        // Zoomed too far out
        height = (int) bounds.getWidth() / 10;
        width = (int) bounds.getWidth() / 10;
        tileFuser = new WMSTileFuser(layer, gridSubset, bounds, width, height);
        tileFuser.determineSourceResolution();
        assertEquals(0,tileFuser.srcIdx);
        
        // Zoomed too far in
        height = (int) bounds.getWidth() * 10000;
        width = (int) bounds.getWidth() * 10000;
        tileFuser = new WMSTileFuser(layer, gridSubset, bounds, width, height);
        tileFuser.determineSourceResolution();
        assertEquals(10,tileFuser.srcIdx);
    }
    
    public void testTileFuserSubset() throws Exception {
        TileLayer layer = createWMSLayer();
        
        // request fits inside -30.0,15.0,45.0,30
        BoundingBox bounds = new BoundingBox(-25.0,17.0,40.0,22);
        
        // One in between
        int width = (int) bounds.getWidth() * 10;
        int height= (int) bounds.getHeight() * 10;
        GridSubset gridSubset = layer.getGridSubset(layer.getGridSubsets().iterator().next());
        WMSTileFuser tileFuser = new WMSTileFuser(layer, gridSubset, bounds, width, height);
        tileFuser.determineSourceResolution();
        tileFuser.determineCanvasLayout();
        
        assertTrue(tileFuser.srcBounds.contains(bounds));
        int[] comparison = {-228, -193, -57, -6}; 
        assertTrue(Arrays.equals(tileFuser.canvOfs,comparison));
    }
    
    public void testTileFuserSuperset() throws Exception {
        TileLayer layer = createWMSLayer();
        
        // request larger than -30.0,15.0,45.0,30
        BoundingBox bounds = new BoundingBox(-35.0,14.0,55.0,39);
        
        // One in between
        int width = (int) bounds.getWidth() * 25;
        int height= (int) bounds.getHeight() * 25;
        GridSubset gridSubset = layer.getGridSubset(layer.getGridSubsets().iterator().next());
        WMSTileFuser tileFuser = new WMSTileFuser(layer, gridSubset, bounds, width, height);
        tileFuser.determineSourceResolution();
        tileFuser.determineCanvasLayout();
    }

    private WMSLayer createWMSLayer() {
        String[] urls = {"http://localhost:38080/wms"};
        List<String> formatList = new LinkedList<String>();
        formatList.add("image/png");
        
        Hashtable<String,GridSubset> grids = new Hashtable<String,GridSubset>();

        GridSubset grid = GridSubsetFactory.createGridSubSet(gridSetBroker.WORLD_EPSG4326, new BoundingBox(-30.0,15.0,45.0,30), 0,10);
        
        grids.put(grid.getName(), grid);
        int[] metaWidthHeight = {3,3};
        
        WMSLayer layer = new WMSLayer("test:layer", urls, "aStyle", "test:layer", formatList, grids, null, metaWidthHeight, "vendorparam=true", false);
        
        layer.initialize(gridSetBroker);
        
        return layer;
    }
}
