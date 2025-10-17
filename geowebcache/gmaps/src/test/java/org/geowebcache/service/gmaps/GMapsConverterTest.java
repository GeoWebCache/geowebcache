package org.geowebcache.service.gmaps;

import jakarta.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.DefaultGridsets;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.filter.parameters.RegexParameterFilter;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.layer.TileLayerDispatcherMock;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.storage.StorageBroker;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

public class GMapsConverterTest {

    @Before
    public void setUp() throws Exception {}

    /** see http://code.google.com/apis/maps/documentation/overlays.html# Custom_Map_Types */
    @Test
    public void testGMapsConverter() throws Exception {
        /* Check origin location */
        int x = 0;
        int y = 0;
        int z = 0;
        long[] gridLoc = GMapsConverter.convert(z, x, y);
        long[] solution = {0, 0, 0};
        assert (Arrays.equals(gridLoc, solution));

        /* Check zoomlevel */
        x = 0;
        y = 0;
        z = 1;
        solution[0] = 0;
        solution[1] = 1;
        solution[2] = 1;
        gridLoc = GMapsConverter.convert(z, x, y);
        assert (Arrays.equals(gridLoc, solution));

        /* Check top right */
        x = 1;
        y = 0;
        z = 1;
        solution[0] = 1;
        solution[1] = 1;
        solution[2] = 1;
        gridLoc = GMapsConverter.convert(z, x, y);
        assert (Arrays.equals(gridLoc, solution));

        /* Check top right, zoomlevel */
        x = 3;
        y = 0;
        z = 2;
        solution[0] = 3;
        solution[1] = 3;
        solution[2] = 2;
        gridLoc = GMapsConverter.convert(z, x, y);
        assert (Arrays.equals(gridLoc, solution));

        /* Check middle */
        x = 2;
        y = 1;
        z = 2;
        solution[0] = 2;
        solution[1] = 2;
        solution[2] = 2;
        gridLoc = GMapsConverter.convert(z, x, y);
        assert (Arrays.equals(gridLoc, solution));

        // System.out.println(Arrays.toString(solution));
        // System.out.println(Arrays.toString(gridLoc));
    }

    private static final String CQL_FILTER_PARAMETER_NAME = "CQL_FILTER";

    private static final String CQL_FILTER_PARAMETER_VALUE = "value='x'";

    private static final String TEST_LAYER_NAME = "testLayer";

    @Test
    public void testConveyorCreation() throws UnsupportedEncodingException, GeoWebCacheException {
        StorageBroker sb = null;

        List<ParameterFilter> filters = new ArrayList<>();
        RegexParameterFilter parameterFilter = new RegexParameterFilter();
        parameterFilter.setKey(CQL_FILTER_PARAMETER_NAME);
        parameterFilter.setDefaultValue("");
        parameterFilter.setRegex("value='.*'");
        filters.add(parameterFilter);

        WMSLayer wmsLayer =
                new WMSLayer(TEST_LAYER_NAME, null, null, null, null, null, filters, null, null, true, null);

        TileLayerDispatcher tld = new TileLayerDispatcherMock(wmsLayer);

        GridSetBroker gsb = new GridSetBroker(Collections.singletonList(new DefaultGridsets(true, true)));

        wmsLayer.initialize(gsb);

        MockHttpServletRequest request = new MockHttpServletRequest();
        HttpServletResponse response = null;

        request.addParameter(CQL_FILTER_PARAMETER_NAME, CQL_FILTER_PARAMETER_VALUE);
        request.addParameter("layers", new String[] {TEST_LAYER_NAME});
        request.addParameter("zoom", "12");
        request.addParameter("x", "0");
        request.addParameter("y", "0");

        GMapsConverter converter = new GMapsConverter(sb, tld, gsb);

        ConveyorTile conveyorTile = converter.getConveyor(request, response);
        Map<String, String> parameters = conveyorTile.getParameters();
        Assert.assertNotNull(parameters);
        // assertTrue(parameters.contains(URLEncoder.encode(CQL_FILTER_PARAMETER_VALUE,"UTF8")));
        Assert.assertEquals(
                CQL_FILTER_PARAMETER_VALUE, URLDecoder.decode(parameters.get(CQL_FILTER_PARAMETER_NAME), "UTF8"));
    }
}
