package org.geowebcache.arcgis.compact;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.geowebcache.io.Resource;

/**
 * Represents complete ArcGIS compact cache data.
 * 
 * On initialization, an index data structure is build according to the directory structure of the
 * cache directory.
 * 
 * @see org.geowebcache.arcgis.compact.ArcGISCacheBundle
 * 
 * @author Bjoern Saxe
 * 
 */
public class ArcGISCompactCache {
    private Map<Integer, ArcGISCacheBundle[][]> bundles;

    private Map<Integer, Integer> baseColOffset;

    private Map<Integer, Integer> baseRowOffset;

    /**
     * Constructs new ArcGIS compact cache.
     * 
     * @param cacheRootPath
     *            Path to compact cache directory (usually ".../_alllayers/"). Path must contain
     *            directories for zoom levels (named "Lxx").
     */
    public ArcGISCompactCache(String cacheRootPath) {
        bundles = new HashMap<Integer, ArcGISCacheBundle[][]>();
        baseColOffset = new HashMap<Integer, Integer>();
        baseRowOffset = new HashMap<Integer, Integer>();

        readCacheIndex(cacheRootPath);
    }

    /**
     * Get tile image data.
     * 
     * @param zoom
     *            Zoom level.
     * @param row
     *            Row of tile.
     * @param col
     *            Column of tile.
     * @return Tile image data if tile exists; null otherwise.
     */
    public final byte[] getTile(int zoom, int row, int col) {
        if (!checkRowAndColIndex(zoom, row, col))
            return null;

        int bundleRowIdx = (row - baseRowOffset.get(zoom)) / 128;
        int bundleColIdx = (col - baseColOffset.get(zoom)) / 128;

        row -= baseRowOffset.get(zoom);
        col -= baseColOffset.get(zoom);

        return (bundles.get(zoom))[bundleColIdx][bundleRowIdx].getTile(row % 128, col % 128);
    }

    /**
     * Get Resource object for tile.
     * 
     * @param zoom
     *            Zoom level.
     * @param row
     *            Row of tile.
     * @param col
     *            Column of tile.
     * @return Resource object associated with tile image data if tile exists; null otherwise.
     */
    public Resource getBundleFileResource(int zoom, int row, int col) {
        if (!checkRowAndColIndex(zoom, row, col))
            return null;

        int bundleRowIdx = (row - baseRowOffset.get(zoom)) / 128;
        int bundleColIdx = (col - baseColOffset.get(zoom)) / 128;

        row -= baseRowOffset.get(zoom);
        col -= baseColOffset.get(zoom);

        return new BundleFileResource((bundles.get(zoom))[bundleColIdx][bundleRowIdx], row % 128,
                col % 128);
    }

    /**
     * Check if tile exists.
     * 
     * @param zoom
     *            Zoom level of tile.
     * @param row
     *            Row of tile.
     * @param col
     *            Column of tile.
     * @return True if tile exists (actual image data is available); false otherwise.
     */
    public boolean tileExists(int zoom, int row, int col) {
        if (!checkRowAndColIndex(zoom, row, col))
            return false;

        ArcGISCacheBundle bundle[][] = bundles.get(zoom);

        int bundleRowIdx = (row - baseRowOffset.get(zoom)) / 128;
        int bundleColIdx = (col - baseColOffset.get(zoom)) / 128;

        row -= baseRowOffset.get(zoom);
        col -= baseColOffset.get(zoom);

        return bundle[bundleColIdx][bundleRowIdx].tileExists(row % 128, col % 128);
    }

    private boolean checkRowAndColIndex(int zoom, int row, int col) {
        if (bundles.get(zoom) == null)
            return false;

        ArcGISCacheBundle bundle[][] = bundles.get(zoom);

        int bundleRowIdx = (row - baseRowOffset.get(zoom)) / 128;
        int bundleColIdx = (col - baseColOffset.get(zoom)) / 128;

        if (bundleColIdx >= bundle.length || bundleColIdx < 0
                || bundleRowIdx >= bundle[bundleColIdx].length || bundleRowIdx < 0)
            return false;

        return true;
    }

