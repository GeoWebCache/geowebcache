package org.geowebcache.util;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class NullURLManglerTest {

    private URLMangler urlMangler;

    @Before
    public void setUp() throws Exception {
        urlMangler = new NullURLMangler();
    }

    @Test
    public void testBuildURL() {
        String url = urlMangler.buildURL("http://foo.example.com", "/foo", "/bar");
        Assert.assertEquals("http://foo.example.com/foo/bar", url);
    }

    @Test
    public void testBuildTrailingSlashes() throws Exception {
        String url = urlMangler.buildURL("http://foo.example.com/", "/foo/", "/bar");
        Assert.assertEquals("http://foo.example.com/foo/bar", url);
    }

    @Test
    public void testBuildNoLeadingSlashes() throws Exception {
        String url = urlMangler.buildURL("http://foo.example.com/", "foo/", "bar");
        Assert.assertEquals("http://foo.example.com/foo/bar", url);
    }

    @Test
    public void testBuildRootContext() throws Exception {
        String url = urlMangler.buildURL("http://foo.example.com/", "/", "/bar");
        Assert.assertEquals("http://foo.example.com/bar", url);
    }

    @Test
    public void testBuildNullContext() throws Exception {
        String url = urlMangler.buildURL("http://foo.example.com/", null, "/bar");
        Assert.assertEquals("http://foo.example.com/bar", url);
    }

    @Test
    public void testBuildEmptyContext() throws Exception {
        String url = urlMangler.buildURL("http://foo.example.com/", "", "/bar");
        Assert.assertEquals("http://foo.example.com/bar", url);
    }

    @Test
    public void testBuildWithQueryParametersLeavesMapUntouched() throws Exception {
        Map<String, String> queryParameters = new LinkedHashMap<>();
        queryParameters.put("projecttoken", "abc123");

        URLMangler.UrlAndParams result =
                urlMangler.buildURL("http://foo.example.com/", "/foo", "/bar", queryParameters);

        Assert.assertEquals("http://foo.example.com/foo/bar", result.url());
        Assert.assertEquals(1, result.queryParameters().size());
        Assert.assertEquals("abc123", result.queryParameters().get("projecttoken"));
    }
}
