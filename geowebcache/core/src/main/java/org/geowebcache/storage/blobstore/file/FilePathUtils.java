/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * <p>Copyright 2008
 */
package org.geowebcache.storage.blobstore.file;

import java.io.File;

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
            padding.append(number); // toString() + Long.toString(number);
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
        String zlevel = parts[0];
        try {
            return Integer.parseInt(zlevel);
        } catch (NumberFormatException e) {
            throw new RuntimeException(
                    String.format("unable to find zoom level in '%s'", gridsetPrefix), e);
        }
    }

    /**
     * Extracts the parametersId from {@code <gridsetPrefix>_<zLevel>[_<parametersId>]})
     *
     * @precondition {@code dirName.startsWith(gridsetPrefix + "_")}
     */
    public static String findParameter(final String gridsetPrefix, final String dirName) {
        assert dirName.startsWith(gridsetPrefix + "_");
        String[] parts = dirName.substring(gridsetPrefix.length() + 1).split("_");
        if (parts.length == 2) {
            return parts[1];
        } else {
            return null;
        }
    }

    /** Adds the gridset and zoom level fors the standard file system layout path */
    public static void appendGridsetZoomLevelDir(String gridSetId, long z, StringBuilder path) {
        appendFiltered(gridSetId, path);
        path.append('_');
        zeroPadder(z, 2, path);
    }

    /**
     * Returns a path built from a root and a list of components. The components are appended to the
     * root with a {@link File#separatorChar} in between. The root is trusted not to need escaping,
     * all other bits are filtered.
     */
    public static String buildPath(String root, String... components) {
        StringBuilder path = new StringBuilder(256);
        path.append(root);

        for (String component : components) {
            path.append(File.separatorChar);
            appendFiltered(component, path);
        }

        return path.toString();
    }
}
