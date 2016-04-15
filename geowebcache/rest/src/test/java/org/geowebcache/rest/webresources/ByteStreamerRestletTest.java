package org.geowebcache.rest.webresources;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import java.util.Collections;
import java.util.Map;

import org.easymock.Capture;
import org.easymock.classextension.EasyMock;
import org.geowebcache.rest.RestletException;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.restlet.data.Method;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.Representation;

public class ByteStreamerRestletTest {
    
    @Rule 
    public ExpectedException exception = ExpectedException.none();
    
    @Before
    public void setUp() throws Exception {
    }
    
    @Test
    public void testResourceNotFound() {
        ByteStreamerRestlet restlet = new ByteStreamerRestlet();
        Request request = EasyMock.createMock("request", Request.class);
        Response response = EasyMock.createMock("response", Response.class);
        
        EasyMock.expect(request.getMethod()).andStubReturn(Method.GET);
        
        Map<String, Object> attributes = Collections.singletonMap("filename", "doesnt_exist");
        
        EasyMock.expect(request.getAttributes()).andStubReturn(attributes);
        
        response.setStatus(Status.CLIENT_ERROR_NOT_FOUND);EasyMock.expectLastCall().once();
        
        EasyMock.replay(request, response);
        
        restlet.handle(request, response);
        
        EasyMock.verify(request, response);
    }
    
    @Test
    public void testResourceFoundPNG() throws Exception {
        ByteStreamerRestlet restlet = new ByteStreamerRestlet();
        Request request = EasyMock.createMock("request", Request.class);
        Response response = EasyMock.createMock("response", Response.class);
        
        EasyMock.expect(request.getMethod()).andStubReturn(Method.GET);
        
        Map<String, Object> attributes = Collections.singletonMap("filename", "test.png");
        
        EasyMock.expect(request.getAttributes()).andStubReturn(attributes);
        
        Capture<Representation> capRep = new Capture<>();
        
        response.setEntity(EasyMock.capture(capRep)); EasyMock.expectLastCall().once();
        
        response.setStatus(Status.SUCCESS_OK);EasyMock.expectLastCall().once();
        
        EasyMock.replay(request, response);
        
        restlet.handle(request, response);
        
        assertThat(capRep.getValue().getText(), Matchers.is("TEST"));
        
        EasyMock.verify(request, response);
    }
    @Test
    public void testResourceFoundCSS() throws Exception {
        ByteStreamerRestlet restlet = new ByteStreamerRestlet();
        Request request = EasyMock.createMock("request", Request.class);
        Response response = EasyMock.createMock("response", Response.class);
        
        EasyMock.expect(request.getMethod()).andStubReturn(Method.GET);
        
        Map<String, Object> attributes = Collections.singletonMap("filename", "test.css");
        
        EasyMock.expect(request.getAttributes()).andStubReturn(attributes);
        
        Capture<Representation> capRep = new Capture<>();
        
        response.setEntity(EasyMock.capture(capRep)); EasyMock.expectLastCall().once();
        
        response.setStatus(Status.SUCCESS_OK);EasyMock.expectLastCall().once();
        
        EasyMock.replay(request, response);
        
        restlet.handle(request, response);
        
        assertThat(capRep.getValue().getText(), Matchers.is("CSS"));
        
        EasyMock.verify(request, response);
    }
    
    @Test
    public void testClass() {
        ByteStreamerRestlet restlet = new ByteStreamerRestlet();
        Request request = EasyMock.createMock("request", Request.class);
        Response response = EasyMock.createMock("response", Response.class);
        
        EasyMock.expect(request.getMethod()).andStubReturn(Method.GET);
        
        Map<String, Object> attributes = Collections.singletonMap("filename", "ByteStreamerRestlet.class");
        
        EasyMock.expect(request.getAttributes()).andStubReturn(attributes);
        
        EasyMock.replay(request, response);
        
        exception.expect(allOf(
                instanceOf(RestletException.class), 
                hasProperty("status", is(Status.CLIENT_ERROR_FORBIDDEN))));
        try {
            restlet.handle(request, response);
        } finally {
            EasyMock.verify(request, response);
        }
    }
    
    @Test
    public void testAbsolute() {
        ByteStreamerRestlet restlet = new ByteStreamerRestlet();
        Request request = EasyMock.createMock("request", Request.class);
        Response response = EasyMock.createMock("response", Response.class);
        
        EasyMock.expect(request.getMethod()).andStubReturn(Method.GET);
        
        Map<String, Object> attributes = Collections.singletonMap("filename", "/org/geowebcache/shouldnt/access/test.png");
        
        EasyMock.expect(request.getAttributes()).andStubReturn(attributes);
        
        EasyMock.replay(request, response);
        
        exception.expect(allOf(
                instanceOf(RestletException.class), 
                hasProperty("status", is(Status.CLIENT_ERROR_FORBIDDEN))));
        try {
            restlet.handle(request, response);
        } finally {
            EasyMock.verify(request, response);
        }
    }
    
    @Test
    public void testBackreference() {
        ByteStreamerRestlet restlet = new ByteStreamerRestlet();
        Request request = EasyMock.createMock("request", Request.class);
        Response response = EasyMock.createMock("response", Response.class);
        
        EasyMock.expect(request.getMethod()).andStubReturn(Method.GET);
        
        Map<String, Object> attributes = Collections.singletonMap("filename", "../../shouldnt/access/test.png");
        
        EasyMock.expect(request.getAttributes()).andStubReturn(attributes);
        
        EasyMock.replay(request, response);
        
        exception.expect(allOf(
                instanceOf(RestletException.class), 
                hasProperty("status", is(Status.CLIENT_ERROR_FORBIDDEN))));
        try {
            restlet.handle(request, response);
        } finally {
            EasyMock.verify(request, response);
        }
    }
    @Test
    public void testBackreference2() {
        ByteStreamerRestlet restlet = new ByteStreamerRestlet();
        Request request = EasyMock.createMock("request", Request.class);
        Response response = EasyMock.createMock("response", Response.class);
        
        EasyMock.expect(request.getMethod()).andStubReturn(Method.GET);
        
        Map<String, Object> attributes = Collections.singletonMap("filename", "foo/../../../shouldnt/access/test.png");
        
        EasyMock.expect(request.getAttributes()).andStubReturn(attributes);
        
        EasyMock.replay(request, response);
        
        exception.expect(allOf(
                instanceOf(RestletException.class), 
                hasProperty("status", is(Status.CLIENT_ERROR_FORBIDDEN))));
        try {
            restlet.handle(request, response);
        } finally {
            EasyMock.verify(request, response);
        }
    }
   
}
