package org.geowebcache.mime;

import static org.junit.Assert.*;

import org.junit.Test;

public class XMLMimeTest {

    @Test
    public void testOGC() {
        MimeType result = null;
        // get The MimeType from Format
        try {
            result = MimeType.createFromFormat("application/vnd.ogc.se_xml");
        } catch (MimeException e) {
            assertTrue("Format not found", false);
        }
        // Ensure it is not null
        assertNotNull(result);
        // Ensure it is the same instance
        assertEquals(XMLMime.ogcxml, result);
    }

    @Test
    public void testKML() {
        MimeType result = null;
        // get The MimeType from Format
        try {
            result = MimeType.createFromFormat("application/vnd.google-earth.kml+xml");
        } catch (MimeException e) {
            assertTrue("Format not found", false);
        }
        // Ensure it is not null
        assertNotNull(result);
        // Ensure it is the same instance
        assertEquals(XMLMime.kml, result);
        // get The MimeType from Extension
        try {
            result = MimeType.createFromExtension("kml");
        } catch (MimeException e) {
            assertTrue("Format not found", false);
        }
        // Ensure it is not null
        assertNotNull(result);
        // Ensure it is the same instance
        assertEquals(XMLMime.kml, result);
    }

    @Test
    public void testKMZ() {
        MimeType result = null;
        // get The MimeType from Format
        try {
            result = MimeType.createFromFormat("application/vnd.google-earth.kmz");
        } catch (MimeException e) {
            assertTrue("Format not found", false);
        }
        // Ensure it is not null
        assertNotNull(result);
        // Ensure it is the same instance
        assertEquals(XMLMime.kmz, result);
        // get The MimeType from Extension
        try {
            result = MimeType.createFromExtension("kmz");
        } catch (MimeException e) {
            assertTrue("Format not found", false);
        }
        // Ensure it is not null
        assertNotNull(result);
        // Ensure it is the same instance
        assertEquals(XMLMime.kmz, result);
    }

    @Test
    public void testGML() {
        MimeType result = null;
        // get The MimeType from Format
        try {
            result = MimeType.createFromFormat("application/vnd.ogc.gml");
        } catch (MimeException e) {
            assertTrue("Format not found", false);
        }
        // Ensure it is not null
        assertNotNull(result);
        // Ensure it is the same instance
        assertEquals(XMLMime.gml, result);
        // get The MimeType from Extension
        try {
            result = MimeType.createFromExtension("gml");
        } catch (MimeException e) {
            assertTrue("Format not found", false);
        }
        // Ensure it is not null
        assertNotNull(result);
        // Ensure it is the same instance
        assertEquals(XMLMime.gml, result);
    }

    @Test
    public void testGML3() {
        MimeType result = null;
        // get The MimeType from Format
        try {
            result = MimeType.createFromFormat("application/vnd.ogc.gml/3.1.1");
        } catch (MimeException e) {
            assertTrue("Format not found", false);
        }
        // Ensure it is not null
        assertNotNull(result);
        // Ensure it is the same instance
        assertEquals(XMLMime.gml3, result);
        // get The MimeType from Extension
        try {
            result = MimeType.createFromExtension("gml3");
        } catch (MimeException e) {
            assertTrue("Format not found", false);
        }
        // Ensure it is not null
        assertNotNull(result);
        // Ensure it is the same instance
        assertEquals(XMLMime.gml3, result);
    }

    @Test
    public void testUnknownExtension() throws MimeException {
        MimeType result = MimeType.createFromExtension("xxx");
        // Ensure it is null
        assertNull(result);
    }

    @Test(expected = MimeException.class)
    public void testUnknownFormat() throws MimeException {
        MimeType result = MimeType.createFromFormat("xxx/xxx");
    }

}
