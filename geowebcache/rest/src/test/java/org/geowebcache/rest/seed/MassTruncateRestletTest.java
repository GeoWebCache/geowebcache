package org.geowebcache.rest.seed;

import static org.easymock.EasyMock.contains;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.*;

import java.io.InputStream;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.Configuration;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.config.XMLConfigurationBackwardsCompatibilityTest;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.layer.AbstractTileLayer;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.seed.TileBreeder;
import org.geowebcache.storage.StorageBroker;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Representation;
import org.restlet.resource.StringRepresentation;

public class MassTruncateRestletTest {

    private MassTruncateRestlet mtr;

    @Before
    public void setUp() throws Exception {
        GridSetBroker gridSetBroker = new GridSetBroker(false, false);
        XMLConfiguration xmlConfig = loadXMLConfig();
        xmlConfig.initialize(gridSetBroker);

        mtr = new MassTruncateRestlet();
        mtr.setXmlConfig(xmlConfig);
    }
    
    @Test
    public void testTruncateLayer() throws Exception{
        String layerName = "test";
        String requestBody = "<truncateLayer><layerName>"+layerName+"</layerName></truncateLayer>";
        
        StorageBroker sb = createMock(StorageBroker.class);
        expect(sb.delete(eq(layerName))).andReturn(true);
        replay(sb);
        
        mtr.setStorageBroker(sb);
        
        Request request;
        Representation entity;
        Response response;

        request = new Request();
        request.setMethod(Method.POST);
        entity = new StringRepresentation(requestBody, MediaType.TEXT_XML);
        request.setEntity(entity);
        response = new Response(request);

        mtr.handle(request, response);
        
        verify(sb);
    }

    @Test
    public void testTruncateLayerTwice() throws Exception {
        String layerName = "test";
        String requestBody = "<truncateLayer><layerName>"+layerName+"</layerName></truncateLayer>";

        StorageBroker sb = createMock(StorageBroker.class);
        expect(sb.delete(eq(layerName))).andReturn(true);
        expect(sb.delete(eq(layerName))).andReturn(false);
        replay(sb);

        Configuration config = createMock(Configuration.class);
        TileLayer tileLayer = new TestTileLayer();
        expect(config.getTileLayer(layerName)).andReturn(tileLayer); 
        replay(config);

        mtr.setStorageBroker(sb);
        mtr.setConfiguration(config);

        // first run, it will get an ok
        Request request = new Request();
        request.setMethod(Method.POST);
        Representation entity = new StringRepresentation(requestBody, MediaType.TEXT_XML);
        request.setEntity(entity);
        Response response = new Response(request);

        // first run, will delete from storage without issues
        mtr.handle(request, response);
        assertEquals(200, response.getStatus().getCode());
        // second run, will get a falso and will check the breeded for layer existence
        mtr.handle(request, response);
        assertEquals(200, response.getStatus().getCode());
        verify(sb);
    }

    @Test
    public void testTruncateNonExistingLayer() throws Exception {
        String layerName = "test";
        String requestBody = "<truncateLayer><layerName>"+layerName+"</layerName></truncateLayer>";

        StorageBroker sb = createMock(StorageBroker.class);
        expect(sb.delete(eq(layerName))).andReturn(false);
        replay(sb);

        Configuration config = createMock(Configuration.class);
        expect(config.getTileLayer(layerName)).andReturn(null);
        replay(config);

        mtr.setStorageBroker(sb);
        mtr.setConfiguration(config);

        // first run, it will get an ok
        Request request = new Request();
        request.setMethod(Method.POST);
        Representation entity = new StringRepresentation(requestBody, MediaType.TEXT_XML);
        request.setEntity(entity);
        Response response = new Response(request);

        // first run, will delete from storage without issues
        mtr.handle(request, response);
        assertEquals(400, response.getStatus().getCode());
        assertThat(((StringRepresentation) response.getEntity()).getText(), 
                CoreMatchers.containsString("Could not find layer test"));
        verify(sb);
    }
    
    private XMLConfiguration loadXMLConfig() {

        InputStream is = XMLConfiguration.class
                .getResourceAsStream(XMLConfigurationBackwardsCompatibilityTest.GWC_125_CONFIG_FILE);
        XMLConfiguration xmlConfig = null;
        try {
            xmlConfig = new XMLConfiguration(is);
        } catch (Exception e) {
            // Do nothing
        }

        return xmlConfig;
    }
}
