package org.geowebcache.service.wmts;

import static org.junit.Assert.assertEquals;

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
}
