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
    protected static String[] tilePath(String prefix, String layerName, long[] tileIndex, 
            String gridSetId, MimeType mimeType, long parameters_id) {
        long x = tileIndex[0];
        long y = tileIndex[1];
        long z = tileIndex[2];
        
        
        String gridSetStr = filteredGridSetId(gridSetId);
        String layerStr = filteredLayerName(layerName);
        
        String paramStr = "";
        if(parameters_id != -1L) {
            paramStr = "_" + Long.toHexString(parameters_id);
        }
        
        long shift = z / 2;
        long half = 2 << shift;
        int digits = 1;
        if (half > 10) {
            digits = (int) (Math.log10(half)) + 1;
        }
        long halfx = x / half;
        long halfy = y / half;

        String fileExtension = mimeType.getFileExtension();
        
        String[] ret = new String[2];
        
        ret[0] = prefix + File.separator + layerStr + File.separator
                + gridSetStr + "_" + zeroPadder(z, 2) + paramStr + File.separator 
                + zeroPadder(halfx, digits) + "_" 
                + zeroPadder(halfy, digits);
        
        ret[1] = zeroPadder(x, 2 * digits) + "_" + zeroPadder(y, 2 * digits) + "." + fileExtension;
        
        return ret;
    }
    

    /**
     * Silly way to pad numbers with leading zeros, since I don't know a fast
     * way of doing this in Java.
     * 
     * @param number
     * @param order
     * @return
     */
    public static String zeroPadder(long number, int order) {
        int numberOrder = 1;

        if (number > 9) {
            if(number > 11) {
                numberOrder = (int) Math.ceil(Math.log10(number) - 0.001);
            } else {
                numberOrder = 2;
            }
        }

        int diffOrder = order - numberOrder;
        
        //if(diffOrder < 0) {
        //    System.out.println("number: " + number + " order: " + order + " diff: " + diffOrder);
        //}
        StringBuilder padding = new StringBuilder(diffOrder);
        
        while (diffOrder > 0) {
            padding.append("0");
            diffOrder--;
        }

        return padding.toString() + Long.toString(number);
    }
    
    public static String filteredGridSetId(String gridSetId) {
        return gridSetId.replace(':', '_');
    }
    
    public static String filteredLayerName(String layerName) {
        return layerName.replace(':', '_').replace(' ', '_');
    }
}
