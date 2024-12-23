/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Kevin Smith, Boundless, 2017
 */
package org.geowebcache.filter.security;

import static org.junit.Assert.assertThrows;

import org.easymock.EasyMock;
import org.geowebcache.MockExtensionRule;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.SRS;
import org.geowebcache.layer.TileLayer;
import org.junit.Rule;
import org.junit.Test;

public class SecurityDispatcherTest {

    @Rule
    public MockExtensionRule extensions = new MockExtensionRule();

    @Test
    public void testOneFilterPass() throws Exception {

        SecurityDispatcher secDispatcher = new SecurityDispatcher();
        secDispatcher.setApplicationContext(extensions.getMockContext());

        ConveyorTile conv = EasyMock.createMock("conv", ConveyorTile.class);
        TileLayer layer = EasyMock.createMock("layer", TileLayer.class);
        GridSubset subset = EasyMock.createMock("subset", GridSubset.class);
        SRS srs = EasyMock.createMock("srs", SRS.class);

        BoundingBox bbox = new BoundingBox(10, 20, 30, 40);

        SecurityFilter filter = EasyMock.createMock("filter", SecurityFilter.class);
        extensions.addBean("testSecurityFilter", filter, SecurityFilter.class);

        EasyMock.expect(conv.getLayer()).andStubReturn(layer);
        EasyMock.expect(conv.getGridSubset()).andStubReturn(subset);
        EasyMock.expect(conv.getTileIndex()).andStubReturn(new long[] {1, 2, 3});
        EasyMock.expect(subset.boundsFromIndex(EasyMock.aryEq(new long[] {1, 2, 3})))
                .andStubReturn(bbox);
        EasyMock.expect(subset.getSRS()).andStubReturn(srs);

        filter.checkSecurity(layer, bbox, srs);
        EasyMock.expectLastCall().once();

        EasyMock.replay(conv, layer, subset, srs, filter);

        secDispatcher.checkSecurity(conv);

        EasyMock.verify(conv, layer, subset, srs, filter);
    }

    @Test
    public void testOneFilterFail() throws Exception {

        SecurityDispatcher secDispatcher = new SecurityDispatcher();
        secDispatcher.setApplicationContext(extensions.getMockContext());

        ConveyorTile conv = EasyMock.createMock("conv", ConveyorTile.class);
        TileLayer layer = EasyMock.createMock("layer", TileLayer.class);
        GridSubset subset = EasyMock.createMock("subset", GridSubset.class);
        SRS srs = EasyMock.createMock("srs", SRS.class);

        BoundingBox bbox = new BoundingBox(10, 20, 30, 40);

        SecurityFilter filter = EasyMock.createMock("filter", SecurityFilter.class);
        extensions.addBean("testSecurityFilter", filter, SecurityFilter.class);

        EasyMock.expect(conv.getLayer()).andStubReturn(layer);
        EasyMock.expect(conv.getGridSubset()).andStubReturn(subset);
        EasyMock.expect(conv.getTileIndex()).andStubReturn(new long[] {1, 2, 3});
        EasyMock.expect(subset.boundsFromIndex(EasyMock.aryEq(new long[] {1, 2, 3})))
                .andStubReturn(bbox);
        EasyMock.expect(subset.getSRS()).andStubReturn(srs);

        filter.checkSecurity(layer, bbox, srs);
        EasyMock.expectLastCall().andThrow(new SecurityException()).once();

        EasyMock.replay(conv, layer, subset, srs, filter);

        assertThrows(SecurityException.class, () -> secDispatcher.checkSecurity(conv));
        EasyMock.verify(conv, layer, subset, srs, filter);
    }

