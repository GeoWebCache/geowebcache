package org.geowebcache.config;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.*;
import static org.easymock.EasyMock.*;

import java.net.URL;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.easymock.Capture;
import org.geotools.data.ows.*;
import org.geotools.data.wms.WebMapServer;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.wms.WMSLayer;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Sets;

public class GetCapabilitiesConfigurationTest {
    
    @Before
    public void setUp() throws Exception {
    }
    
    @Test
    public void testDelegateInitializingLayers() throws Exception {
        GridSetBroker broker = new GridSetBroker(false, false);
        String url = "http://test/wms";
        String mimeTypes = "image/png";
        
        final WebMapServer server = createMock(WebMapServer.class);
        WMSCapabilities cap = createMock(WMSCapabilities.class);
        WMSRequest req = createMock(WMSRequest.class);
        OperationType gcOpType = createMock(OperationType.class);
        XMLConfiguration globalConfig = createMock(XMLConfiguration.class);
        Capture<TileLayer> layerCapture = new Capture<TileLayer>();
        
        GetCapabilitiesConfiguration config = 
                new GetCapabilitiesConfiguration(broker, url, mimeTypes, "3x3", "false"){

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
    }
    
}
