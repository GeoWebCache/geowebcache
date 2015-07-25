/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Kevin Smith - Boundless (2015)
 *  
 */

package org.geowebcache.config;

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;

import org.easymock.Capture;
import org.easymock.IAnswer;
import org.easymock.classextension.EasyMock;
import org.geowebcache.io.GeoWebCacheXStream;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.StaticWebApplicationContext;

import com.thoughtworks.xstream.XStream;

public class XMLConfigurationXSchemaTest {
    
    @Rule
    public ExpectedException exception = ExpectedException.none();
    
    @Test
    public void testNotAllowNonGWCClass() throws Exception {
        // Check that classes from other packages on the class path can't be serialized
        ContextualConfigurationProvider.Context pc = ContextualConfigurationProvider.Context.REST;
        WebApplicationContext wac = new StaticWebApplicationContext();
        
        XStream xs = new GeoWebCacheXStream();
        
        xs = XMLConfiguration.getConfiguredXStreamWithContext(xs, wac, pc);
        
        exception.expect(com.thoughtworks.xstream.security.ForbiddenClassException.class);
        
        @SuppressWarnings("unused")
        Object o = xs.fromXML("<"+org.easymock.Capture.class.getCanonicalName()+" />");
    }
    
    @Ignore // Need to tighten the XStream permissions to get this to pass
    @Test
    public void testNotAllowNonXMLGWCClass() throws Exception {
        // Check that a class in GWC that shouldn't be serialized to XML can't be
        ContextualConfigurationProvider.Context pc = ContextualConfigurationProvider.Context.REST;
        WebApplicationContext wac = new StaticWebApplicationContext();
        
        XStream xs = new GeoWebCacheXStream();
        
        xs = XMLConfiguration.getConfiguredXStreamWithContext(xs, wac, pc);
        
        exception.expect(com.thoughtworks.xstream.security.ForbiddenClassException.class);
        
        @SuppressWarnings("unused")
        Object o = xs.fromXML("<"+XMLConfigurationXSchemaTest.class.getCanonicalName()+" />");
    }
    
    @Test
    public void testExtensionsCanAllow() throws Exception {
        // Check that an XMLConfigurationProvider can add a class to the whitelist
        
        XStream xs = new GeoWebCacheXStream();
        
        ContextualConfigurationProvider.Context pc = ContextualConfigurationProvider.Context.REST;
        WebApplicationContext wac = EasyMock.createMock("wac", WebApplicationContext.class);
        XMLConfigurationProvider provider = EasyMock.createMock("provider", XMLConfigurationProvider.class);
        EasyMock.expect(wac.getBeanNamesForType(XMLConfigurationProvider.class)).andReturn(new String[]{"provider"});
        EasyMock.expect(wac.getBean("provider")).andReturn(provider);
        final Capture<XStream> xsCap = new Capture<>();
        EasyMock.expect(provider.getConfiguredXStream(EasyMock.capture(xsCap))).andStubAnswer(new IAnswer<XStream>(){

            @Override
            public XStream answer() throws Throwable {
                XStream xs = xsCap.getValue();
                xs.allowTypes(new Class[]{org.easymock.Capture.class});
                return xs;
            }
            
        });
        
        EasyMock.replay(wac,provider);
        
        xs = XMLConfiguration.getConfiguredXStreamWithContext(xs, wac, pc);
        
        Object o = xs.fromXML("<"+org.easymock.Capture.class.getCanonicalName()+" />");
        
        assertThat(o, instanceOf(org.easymock.Capture.class));
        
        EasyMock.verify(wac,provider);
    }

}
