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
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 *  
 */
package org.geowebcache.storage.blobstore.file;

import java.io.File;

import org.geowebcache.mime.MimeType;

public class FilePathGenerator {

    /**
     * Builds the storage path for a tile and returns it as two components, the directory path and
     * the tile file name.
     * <p>
     * </p>
     * 
     * @param prefix
     *            the cache root directory path
     * @param layerName
     *            name of the layer the tile belongs to
     * @param tileIndex
     *            the [x,y,z] index for the tile
     * @param gridSetId
     *            the name of the gridset for the tile inside layer
     * @param mimeType
     *            the storage mime type
     * @param parameters_id
     *            the parameters identifier
     * @return File pointer to the tile image
     */
    public static File tilePath(String prefix, String layerName, long[] tileIndex,
            String gridSetId, MimeType mimeType, long parameters_id) {
        long x = tileIndex[0];
        long y = tileIndex[1];
        long z = tileIndex[2];

        StringBuilder path = new StringBuilder(256);

        long shift = z / 2;
        long half = 2 << shift;
        int digits = 1;
        if (half > 10) {
            digits = (int) (Math.log10(half)) + 1;
        }
        long halfx = x / half;
        long halfy = y / half;

        String fileExtension = mimeType.getFileExtension();

        path.append(prefix);
        path.append(File.separatorChar);
        appendFiltered(layerName, path);
        path.append(File.separatorChar);
        appendGridsetZoomLevelDir(gridSetId, z, path);
        if (parameters_id != -1L) {
            path.append('_');
            path.append(Long.toHexString(parameters_id));
        }
        path.append(File.separatorChar);
        zeroPadder(halfx, digits, path);
        path.append('_');
        zeroPadder(halfy, digits, path);
        path.append(File.separatorChar);

        zeroPadder(x, 2 * digits, path);
        path.append('_');
        zeroPadder(y, 2 * digits, path);
        path.append('.');
        path.append(fileExtension);

        File tileFile = new File(path.toString());
        return tileFile;
    }

    public static String gridsetZoomLevelDir(String gridSetId, long zoomLevel) {
        String gridSetStr = filteredGridSetId(gridSetId);
        StringBuilder sb = new StringBuilder(gridSetStr);
        sb.append('_');
        zeroPadder(zoomLevel, 2, sb);
        return sb.toString();
    }

    private static void appendGridsetZoomLevelDir(String gridSetId, long z, StringBuilder path) {
        appendFiltered(gridSetId, path);
        path.append('_');
        zeroPadder(z, 2, path);
    }
    
    
    public static String zeroPadder(long number, int order) {
        StringBuilder sb = new StringBuilder();
        zeroPadder(number, order, sb);
        return sb.toString();
    }
    
    /**
     * Silly way to pad numbers with leading zeros, since I don't know a fast
     * way of doing this in Java.
     * 
     * @param number
     * @param order
     * @return
     */
    private static void zeroPadder(long number, int order, StringBuilder padding) {
        int numberOrder = 1;

        if (number > 9) {
            if(number > 11) {
                numberOrder = (int) Math.ceil(Math.log10(number) - 0.001);
            } else {
                numberOrder = 2;
            }
        }

        int diffOrder = order - numberOrder;
        
        if(diffOrder > 0) {
            
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

    private static void appendFiltered(String str, StringBuilder path) {
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
     * Extracts the zoomLevel from something like EPSG_2163_01
     * 
     * @param dirName
     * @return
     */
    public static int findZoomLevel(String dirName) {
        return Integer.parseInt(dirName.substring(dirName.lastIndexOf('_') + 1));
    }
}
