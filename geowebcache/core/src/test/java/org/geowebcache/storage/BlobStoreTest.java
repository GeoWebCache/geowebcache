/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Arne Kepp / The Open Planning Project 2009
 *  
 */
package org.geowebcache.storage;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.easymock.classextension.EasyMock;
import org.geowebcache.grid.SRS;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.blobstore.file.FileBlobStore;

public class BlobStoreTest extends TestCase {
    public static final String TEST_BLOB_DIR_NAME = "gwcTestBlobs";

    public void testTile() throws Exception {
        BlobStore fbs = setup();

        Resource bytes = new ByteArrayResource("1 2 3 4 5 6 test".getBytes());
        long[] xyz = { 1L, 2L, 3L };
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("a", "x");
        parameters.put("b", "ø");
        TileObject to = TileObject.createCompleteTileObject("test:123123 112", xyz, "EPSG:4326",
                "image/jpeg", parameters, bytes);

        fbs.put(to);

        TileObject to2 = TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326",
                "image/jpeg", parameters);
        fbs.get(to2);

        assertEquals(to.getBlobFormat(), to2.getBlobFormat());
        InputStream is = to.getBlob().getInputStream();
        InputStream is2 = to2.getBlob().getInputStream();
        try {
            assertTrue(IOUtils.contentEquals(is, is2));
        } finally {
            is.close();
            is2.close();
        }
    }

    public void testTileDelete() throws Exception {
        BlobStore fbs = setup();

        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("a", "x");
        parameters.put("b", "ø");

        Resource bytes = new ByteArrayResource("1 2 3 4 5 6 test".getBytes());
        long[] xyz = { 5L, 6L, 7L };
        TileObject to = TileObject.createCompleteTileObject("test:123123 112", xyz, "EPSG:4326",
                "image/jpeg", parameters, bytes);

        fbs.put(to);

        TileObject to2 = TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326",
                "image/jpeg", parameters);
        fbs.get(to2);

        InputStream is = to2.getBlob().getInputStream();
        InputStream is2 = bytes.getInputStream();
        try {
            assertTrue(IOUtils.contentEquals(is, is2));
        } finally {
            is.close();
            is2.close();
        }

        TileObject to3 = TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326",
                "image/jpeg", parameters);
        fbs.delete(to3);

        TileObject to4 = TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326",
                "image/jpeg", parameters);
        assertFalse(fbs.get(to4));
    }

    public void testTilRangeDelete() throws Exception {
        BlobStore fbs = setup();

        Resource bytes = new ByteArrayResource("1 2 3 4 5 6 test".getBytes());
        Map<String, String> parameters = new HashMap<String, String>();
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
            long[] xyz = { x + i - 1, y, zoomLevel };
            tos[i] = TileObject.createCompleteTileObject(layerName, xyz, srs.toString(),
                    mime.getFormat(), parameters, bytes);
            fbs.put(tos[i]);
        }

        long[][] rangeBounds = new long[zoomLevel + 2][5];
        int zoomStart = zoomLevel - 1;
        int zoomStop = zoomLevel + 1;

        long[] range = { x, y, x + tos.length - 3, y, zoomLevel};
        rangeBounds[zoomLevel] = range;

        TileRange trObj = new TileRange(layerName, srs.toString(), zoomStart, zoomStop,
                rangeBounds, mime, parameters);

        fbs.delete(trObj);

        // starting x and x + tos.length should have data, the remaining should not
        TileObject firstTO = TileObject.createQueryTileObject(layerName, tos[0].xyz,
                srs.toString(), mime.getFormat(), parameters);
        fbs.get(firstTO);
        InputStream is = firstTO.getBlob().getInputStream();
        InputStream is2 = bytes.getInputStream();
        try {
            assertTrue(IOUtils.contentEquals(is, is2));
        } finally {
            is.close();
            is2.close();
        }

        TileObject lastTO = TileObject.createQueryTileObject(layerName, tos[tos.length - 1].xyz,
                srs.toString(), mime.getFormat(), parameters);
        fbs.get(lastTO);
        is = lastTO.getBlob().getInputStream();
        is2 = bytes.getInputStream();
        try {
            assertTrue(IOUtils.contentEquals(is, is2));
        } finally {
            is.close();
            is2.close();
        }

        TileObject midTO = TileObject.createQueryTileObject(layerName,
                tos[(tos.length - 1) / 2].xyz, srs.toString(), mime.getFormat(), parameters);
        fbs.get(midTO);
        Resource res = midTO.getBlob();

        assertNull(res);
    }

    public void testRenameLayer() throws Exception {
        BlobStore fbs = setup();
        Resource bytes = new ByteArrayResource("1 2 3 4 5 6 test".getBytes());
        Map<String, String> parameters = new HashMap<String, String>();
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
            long[] xyz = { x + i - 1, y, zoomLevel };
            tos[i] = TileObject.createCompleteTileObject(layerName, xyz, srs.toString(),
                    mime.getFormat(), parameters, bytes);
            fbs.put(tos[i]);
        }

        final String newLayerName = "modifiedLayerName";
        BlobStoreListener listener = EasyMock.createNiceMock(BlobStoreListener.class);
        listener.layerRenamed(EasyMock.eq(layerName), EasyMock.eq(newLayerName));
        EasyMock.replay(listener);

        fbs.addListener(listener);

        boolean renamed = fbs.rename(layerName, newLayerName);
        assertTrue(renamed);

        EasyMock.verify(listener);

        try {
            fbs.rename(layerName, newLayerName);
            fail("Expected StorageException, target dir already exists");
        } catch (StorageException e) {
            assertTrue(true);
        }
    }

    public BlobStore setup() throws Exception {
        File fh = new File(StorageBrokerTest.findTempDir() + File.separator + TEST_BLOB_DIR_NAME);

        if (fh.exists()) {
            FileUtils.deleteDirectory(fh);
        }
        if (!fh.exists() && !fh.mkdirs()) {
            throw new StorageException("Unable to create " + fh.getAbsolutePath());
        }

        return new FileBlobStore(StorageBrokerTest.findTempDir() + File.separator
                + TEST_BLOB_DIR_NAME);
    }

    public void testLayerMetadata() throws Exception {
        BlobStore fbs = setup();

        final String layerName = "TestLayer";
        final String key1 = "Test.Metadata.Property_1";
        final String key2 = "Test.Metadata.Property_2";

        assertNull(fbs.getLayerMetadata(layerName, key1));
        assertNull(fbs.getLayerMetadata(layerName, key2));

        fbs.putLayerMetadata(layerName, key1, "value 1");
        fbs.putLayerMetadata(layerName, key2, "value 2");
        assertEquals("value 1", fbs.getLayerMetadata(layerName, key1));
        assertEquals("value 2", fbs.getLayerMetadata(layerName, key2));

        fbs.putLayerMetadata(layerName, key1, "value 1_1");
        fbs.putLayerMetadata(layerName, key2, null);
        assertEquals("value 1_1", fbs.getLayerMetadata(layerName, key1));
        assertNull(fbs.getLayerMetadata(layerName, key2));
    }
}
