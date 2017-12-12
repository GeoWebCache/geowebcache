package org.geowebcache.config;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.text.IsEqualIgnoringCase.equalToIgnoringCase;
import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;

import static org.junit.Assert.*;
import static org.easymock.EasyMock.*;

import java.net.URL;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.HashMap;


import org.easymock.Capture;
import org.geotools.data.ows.*;
import org.geotools.data.wms.WebMapServer;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.filter.parameters.StringParameterFilter;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.wms.WMSLayer;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Sets;

public class GetCapabilitiesConfigurationTest {
    WebMapServer server;
    WMSCapabilities cap;
    WMSRequest req;
    OperationType gcOpType;
    XMLConfiguration globalConfig;
    Capture<TileLayer> layerCapture;
    GridSetBroker broker;

    @Before
    public void setUp() throws Exception {
        server = createNiceMock(WebMapServer.class);
        cap = createNiceMock(WMSCapabilities.class);
        req = createNiceMock(WMSRequest.class);
        gcOpType = createNiceMock(OperationType.class);
        globalConfig = createNiceMock(XMLConfiguration.class);
        layerCapture = new Capture<TileLayer>();
        broker = new GridSetBroker(false, false);

        expect(server.getCapabilities()).andStubReturn(cap);
        expect(cap.getRequest()).andStubReturn(req);
        expect(req.getGetCapabilities()).andStubReturn(gcOpType);
        expect(gcOpType.getGet()).andStubReturn(new URL("http://test/wms?SERVICE=WMS&VERSION=1.1.1&REQUEST=getcapabilities"));
        expect(cap.getVersion()).andStubReturn("1.1.1");
    }

    @Test
    public void testNullParameterFilters() throws Exception {
        Layer l = new Layer();
        l.setName("Foo");
        l.setLatLonBoundingBox(new CRSEnvelope());
        List<Layer> layers = new LinkedList<Layer>();
        layers.add(l);
        expect(cap.getLayerList()).andReturn(layers);

        GetCapabilitiesConfiguration config =
            new GetCapabilitiesConfiguration(broker,  "http://test/wms", "image/png", "3x3", "", null, "false"){

                @Override
                WebMapServer getWMS() {
                    return server;
                }

            };

        replay(server, cap, req, gcOpType, globalConfig);
        config.setPrimaryConfig(globalConfig);
        config.initialize(broker);

        WMSLayer wmsLayer = (WMSLayer) config.getTileLayers().get(0);
        List<ParameterFilter> outputParameterFilters = wmsLayer.getParameterFilters();

        assertThat(outputParameterFilters, containsInAnyOrder(
                hasProperty("key", equalToIgnoringCase("styles"))));
    }

    @Test
    public void testEmptyParameterFilters() throws Exception {
        Layer l = new Layer();
        l.setName("Foo");
        l.setLatLonBoundingBox(new CRSEnvelope());
        List<Layer> layers = new LinkedList<Layer>();
        layers.add(l);
        expect(cap.getLayerList()).andReturn(layers);

        GetCapabilitiesConfiguration config =
                new GetCapabilitiesConfiguration(broker,  "http://test/wms", "image/png", "3x3", "", new HashMap(), "false"){

                    @Override
                    WebMapServer getWMS() {
                        return server;
                    }

                };

        replay(server, cap, req, gcOpType, globalConfig);
        config.setPrimaryConfig(globalConfig);
        config.initialize(broker);

        WMSLayer wmsLayer = (WMSLayer) config.getTileLayers().get(0);
        List<ParameterFilter> outputParameterFilters = wmsLayer.getParameterFilters();

        assertThat(outputParameterFilters, containsInAnyOrder(
                hasProperty("key", equalToIgnoringCase("styles"))));
    }

