package org.geowebcache.blobstore.file;

import org.geowebcache.storage.blobstore.file.FilePathUtils;

import junit.framework.TestCase;

public class FileUtilsTest extends TestCase {
    
    public void testPadder() throws Exception {
        String actual = FilePathUtils.zeroPadder(0, 1);
        assertEquals("0", actual);

        actual = FilePathUtils.zeroPadder(1, 2);
        assertEquals("01", actual);

        actual = FilePathUtils.zeroPadder(1, 3);
        assertEquals("001", actual);

        actual = FilePathUtils.zeroPadder(12, 2);
        assertEquals("12", actual);

        actual = FilePathUtils.zeroPadder(11, 2);
        assertEquals("11", actual);

        actual = FilePathUtils.zeroPadder(10, 2);
        assertEquals("10", actual);

        actual = FilePathUtils.zeroPadder(100, 2);
        assertEquals("100", actual);

        actual = FilePathUtils.zeroPadder(101, 3);
        assertEquals("101", actual);

        actual = FilePathUtils.zeroPadder(102, 3);
        assertEquals("102", actual);

        actual = FilePathUtils.zeroPadder(103, 3);
        assertEquals("103", actual);

        actual = FilePathUtils.zeroPadder(99, 2);
        assertEquals("99", actual);

        actual = FilePathUtils.zeroPadder(99, 3);
        assertEquals("099", actual);
    }

    public void testFindZoomLevel() {
        assertEquals(5, FilePathUtils.findZoomLevel("nyc", "nyc_05_01"));
        assertEquals(9, FilePathUtils.findZoomLevel("EPSG_4326", "EPSG_4326_09"));
        assertEquals(9, FilePathUtils.findZoomLevel("EPSG_4326", "EPSG_4326_09_21"));
        assertEquals(7, FilePathUtils.findZoomLevel("My_Weird_Gridset_Name", "My_Weird_Gridset_Name_0007"));
    }
}
