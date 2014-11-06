package org.geowebcache.service.wms;

import static org.easymock.EasyMock.expect;
import static org.easymock.classextension.EasyMock.createMock;
import static org.easymock.classextension.EasyMock.replay;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.easymock.classextension.EasyMock;
import org.geowebcache.config.meta.ServiceInformation;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.AbstractTileLayer;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.util.NullURLMangler;
import org.geowebcache.util.URLMangler;
import org.hamcrest.xml.HasXPath;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

public class WMSGetCapabilitiesTest {
    

    @Test
    public void testEscapeXMLChars() throws Exception {
        TileLayerDispatcher tld = createMock(TileLayerDispatcher.class);
        HttpServletRequest servReq = createMock(HttpServletRequest.class);
        HttpServletResponse response = createMock(HttpServletResponse.class);
        String baseUrl = "http://example.com/geowebcache/";
        String contextPath = "service/";
        URLMangler urlMangler = new NullURLMangler();
        ServiceInformation servInfo = createMock(ServiceInformation.class);
        
        Map<String, String[]> parameterMap = new HashMap<>();
        parameterMap.put("SERVICE", new String[]{"WMS"});
        parameterMap.put("VERSION", new String[]{"1.1.1"});
        parameterMap.put("REQUEST", new String[]{"getcapabilities"});
        parameterMap.put("TILED", new String[]{"true"});
        
        expect(servReq.getParameterMap()).andStubReturn(Collections.unmodifiableMap(parameterMap));
        expect(servReq.getCharacterEncoding()).andStubReturn("UTF-8");
        
        expect(servInfo.getTitle()).andStubReturn("Title & \"stuff\"");
        expect(servInfo.getDescription()).andStubReturn("This \"description\" contains <characters> which & should be \'escaped\'.");
        expect(servInfo.getKeywords()).andStubReturn(null);
        expect(servInfo.getServiceProvider()).andStubReturn(null);
        expect(servInfo.getFees()).andStubReturn("NONE");
        expect(servInfo.getAccessConstraints()).andStubReturn("NONE");
        expect(tld.getServiceInformation()).andStubReturn(servInfo);
        
        // Creating an advertised Layer and an unadvertised one
        HashMap<String, GridSubset> subSets = new HashMap<String, GridSubset>();
        TileLayer advertisedLayer = new WMSLayer("testAdv", null, null, null,
                null, subSets, null, null, null, false, null);      
        advertisedLayer.setEnabled(true);
        advertisedLayer.setAdvertised(true);
        TileLayer unAdvertisedLayer = new WMSLayer("testNotAdv", null, null, null,
                null, subSets, null, null, null, false, null); 
        unAdvertisedLayer.setEnabled(true);
        unAdvertisedLayer.setAdvertised(false);
        
        expect(tld.getLayerList()).andStubReturn(Arrays.asList(advertisedLayer, unAdvertisedLayer));

        replay(tld, servReq, response, servInfo);
        
        WMSGetCapabilities capabilities = new WMSGetCapabilities(tld, servReq, baseUrl, contextPath, urlMangler);
        
        String xml = capabilities.generateGetCapabilities(StandardCharsets.UTF_8);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        InputSource is = new InputSource();
        is.setCharacterStream(new StringReader(xml));
        Document document = builder.parse(is);

        assertThat(document.getDocumentElement(), HasXPath.hasXPath("/WMT_MS_Capabilities/Service/Title[text()='Title & \"stuff\"']"));
        assertThat(document.getDocumentElement(), HasXPath.hasXPath("/WMT_MS_Capabilities/Service/Abstract[text()="+xpathString("This \"description\" contains <characters> which & should be \'escaped\'.")+"]"));
        
        // Be extra strict
        assertThat(xml, not(containsString("& ")));
        assertThat(xml, not(containsString("<characters>")));
        assertThat(xml, not(containsString("'escaped'")));
        assertThat(xml, not(containsString("\"description\"")));
        
        assertThat(xml, containsString("testAdv"));
        assertThat(xml, not(containsString("testNotAdv")));
        
        EasyMock.verify(tld, servReq, response, servInfo);
    }

    /**
     * Returns an XPath expression equivalent to the given string which can safely include both " and ' characters.
     * @param s
     * @return
     */
    String xpathString(String s) {
        StringBuilder b = new StringBuilder();
        int len = s.length();
        b.append("concat('");
        for(int i = 0; i<len; i++) {
            char c = s.charAt(i);
            if(c=='\'') {
                b.append("',\"'\",'");
            } else {
                b.append(c);
            }
        }
        b.append("')");
        return b.toString();
    }
}
