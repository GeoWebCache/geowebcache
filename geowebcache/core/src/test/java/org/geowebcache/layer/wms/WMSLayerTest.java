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
 */
package org.geowebcache.layer.wms;

import static org.easymock.EasyMock.expect;
import static org.easymock.classextension.EasyMock.replay;
import static org.easymock.classextension.EasyMock.verify;

import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import junit.framework.TestCase;

import org.easymock.Capture;
import org.easymock.classextension.EasyMock;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.TileObject;
import org.geowebcache.util.MockWMSSourceHelper;

import com.mockrunner.mock.web.MockHttpServletRequest;
import com.mockrunner.mock.web.MockHttpServletResponse;

/**
 * Unit test suite for {@link WMSLayer}
 * 
 * @author Gabriel Roldan (OpenGeo)
 * @version $Id$
 */
public class WMSLayerTest extends TestCase {

    private final GridSetBroker gridSetBroker = new GridSetBroker(false, false);

    public void testSeedMetaTiled() throws Exception {
        WMSLayer layer = createWMSLayer("image/png");

        WMSSourceHelper mockSourceHelper = new MockWMSSourceHelper();

        layer.setSourceHelper(mockSourceHelper);

        final StorageBroker mockStorageBroker = EasyMock.createMock(StorageBroker.class);
        Capture<TileObject> captured = new Capture<TileObject>();
        expect(mockStorageBroker.put(EasyMock.capture(captured))).andReturn(true).anyTimes();
        replay(mockStorageBroker);

        String layerId = layer.getName();
        HttpServletRequest servletReq = new MockHttpServletRequest();
        HttpServletResponse servletResp = new MockHttpServletResponse();

        long[] gridLoc = { 0, 0, 0 };// x, y, level
        MimeType mimeType = layer.getMimeTypes().get(0);
        GridSet gridSet = gridSetBroker.WORLD_EPSG4326;
        String gridSetId = gridSet.getName();
        ConveyorTile tile = new ConveyorTile(mockStorageBroker, layerId, gridSetId, gridLoc,
                mimeType, null, null, servletReq, servletResp);

        boolean tryCache = false;
        layer.seedTile(tile, tryCache);

        assertEquals(1, captured.getValues().size());
        TileObject value = captured.getValue();
        assertNotNull(value);
        assertEquals("image/png", value.getBlobFormat());
        assertNotNull(value.getBlob());
        assertTrue(value.getBlob().getSize() > 0);

        verify(mockStorageBroker);
    }

    private WMSLayer createWMSLayer(final String format) {

        String[] urls = { "http://localhost:38080/wms" };
        List<String> formatList = Collections.singletonList(format);

        Hashtable<String, GridSubset> grids = new Hashtable<String, GridSubset>();

        GridSubset grid = GridSubsetFactory.createGridSubSet(gridSetBroker.WORLD_EPSG4326,
                new BoundingBox(-30.0, 15.0, 45.0, 30), 0, 10);

        grids.put(grid.getName(), grid);
        int[] metaWidthHeight = { 3, 3 };

        WMSLayer layer = new WMSLayer("test:layer", urls, "aStyle", "test:layer", formatList,
                grids, null, metaWidthHeight, "vendorparam=true", false);

        layer.initialize(gridSetBroker);

        return layer;
    }

}
