package org.geowebcache.config;

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.geowebcache.util.TestUtils.isPresent;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.Assert.assertThat;

import java.net.URL;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.easymock.EasyMock;
import org.geotools.data.ows.CRSEnvelope;
import org.geotools.data.ows.Layer;
import org.geotools.data.ows.OperationType;
import org.geotools.data.ows.WMSCapabilities;
import org.geotools.data.ows.WMSRequest;
import org.geotools.data.wms.WebMapServer;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.wms.WMSLayer;
import org.hamcrest.CustomMatcher;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.opengis.filter.IncludeFilter;

import com.google.common.base.Objects;

public class GetCapabilitiesGridSetConfigurationConformanceTest extends GridSetConfigurationTest {
    
    private GridSetBroker broker;
    
    @Before
    public void setupBroker() {
        if( broker==null) {
            broker = new GridSetBroker(false, false);
        }
    }
    
    @Override
    protected void doModifyInfo(GridSet info, int rand) throws Exception {
        info.setDescription(Integer.toString(rand));
    }

    @Override
    protected String getExistingInfo() {
        return "testExisting:EPSG:3978";
    }

    @Override
    protected GridSetConfiguration getConfig() throws Exception {
        WebMapServer server;
        WMSCapabilities cap;
        WMSRequest req;
        OperationType gcOpType;
        DefaultingConfiguration globalConfig;
        server = createNiceMock(WebMapServer.class);
        cap = createNiceMock(WMSCapabilities.class);
        req = createNiceMock(WMSRequest.class);
        gcOpType = createNiceMock(OperationType.class);
        globalConfig = createNiceMock(DefaultingConfiguration.class);
        setupBroker();
        
        Layer l = new Layer();
        l.setName("testExisting");
        l.setBoundingBoxes(new CRSEnvelope("EPSG:3978", -2259049,347711, -1994160,827919));
        l.setLatLonBoundingBox(new CRSEnvelope());
        List<Layer> layers = new LinkedList<Layer>();
        layers.add(l);
        expect(cap.getLayerList()).andReturn(layers);

        expect(server.getCapabilities()).andStubReturn(cap);
        expect(cap.getRequest()).andStubReturn(req);
        expect(req.getGetCapabilities()).andStubReturn(gcOpType);
        expect(gcOpType.getGet()).andStubReturn(new URL("http://test/wms?SERVICE=WMS&VERSION=1.1.1&REQUEST=getcapabilities"));
        expect(cap.getVersion()).andStubReturn("1.1.1");
        EasyMock.replay(server, cap, req, gcOpType, globalConfig);
        
        GetCapabilitiesConfiguration config =
                new GetCapabilitiesConfiguration(broker,  "http://test/wms", "image/png", "3x3", "", null, "false"){

                    @Override
                    WebMapServer getWMS() {
                        return server;
                    }

                };
        config.initialize(broker);
        return config;
    }

    
    @Test
    public void testLayerGridsets() throws Exception {
        Optional<TileLayer> layer = ((GetCapabilitiesConfiguration)config).getLayer("testExisting");
        Optional<GridSet> gridset = config.getGridSet("testExisting:EPSG:3978");
        
        TileLayer tileLayer = layer.get();
        GridSubset gridSubset = tileLayer.getGridSubset("testExisting:EPSG:3978");
        GridSet gridSet2 = gridSubset.getGridSet();
        assertThat(gridSet2, equalTo(gridset.get()));
    }

    @Override
    protected Matcher<GridSet> infoEquals(GridSet expected) {
        return new CustomMatcher<GridSet>("GridSet matching "+expected.getName()+" with " + expected.getDescription()){
            
            @Override
            public boolean matches(Object item) {
                return item instanceof GridSet && ((GridSet)item).getName().equals(((GridSet)expected).getName()) &&
                    ((GridSet)item).getDescription().equals(((GridSet)expected).getDescription());
            }
            
        };
    }
    
    @Override
    protected Matcher<GridSet> infoEquals(int expected) {
        return new CustomMatcher<GridSet>("GridSet with value " + expected){
            
            @Override
            public boolean matches(Object item) {
                return item instanceof GridSet && Objects.equal(((GridSet)item).getDescription(),Integer.toString(expected));
            }
            
        };
    }
    
    @Override
    public void failNextRead() {
        Assume.assumeFalse(true);
    }

    @Override
    public void failNextWrite() {
        Assume.assumeFalse(true);
    }

    @Override
    protected void renameInfo(GridSetConfiguration config, String name1, String name2)
            throws Exception {
        Assume.assumeFalse(true);
    }

    @Override
    protected void addInfo(GridSetConfiguration config, GridSet info) throws Exception {
        // TODO Auto-generated method stub
        Assume.assumeFalse(true);
    }

    @Override
    protected void removeInfo(GridSetConfiguration config, String name) throws Exception {
        Assume.assumeFalse(true);
    }

    @Override
    protected void modifyInfo(GridSetConfiguration config, GridSet info) throws Exception {
        Assume.assumeFalse(true);
    }

    @Override
    public void testCanSaveGoodInfo() throws Exception {
        // Should not be able to save anything as it is read only
        assertThat(config.canSave(getGoodInfo("test", 1)), equalTo(false));
    }
}
