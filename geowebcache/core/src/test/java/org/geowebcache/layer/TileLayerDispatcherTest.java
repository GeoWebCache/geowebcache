/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * <p>Copyright 2018
 */
package org.geowebcache.layer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.GWCConfigIntegrationTest;
import org.geowebcache.config.GWCConfigIntegrationTestData;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetFactory;
import org.geowebcache.grid.SRS;
import org.geowebcache.layer.wms.WMSLayer;
import org.junit.Test;
import org.mockito.Mockito;

public class TileLayerDispatcherTest extends GWCConfigIntegrationTest {

    @Test
    public void testLayerList() {
        int count = tileLayerDispatcher.getLayerCount();
        Set<String> names = tileLayerDispatcher.getLayerNames();

        int i = 0;
        for (TileLayer layer : tileLayerDispatcher.getLayerList()) {
            assertTrue(names.contains(layer.getName()));
            i++;
        }
        assertEquals(count, i);
        assertEquals(count, names.size());
    }

    @Test
    public void testAddLayer() throws GeoWebCacheException {
        String layerName = "newLayer";

        WMSLayer layer =
                new WMSLayer(
                        layerName,
                        new String[] {"http://example.com/"},
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        null);

        tileLayerDispatcher.addLayer(layer);

        assertTrue(tileLayerDispatcher.getLayerNames().contains(layerName));
        assertEquals(layer, tileLayerDispatcher.getTileLayer(layerName));
    }

    @Test
    public void testAddBadLayer() throws GeoWebCacheException {
        String duplicateLayerName = GWCConfigIntegrationTestData.LAYER_TOPP_STATES;
        String transientLayerName = "transientLayer";

        TileLayer layer = tileLayerDispatcher.getTileLayer(duplicateLayerName);
        try {
            tileLayerDispatcher.addLayer(layer);
            fail("Expected error when adding a layer that already exists");
        } catch (IllegalArgumentException e) {

        }
        layer =
                new WMSLayer(
                        transientLayerName,
                        new String[] {"http://example.com/"},
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        null);
        layer.setTransientLayer(true);

        try {
            tileLayerDispatcher.addLayer(layer);
            fail("Expected error when adding a transient layer");
        } catch (IllegalArgumentException e) {

        }

        try {
            tileLayerDispatcher.addLayer(null);
            fail("Expected error when adding a null layer");
        } catch (NullPointerException e) {

        }
    }

    @Test
    public void testRemoveLayer() {
        String layerToRemove = GWCConfigIntegrationTestData.LAYER_TOPP_STATES;
        tileLayerDispatcher.removeLayer(layerToRemove);

        assertFalse(tileLayerDispatcher.getLayerNames().contains(layerToRemove));

        try {
            tileLayerDispatcher.getTileLayer(layerToRemove);
            fail("Expected exception when trying to get removed layer");
        } catch (GeoWebCacheException e) {

        }
    }

    @Test
    public void testRemoveLayerException() {
        try {
            tileLayerDispatcher.removeLayer(null);
            fail("Expected failure when trying to remove null layer");
        } catch (Exception e) {
        }

        try {
            tileLayerDispatcher.removeLayer("nonexistantLayer");
            fail("Expected failure when trying to remove nonexistant layer");
        } catch (Exception e) {
        }
    }

    @Test
    public void testModifyLayer() throws GeoWebCacheException {
        String modifiedLayerName = GWCConfigIntegrationTestData.LAYER_TOPP_STATES;
        TileLayer layer = tileLayerDispatcher.getTileLayer(modifiedLayerName);
        layer.removeGridSubset(GWCConfigIntegrationTestData.GRIDSET_EPSG2163);
        boolean advertised = !layer.isAdvertised();
        layer.setAdvertised(advertised);

        tileLayerDispatcher.modify(layer);

        TileLayer modifiedLayer = tileLayerDispatcher.getTileLayer(modifiedLayerName);
        assertEquals(layer, modifiedLayer);
        assertEquals(advertised, modifiedLayer.isAdvertised());
        assertNull(modifiedLayer.getGridSubset(GWCConfigIntegrationTestData.GRIDSET_EPSG2163));
    }

    @Test
    public void testModifyBadLayer() {
        String layerName = "newLayer";

        WMSLayer layer =
                new WMSLayer(
                        layerName,
                        new String[] {"http://example.com/"},
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        null);

        try {
            tileLayerDispatcher.modify(layer);
            fail("Expected exception when modifiying nonexistant layer");
        } catch (IllegalArgumentException e) {

        }
        try {
            tileLayerDispatcher.modify(null);
            fail("Expected exception when modifiying null layer");
        } catch (IllegalArgumentException e) {

        }
    }

