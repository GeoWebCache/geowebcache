package org.geowebcache.arcgis.compact;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

/**
 * Represents ArcGIS compact cache bundle (index file (*.bundlx) and bundle file with actual data
 * (*.bundle))
 *
 * Every .bundlx file contains a 16 byte header and 16 byte footer. Between header and footer is
 * 128x128 matrix (16384 tiles) of 5 byte offsets. Every offset points to a 4 byte word in the
 * corresponding .bundle file which contains the size of the tile image data. The actual image data
 * starts at offset+4. If the size is zero there is no image data available and the index entry is
 * not used. If the map cache has more than 128 rows or columns it is divided into several .bundlx +
 * .bundle files.
 *
 * @author Bjoern Saxe
 *
 */
public class ArcGISCacheBundle {
    private long[][] bundleOffsetMatrix;

    private String bundleFileName;

    private FileChannel bundleFileChannel;

    public static final int indexMaxRow = 128;

    public static final int indexMaxCol = 128;

    private byte idxFile[];

    /**
     * Constructs new ArcGIS compact cache bundle. The .bundle file must be in the same directory as
     * the .bundlx file.
     */
    public ArcGISCacheBundle(String pathToIndexFile) {
        bundleOffsetMatrix = new long[indexMaxCol][indexMaxRow];

        bundleFileName = pathToIndexFile.replaceAll("bundlx", "bundle");

        try {
            FileInputStream fis = new FileInputStream(new File(bundleFileName));
            bundleFileChannel = fis.getChannel();
        } catch (FileNotFoundException e) {
            System.err.println(e.toString());
        }

        // read .bundlx file into memory
        readIndexFile(pathToIndexFile);

        processIndex();
    }

    /**
     * Get tile specified by row and column
     *
     * @param row
     *            row of the tile
     * @param col
     *            column of the tile
     * @return The tile image data
     */
    public final byte[] getTile(int row, int col) {
        if (!rowAndColAreValid(row, col))
            return null;

        long offset = bundleOffsetMatrix[col][row];

        int tileSize = readTileSize(offset);

        if (tileSize == 0)
            return null;

        return readBundleFile(offset + 4, tileSize);
    }

    /**
     * Get tile specified by row and column
     *
     * @param row
     *            row of the tile
     * @param col
     *            column of the tile
     * @return Offset for tile. -1 if row and or column are invalid
     */
    public long getTileOffset(int row, int col) {
        if (!rowAndColAreValid(row, col))
            return -1;

        // offset points to tile size. Actual data is at offset+4
        return bundleOffsetMatrix[col][row] + 4;
    }

    /**
     * Get name of associated bundle file.
     *
     * @return Associated name of bundle file.
     */
    public String getBundleFileName() {
        return bundleFileName;
    }

    /**
     * Check if image data exists for tile specified by row and column
     *
     * @param row
     *            row of the tile
     * @param col
     *            column of the tile
     * @return true if image data exists; false if no data exists or row and or column are invalid
     */
    public boolean tileExists(int row, int col) {
        if (!rowAndColAreValid(row, col))
            return false;

        long offset = bundleOffsetMatrix[col][row];
        int tileSize = readTileSize(offset);

        return (tileSize > 0);
    }

    /**
     * Get size of tile specified by row and column
     *
     * @param row
     *            row of the tile
     * @param col
     *            column of the tile
     * @return size of image data; -1 if row and or column are invalid
     */
    public int getTileSize(int row, int col) {
        if (!rowAndColAreValid(row, col))
            return -1;

        long offset = bundleOffsetMatrix[col][row];

        return readTileSize(offset);
    }

    // debug
    public boolean extractTile(int row, int col, String path) {
        try {
            // TODO: FileInputStream bundle --> FileOutputStream tile
            long offset = bundleOffsetMatrix[col][row];
            int tileSize = readTileSize(offset);

            String fileName = "R" + row + "C" + col + ".png";

            FileOutputStream outputStream = new FileOutputStream(new File(path + "/" + fileName));

            outputStream.write(readBundleFile(offset, tileSize), 0, tileSize);
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

    public void printTileMatrix() {
        for (int row = 0; row < indexMaxRow; row++) {
            for (int col = 0; col < indexMaxCol; col++) {
                if (tileExists(row, col))
                    System.out.print("X");
                else
                    System.out.print("-");
            }
            System.out.println();
        }
        System.out.println();
    }

    // debug
	
	private boolean rowAndColAreValid(int row, int col) {
-        return (row < indexMaxRow && col < indexMaxCol && row >= 0 && col >= 0);
-    }

    private void processIndex() {
        int row = 0, col = 0;

        for (int offset = 16; offset < (idxFile.length - 16); offset += 5) {
            ByteBuffer idxBytes = ByteBuffer.allocate(8);
            idxBytes.order(ByteOrder.LITTLE_ENDIAN);
            idxBytes.put(idxFile, offset, 5);
            idxBytes.rewind();

            long tileOffset = idxBytes.getLong();

            int temp = (offset - 16) / 5;
            if ((temp % 128) == 0 && temp != 0)
                row++;

            bundleOffsetMatrix[row][col] = tileOffset;

            col++;
            if (col == 128)
                col = 0;
        }
    }

    private final byte[] readBundleFile(long offset, int length) {
        ByteBuffer content = ByteBuffer.allocate(length);
        try {
            bundleFileChannel.read(content, offset);

            return content.array();
        } catch (IOException e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    private int readTileSize(long offset) {
        byte chunkSizeBuf[] = readBundleFile(offset, 4);
        ByteBuffer chunkSize = ByteBuffer.allocate(4);
        chunkSize.put(chunkSizeBuf);
        chunkSize.rewind();
        chunkSize.order(ByteOrder.LITTLE_ENDIAN);
        return chunkSize.getInt();
    }

    private void readIndexFile(String fileName) {
        File file = new File(fileName);

        idxFile = new byte[(int) file.length()];

        try {
            InputStream input = null;
            try {
                input = new BufferedInputStream(new FileInputStream(file));
                input.read(idxFile, 0, idxFile.length);
            } finally {
                if (input != null)
                    input.close();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}