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
import java.io.FilenameFilter;

import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileRange;

public class FilePathFilter implements FilenameFilter {
    String gridSetPrefix = null;
    String mimeExtension = null;
    TileRange tr;
    
    public FilePathFilter(TileRange trObj) throws StorageException {
        this.tr = trObj;
        
        if(tr.gridSetId == null) {
            throw new StorageException("Specifying the grid set id is currently mandatory.");
        }
       
        gridSetPrefix = FilePathGenerator.filteredGridSetId(tr.gridSetId);

        if(tr.mimeType != null) {
            mimeExtension = tr.mimeType.getFileExtension();
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
        boolean ret;
        if(name.startsWith(gridSetPrefix)) {
            // gridset and zoomlevel level
            ret = acceptZoomLevelDir(name);
        } else if(name.contains(".")) {
            // filename
            ret = acceptFileName(dir, name);
        } else {
            // intermediate
            ret = acceptIntermediateDir(name);
        }
        
        //System.out.println(ret + " " + name);
        return ret;
    }
        
    /**
     * Example: EPSG_2163_01
     */
    private boolean acceptZoomLevelDir(String name) {
        if(! name.startsWith(gridSetPrefix)) {
            return false;
        }
        
        if(tr.zoomStart == -1 && tr.zoomStop == -1) {
            // All zoomlevels
        } else {
            int tmp = findZoomLevel(name);
            if(tmp < tr.zoomStart || tmp > tr.zoomStop) {
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
        
        if(! parts[parts.length - 1].equalsIgnoreCase(this.mimeExtension)) {
            return false;
        }
        
        //if (mimeExtension != null) {
            //boolean foundOne = false;
            
            //for(String ext : mimeExtensions) {
            //    if(parts[parts.length - 1].equalsIgnoreCase(ext)) {
            //        foundOne = true;
            //    }
            //}
            
            //if(! foundOne) {
            //    return false;
            //}
            
        //}
        
        // Check coordinates
        if (tr.rangeBounds != null) {
            String[] coords = parts[0].split("_");

            int zoomLevel = findZoomLevel(dir.getParentFile().getName());
            long x = Long.parseLong(coords[0]);
            long y = Long.parseLong(coords[1]);
            
            boolean contains = tr.contains(x, y, zoomLevel);
            
            return contains;
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
    static int findZoomLevel(String dirName) {
        return Integer.parseInt(dirName.substring(dirName.lastIndexOf('_') + 1));
    }
}