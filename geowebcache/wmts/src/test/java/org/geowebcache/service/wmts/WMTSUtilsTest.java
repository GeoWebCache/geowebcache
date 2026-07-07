package org.geowebcache.service.wmts;

import static org.junit.Assert.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class WMTSUtilsTest {

    @Test
    public void testKvpServiceMetadataURLNoParameters() throws Exception {
        String result = WMTSUtils.getKvpServiceMetadataURL("https://www.foo.com/");
        assertEquals("https://www.foo.com/?SERVICE=wmts&REQUEST=getcapabilities&VERSION=1.0.0", result);
    }

    @Test
    public void testKvpServiceMetadataURLNoParametersWithAnchor() throws Exception {
        String result = WMTSUtils.getKvpServiceMetadataURL("https://www.foo.com/#anchor");
        assertEquals("https://www.foo.com/?SERVICE=wmts&REQUEST=getcapabilities&VERSION=1.0.0#anchor", result);
    }

    @Test
    public void testKvpServiceMetadataURLNoParameters_questionMark() throws Exception {
        String result = WMTSUtils.getKvpServiceMetadataURL("https://www.foo.com/?");
        assertEquals("https://www.foo.com/?SERVICE=wmts&REQUEST=getcapabilities&VERSION=1.0.0", result);
    }

    @Test
    public void testKvpServiceMetadataURLSingleParameter() throws Exception {
        String result = WMTSUtils.getKvpServiceMetadataURL("https://www.foo.com/?bar=doo");
        assertEquals("https://www.foo.com/?bar=doo&SERVICE=wmts&REQUEST=getcapabilities&VERSION=1.0.0", result);
    }

    @Test
    public void testKvpServiceMetadataURLSingleParameter_ampersand() throws Exception {
        String result = WMTSUtils.getKvpServiceMetadataURL("https://www.foo.com/?bar=doo&");
        assertEquals("https://www.foo.com/?bar=doo&SERVICE=wmts&REQUEST=getcapabilities&VERSION=1.0.0", result);
    }

    @Test
    public void testKvpServiceMetadataURLMultipleParameters() throws Exception {
        String result = WMTSUtils.getKvpServiceMetadataURL("https://www.foo.com/?bar=doo&dii=daa");
        assertEquals("https://www.foo.com/?bar=doo&dii=daa&SERVICE=wmts&REQUEST=getcapabilities&VERSION=1.0.0", result);
    }

    @Test
    public void testKvpServiceMetadataURLMultipleParameters_manyAmpersands() throws Exception {
        String result = WMTSUtils.getKvpServiceMetadataURL("https://www.foo.com/?bar=doo&dii=daa&&&");
        assertEquals("https://www.foo.com/?bar=doo&dii=daa&SERVICE=wmts&REQUEST=getcapabilities&VERSION=1.0.0", result);
    }

    @Test
    public void testKvpServiceMetadataURLMultipleParameters_manyAmpersands_withAnchor() throws Exception {
        String result = WMTSUtils.getKvpServiceMetadataURL("https://www.foo.com/?bar=doo&dii=daa&&&#hello");
        assertEquals(
                "https://www.foo.com/?bar=doo&dii=daa&SERVICE=wmts&REQUEST=getcapabilities&VERSION=1.0.0#hello",
                result);
    }

    /**
     * Verifies that query parameters are appended to a bare WMTS URL and that the first parameter starts with a
     * question mark.
     */
    @Test
    public void testAppendQueryParametersNoQuery() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("projecttoken", "abc123");

        String result = WMTSUtils.appendQueryParameters("https://www.foo.com/wmts/rest/WMTSCapabilities.xml", params);

        assertEquals("https://www.foo.com/wmts/rest/WMTSCapabilities.xml?projecttoken=abc123", result);
    }

    /** Verifies that query parameters are appended after an existing query string using an ampersand separator. */
    @Test
    public void testAppendQueryParametersWithQuery() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("projecttoken", "abc123");

        String result = WMTSUtils.appendQueryParameters(
                "https://www.foo.com/wmts/rest/WMTSCapabilities.xml?format=image/png", params);

        assertEquals("https://www.foo.com/wmts/rest/WMTSCapabilities.xml?format=image/png&projecttoken=abc123", result);
    }

    /**
     * Verifies that propagated WMTS query parameters are appended in the same order they were provided and that the
     * existing URL path remains unchanged. Values are percent-encoded, so reserved characters such as {@code /},
     * {@code {} and {@code }} show up encoded in the expected result.
     */
    @Test
    public void testAppendQueryParametersOrder() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("format", "image/png");
        params.put("time", "{time}");
        params.put("elevation", "{elevation}");
        params.put("projecttoken", "abc123");

        String result = WMTSUtils.appendQueryParameters("https://www.foo.com/wmts/rest/layer", params);

        assertEquals(
                "https://www.foo.com/wmts/rest/layer?format=image%2Fpng&time=%7Btime%7D&elevation=%7Belevation%7D&projecttoken=abc123",
                result);
    }

    /**
     * Verifies that a propagated parameter value containing reserved URL characters (such as {@code &} and {@code =})
     * is percent-encoded rather than being allowed to inject additional query parameters.
     */
    @Test
    public void testAppendQueryParametersEncodesReservedCharacters() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("projecttoken", "abc&evil=1");

        String result = WMTSUtils.appendQueryParameters("https://www.foo.com/wmts/rest/layer", params);

        assertEquals("https://www.foo.com/wmts/rest/layer?projecttoken=abc%26evil%3D1", result);
    }
}