    /**
     * Read cache directory structure. All needed information (available zoom levels, rows and
     * columns) is extracted from path and file names.
     * 
     * Steps: 1. Check for available zoom levels (how many Lxx directories?) 2. For every zoom level
     * determine highest values for row and column and add a two-dimensional array of cache bundle
     * objects (each representing a .bundlx file) 3. Initialize cache bundle objects
     */
    private void readCacheIndex(String cacheRootPath) {
        // Get a list of all bundlx index files in all subdirectories
        List<String> indexFilesFullPath = listIndexFiles(cacheRootPath);

        Collections.sort(indexFilesFullPath);

        Map<Integer, Integer> maxRow = new HashMap<Integer, Integer>();
        Map<Integer, Integer> maxCol = new HashMap<Integer, Integer>();

        for (String indexFile : indexFilesFullPath) {
            // Check for available zoom levels
            // Lzz\RrrrrCcccc.bundlx => get zz
            int zoomLevel;
            try {
                int idx = indexFile.lastIndexOf('L') + 1;
                zoomLevel = Integer.parseInt(indexFile.substring(idx, idx + 2)); // zoom level is
                                                                                 // decimal, not
                                                                                 // hexadecimal like
                                                                                 // rows and
                                                                                 // columns!
            } catch (NumberFormatException e) {
                System.err
                        .println("Error parsing zoom level xx from cache directory path (.../Lxx/...): "
                                + indexFile);
                continue;
            }
            ;

            // determine highest values for row and column from file names
            try {
                // Lzz\RrrrrCcccc.bundlx => get rrrr
                int startIdx = indexFile.lastIndexOf('R') + 1;
                int endIdx = indexFile.lastIndexOf('C');
                int row = Integer.parseInt(indexFile.substring(startIdx, endIdx), 16);
                // Lzz\RrrrrCcccc.bundlx => get cccc
                startIdx = indexFile.lastIndexOf('C') + 1;
                endIdx = indexFile.lastIndexOf('.');
                int col = Integer.parseInt(indexFile.substring(startIdx, endIdx), 16);

                if (maxRow.get(zoomLevel) == null || maxRow.get(zoomLevel) < row)
                    maxRow.put(zoomLevel, row);
                if (maxCol.get(zoomLevel) == null || maxCol.get(zoomLevel) < col)
                    maxCol.put(zoomLevel, col);

                if (baseRowOffset.get(zoomLevel) == null || baseRowOffset.get(zoomLevel) > row)
                    baseRowOffset.put(zoomLevel, row);
                if (baseColOffset.get(zoomLevel) == null || baseColOffset.get(zoomLevel) > col)
                    baseColOffset.put(zoomLevel, col);
            } catch (NumberFormatException e) {
                System.err
                        .println("Error parsing row / column from bundle file name (RrrrrCcccc.bundlx): "
                                + indexFile);
                continue;
            }
        }

        // for every available zoom level, add cache bundle object array of particular size
        for (int zoom : maxRow.keySet()) {
            int nRows = (maxRow.get(zoom) - baseRowOffset.get(zoom)) / 128 + 1;
            int nCols = (maxCol.get(zoom) - baseColOffset.get(zoom)) / 128 + 1;

            bundles.put(zoom, new ArcGISCacheBundle[nCols][nRows]);
        }

        // for every zoom level, initialize cache bundle object arrays with ArcGISCacheBundle
        // objects
        for (int zoom : bundles.keySet()) {
            ArcGISCacheBundle[][] bundle = bundles.get(zoom);

            // column and row part of bundle file names are at least four bytes long, but can be
            // longer
            int colHexIdxPadding = Math.max(4, Integer.toHexString(maxCol.get(zoom)).length());
            int rowHexIdxPadding = Math.max(4, Integer.toHexString(maxRow.get(zoom)).length());

            for (int col = 0; col < bundle.length; col++) {
                for (int row = 0; row < bundle[col].length; row++) {
                    StringBuilder colHexIdx = new StringBuilder(Integer.toHexString((col * 128)
                            + baseColOffset.get(zoom)));
                    StringBuilder rowHexIdx = new StringBuilder(Integer.toHexString((row * 128)
                            + baseRowOffset.get(zoom)));

                    while (colHexIdx.length() < colHexIdxPadding)
                        colHexIdx.insert(0, 0);
                    while (rowHexIdx.length() < rowHexIdxPadding)
                        rowHexIdx.insert(0, 0);

                    String zoomStr = Integer.toString(zoom);
                    if (zoomStr.length() < 2)
                        zoomStr = "0" + zoomStr;

                    String fileName = "/L" + zoomStr + "/R" + rowHexIdx.toString() + "C"
                            + colHexIdx.toString() + ".bundlx";
                    File idxFile = new File(cacheRootPath + fileName);
                    // on some caches, row and column numbers do not start at zero
                    if (idxFile.exists())
                        bundle[col][row] = new ArcGISCacheBundle(idxFile.getAbsolutePath());
                }
            }
        }
    }

    private List<String> listIndexFiles(String path) {
        File f = new File(path);

        String[] dirContents = f.list();

        if (dirContents == null)
            return null;

        List<String> indexFilesList = new ArrayList<String>();

        for (String file : dirContents) {
            f = new File(path + System.getProperty("file.separator") + file);

            if (f.isDirectory())
                indexFilesList.addAll(listIndexFiles(f.getAbsolutePath()));

            if (f.getName().endsWith(".bundlx"))
                indexFilesList.add(f.getAbsolutePath());
        }

        return indexFilesList;
    }
}
