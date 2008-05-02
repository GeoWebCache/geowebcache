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
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileRequest;
import org.geowebcache.mime.ImageMime;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.service.ServiceRequest;
import org.geowebcache.util.wms.BBOX;

/**
 * Temporary code for truncating
 * 
 * @author ak
 */
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
    protected int doTruncate(int zoomStart, int zoomStop, MimeType mimeType,
            SRS srs, BBOX bounds, HttpServletResponse response) throws GeoWebCacheException, IOException {
        response.setContentType("text/html");

        PrintWriter pw = response.getWriter();
        int srsIdx = layer.getSRSIndex(srs);
        int[][] coveredGridLevels = layer.getCoveredGridLevels(srsIdx, bounds);
        int[] metaTilingFactors = layer.getMetaTilingFactors();
        
        for (int level = zoomStart; level <= zoomStop; level++) {
            int[] gridBounds = coveredGridLevels[level];
            
            int count = 0;
            
            for (int gridy = gridBounds[1]; gridy <= gridBounds[3];  ) {
                
                for (int gridx = gridBounds[0]; gridx <= gridBounds[2]; ) {
                    
                    int[] gridLoc = { gridx , gridy , level };
                    
                    TileRequest tileReq = new TileRequest(gridLoc, mimeType, srs);
                    layer.getResponse(tileReq, new ServiceRequest(null) , response);
                    
                    // Next column
                    gridx++;
                }
                // Next row
                gridy++;
            }
        }

        pw.close();
        return 0;
    }
    
    protected int doTruncateWorld(MimeType mimeType, SRS srs, 
            HttpServletResponse response) 
    throws IOException {
        if(mimeType == null) {
            // Err.. we'll just clear everything
        }
        
        // Just unlink the directory for the layer
        String cachePfx = this.layer.getCachePrefix();

        if (srs != null) {
            cachePfx = cachePfx + File.separator + srs.filePath();
        }
        File dir = new File(cachePfx);

        // Acquire lock
        layer.acquireLayerLock();
        try {
            if (dir.exists()) {
                dir.delete();
            }
        } finally {
            layer.releaseLayerLock();
        }
        return 0;
    }
    
    
}
