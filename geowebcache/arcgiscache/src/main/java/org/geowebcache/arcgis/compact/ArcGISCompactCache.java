package org.geowebcache.arcgis.compact;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.geowebcache.io.Resource;

/**
 * Represents complete ArcGIS compact cache data.
 *
 * @author Bjoern Saxe
 */

public class ArcGISCompactCache {
    private static final String BUNDLX_EXT = ".bundlx";

    private static final String BUNDLE_EXT = ".bundle";

    private static final int BUNDLX_MAXIDX = 128;

    private int padding = 0;

    private final String pathToCacheRoot;

    /**
     * Constructs new ArcGIS compact cache.
     *
     * @param pathToCacheRoot Path to compact cache directory (usually ".../_alllayers/"). Path must contain
     *                        directories for zoom levels (named "Lxx").
     */
    public ArcGISCompactCache(String pathToCacheRoot) {
        if (pathToCacheRoot.endsWith("" + File.separatorChar))
            this.pathToCacheRoot = pathToCacheRoot;
        else
            this.pathToCacheRoot = pathToCacheRoot + File.separatorChar;

        // the column and row part of bundle file names are at least four characters long
        // but can be longer for really big caches so we check the size of the bundl* files
        // to see how much 0's we need to add when building paths
        File f = new File(pathToCacheRoot);

        File[] files = f.listFiles();
        for (File file : files) {
            if (file.getName().startsWith("L")) {
                File[] bundles = file.listFiles(new FilenameFilter() {
                    public boolean accept(File dir, String name) {
                        return name.endsWith("bundle");
                    }
                });

                if (bundles.length > 0) {
                    // RrrrrCcccc.bundle
                    // length of "R"+"C"+".bundle" = 9
                    // divided by 2 because row and column have same length
                    padding = (bundles[0].getName().length() - 9) / 2;
                    break;
                }
            }
        }
    }

    /**
     * Check if tile exists.
     *
     * @param zoom Zoom level of tile.
     * @param row  Row of tile.
     * @param col  Column of tile.
     * @return True if tile exists (actual image data is available); false otherwise.
     */
    public boolean tileExists(int zoom, int row, int col) {
        if (zoom < 0 || col < 0 || row < 0)
            return false;

        String pathToBundleFile = buildBundleFilePath(zoom, row, col, BUNDLE_EXT);
        String pathToBundlxFile = buildBundleFilePath(zoom, row, col, BUNDLX_EXT);

        if (!Files.exists(Paths.get(pathToBundlxFile)) || !Files
            .exists(Paths.get(pathToBundleFile)))
            return false;

        long tileOffset = readTileStartOffset(pathToBundlxFile, row, col);
        int tileSize = readTileSize(pathToBundleFile, tileOffset);

        return tileSize > 0;
    }

    /**
     * Get Resource object for tile.
     *
     * @param zoom Zoom level.
     * @param row  Row of tile.
     * @param col  Column of tile.
     * @return Resource object associated with tile image data if tile exists; null otherwise.
     */
    public Resource getBundleFileResource(int zoom, int row, int col) {
        String pathToBundleFile = buildBundleFilePath(zoom, row, col, BUNDLE_EXT);
        String pathToBundlxFile = buildBundleFilePath(zoom, row, col, BUNDLX_EXT);

        long tileOffset = readTileStartOffset(pathToBundlxFile, row, col);
        int tileSize = readTileSize(pathToBundleFile, tileOffset);

        tileOffset += 4;

        Resource res =
            tileSize > 0 ? new BundleFileResource(pathToBundleFile, tileOffset, tileSize) : null;

        return res;
    }

    private String buildBundleFilePath(int zoom, int row, int col, String fileExtension) {
        StringBuilder bundlePath = new StringBuilder(pathToCacheRoot);

        int baseRow = (row / BUNDLX_MAXIDX) * BUNDLX_MAXIDX;
        int baseCol = (col / BUNDLX_MAXIDX) * BUNDLX_MAXIDX;

        String zoomStr = Integer.toString(zoom);
        if (zoomStr.length() < 2)
            zoomStr = "0" + zoomStr;

        StringBuilder rowStr = new StringBuilder(Integer.toHexString(baseRow));
        StringBuilder colStr = new StringBuilder(Integer.toHexString(baseCol));

        while (colStr.length() < padding)
            colStr.insert(0, "0");

        while (rowStr.length() < padding)
            rowStr.insert(0, "0");

        bundlePath.append("L").append(zoomStr).append(File.separatorChar).append("R").append(rowStr)
            .append("C").append(colStr).append(fileExtension);

        return bundlePath.toString();
    }

    private long readTileStartOffset(String bundlxFile, int row, int col) {
        int index = BUNDLX_MAXIDX * col + row;

        byte[] data = readFromFile(bundlxFile, (index * 5) + 16, 5);

        ByteBuffer idxBytes = ByteBuffer.allocate(8);
        idxBytes.order(ByteOrder.LITTLE_ENDIAN);
        idxBytes.put(data);
        idxBytes.rewind();

        return idxBytes.getLong();
    }

    private int readTileSize(String bundlxFile, long offset) {
        byte[] data = readFromFile(bundlxFile, offset, 4);
        ByteBuffer tileSize = ByteBuffer.allocate(4);
        tileSize.put(data);
        tileSize.rewind();
        tileSize.order(ByteOrder.LITTLE_ENDIAN);

        return tileSize.getInt();
    }

    private byte[] readFromFile(String filePath, long offset, int length) {
        byte[] result = new byte[0];

        try (FileInputStream fileInputStream = new FileInputStream(
            new File(filePath)); FileChannel fileChannel = fileInputStream.getChannel()) {
            ByteBuffer content = ByteBuffer.allocate(length);
            fileChannel.read(content, offset);

            result = content.array();
        } catch (IOException e) {
            System.err.println(e);
        }

        return result;
    }
}
