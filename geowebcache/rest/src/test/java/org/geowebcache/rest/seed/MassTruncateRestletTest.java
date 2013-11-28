package org.geowebcache.rest.seed;

import static org.junit.Assert.*;
import static org.easymock.classextension.EasyMock.*;

import java.io.InputStream;

import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.config.XMLConfigurationBackwardsCompatibilityTest;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.storage.StorageBroker;
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
