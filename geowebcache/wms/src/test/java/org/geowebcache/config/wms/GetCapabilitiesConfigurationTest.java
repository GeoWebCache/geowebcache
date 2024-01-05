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
package org.geowebcache.config.wms;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.newCapture;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.text.IsEqualIgnoringCase.equalToIgnoringCase;
import static org.junit.Assert.assertThat;

import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.geotools.data.ows.OperationType;
import org.geotools.ows.wms.CRSEnvelope;
import org.geotools.ows.wms.Layer;
import org.geotools.ows.wms.StyleImpl;
import org.geotools.ows.wms.WMSCapabilities;
import org.geotools.ows.wms.WMSRequest;
import org.geotools.ows.wms.WebMapServer;
import org.geowebcache.config.DefaultGridsets;
import org.geowebcache.config.DefaultingConfiguration;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.util.URLs;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

public class GetCapabilitiesConfigurationTest {

    WebMapServer server;
    WMSCapabilities cap;
    WMSRequest req;
    OperationType gcOpType;
    DefaultingConfiguration globalConfig;
    Capture<TileLayer> layerCapture;
    GridSetBroker broker;

    @Before
    public void setUp() throws Exception {
        server = createNiceMock(WebMapServer.class);
        cap = createNiceMock(WMSCapabilities.class);
        req = createNiceMock(WMSRequest.class);
        gcOpType = createNiceMock(OperationType.class);
        globalConfig = createNiceMock(DefaultingConfiguration.class);
        layerCapture = newCapture(CaptureType.LAST);
        broker = new GridSetBroker(Collections.singletonList(new DefaultGridsets(false, false)));

        expect(server.getCapabilities()).andStubReturn(cap);
        expect(cap.getRequest()).andStubReturn(req);
        expect(req.getGetCapabilities()).andStubReturn(gcOpType);
        expect(gcOpType.getGet())
                .andStubReturn(
                        URLs.of(
                                "http://test/wms?SERVICE=WMS&VERSION=1.1.1&REQUEST=getcapabilities"));
        expect(cap.getVersion()).andStubReturn("1.1.1");
    }

    @Test
    public void testNullParameterFilters() throws Exception {
        Layer l = new Layer();
        l.setName("Foo");
        l.setLatLonBoundingBox(new CRSEnvelope());
        List<Layer> layers = new LinkedList<>();
        layers.add(l);
        expect(cap.getLayerList()).andReturn(layers);

        GetCapabilitiesConfiguration config =
                new GetCapabilitiesConfiguration(
                        broker, "http://test/wms", "image/png", "3x3", "", null, "false") {

                    @Override
                    WebMapServer getWMS() {
                        return server;
                    }
                };

        replay(server, cap, req, gcOpType, globalConfig);
        config.setPrimaryConfig(globalConfig);
        config.setGridSetBroker(broker);
        config.afterPropertiesSet();

        WMSLayer wmsLayer = (WMSLayer) config.getLayers().iterator().next();
        List<ParameterFilter> outputParameterFilters = wmsLayer.getParameterFilters();

        assertThat(
                outputParameterFilters,
                Matchers.contains(hasProperty("key", equalToIgnoringCase("styles"))));
    }

    @Test
    public void testEmptyParameterFilters() throws Exception {
        Layer l = new Layer();
        l.setName("Foo");
        l.setLatLonBoundingBox(new CRSEnvelope());
        List<Layer> layers = new LinkedList<>();
        layers.add(l);
        expect(cap.getLayerList()).andReturn(layers);

        GetCapabilitiesConfiguration config =
                new GetCapabilitiesConfiguration(
                        broker,
                        "http://test/wms",
                        "image/png",
                        "3x3",
                        "",
                        new HashMap<>(),
                        "false") {

                    @Override
                    WebMapServer getWMS() {
                        return server;
                    }
                };

        replay(server, cap, req, gcOpType, globalConfig);
        config.setPrimaryConfig(globalConfig);
        config.setGridSetBroker(broker);
        config.afterPropertiesSet();
        WMSLayer wmsLayer = (WMSLayer) config.getLayers().iterator().next();
        List<ParameterFilter> outputParameterFilters = wmsLayer.getParameterFilters();

        assertThat(
                outputParameterFilters,
                Matchers.contains(hasProperty("key", equalToIgnoringCase("styles"))));
    }

