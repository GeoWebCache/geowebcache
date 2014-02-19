package org.geowebcache.config;

import static org.junit.Assert.*;
import static org.easymock.classextension.EasyMock.*;

import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import org.easymock.Capture;
import org.geotools.data.ows.CRSEnvelope;
import org.geotools.data.ows.Layer;
import org.geotools.data.ows.OperationType;
import org.geotools.data.ows.WMSCapabilities;
import org.geotools.data.ows.WMSRequest;
import org.geotools.data.wms.WebMapServer;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.layer.TileLayer;
import org.hamcrest.Matchers;
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
        layers.add(l);
        
        globalConfig.setDefaultValues(capture(layerCapture)); expectLastCall().times(layers.size());
        
        expect(cap.getLayerList()).andReturn(layers);
        
        replay(server, cap, req, gcOpType, globalConfig);
        
        config.setPrimaryConfig(globalConfig);
        
        config.initialize(broker);
        
        // Check that the XMLConfiguration's setDefaultValues method has been called on each of the layers returened.
        assertThat(Sets.newHashSet(config.getLayers()), Matchers.is(Sets.newHashSet(layerCapture.getValues())));
        
        verify(server, cap, req, gcOpType, globalConfig);
    }
    
}
