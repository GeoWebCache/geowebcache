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
import org.geowebcache.layer.SRS;
import org.geowebcache.mime.MimeType;
import org.geowebcache.tile.Tile;

public class FilePathKey2 implements CacheKey {
    public void init() {
    }
    
    public Object createKey(Tile tile) {
        return (Object) this.createKey(tile.getLayer().getCachePrefix(), tile.getTileIndex(), tile.getSRS(), tile.getMimeType());
    }

    protected String createKey(String prefix, int[] tileIndex, SRS srs, MimeType mimeType) {
        int x = tileIndex[0];
        int y = tileIndex[1];
        int z = tileIndex[2];
        
        String srsStr = srs.filePath();

        int shift = z / 2;
        int half = 2 << shift;
        int digits = 1;
        if (half > 10) {
            digits = (int) (Math.log10(half)) + 1;
        }
        int halfx = x / half;
        int halfy = y / half;
        // System.out.println("preFileName, shift:" + shift + ", half:" + half +
        // ", digits:"+digits);
        String filename = 
                prefix + File.separator 
                + srsStr + "_" + zeroPadder(z, 2) + File.separator 
                + zeroPadder(halfx, digits) + "_" 
                + zeroPadder(halfy, digits) + File.separator
                + zeroPadder(x, 2 * digits) + "_" 
                + zeroPadder(y, 2 * digits) + "." + mimeType.getFileExtension();
        // System.out.println(""+x+","+y+","+z+ " " + filename);
        return filename;
    }

    //public int getType() {
    //    return KEY_FILE_PATH;
    //}

    /**
     * Silly way to pad numbers with leading zeros, since I don't know a fast
     * way of doing this in Java.
     * 
     * @param number
     * @param order
     * @return
     */
    public static String zeroPadder(int number, int order) {
        int numberOrder = 1;

        if (number > 9) {
            numberOrder = (int) Math.ceil(Math.log10(number));
        }

        int diffOrder = order - numberOrder;

        String padding = "";
        while (diffOrder > 0) {
            padding += "0";
            diffOrder--;
        }

        return padding + Integer.toString(number);
    }
}
