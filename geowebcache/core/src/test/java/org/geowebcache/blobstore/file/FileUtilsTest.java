package org.geowebcache.blobstore.file;

import org.geowebcache.storage.blobstore.file.FilePathUtils;
import org.junit.Assert;
import org.junit.Test;

public class FileUtilsTest {

    @Test
    public void testPadder() throws Exception {
        String actual = FilePathUtils.zeroPadder(0, 1);
        Assert.assertEquals("0", actual);

        actual = FilePathUtils.zeroPadder(1, 2);
        Assert.assertEquals("01", actual);

        actual = FilePathUtils.zeroPadder(1, 3);
        Assert.assertEquals("001", actual);

        actual = FilePathUtils.zeroPadder(12, 2);
        Assert.assertEquals("12", actual);

        actual = FilePathUtils.zeroPadder(11, 2);
        Assert.assertEquals("11", actual);

        actual = FilePathUtils.zeroPadder(10, 2);
        Assert.assertEquals("10", actual);

        actual = FilePathUtils.zeroPadder(100, 2);
        Assert.assertEquals("100", actual);

        actual = FilePathUtils.zeroPadder(101, 3);
        Assert.assertEquals("101", actual);

        actual = FilePathUtils.zeroPadder(102, 3);
        Assert.assertEquals("102", actual);

        actual = FilePathUtils.zeroPadder(103, 3);
        Assert.assertEquals("103", actual);

        actual = FilePathUtils.zeroPadder(99, 2);
        Assert.assertEquals("99", actual);

        actual = FilePathUtils.zeroPadder(99, 3);
        Assert.assertEquals("099", actual);
    }

    @Test
    public void testFindZoomLevel() {
        Assert.assertEquals(5, FilePathUtils.findZoomLevel("nyc", "nyc_05_01"));
        Assert.assertEquals(9, FilePathUtils.findZoomLevel("EPSG_4326", "EPSG_4326_09"));
        Assert.assertEquals(9, FilePathUtils.findZoomLevel("EPSG_4326", "EPSG_4326_09_21"));
        Assert.assertEquals(7, FilePathUtils.findZoomLevel("My_Weird_Gridset_Name", "My_Weird_Gridset_Name_0007"));
    }
}