    // add / remove gridset
    @Test
    public void testAddGridset() throws GeoWebCacheException, IOException {
        String gridsetName = "EPSG:3005";
        GridSet epsg3005 =
                GridSetFactory.createGridSet(
                        gridsetName,
                        SRS.getSRS(gridsetName),
                        new BoundingBox(35043.6538, 440006.8768, 1885895.3117, 1735643.8497),
                        false,
                        null,
                        new double[] {25000000, 1250000, 500000, 250000},
                        null,
                        GridSetFactory.DEFAULT_PIXEL_SIZE_METER,
                        null,
                        256,
                        256,
                        false);

        tileLayerDispatcher.addGridSet(epsg3005);

        assertTrue(gridSetBroker.getNames().contains(gridsetName));
        assertEquals(gridSetBroker.get(gridsetName), epsg3005);
    }

    @Test
    public void testAddBadGridset() throws IOException {
        // existing
        GridSet existingGridSet = gridSetBroker.get(GWCConfigIntegrationTestData.GRIDSET_EPSG2163);
        try {
            tileLayerDispatcher.addGridSet(existingGridSet);
            fail("Expected exception adding existing gridset");
        } catch (IllegalArgumentException e) {

        }
        try {
            tileLayerDispatcher.addGridSet(null);
            fail("Expected exception adding null gridset");
        } catch (NullPointerException e) {

        }
    }

    @Test
    public void testRemoveGridset() throws IOException {
        String gridsetToRemove = GWCConfigIntegrationTestData.GRIDSET_EPSG2163;
        // remove the only layer referencing the gridset first
        tileLayerDispatcher.removeLayer(GWCConfigIntegrationTestData.LAYER_TOPP_STATES);
        tileLayerDispatcher.removeGridSet(gridsetToRemove);

        assertFalse(gridSetBroker.getNames().contains(gridsetToRemove));
        assertNull(gridSetBroker.get(gridsetToRemove));
    }

    @Test
    public void testRemoveGridsetException() throws IOException {
        String gridsetToRemove = GWCConfigIntegrationTestData.GRIDSET_EPSG2163;
        try {
            tileLayerDispatcher.removeGridSet(gridsetToRemove);
            fail("Expected exception removing a gridset referenced by a layer");
        } catch (IllegalStateException e) {

        }
    }

    /**
     * this tests that the GetLayerListFiltered is working correctly. It should be the results of
     * getLayerList() run through the TileLayerDispatcherFilter.
     */
    @Test
    public void testGetLayerListFiltered() {
        // we don't really care about the internals of these layer, just making sure that exclude()
        // is called on them
        TileLayer tileLayer1 = Mockito.mock(TileLayer.class);
        TileLayer tileLayer2 = Mockito.mock(TileLayer.class);

        // setup the tileLayerDispatcherFilter so that
        // tileLayer1 -> excluded
        // tileLayer2 -> NOT excluded
        TileLayerDispatcherFilter tileLayerDispatcherFilter =
                Mockito.mock(TileLayerDispatcherFilter.class);
        Mockito.doReturn(true).when(tileLayerDispatcherFilter).exclude(tileLayer1);
        Mockito.doReturn(false).when(tileLayerDispatcherFilter).exclude(tileLayer2);

        // we use mokito spy to make testing the getLayerListFiltered() method easy
        // tileLayerDispatcher will return the list [tileLayer1,tileLayer2] when getLayerList()
        // called
        TileLayerDispatcher tileLayerDispatcher =
                new TileLayerDispatcher(null, tileLayerDispatcherFilter);
        TileLayerDispatcher tileLayerDispatcherSpy = Mockito.spy(tileLayerDispatcher);
        Mockito.doReturn(Arrays.asList(tileLayer1, tileLayer2))
                .when(tileLayerDispatcherSpy)
                .getLayerList();

        // run our actual method
        Iterable<TileLayer> filteredResult = tileLayerDispatcherSpy.getLayerListFiltered();
        // iterator->list
        List<TileLayer> result = new ArrayList<>();
        filteredResult.forEach(result::add);

        // should only have tileLayer2 in the result
        assertEquals(1, result.size());
        assertEquals(tileLayer2, result.get(0));

        // verify that exclude(tileLayer1) and exclude(tileLayer2) were called
        Mockito.verify(tileLayerDispatcherFilter).exclude(tileLayer1);
        Mockito.verify(tileLayerDispatcherFilter).exclude(tileLayer2);
    }
}
