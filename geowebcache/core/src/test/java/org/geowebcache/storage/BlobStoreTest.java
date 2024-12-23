/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Arne Kepp / The Open Planning Project 2009
 */
package org.geowebcache.storage;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.easymock.EasyMock;
import org.geowebcache.grid.SRS;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.blobstore.file.FileBlobStore;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class BlobStoreTest {
    public static final String TEST_BLOB_DIR_NAME = "gwcTestBlobs";
    private BlobStore fbs;

    @After
    public void tearDown() throws Exception {
        if (fbs != null) {
            fbs.destroy();
        }

        File fh = new File(StorageBrokerTest.findTempDir() + File.separator + TEST_BLOB_DIR_NAME);

        if (fh.exists()) {
            FileUtils.deleteDirectory(fh);
            if (fh.exists()) {
                Assert.fail("Could not cleanup blob store directory\n"
                        + "Unable to delete "
                        + org.geowebcache.util.FileUtils.printFileTree(fh));
            }
        }
    }

    @Test
    public void testTile() throws Exception {
        fbs = setup();

        Resource bytes = new ByteArrayResource("1 2 3 4 5 6 test".getBytes());
        long[] xyz = {1L, 2L, 3L};
        Map<String, String> parameters = new HashMap<>();
        parameters.put("a", "x");
        parameters.put("b", "ø");
        TileObject to = TileObject.createCompleteTileObject(
                "test:123123 112", xyz, "EPSG:4326", "image/jpeg", parameters, bytes);

        fbs.put(to);

        TileObject to2 =
                TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326", "image/jpeg", parameters);
        fbs.get(to2);

        Assert.assertEquals(to.getBlobFormat(), to2.getBlobFormat());

        try (InputStream is = to.getBlob().getInputStream();
                InputStream is2 = to2.getBlob().getInputStream()) {
            Assert.assertTrue(IOUtils.contentEquals(is, is2));
        }
    }

    @Test
    public void testTileDelete() throws Exception {
        fbs = setup();

        Map<String, String> parameters = new HashMap<>();
        parameters.put("a", "x");
        parameters.put("b", "ø");

        Resource bytes = new ByteArrayResource("1 2 3 4 5 6 test".getBytes());
        long[] xyz = {5L, 6L, 7L};
        TileObject to = TileObject.createCompleteTileObject(
                "test:123123 112", xyz, "EPSG:4326", "image/jpeg", parameters, bytes);

        fbs.put(to);

        TileObject to2 =
                TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326", "image/jpeg", parameters);
        fbs.get(to2);

        try (InputStream is = to2.getBlob().getInputStream();
                InputStream is2 = bytes.getInputStream()) {
            Assert.assertTrue(IOUtils.contentEquals(is, is2));
        }
        TileObject to3 =
                TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326", "image/jpeg", parameters);
        fbs.delete(to3);

        TileObject to4 =
                TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326", "image/jpeg", parameters);
        Assert.assertFalse(fbs.get(to4));
    }

    @Test
    public void testTilRangeDelete() throws Exception {
        fbs = setup();

        Resource bytes = new ByteArrayResource("1 2 3 4 5 6 test".getBytes());
        Map<String, String> parameters = new HashMap<>();
        parameters.put("a", "x");
        parameters.put("b", "ø");
        MimeType mime = ImageMime.png;
        SRS srs = SRS.getEPSG4326();
        String layerName = "test:123123 112";

        int zoomLevel = 7;
        int x = 25;
        int y = 6;

        // long[] origXYZ = {x,y,zoomLevel};

        TileObject[] tos = new TileObject[6];

        for (int i = 0; i < tos.length; i++) {
            long[] xyz = {x + i - 1, y, zoomLevel};
            tos[i] = TileObject.createCompleteTileObject(
                    layerName, xyz, srs.toString(), mime.getFormat(), parameters, bytes);
            fbs.put(tos[i]);
        }

        long[][] rangeBounds = new long[zoomLevel + 2][5];
        int zoomStart = zoomLevel - 1;
        int zoomStop = zoomLevel + 1;

        long[] range = {x, y, x + tos.length - 3, y, zoomLevel};
        rangeBounds[zoomLevel] = range;

        TileRange trObj = new TileRange(layerName, srs.toString(), zoomStart, zoomStop, rangeBounds, mime, parameters);

        fbs.delete(trObj);

        // starting x and x + tos.length should have data, the remaining should not
        TileObject firstTO =
                TileObject.createQueryTileObject(layerName, tos[0].xyz, srs.toString(), mime.getFormat(), parameters);
        fbs.get(firstTO);
        try (InputStream is = firstTO.getBlob().getInputStream();
                InputStream is2 = bytes.getInputStream()) {
            Assert.assertTrue(IOUtils.contentEquals(is, is2));
        }
        TileObject lastTO = TileObject.createQueryTileObject(
                layerName, tos[tos.length - 1].xyz, srs.toString(), mime.getFormat(), parameters);
        fbs.get(lastTO);
        try (InputStream is = lastTO.getBlob().getInputStream();
                InputStream is2 = bytes.getInputStream()) {
            Assert.assertTrue(IOUtils.contentEquals(is, is2));
        }

        TileObject midTO = TileObject.createQueryTileObject(
                layerName, tos[(tos.length - 1) / 2].xyz, srs.toString(), mime.getFormat(), parameters);
        fbs.get(midTO);
        Resource res = midTO.getBlob();

        Assert.assertNull(res);
    }

    @Test
    public void testRenameLayer() throws Exception {
        fbs = setup();
        Resource bytes = new ByteArrayResource("1 2 3 4 5 6 test".getBytes());
        Map<String, String> parameters = new HashMap<>();
        parameters.put("a", "x");
        parameters.put("b", "ø");
        MimeType mime = ImageMime.png;
        SRS srs = SRS.getEPSG4326();
        final String layerName = "test:123123 112";

        int zoomLevel = 7;
        int x = 25;
        int y = 6;

        // long[] origXYZ = {x,y,zoomLevel};

        TileObject[] tos = new TileObject[6];

        for (int i = 0; i < tos.length; i++) {
            long[] xyz = {x + i - 1, y, zoomLevel};
            tos[i] = TileObject.createCompleteTileObject(
                    layerName, xyz, srs.toString(), mime.getFormat(), parameters, bytes);
            fbs.put(tos[i]);
        }

        final String newLayerName = "modifiedLayerName";
        BlobStoreListener listener = EasyMock.createNiceMock(BlobStoreListener.class);
        listener.layerRenamed(EasyMock.eq(layerName), EasyMock.eq(newLayerName));
        EasyMock.replay(listener);

        fbs.addListener(listener);

        boolean renamed = fbs.rename(layerName, newLayerName);
        Assert.assertTrue(renamed);

        EasyMock.verify(listener);

        try {
            fbs.rename(layerName, newLayerName);
            Assert.fail("Expected StorageException, target dir already exists");
        } catch (StorageException e) {
            Assert.assertTrue(true);
        }
    }

    public BlobStore setup() throws Exception {
        File fh = new File(StorageBrokerTest.findTempDir() + File.separator + TEST_BLOB_DIR_NAME);

        if (!fh.exists()) {
            Files.createDirectory(fh.toPath());
            if (!fh.exists()) {
                throw new StorageException("Unable to create "
                        + fh.getAbsolutePath()
                        + "\nUnable to create "
                        + org.geowebcache.util.FileUtils.printFileTree(fh));
            }
        }

        return new FileBlobStore(StorageBrokerTest.findTempDir() + File.separator + TEST_BLOB_DIR_NAME);
    }

    @Test
    public void testLayerMetadata() throws Exception {
        fbs = setup();

        final String layerName = "TestLayer";
        final String key1 = "Test.Metadata.Property_1";
        final String key2 = "Test.Metadata.Property_2";

        Assert.assertNull(fbs.getLayerMetadata(layerName, key1));
        Assert.assertNull(fbs.getLayerMetadata(layerName, key2));

        fbs.putLayerMetadata(layerName, key1, "value 1");
        fbs.putLayerMetadata(layerName, key2, "value 2");
        Assert.assertEquals("value 1", fbs.getLayerMetadata(layerName, key1));
        Assert.assertEquals("value 2", fbs.getLayerMetadata(layerName, key2));

        fbs.putLayerMetadata(layerName, key1, "value 1_1");
        fbs.putLayerMetadata(layerName, key2, null);
        Assert.assertEquals("value 1_1", fbs.getLayerMetadata(layerName, key1));
        Assert.assertNull(fbs.getLayerMetadata(layerName, key2));
    }
}