    @Test
    public void testBlankParameterFilters() throws Exception {
        Layer l = new Layer();
        l.setName("Foo");
        l.setLatLonBoundingBox(new CRSEnvelope());
        List<Layer> layers = new LinkedList<Layer>();
        layers.add(l);
        expect(cap.getLayerList()).andReturn(layers);

        HashMap<String, String> cachedParams = new HashMap<String, String>();
        cachedParams.put("", "");

        GetCapabilitiesConfiguration config =
                new GetCapabilitiesConfiguration(broker,  "http://test/wms", "image/png", "3x3", "", cachedParams, "false"){

                    @Override
                    WebMapServer getWMS() {
                        return server;
                    }

                };

        replay(server, cap, req, gcOpType, globalConfig);
        config.setPrimaryConfig(globalConfig);
        config.initialize(broker);

        WMSLayer wmsLayer = (WMSLayer) config.getTileLayers().get(0);
        List<ParameterFilter> outputParameterFilters = wmsLayer.getParameterFilters();

        assertThat(outputParameterFilters, containsInAnyOrder(
                hasProperty("key", equalToIgnoringCase("styles"))));
    }

    @Test
    public void testDelegateInitializingLayers() throws Exception {
        String url = "http://test/wms";
        String mimeTypes = "image/png";
        String vendorParameters = "map=/osgeo/mapserver/msautotest/world/world.map";

        HashMap<String, String> cachedParams = new HashMap<String, String>();

        cachedParams.put("angle", "");
        cachedParams.put("CQL_FILTER", "1=1");

        GetCapabilitiesConfiguration config =
            new GetCapabilitiesConfiguration(broker, url, mimeTypes, "3x3", vendorParameters, cachedParams, "false"){

                    @Override
                    WebMapServer getWMS() {
                        return server;
                    }

        };

        expect(server.getCapabilities()).andStubReturn(cap);
        expect(cap.getRequest()).andStubReturn(req);
        expect(req.getGetCapabilities()).andStubReturn(gcOpType);
        expect(gcOpType.getGet()).andStubReturn(new URL("http://test/wms?SERVICE=WMS&VERSION=1.1.1&REQUEST=getcapabilities"));

        expect(cap.getVersion()).andStubReturn("1.1.1");

        List<Layer> layers = new LinkedList<Layer>();

        Layer l = new Layer();
        l.setName("Foo");
        l.setLatLonBoundingBox(new CRSEnvelope());
        // create a style for this layer
        StyleImpl style = new StyleImpl();
        style.setName("style1");
        style.setLegendURLs(Collections.singletonList("http://localhost:8080/geoserver/topp/wms?" +
                "service=WMS&request=GetLegendGraphic&format=image/gif&width=50&height=100&layer=topp:states&style=polygon"));
        l.setStyles(Collections.singletonList(style));
        // add the test layer
        layers.add(l);

        globalConfig.setDefaultValues(capture(layerCapture)); expectLastCall().times(layers.size());

        expect(cap.getLayerList()).andReturn(layers);

        replay(server, cap, req, gcOpType, globalConfig);

        config.setPrimaryConfig(globalConfig);

        config.initialize(broker);

        // Check that the XMLConfiguration's setDefaultValues method has been called on each of the layers returened.
        assertThat(Sets.newHashSet(config.getLayers()), is(Sets.newHashSet(layerCapture.getValues())));

        verify(server, cap, req, gcOpType, globalConfig);

        // check legends information
        WMSLayer wmsLayer = (WMSLayer) config.getTileLayer("Foo");
        assertThat(wmsLayer, notNullValue());
        assertThat(wmsLayer.getLegends(), notNullValue());
        // check legends default for the test layer
        assertThat(wmsLayer.getLegends().getDefaultWidth(), is(20));
        assertThat(wmsLayer.getLegends().getDefaultHeight(), is(20));
        assertThat(wmsLayer.getLegends().getDefaultFormat(), is("image/png"));
        // check style legend information
        assertThat(wmsLayer.getLegends().getLegendsRawInfo(), notNullValue());
        assertThat(wmsLayer.getLegends().getLegendsRawInfo().size(), is(1));
        assertThat(wmsLayer.getLegends().getLegendsRawInfo().get(0).getWidth(), is(50));
        assertThat(wmsLayer.getLegends().getLegendsRawInfo().get(0).getHeight(), is(100));
        assertThat(wmsLayer.getLegends().getLegendsRawInfo().get(0).getFormat(), is("image/gif"));

        List<ParameterFilter> outputParameterFilters = wmsLayer.getParameterFilters();

        assertThat(outputParameterFilters, containsInAnyOrder(
                hasProperty("key", equalToIgnoringCase("styles")),
                hasProperty("key", equalToIgnoringCase("CQL_FILTER")),
                hasProperty("key", equalToIgnoringCase("angle"))));

    }
}
