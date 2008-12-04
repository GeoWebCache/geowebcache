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
package org.geowebcache.cache.file;

import java.io.File;
import java.io.FilenameFilter;

import org.geowebcache.cache.CacheException;
import org.geowebcache.layer.SRS;
import org.geowebcache.mime.MimeType;

public class FilePathKey2Filter implements FilenameFilter {
    final String srsPrefix;
    final int zoomStart;
    final int zoomStop;
    final int[][] bounds;
    final String[] mimeExtensions;
    
    public FilePathKey2Filter(SRS srs, int zoomStart, int zoomStop, 
            int[][] bounds, MimeType[] mimeTypes) throws CacheException {
        
        if(srs == null) {
            throw new CacheException("Specifying the SRS is currently mandatory.");
        }
       
        this.srsPrefix = "EPSG_"+srs.getNumber();
        this.zoomStart = zoomStart;
        this.zoomStop = zoomStop;
        this.bounds = bounds;
        if(mimeTypes != null) {
        this.mimeExtensions = new String[mimeTypes.length];
        for(int i=0; i< mimeExtensions.length; i++) {
            mimeExtensions[i] = mimeTypes[i].getFileExtension();
        }
        } else {
            mimeExtensions = null;
        }
    }

    /**
     * Assumes it will get fed something like
     *  path:             name:
     *  *EPSG_2163_01/0_0 01_01.png 
     *  *EPSG_2163_01/    0_0
     *  *                 EPSG_2163_01
     */
    public boolean accept(File dir, String name) {  
        if(name.startsWith("EPSG_")) {
            // srs and zoomlevel level
            return acceptZoomLevelDir(name);
        } else if(name.contains(".")) {
            // filename
            return acceptFileName(dir, name);
        } else {
            // intermediate
            return acceptIntermediateDir(name);
        }
    }
        
    /**
     * Example: EPSG_2163_01
     */
    private boolean acceptZoomLevelDir(String name) {
        if(! name.startsWith(srsPrefix)) {
            return false;
        }
        
        if(zoomStart == -1 && zoomStop == -1) {
            // All zoomlevels
        } else {
            int tmp = findZoomLevel(name);
            if(tmp < zoomStart || tmp > zoomStop) {
                return false;
            }
        }
        
        return true;   
    }
    
    private boolean acceptIntermediateDir(String name) {
        //if(bounds == null) {
        //    return true;
        //}
        
        // For now we'll do all of them,
        // otherwise we have to extract zoomlevel from path
        return true;
    }
    
    private boolean acceptFileName(File dir, String name) {
        String[] parts = name.split("\\.");

        // Check mime type
        if (mimeExtensions != null) {
            boolean foundOne = false;
            
            for(String ext : mimeExtensions) {
                if(parts[parts.length - 1].equalsIgnoreCase(ext)) {
                    foundOne = true;
                }
            }
            
            if(! foundOne) {
                return false;
            }
            
        }
        
        // Check coordinates
        if (bounds != null) {
            String[] coords = parts[0].split("_");

            int zoomLevel = findZoomLevel(dir.getParentFile().getName());

            int x = Integer.parseInt(coords[0]);
            int y = Integer.parseInt(coords[1]);

            int[] box = bounds[zoomLevel];

            
            if (x < box[0] || x > box[2]) {
                return false;
            }

            if (y < box[1] || y > box[3]) {
                return false;
            }
        }
        
        //System.out.println(dir.getAbsolutePath() + " " + name);
        
        return true;
    }
    
    /**
     * Extracts the zoomLevel from something like EPSG_2163_01
     * 
     * @param dirName
     * @return
     */
    private int findZoomLevel(String dirName) {
        return Integer.parseInt(dirName.substring(srsPrefix.length() +1));
    }
}
