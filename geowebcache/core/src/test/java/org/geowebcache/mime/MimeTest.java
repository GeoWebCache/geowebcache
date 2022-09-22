package org.geowebcache.mime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MimeTest {
    // Test that static binary check matches the instance method
    @Test
    public void testIsBinary() throws Exception {
        assertTrue(MimeType.isBinary(ImageMime.png.mimeType));
        assertFalse(MimeType.isBinary(XMLMime.gml.mimeType));
        for (MimeType mt : ApplicationMime.ALL) {
            assertEquals(mt.isBinary(), MimeType.isBinary(mt.mimeType));
        }
    }
}
