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
 */

package org.geowebcache.seeder;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.cache.CacheKey;
import org.geowebcache.cache.file.FilePathKey;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileRequest;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.service.ServiceRequest;
import org.geowebcache.util.wms.BBOX;
import org.geowebcache.util.FileUtils;

/**
 * Temporary code for truncating...
 * 
 * @author ak
 */
//TODO this stuff needs to be moved to the cache
public class Truncater {
    private static Log log = LogFactory.getLog(org.geowebcache.seeder.Truncater.class);

    TileLayer layer = null;

    public Truncater(TileLayer layer) {
        this.layer = layer;
    }

    /**
     * 
     * @param zoomStart
     * @param zoomStop
     * @param imageFormat
     * @param srs
     * @param bounds
     * @param response
     * @return
     * @throws IOException
     */
    protected void doTruncate(int zoomStart, int zoomStop, MimeType mimeType,
            SRS srs, BBOX bounds, HttpServletResponse response) throws GeoWebCacheException, IOException {

        if(bounds == null && mimeType == null) {
            doTruncateWorld(zoomStart, zoomStop, srs, response);
        } else {
            doTruncateTiles(zoomStart, zoomStop, mimeType, srs, bounds, response);
        }
    }
    
    protected int doTruncateTiles(int zoomStart, int zoomStop, MimeType mimeType,
            SRS srs, BBOX bounds, HttpServletResponse response) throws GeoWebCacheException, IOException {

        response.setContentType("text/html");

        PrintWriter pw = response.getWriter();
        int srsIdx = layer.getSRSIndex(srs);
        
        String cachePfx = this.layer.getCachePrefix();
        
        int[][] coveredGridLevels = layer.getCoveredGridLevels(srsIdx, bounds);
        
        // Acquire lock
        layer.acquireLayerLock();
        
        try {
            for (int level = zoomStart; level <= zoomStop; level++) {
                int[] gridBounds = coveredGridLevels[level];

                for (int gridy = gridBounds[1]; gridy <= gridBounds[3];) {

                    for (int gridx = gridBounds[0]; gridx <= gridBounds[2];) {

                        int[] gridLoc = { gridx, gridy, level };

                        CacheKey ck = (CacheKey) layer.getCacheKey();

                        // TODO need to revisit case where this is not a string
                        String strCK = (String) ck.createKey(cachePfx, gridLoc[0], gridLoc[1],gridLoc[2], srs, mimeType.getFileExtension());
                        System.out.println(strCK);
                        
                        // Next column
                        gridx++;
                    }
                    // Next row
                    gridy++;
                }
            }
        } finally {
            layer.releaseLayerLock();
        }

        pw.close();
        return 0;
    }
    
    // TODO move to cache
    
    private int doTruncateWorld(int zoomStart, int zoomStop, SRS srs, 
            HttpServletResponse response) 
    throws IOException, SeederException {        
        // Just unlink the directory for the layer
        String cachePfx = this.layer.getCachePrefix();
        
        if (srs == null && zoomStart < 0) {
            // Acquire lock
            layer.acquireLayerLock();
            try {
                File dir = new File(cachePfx);
                if (dir.exists()) {
                    System.out.println("Deleting " + dir.getAbsolutePath() + " " + FileUtils.rmFileCacheDir(dir,null));
                }
            } finally {
                layer.releaseLayerLock();
            }
            
            return 0;
        }
        
        String[] srsList = null;
        if (srs != null) {
            srsList = new String[1];
            srsList[0] = srs.filePath();
        } else {
            SRS[] srss = layer.getProjections();
            srsList = new String[srss.length];
            
            for(int i=0; i<srss.length; i++) {
                srsList[i] = srss[i].filePath();
            }
        }
        
        String[] zoomList = null;
        
        if(zoomStart >= 0) {
            if(zoomStop < 0) {
                zoomStop = layer.getZoomStop();
            }
            if(zoomStart <= zoomStop) {
                zoomList = new String[zoomStop - zoomStart + 1];
            } else {
                throw new SeederException("zoomStart ("
                        +zoomStart+") and zoomStop ("
                        +zoomStop+") do not make sense");
            }
            
            for(int i=0; i<zoomList.length; i++) {
                zoomList[i] =  FilePathKey.zeroPadder(zoomStart + i, 2);
            }
        } else {
            // do nothing
        }
        
        // Acquire lock
        layer.acquireLayerLock();
        try {
            // Now merge the lists for SRSs and ZoomStart / zoomStop
            for (int i = 0; i < srsList.length; i++) {
                if (zoomList == null) {
                    File dir = new File(cachePfx + File.separator + srsList[i]);
                    if (dir.exists()) {
                        System.out.println("Deleting " + dir.getAbsolutePath());
                        FileUtils.rmFileCacheDir(dir,null);
                    }
                } else {
                    for (int j = 0; j < zoomList.length; j++) {
                        File dir = new File(cachePfx + File.separator
                                + srsList[i] + File.separator + zoomList[j]);
                        if (dir.exists()) {
                            System.out.println("Deleting "
                                    + dir.getAbsolutePath());
                            FileUtils.rmFileCacheDir(dir,null);
                        } else {
                            System.out.println(dir.getAbsolutePath());
                        }
                    }
                }
            }
        } finally {
            layer.releaseLayerLock();
        }
        return 0;
    }
    
    
}