    @Test
    public void testBlankParameterFilters() throws Exception {
        Layer l = new Layer();
        l.setName("Foo");
        l.setLatLonBoundingBox(new CRSEnvelope());
        List<Layer> layers = new LinkedList<>();
        layers.add(l);
        expect(cap.getLayerList()).andReturn(layers);

        HashMap<String, String> cachedParams = new HashMap<>();
        cachedParams.put("", "");

        GetCapabilitiesConfiguration config =
                new GetCapabilitiesConfiguration(
                        broker, "http://test/wms", "image/png", "3x3", "", cachedParams, "false") {

                    @Override
                    WebMapServer getWMS() {
                        return server;
                    }
                };

        replay(server, cap, req, gcOpType, globalConfig);
        config.setPrimaryConfig(globalConfig);
        config.setGridSetBroker(broker);
        config.afterPropertiesSet();
        WMSLayer wmsLayer = (WMSLayer) config.getLayers().iterator().next();
        List<ParameterFilter> outputParameterFilters = wmsLayer.getParameterFilters();

        assertThat(
                outputParameterFilters,
                Matchers.contains(hasProperty("key", equalToIgnoringCase("styles"))));
    }

    @Test
    @SuppressWarnings("unchecked") // to be removed once we upgrade to Hamcrest 2, @SafeVarArgs
    public void testDelegateInitializingLayers() throws Exception {
        GridSetBroker broker =
                new GridSetBroker(Collections.singletonList(new DefaultGridsets(false, false)));
        String url = "http://test/wms";
        String mimeTypes = "image/png";
        String vendorParameters = "map=/osgeo/mapserver/msautotest/world/world.map";

        HashMap<String, String> cachedParams = new HashMap<>();

        cachedParams.put("angle", "");
        cachedParams.put("CQL_FILTER", "1=1");

        GetCapabilitiesConfiguration config =
                new GetCapabilitiesConfiguration(
                        broker, url, mimeTypes, "3x3", vendorParameters, cachedParams, "false") {

                    @Override
                    WebMapServer getWMS() {
                        return server;
                    }
                };

        expect(server.getCapabilities()).andStubReturn(cap);
        expect(cap.getRequest()).andStubReturn(req);
        expect(req.getGetCapabilities()).andStubReturn(gcOpType);
        expect(gcOpType.getGet())
                .andStubReturn(
                        URLs.of(
                                "http://test/wms?SERVICE=WMS&VERSION=1.1.1&REQUEST=getcapabilities"));

        expect(cap.getVersion()).andStubReturn("1.1.1");

        List<Layer> layers = new LinkedList<>();

        Layer l = new Layer();
        l.setName("Foo");
        l.setLatLonBoundingBox(new CRSEnvelope());
        // create a style for this layer
        StyleImpl style = new StyleImpl();
        style.setName("style1");
        style.setLegendURLs(
                Collections.singletonList(
                        "http://localhost:8080/geoserver/topp/wms?"
                                + "service=WMS&request=GetLegendGraphic&format=image/gif&width=50&height=100&layer=topp:states&style=polygon"));
        l.setStyles(Collections.singletonList(style));
        // add the test layer
        layers.add(l);

        globalConfig.setDefaultValues(capture(layerCapture));
        expectLastCall().times(layers.size());

        expect(cap.getLayerList()).andReturn(layers);

        replay(server, cap, req, gcOpType, globalConfig);

        config.setPrimaryConfig(globalConfig);
        config.setGridSetBroker(broker);
        config.afterPropertiesSet();

        // Check that the XMLConfiguration's setDefaultValues method has been called on each of the
        // layers returened.
        assertThat(
                Sets.newHashSet(config.getLayers()), is(Sets.newHashSet(layerCapture.getValues())));

        verify(server, cap, req, gcOpType, globalConfig);

        // check legends information
        WMSLayer wmsLayer = (WMSLayer) config.getLayer("Foo").get();
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

        assertThat(
                outputParameterFilters,
                containsInAnyOrder(
                        hasProperty("key", equalToIgnoringCase("styles")),
                        hasProperty("key", equalToIgnoringCase("CQL_FILTER")),
                        hasProperty("key", equalToIgnoringCase("angle"))));
    }
}
