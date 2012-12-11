package org.geowebcache.util;

import junit.framework.TestCase;

public class NullURLManglerTest extends TestCase {

    private URLMangler urlMangler;

    @Override
    protected void setUp() throws Exception {
        urlMangler = new NullURLMangler();
    }

    public void testBuildURL() {
        String url = urlMangler.buildURL("http://foo.example.com", "/foo", "/bar");
        assertEquals("http://foo.example.com/foo/bar", url);
    }

    public void testBuildTrailingSlashes() throws Exception {
        String url = urlMangler.buildURL("http://foo.example.com/", "/foo/", "/bar");
        assertEquals("http://foo.example.com/foo/bar", url);
    }

    public void testBuildNoLeadingSlashes() throws Exception {
        String url = urlMangler.buildURL("http://foo.example.com/", "foo/", "bar");
        assertEquals("http://foo.example.com/foo/bar", url);
    }

}
