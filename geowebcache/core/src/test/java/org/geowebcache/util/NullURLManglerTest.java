package org.geowebcache.util;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class NullURLManglerTest {

    private URLMangler urlMangler;

    @Before
    public void setUp() {
        urlMangler = new NullURLMangler();
    }

    @Test
    public void testBuildURL() {
        Assert.assertEquals(
                "http://foo.example.com/foo/bar",
                URLManglerUtils.buildURL(
                        "http://foo.example.com", "/foo", "/bar", null, urlMangler, URLMangler.URLType.SERVICE));
    }

    @Test
    public void testBuildURLNormalizesSlashes() {
        Assert.assertEquals(
                "http://foo.example.com/foo/bar",
                URLManglerUtils.buildURL(
                        "http://foo.example.com/", "/foo/", "/bar", null, urlMangler, URLMangler.URLType.SERVICE));
        Assert.assertEquals(
                "http://foo.example.com/foo/bar",
                URLManglerUtils.buildURL(
                        "http://foo.example.com/", "foo/", "bar", null, urlMangler, URLMangler.URLType.SERVICE));
    }

    @Test
    public void testBuildURLWithEmptyContext() {
        Assert.assertEquals(
                "http://foo.example.com/bar",
                URLManglerUtils.buildURL(
                        "http://foo.example.com/", "/", "/bar", null, urlMangler, URLMangler.URLType.SERVICE));
        Assert.assertEquals(
                "http://foo.example.com/bar",
                URLManglerUtils.buildURL(
                        "http://foo.example.com/", null, "/bar", null, urlMangler, URLMangler.URLType.SERVICE));
    }

    @Test
    public void testBuildURLWithQueryParameters() {
        Map<String, String> queryParameters = new LinkedHashMap<>();
        queryParameters.put("projecttoken", "abc123");
        Assert.assertEquals(
                "http://foo.example.com/foo/bar?projecttoken=abc123",
                URLManglerUtils.buildURL(
                        "http://foo.example.com/",
                        "/foo",
                        "/bar",
                        queryParameters,
                        urlMangler,
                        URLMangler.URLType.SERVICE));
    }
}
