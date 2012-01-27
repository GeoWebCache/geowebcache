package org.geowebcache.blobstore.file;

import static org.geowebcache.storage.blobstore.file.FilePathGenerator.*;
import junit.framework.TestCase;

import org.geowebcache.storage.blobstore.file.FilePathGenerator;

public class FilePathKey2Test extends TestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    public void testPadder() throws Exception {
        String actual = FilePathGenerator.zeroPadder(0, 1);
        assertEquals("0", actual);

        actual = FilePathGenerator.zeroPadder(1, 2);
        assertEquals("01", actual);

        actual = FilePathGenerator.zeroPadder(1, 3);
        assertEquals("001", actual);

        actual = FilePathGenerator.zeroPadder(12, 2);
        assertEquals("12", actual);

        actual = FilePathGenerator.zeroPadder(11, 2);
        assertEquals("11", actual);

        actual = FilePathGenerator.zeroPadder(10, 2);
        assertEquals("10", actual);

        actual = FilePathGenerator.zeroPadder(100, 2);
        assertEquals("100", actual);

        actual = FilePathGenerator.zeroPadder(101, 3);
        assertEquals("101", actual);

        actual = FilePathGenerator.zeroPadder(102, 3);
        assertEquals("102", actual);

        actual = FilePathGenerator.zeroPadder(103, 3);
        assertEquals("103", actual);

        actual = FilePathGenerator.zeroPadder(99, 2);
        assertEquals("99", actual);

        actual = FilePathGenerator.zeroPadder(99, 3);
        assertEquals("099", actual);
    }

    public void testFindZoomLevel() {
        assertEquals(5, findZoomLevel("nyc", "nyc_05_01"));
        assertEquals(9, findZoomLevel("EPSG_4326", "EPSG_4326_09"));
        assertEquals(9, findZoomLevel("EPSG_4326", "EPSG_4326_09_21"));
        assertEquals(7, findZoomLevel("My_Weird_Gridset_Name", "My_Weird_Gridset_Name_0007"));
    }
}