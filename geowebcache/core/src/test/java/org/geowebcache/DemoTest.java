package org.geowebcache;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import org.geowebcache.demo.Demo;
import org.geowebcache.filter.parameters.RegexParameterFilter;
import org.geowebcache.filter.parameters.StringParameterFilter;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.mime.MimeType;
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
                new WMSLayer("testAdv", null, null, null, null, subSets, null, null, null, false, null);
        advertisedLayer.setEnabled(true);
        advertisedLayer.setAdvertised(true);
        TileLayer unAdvertisedLayer =
                new WMSLayer("testNotAdv", null, null, null, null, subSets, null, null, null, false, null);
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
        assertThat(result, containsString("testAdv"));
        assertThat(result, not(containsString("testNotAdv")));
    }

    @Test
    public void testEscapingWithoutLayer() throws Exception {
        String unescapedLayer = "layer\"><";
        String escapedLayer = "layer&quot;&gt;&lt;";
        String unescapedSubset = "ESPG:1234\"><";
        String escapedSubset = "ESPG:1234&quot;&gt;&lt;";
        String epsg4326 = "ESPG:4326";

        GridSubset subSet1 = mock(GridSubset.class);
        when(subSet1.getName()).thenReturn(unescapedSubset);
        GridSubset subSet2 = mock(GridSubset.class);
        when(subSet2.getName()).thenReturn(epsg4326);
        Set<String> gridSubsets = new LinkedHashSet<>();
        gridSubsets.add(unescapedSubset);
        gridSubsets.add(epsg4326);

        GridSet gridSet = mock(GridSet.class);
        when(gridSet.getName()).thenReturn(epsg4326);

        GridSetBroker gsb = mock(GridSetBroker.class);
        when(gsb.getWorldEpsg4326()).thenReturn(gridSet);

        TileLayer layer = mock(TileLayer.class);
        when(layer.getName()).thenReturn(unescapedLayer);
        when(layer.isAdvertised()).thenReturn(true);
        when(layer.getGridSubsets()).thenReturn(gridSubsets);
        when(layer.getGridSubset(epsg4326)).thenReturn(subSet2);
        when(layer.getGridSubset(unescapedSubset)).thenReturn(subSet1);
        when(layer.getMimeTypes()).thenReturn(Collections.singletonList(MimeType.createFromFormat("image/png")));

        TileLayerDispatcher tld = mock(TileLayerDispatcher.class);
        when(tld.getLayerNames()).thenReturn(Collections.singleton(unescapedLayer));
        when(tld.getTileLayer(unescapedLayer)).thenReturn(layer);

        MockHttpServletResponse response = new MockHttpServletResponse();
        Demo.makeMap(tld, gsb, null, new MockHttpServletRequest(), response);
        String result = response.getContentAsString();

        assertThat(result, not(containsString(unescapedLayer)));
        assertThat(result, containsString(escapedLayer));
        assertThat(result, not(containsString(unescapedSubset)));
        assertThat(result, containsString(escapedSubset));
    }

    @Test
    public void testEscapingWithLayer() throws Exception {
        String unescapedLayer = "layer'\"><";
        String escapedLayer = "layer'&quot;&gt;&lt;";
        String unescapedSubset = "ESPG:1234'\"><";
        String escapedSubset = "ESPG:1234'&quot;&gt;&lt;";

        GridSet gridSet = mock(GridSet.class);
        when(gridSet.getName()).thenReturn(unescapedSubset);

        GridSubset subSet = mock(GridSubset.class);
        when(subSet.getName()).thenReturn(unescapedSubset);
        when(subSet.getGridSet()).thenReturn(gridSet);
        when(subSet.getGridNames()).thenReturn(new String[] {unescapedSubset});
        when(subSet.getSRS()).thenReturn(SRS.getEPSG4326());
        when(subSet.getGridSetBounds()).thenReturn(BoundingBox.WORLD4326);
        when(subSet.getOriginalExtent()).thenReturn(BoundingBox.WORLD4326);

        TileLayer layer = mock(TileLayer.class);
        when(layer.getName()).thenReturn(unescapedLayer);
        when(layer.isAdvertised()).thenReturn(true);
        when(layer.getGridSubsets()).thenReturn(Collections.singleton(unescapedSubset));
        when(layer.getGridSubset(unescapedSubset)).thenReturn(subSet);
        when(layer.getDefaultMimeType()).thenReturn(MimeType.createFromFormat("image/png"));

        String unescapedString = "string'\"><";
        String escapedString = "string'&quot;&gt;&lt;";
        String unescapedRegex = "regex'\"><";
        String escapedRegex = "regex'&quot;&gt;&lt;";
        StringParameterFilter stringFilter = new StringParameterFilter();
        stringFilter.setKey(unescapedString);
        RegexParameterFilter regexFilter = new RegexParameterFilter();
        regexFilter.setKey(unescapedRegex);
        when(layer.getParameterFilters()).thenReturn(Arrays.asList(stringFilter, regexFilter));

        TileLayerDispatcher tld = mock(TileLayerDispatcher.class);
        when(tld.getTileLayer(unescapedLayer)).thenReturn(layer);

        MockHttpServletResponse response = new MockHttpServletResponse();
        Demo.makeMap(tld, null, unescapedLayer, new MockHttpServletRequest(), response);
        String result = response.getContentAsString();

        assertThat(result, not(containsString(unescapedLayer)));
        assertThat(result, containsString(escapedLayer));
        assertThat(result, not(containsString(unescapedSubset)));
        assertThat(result, containsString(escapedSubset));
        assertThat(result, not(containsString(unescapedString)));
        assertThat(result, containsString(escapedString));
        assertThat(result, not(containsString(unescapedRegex)));
        assertThat(result, containsString(escapedRegex));
    }

    @Test
    public void testRemovedInlineJavaScript() throws Exception {
        GridSet gridSet = mock(GridSet.class);
        when(gridSet.getName()).thenReturn("EPSG:4326");

        GridSubset subSet = mock(GridSubset.class);
        when(subSet.getName()).thenReturn("EPSG:4326");
        when(subSet.getGridSet()).thenReturn(gridSet);
        when(subSet.getGridNames()).thenReturn(new String[] {"EPSG:4326"});
        when(subSet.getSRS()).thenReturn(SRS.getEPSG4326());
        when(subSet.getGridSetBounds()).thenReturn(BoundingBox.WORLD4326);
        when(subSet.getOriginalExtent()).thenReturn(BoundingBox.WORLD4326);

        TileLayer layer = mock(TileLayer.class);
        when(layer.getName()).thenReturn("layer");
        when(layer.isAdvertised()).thenReturn(true);
        when(layer.getGridSubsets()).thenReturn(Collections.singleton("EPSG:4326"));
        when(layer.getGridSubset("EPSG:4326")).thenReturn(subSet);
        when(layer.getDefaultMimeType()).thenReturn(MimeType.createFromFormat("image/png"));

        StringParameterFilter stringFilter = new StringParameterFilter();
        stringFilter.setKey("STRING");
        RegexParameterFilter regexFilter = new RegexParameterFilter();
        regexFilter.setKey("REGEX");
        when(layer.getParameterFilters()).thenReturn(Arrays.asList(stringFilter, regexFilter));

        TileLayerDispatcher tld = mock(TileLayerDispatcher.class);
        when(tld.getTileLayer("layer")).thenReturn(layer);

        MockHttpServletResponse response = new MockHttpServletResponse();
        Demo.makeMap(tld, null, "layer", new MockHttpServletRequest(), response);
        String result = response.getContentAsString();

        assertThat(result, containsString("<script src=\"../rest/web/openlayers3/ol.js\"></script>"));
        assertThat(result, containsString("<script src=\"../rest/web/demo.js\"></script>"));
        assertThat(result, not(containsString("<script>")));
        assertThat(result, not(containsString(" onchange=")));
        assertThat(result, not(containsString(" onblur=")));
    }
}
