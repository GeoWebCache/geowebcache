package org.geowebcache.arcgis.compact;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URL;
import org.geowebcache.io.Resource;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit test for ArcGIS compact cache classes. Available data in supplied test caches:
 *
 * <p>10.0 - 10.2 cache
 *
 * <p>zoom level | min row | max row | min col | max col | | | | 5 | 10 | 13 | 4 | 10 | | | | 6 | 22 | 28 | 10 | 21
 *
 * <p>- image format is JPEG - tile size for (5,12,7) is 6342 bytes - tile size for (6,25,17) is 6308 bytes
 *
 * <p>10.3 cache
 *
 * <p>zoom level | min row | max row | min col | max col | | | | 4 | 5 | 6 | 2 | 5 | | | | 5 | 10 | 13 | 4 | 10
 *
 * <p>- image format is JPEG - tile size for (4,5,4) is 7288 bytes - tile size for (5,11,5) is 6055 bytes
 *
 * <p>Not verifiable with this unit test because the supplied test cache is too small:
 *
 * <p>- zoom levels can contain more than one .bundle/.bundlx file - row and column numbers have at least 4 digits in
 * bundle file name, but with really big caches row and column numbers can have more than 4 digits
 *
 * @author Bjoern Saxe
 */
public class ArcGISCompactCacheTest {
    private static final byte[] JFIFHeader = {
        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
    };

    @Test
    public void testCompactCacheV1() throws Exception {
        URL url = getClass().getResource("/compactcache/_alllayers/");
        ArcGISCompactCache cache = new ArcGISCompactCacheV1(url.toURI().getPath());

        Assert.assertNotNull(cache);

        Assert.assertNull(cache.getBundleFileResource(5, -1, -1));
        Assert.assertNull(cache.getBundleFileResource(4, 10, 4));
        Assert.assertNull(cache.getBundleFileResource(7, 22, 10));

        Assert.assertNull(cache.getBundleFileResource(5, 0, 0));
        Assert.assertNotNull(cache.getBundleFileResource(5, 10, 4));
        Assert.assertNotNull(cache.getBundleFileResource(5, 13, 10));
        Assert.assertNotNull(cache.getBundleFileResource(5, 12, 7));

        Assert.assertNull(cache.getBundleFileResource(6, 0, 0));
        Assert.assertNotNull(cache.getBundleFileResource(6, 22, 10));
        Assert.assertNotNull(cache.getBundleFileResource(6, 22, 10));
        Assert.assertNotNull(cache.getBundleFileResource(6, 25, 17));
    }

    @Test
    public void testCompactCacheV2() throws Exception {
        URL url = getClass().getResource("/compactcacheV2/_alllayers/");
        ArcGISCompactCache cache = new ArcGISCompactCacheV2(url.toURI().getPath());

        Assert.assertNotNull(cache);

        Assert.assertNull(cache.getBundleFileResource(5, -1, -1));
        Assert.assertNull(cache.getBundleFileResource(3, 5, 2));
        Assert.assertNull(cache.getBundleFileResource(4, 4, 1));
        Assert.assertNull(cache.getBundleFileResource(4, 7, 6));
        Assert.assertNull(cache.getBundleFileResource(5, 9, 4));
        Assert.assertNull(cache.getBundleFileResource(6, 13, 11));

        Assert.assertNotNull(cache.getBundleFileResource(4, 5, 2));
        Assert.assertNotNull(cache.getBundleFileResource(4, 5, 4));
        Assert.assertNotNull(cache.getBundleFileResource(4, 6, 5));
        Assert.assertNotNull(cache.getBundleFileResource(5, 10, 4));
        Assert.assertNotNull(cache.getBundleFileResource(5, 11, 9));
        Assert.assertNotNull(cache.getBundleFileResource(5, 13, 10));
    }

    @Test
    public void testBundleFileResourceV1() throws Exception {
        URL url = getClass().getResource("/compactcache/_alllayers/");
        ArcGISCompactCache cache = new ArcGISCompactCacheV1(url.toURI().getPath());

        Assert.assertNotNull(cache);

        Resource resource = cache.getBundleFileResource(5, 12, 7);
        Assert.assertNotNull(resource);
        Assert.assertEquals(6342, resource.getSize());

        File f = new File("5_12_7.jpg");
        try (FileOutputStream fos = new FileOutputStream(f)) {
            resource.transferTo(fos.getChannel());
        }

        Assert.assertTrue(startsWithJPEGHeader(f));

        f.delete();

        resource = cache.getBundleFileResource(6, 25, 17);
        Assert.assertNotNull(resource);
        Assert.assertEquals(6308, resource.getSize());

        f = new File("6_25_17.jpg");
        try (FileOutputStream fos = new FileOutputStream(f)) {
            resource.transferTo(fos.getChannel());
        }

        Assert.assertTrue(startsWithJPEGHeader(f));

        f.delete();
    }

    @Test
    public void testBundleFileResourceV2() throws Exception {
        URL url = getClass().getResource("/compactcacheV2/_alllayers/");
        ArcGISCompactCache cache = new ArcGISCompactCacheV2(url.toURI().getPath());

        Assert.assertNotNull(cache);

        Resource resource = cache.getBundleFileResource(4, 5, 4);
        Assert.assertNotNull(resource);
        Assert.assertEquals(7288, resource.getSize());

        File f = new File("4_5_4.jpg");
        try (FileOutputStream fos = new FileOutputStream(f)) {
            resource.transferTo(fos.getChannel());
        }

        Assert.assertTrue(startsWithJPEGHeader(f));

        f.delete();

        resource = cache.getBundleFileResource(5, 11, 5);
        Assert.assertNotNull(resource);
        Assert.assertEquals(6055, resource.getSize());

        f = new File("5_11_5.jpg");
        try (FileOutputStream fos = new FileOutputStream(f)) {
            resource.transferTo(fos.getChannel());
        }

        Assert.assertTrue(startsWithJPEGHeader(f));

        f.delete();
    }

    private boolean startsWithJPEGHeader(File f) {
        try (FileInputStream fis = new FileInputStream(f)) {

            byte[] fileHeader = new byte[JFIFHeader.length];

            fis.read(fileHeader, 0, JFIFHeader.length);
            fis.close();

            for (int i = 0; i < fileHeader.length; i++) {
                if (fileHeader[i] != JFIFHeader[i]) return false;
            }
        } catch (Exception e) {
            return false;
        }

        return true;
    }
}
