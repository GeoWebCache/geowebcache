package org.geowebcache;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;

import junit.framework.TestCase;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.springframework.context.ApplicationContext;

/**
 * Unit test suite for {@link GeoWebCacheExtensions}
 * 
 * @author Gabriel Roldan (TOPP)
 * @version $Id$
 */
public class GeoWebCacheExtensionsTest extends TestCase {

    protected void setUp() throws Exception {
        super.setUp();
        System.setProperty("TEST_PROPERTY", "ABC");
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        System.setProperty("TEST_PROPERTY", "");
    }

    public void testSetApplicationContext() {
        ApplicationContext appContext1 = createMock(ApplicationContext.class);
        ApplicationContext appContext2 = createMock(ApplicationContext.class);

        GeoWebCacheExtensions gse = new GeoWebCacheExtensions();
        gse.setApplicationContext(appContext1);
        GeoWebCacheExtensions.extensionsCache.put(GeoWebCacheExtensionsTest.class,
                new String[] { "fake" });

        assertSame(appContext1, GeoWebCacheExtensions.context);

        gse.setApplicationContext(appContext2);
        assertSame(appContext2, GeoWebCacheExtensions.context);
        assertEquals(0, GeoWebCacheExtensions.extensionsCache.size());
    }

    public void testExtensions() {
        ApplicationContext appContext = createMock(ApplicationContext.class);
        GeoWebCacheExtensions gse = new GeoWebCacheExtensions();
        gse.setApplicationContext(appContext);

        // context beans
        Map<String, GeoWebCacheExtensionsTest> beans = new HashMap<>();
        beans.put("testKey", this);
        beans.put("fakeKey", null);

        assertEquals(0, GeoWebCacheExtensions.extensionsCache.size());
        expect(appContext.getBeansOfType(GeoWebCacheExtensionsTest.class)).andReturn(beans);
        expect(appContext.getBean("testKey")).andReturn(this);
        // note I'm testing null is a valid value. If that's not the case, it
        // should be reflected in the code, but I'm writing the test after the
        // code so that's what it does
        expect(appContext.getBean("fakeKey")).andReturn(null);
        replay(appContext);

        List<GeoWebCacheExtensionsTest> extensions = GeoWebCacheExtensions
                .extensions(GeoWebCacheExtensionsTest.class);
        assertNotNull(extensions);
        assertEquals(2, extensions.size());
        assertTrue(extensions.contains(this));
        assertTrue(extensions.contains(null));

        assertEquals(1, GeoWebCacheExtensions.extensionsCache.size());
        assertTrue(GeoWebCacheExtensions.extensionsCache
                .containsKey(GeoWebCacheExtensionsTest.class));
        assertNotNull(GeoWebCacheExtensions.extensionsCache.get(GeoWebCacheExtensionsTest.class));
        assertEquals(2,
                GeoWebCacheExtensions.extensionsCache.get(GeoWebCacheExtensionsTest.class).length);

        verify(appContext);
    }

    /**
     * If a context is explicitly provided that is not the one set through setApplicationContext(),
     * the extensions() method shall look into it and bypass the cache
     */
    public void testExtensionsApplicationContext() {
        ApplicationContext appContext = createMock(ApplicationContext.class);
        ApplicationContext customAppContext = createMock(ApplicationContext.class);

        GeoWebCacheExtensions gse = new GeoWebCacheExtensions();
        gse.setApplicationContext(appContext);

        // context beans
        Map<String, GeoWebCacheExtensionsTest> beans = new HashMap<>();
        beans.put("itDoesntMatterForThePurpose", this);

        // setApplicationContext cleared the static cache
        assertEquals(0, GeoWebCacheExtensions.extensionsCache.size());
        // set the expectation over the app context used as argument
        expect(customAppContext.getBeansOfType(GeoWebCacheExtensionsTest.class)).andReturn(beans);
        expect(customAppContext.getBean("itDoesntMatterForThePurpose")).andReturn(this);
        replay(customAppContext);
        replay(appContext);

        List<GeoWebCacheExtensionsTest> extensions = GeoWebCacheExtensions.extensions(
                GeoWebCacheExtensionsTest.class, customAppContext);

        assertNotNull(extensions);
        assertEquals(1, extensions.size());
        assertSame(this, extensions.get(0));
        // cache should be untouched after this since our own context were used
        assertEquals(0, GeoWebCacheExtensions.extensionsCache.size());

        verify(appContext);
        verify(customAppContext);
    }

