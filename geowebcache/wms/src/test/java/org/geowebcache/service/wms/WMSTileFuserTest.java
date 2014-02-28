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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Date;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;

import junit.framework.TestCase;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.io.FileResource;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.stats.RuntimeStats;
import org.geowebcache.storage.DefaultStorageBroker;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.blobstore.file.FileBlobStore;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.mockrunner.mock.web.MockHttpServletRequest;
import com.mockrunner.mock.web.MockHttpServletResponse;

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
        WMSTileFuser.PixelOffsets comparison = new WMSTileFuser.PixelOffsets();
        //-228, -193, -56, -6
        comparison.left=-228;
        comparison.bottom=-193;
        comparison.right=-56;
        comparison.top=-6;
        assertEquals(comparison.left, tileFuser.canvOfs.left);
        assertEquals(comparison.bottom, tileFuser.canvOfs.bottom);
        assertEquals(comparison.right, tileFuser.canvOfs.right);
        assertEquals(comparison.top, tileFuser.canvOfs.top);
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

    public void testWriteResponse() throws Exception {
    	final TileLayer layer = createWMSLayer();
    	// request larger than -30.0,15.0,45.0,30
        BoundingBox bounds = new BoundingBox(-35.0,14.0,55.0,39);
        
        // One in between
        int width = (int) bounds.getWidth() * 25;
        int height= (int) bounds.getHeight() * 25;
        layer.getGridSubset(layer.getGridSubsets().iterator().next());
        File temp = File.createTempFile("gwc", "wms");
        temp.delete();
        temp.mkdirs();
        try {
	        TileLayerDispatcher dispatcher = new TileLayerDispatcher(gridSetBroker) {

				@Override
				public TileLayer getTileLayer(String layerName)
						throws GeoWebCacheException {
					return layer;
				}
	        	
	        };  
	        
	        MockHttpServletRequest request = new MockHttpServletRequest();
	        request.setupAddParameter("layers", new String[] { "test:layer" });
	        request.setupAddParameter("srs", new String[] { "EPSG:4326" });
	        request.setupAddParameter("format", new String[] { "image/png8" });
	        request.setupAddParameter("width", width +"");
	        request.setupAddParameter("height", height +"");
	        request.setupAddParameter("bbox", bounds.toString());
	        final File imageTile = new File(getClass().getResource("/image.png").toURI());
	        
	        StorageBroker broker = new DefaultStorageBroker(
	        	new FileBlobStore(temp.getAbsolutePath()) {

					@Override
					public boolean get(TileObject stObj)
							throws StorageException {
						stObj.setBlob(new FileResource(imageTile));
			            stObj.setCreated((new Date()).getTime());
			            stObj.setBlobSize(1000);
						return true;
					}
	        		
	        	}
	        );
	        
	        WMSTileFuser tileFuser = new WMSTileFuser(dispatcher, broker, request);
	        	
	        // Selection of the ApplicationContext associated
	        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("appContextTest.xml");
	        tileFuser.setApplicationContext(context);
	        MockHttpServletResponse response = new MockHttpServletResponse();

            tileFuser.writeResponse(response,
                    new RuntimeStats(1, Arrays.asList(1), Arrays.asList("desc")));

            assertTrue(response.getOutputStreamContent().length() > 0);
        } finally {
        	temp.delete();
        }
    }
    
    private WMSLayer createWMSLayer() {
        String[] urls = {"http://localhost:38080/wms"};
        List<String> formatList = new LinkedList<String>();
        formatList.add("image/png");
        
        Hashtable<String,GridSubset> grids = new Hashtable<String,GridSubset>();

        GridSubset grid = GridSubsetFactory.createGridSubSet(gridSetBroker.WORLD_EPSG4326, new BoundingBox(-30.0,15.0,45.0,30), 0,10);
        
        grids.put(grid.getName(), grid);
        int[] metaWidthHeight = {3,3};
        
        WMSLayer layer = new WMSLayer("test:layer", urls, "aStyle", "test:layer", formatList, grids, null, metaWidthHeight, "vendorparam=true", false, null);
        
        layer.initialize(gridSetBroker);
        
        return layer;
    }
}
