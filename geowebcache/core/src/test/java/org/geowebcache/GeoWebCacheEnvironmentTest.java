package org.geowebcache;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;

/**
 * Unit test suite for {@link GeoWebCacheExtensions}
 *
 * @author Gabriel Roldan (TOPP)
 * @version $Id$
 */
public class GeoWebCacheEnvironmentTest {

    @Before
    public void setUp() throws Exception {
        System.setProperty("TEST_SYS_PROPERTY", "ABC");
        System.setProperty("ALLOW_ENV_PARAMETRIZATION", "true");
    }

    @After
    public void tearDown() throws Exception {
        System.setProperty("TEST_SYS_PROPERTY", "");
        System.setProperty("ALLOW_ENV_PARAMETRIZATION", "");
    }

    @Test
    public void testEnvironment() {
        ApplicationContext appContext = createMock(ApplicationContext.class);
        GeoWebCacheEnvironment genv = new GeoWebCacheEnvironment();

        Assert.assertEquals(0, GeoWebCacheExtensions.extensionsCache.size());
        expect(appContext.getBeanNamesForType(GeoWebCacheEnvironment.class))
                .andReturn(new String[] {"geoWebCacheEnvironment"});
        expect(appContext.getBean("geoWebCacheEnvironment")).andReturn(genv);
        Map<String, GeoWebCacheEnvironment> genvMap = new HashMap<>();
        genvMap.put("geoWebCacheEnvironment", genv);
        expect(appContext.getBeansOfType(GeoWebCacheEnvironment.class))
                .andReturn(genvMap)
                .anyTimes();
        replay(appContext);

        GeoWebCacheExtensions gse = new GeoWebCacheExtensions();
        gse.setApplicationContext(appContext);

        List<GeoWebCacheEnvironment> extensions =
                GeoWebCacheExtensions.extensions(GeoWebCacheEnvironment.class);
        Assert.assertNotNull(extensions);
        Assert.assertEquals(1, extensions.size());
        Assert.assertTrue(extensions.contains(genv));

        Assert.assertTrue(GeoWebCacheEnvironment.ALLOW_ENV_PARAMETRIZATION);
    }

    @Test
    public void testSystemProperty() {
        // check for a property we did set up in the setUp
        GeoWebCacheEnvironment genv = new GeoWebCacheEnvironment();
        Assert.assertEquals("ABC", genv.resolveValue("${TEST_SYS_PROPERTY}"));

        Properties props = new Properties();
        props.setProperty("TEST_SYS_PROPERTY", "DEF");
        props.setProperty("TEST_PROPERTY", "WWW");
        genv.setProps(props);

        Assert.assertEquals("ABC", genv.resolveValue("${TEST_SYS_PROPERTY}"));
        Assert.assertEquals("WWW", genv.resolveValue("${TEST_PROPERTY}"));
    }
}