    public void testBeanString() {
        ApplicationContext appContext = createMock(ApplicationContext.class);

        GeoWebCacheExtensions gse = new GeoWebCacheExtensions();

        gse.setApplicationContext(null);
        assertNull(GeoWebCacheExtensions.bean("beanName"));

        gse.setApplicationContext(appContext);

        expect(appContext.getBean("beanName")).andReturn(null); // call #1
        expect(appContext.getBean("beanName")).andReturn(this); // call #2
        replay(appContext);

        assertNull(GeoWebCacheExtensions.bean("beanName")); // call #1
        assertSame(this, GeoWebCacheExtensions.bean("beanName")); // call #2

        verify(appContext);
    }

    public void testSystemProperty() {
        // check for a property we did set up in the setUp
        assertEquals("ABC",
                GeoWebCacheExtensions.getProperty("TEST_PROPERTY", (ApplicationContext) null));
        assertEquals("ABC",
                GeoWebCacheExtensions.getProperty("TEST_PROPERTY", (ServletContext) null));
    }

    public void testWebProperty() {
        ServletContext servletContext = createMock(ServletContext.class);
        expect(servletContext.getInitParameter("TEST_PROPERTY")).andReturn("DEF").anyTimes();
        expect(servletContext.getInitParameter("WEB_PROPERTY")).andReturn("WWW").anyTimes();
        replay(servletContext);

        assertEquals("ABC", GeoWebCacheExtensions.getProperty("TEST_PROPERTY", servletContext));
        assertEquals("WWW", GeoWebCacheExtensions.getProperty("WEB_PROPERTY", servletContext));
    }

    public void testExtensionsWithPriority() {
        // creating a spring context with some beans that will implements priority interface
        ApplicationContext appContext = createMock(ApplicationContext.class);
        BeanWithPriority beanA = new BeanWithPriority(15, "beanA");
        BeanWithPriority beanB = new BeanWithPriority(3, "beanB");
        BeanWithPriority beanC = new BeanWithPriority(27, "beanC");
        Map<String, BeanWithPriority> beans = new HashMap<>();
        beans.put("beanA", beanA);
        beans.put("beanB", beanB);
        beans.put("beanC", beanC);
        // defining invocations expectations
        expect(appContext.getBeansOfType(BeanWithPriority.class)).andReturn(beans);
        expect(appContext.getBean("beanA")).andReturn(beanA);
        expect(appContext.getBean("beanB")).andReturn(beanB);
        expect(appContext.getBean("beanC")).andReturn(beanC);
        replay(appContext);
        // registering our mocked spring application context
        GeoWebCacheExtensions gwcExtensions = new GeoWebCacheExtensions();
        gwcExtensions.setApplicationContext(appContext);
        // the cache should be empty
        assertThat(GeoWebCacheExtensions.extensionsCache.size(), is(0));
        // we should get beans ordered by their priority
        List<BeanWithPriority> extensions = GeoWebCacheExtensions.extensions(BeanWithPriority.class);
        assertThat(extensions.size(), is(3));
        assertThat(extensions, Matchers.contains(beanB, beanA, beanC));
        verify(appContext);
    }

    /**
     * Helper to test extensions points priorities.
     */
    private static final class BeanWithPriority implements GeoWebCacheExtensionPriority {

        final int priority;
        final String id;

        public BeanWithPriority(int priority, String id) {
            this.priority = priority;
            this.id = id;
        }

        @Override
        public int getPriority() {
            return priority;
        }
    }
}
