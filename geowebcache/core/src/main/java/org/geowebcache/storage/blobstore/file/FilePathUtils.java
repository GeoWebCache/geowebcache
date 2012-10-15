package org.geowebcache.storage.blobstore.file;

import static org.geowebcache.storage.blobstore.file.FilePathUtils.appendFiltered;
import static org.geowebcache.storage.blobstore.file.FilePathUtils.zeroPadder;

public class FilePathUtils {

    public static String gridsetZoomLevelDir(String gridSetId, long zoomLevel) {
        String gridSetStr = filteredGridSetId(gridSetId);
        StringBuilder sb = new StringBuilder(gridSetStr);
        sb.append('_');
        zeroPadder(zoomLevel, 2, sb);
        return sb.toString();
    }

    public static String zeroPadder(long number, int order) {
        StringBuilder sb = new StringBuilder();
        zeroPadder(number, order, sb);
        return sb.toString();
    }

    /**
     * Silly way to pad numbers with leading zeros, since I don't know a fast way of doing this in
     * Java.
     * 
     * @param number
     * @param order
     * @return
     */
    public static void zeroPadder(long number, int order, StringBuilder padding) {
        int numberOrder = 1;

        if (number > 9) {
            if (number > 11) {
                numberOrder = (int) Math.ceil(Math.log10(number) - 0.001);
            } else {
                numberOrder = 2;
            }
        }

        int diffOrder = order - numberOrder;

        if (diffOrder > 0) {

            while (diffOrder > 0) {
                padding.append('0');
                diffOrder--;
            }
            padding.append(number);// toString() + Long.toString(number);
        } else {
            padding.append(number);
        }
    }

    public static String filteredGridSetId(String gridSetId) {
        return gridSetId.replace(':', '_');
    }

    public static String filteredLayerName(String layerName) {
        return layerName.replace(':', '_').replace(' ', '_');
    }

    public static void appendFiltered(String str, StringBuilder path) {
        char c;
        for (int i = 0; i < str.length(); i++) {
            c = str.charAt(i);
            if (':' == c || ' ' == c) {
                c = '_';
            }
            path.append(c);
        }
    }

    /**
     * Extracts the zoomLevel from {@code <gridsetPrefix>_<zLevel>[_<parametersId>]})
     * 
     * @precondition {@code dirName.startsWith(gridsetPrefix + "_")}
     */
    public static int findZoomLevel(final String gridsetPrefix, final String dirName) {
        assert dirName.startsWith(gridsetPrefix + "_");
        String[] parts = dirName.substring(gridsetPrefix.length() + 1).split("_");
        return Integer.parseInt(parts[0]);
    }
    
    /**
     * Extracts the parametersId from {@code <gridsetPrefix>_<zLevel>[_<parametersId>]})
     * 
     * @precondition {@code dirName.startsWith(gridsetPrefix + "_")}
     */
    public static String findParameter(final String gridsetPrefix, final String dirName) {
        assert dirName.startsWith(gridsetPrefix + "_");
        String[] parts = dirName.substring(gridsetPrefix.length() + 1).split("_");
        if(parts.length == 2) {
            return parts[1];
        } else {
            return null;
        }
    }
    
    
    /**
     * Adds the gridset and zoom level fors the standard file system layout path
     */
    public static void appendGridsetZoomLevelDir(String gridSetId, long z, StringBuilder path) {
        appendFiltered(gridSetId, path);
        path.append('_');
        zeroPadder(z, 2, path);
    }

}
