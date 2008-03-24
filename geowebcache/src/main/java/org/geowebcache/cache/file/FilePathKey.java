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
 * @author Arne Kepp, The Open Planning Project, Copyright 2007
 *  
 */
package org.geowebcache.cache.file;

import java.io.File;

import org.geowebcache.cache.CacheKey;

public class FilePathKey implements CacheKey {
    String layerPrefix;

    public void init(String prefix) {
        layerPrefix = prefix;
    }

    public String createKey(int x, int y, int z, String format) {
        String filename = layerPrefix + File.separator + zeroPadder(z, 2)
                + File.separator + zeroPadder(x / 1000000, 3) + File.separator
                + zeroPadder((x / 1000) % 1000, 3) + File.separator
                + zeroPadder(x % 1000, 3) + File.separator
                + zeroPadder(y / 1000000, 3) + File.separator
                + zeroPadder((y / 1000) % 1000, 3) + File.separator
                + zeroPadder(y % 1000, 3) + "." + format;
        // String fileName = Integer.toString(z) +"-"+ Integer.toString(x) +"-"+
        // Integer.toString(y) + format + "";
        return filename;
    }

    public int getType() {
        return KEY_FILE_PATH;
    }

    /**
     * Silly way to pad numbers with leading zeros, since I don't know a fast
     * way of doing this in Java.
     * 
     * @param number
     * @param order
     * @return
     */
    private static String zeroPadder(int number, int order) {
        if (order == 3) {
            if (number < 100) {
                if (number < 10) {
                    return "00" + Integer.toString(number);
                } else {
                    return "0" + Integer.toString(number);
                }
            } else {
                return Integer.toString(number);
            }
        } else { // assume (order == 2)
            if (number < 10) {
                return "0" + Integer.toString(number);
            } else {
                return Integer.toString(number);
            }
        }
    }
}
