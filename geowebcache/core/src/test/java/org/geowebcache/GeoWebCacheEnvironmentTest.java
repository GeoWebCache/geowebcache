package org.geowebcache;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.springframework.context.ApplicationContext;

import junit.framework.TestCase;

/**
 * Unit test suite for {@link GeoWebCacheExtensions}
 * 
 * @author Gabriel Roldan (TOPP)
 * @version $Id$
 */
public class GeoWebCacheEnvironmentTest extends TestCase {

    protected void setUp() throws Exception {
        super.setUp();
        System.setProperty("TEST_SYS_PROPERTY", "ABC");
        System.setProperty("ALLOW_ENV_PARAMETRIZATION", "true");
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        System.setProperty("TEST_SYS_PROPERTY", "");
        System.setProperty("ALLOW_ENV_PARAMETRIZATION", "");
    }

    public void testEnvironment() {
        ApplicationContext appContext = createMock(ApplicationContext.class);
        GeoWebCacheEnvironment genv = new GeoWebCacheEnvironment();

        assertEquals(0, GeoWebCacheExtensions.extensionsCache.size());
        expect(appContext.getBeanNamesForType(GeoWebCacheEnvironment.class)).andReturn(
                new String[] { "geoWebCacheEnvironment" });
        expect(appContext.getBean("geoWebCacheEnvironment")).andReturn(genv);
        Map<String, GeoWebCacheEnvironment> genvMap = new HashMap<>();
        genvMap.put("geoWebCacheEnvironment", genv);
        expect(appContext.getBeansOfType(GeoWebCacheEnvironment.class))
                .andReturn(genvMap).anyTimes();
        replay(appContext);

        GeoWebCacheExtensions gse = new GeoWebCacheExtensions();
        gse.setApplicationContext(appContext);

        List<GeoWebCacheEnvironment> extensions = GeoWebCacheExtensions
                .extensions(GeoWebCacheEnvironment.class);
        assertNotNull(extensions);
        assertEquals(1, extensions.size());
        assertTrue(extensions.contains(genv));

        assertTrue(GeoWebCacheEnvironment.ALLOW_ENV_PARAMETRIZATION);
    }

    public void testSystemProperty() {
        // check for a property we did set up in the setUp
        GeoWebCacheEnvironment genv = new GeoWebCacheEnvironment();
        assertEquals("ABC", genv.resolveValue("${TEST_SYS_PROPERTY}"));
        
        Properties props = new Properties();
        props.setProperty("TEST_SYS_PROPERTY", "DEF");
        props.setProperty("TEST_PROPERTY", "WWW");
        genv.setProps(props);
        
        assertEquals("ABC", genv.resolveValue("${TEST_SYS_PROPERTY}"));
        assertEquals("WWW", genv.resolveValue("${TEST_PROPERTY}"));
    }
    
}