    @Test
    public void testTwoFilterPass() throws Exception {

        SecurityDispatcher secDispatcher = new SecurityDispatcher();
        secDispatcher.setApplicationContext(extensions.getMockContext());

        ConveyorTile conv = EasyMock.createMock("conv", ConveyorTile.class);
        TileLayer layer = EasyMock.createMock("layer", TileLayer.class);
        GridSubset subset = EasyMock.createMock("subset", GridSubset.class);
        SRS srs = EasyMock.createMock("srs", SRS.class);

        BoundingBox bbox = new BoundingBox(10, 20, 30, 40);

        SecurityFilter filter1 = EasyMock.createMock("filter1", SecurityFilter.class);
        SecurityFilter filter2 = EasyMock.createMock("filter2", SecurityFilter.class);
        extensions.addBean("testSecurityFilter1", filter1, SecurityFilter.class);
        extensions.addBean("testSecurityFilter2", filter2, SecurityFilter.class);

        EasyMock.expect(conv.getLayer()).andStubReturn(layer);
        EasyMock.expect(conv.getGridSubset()).andStubReturn(subset);
        EasyMock.expect(conv.getTileIndex()).andStubReturn(new long[] {1, 2, 3});
        EasyMock.expect(subset.boundsFromIndex(EasyMock.aryEq(new long[] {1, 2, 3})))
                .andStubReturn(bbox);
        EasyMock.expect(subset.getSRS()).andStubReturn(srs);

        filter1.checkSecurity(layer, bbox, srs);
        EasyMock.expectLastCall().once();
        filter2.checkSecurity(layer, bbox, srs);
        EasyMock.expectLastCall().once();

        EasyMock.replay(conv, layer, subset, srs, filter1, filter2);

        secDispatcher.checkSecurity(conv);

        EasyMock.verify(conv, layer, subset, srs, filter1, filter2);
    }

    @Test
    public void testTwoFilterFail() throws Exception {

        SecurityDispatcher secDispatcher = new SecurityDispatcher();
        secDispatcher.setApplicationContext(extensions.getMockContext());

        ConveyorTile conv = EasyMock.createMock("conv", ConveyorTile.class);
        TileLayer layer = EasyMock.createMock("layer", TileLayer.class);
        GridSubset subset = EasyMock.createMock("subset", GridSubset.class);
        SRS srs = EasyMock.createMock("srs", SRS.class);

        BoundingBox bbox = new BoundingBox(10, 20, 30, 40);

        SecurityFilter filter1 = EasyMock.createMock("filter1", SecurityFilter.class);
        SecurityFilter filter2 = EasyMock.createMock("filter2", SecurityFilter.class);
        extensions.addBean("testSecurityFilter1", filter1, SecurityFilter.class);
        extensions.addBean("testSecurityFilter2", filter2, SecurityFilter.class);

        EasyMock.expect(conv.getLayer()).andStubReturn(layer);
        EasyMock.expect(conv.getGridSubset()).andStubReturn(subset);
        EasyMock.expect(conv.getTileIndex()).andStubReturn(new long[] {1, 2, 3});
        EasyMock.expect(subset.boundsFromIndex(EasyMock.aryEq(new long[] {1, 2, 3})))
                .andStubReturn(bbox);
        EasyMock.expect(subset.getSRS()).andStubReturn(srs);

        filter1.checkSecurity(layer, bbox, srs);
        EasyMock.expectLastCall().andThrow(new SecurityException()).once();
        filter2.checkSecurity(layer, bbox, srs);
        EasyMock.expectLastCall().times(0, 1);

        EasyMock.replay(conv, layer, subset, srs, filter1, filter2);

        assertThrows(SecurityException.class, () -> secDispatcher.checkSecurity(conv));
        EasyMock.verify(conv, layer, subset, srs, filter1, filter2);
    }

    @Test
    public void testNoFilterPass() throws Exception {

        SecurityDispatcher secDispatcher = new SecurityDispatcher();
        secDispatcher.setApplicationContext(extensions.getMockContext());

        ConveyorTile conv = EasyMock.createMock("conv", ConveyorTile.class);
        TileLayer layer = EasyMock.createMock("layer", TileLayer.class);
        GridSubset subset = EasyMock.createMock("subset", GridSubset.class);
        SRS srs = EasyMock.createMock("srs", SRS.class);

        BoundingBox bbox = new BoundingBox(10, 20, 30, 40);

        EasyMock.expect(conv.getLayer()).andStubReturn(layer);
        EasyMock.expect(conv.getGridSubset()).andStubReturn(subset);
        EasyMock.expect(conv.getTileIndex()).andStubReturn(new long[] {1, 2, 3});
        EasyMock.expect(subset.boundsFromIndex(EasyMock.aryEq(new long[] {1, 2, 3})))
                .andStubReturn(bbox);
        EasyMock.expect(subset.getSRS()).andStubReturn(srs);

        EasyMock.replay(conv, layer, subset, srs);

        secDispatcher.checkSecurity(conv);

        EasyMock.verify(conv, layer, subset, srs);
    }
}
