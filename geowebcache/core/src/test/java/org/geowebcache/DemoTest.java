package org.geowebcache;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.TreeSet;
import org.geowebcache.demo.Demo;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.layer.wms.WMSLayer;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Simple test class for testing advertised and unadvertised Layers in the Demo Page
 *
 * @author Nicola Lagomarsini
 */
public class DemoTest {

    @Test
    public void testAdvertised() throws GeoWebCacheException, IOException {
        // Initialize mock resources
        TileLayerDispatcher tld = mock(TileLayerDispatcher.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest servReq = new MockHttpServletRequest();
        servReq.setRequestURI("/test");

        // Creating an advertised Layer and an unadvertised one
        HashMap<String, GridSubset> subSets = new HashMap<>();
        TileLayer advertisedLayer =
                new WMSLayer(
                        "testAdv", null, null, null, null, subSets, null, null, null, false, null);
        advertisedLayer.setEnabled(true);
        advertisedLayer.setAdvertised(true);
        TileLayer unAdvertisedLayer =
                new WMSLayer(
                        "testNotAdv",
                        null,
                        null,
                        null,
                        null,
                        subSets,
                        null,
                        null,
                        null,
                        false,
                        null);
        unAdvertisedLayer.setEnabled(true);
        unAdvertisedLayer.setAdvertised(false);

        // Define used method
        TreeSet<String> set = new TreeSet<>();
        set.add(advertisedLayer.getName());
        set.add(unAdvertisedLayer.getName());

        when(tld.getLayerNames()).thenReturn(set);

        when(tld.getTileLayer("testAdv")).thenReturn(advertisedLayer);
        when(tld.getTileLayer("testNotAdv")).thenReturn(unAdvertisedLayer);

        // Generate the HTML
        Demo.makeMap(tld, null, null, servReq, response);
        // Get the HTML from the response
        String result = response.getContentAsString();

        // Ensure the Advertised Layer is present and the Unadvertised Layer is not
        assertTrue(result.contains("testAdv"));
        assertFalse(result.contains("testNotAdv"));
    }
}
