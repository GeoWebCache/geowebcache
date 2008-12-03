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
 * @author Arne Kepp / The Open Planning Project 2008 
 */
package org.geowebcache.rest;

import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.cache.Cache;
import org.geowebcache.layer.GridCalculator;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.mime.MimeType;
import org.geowebcache.mime.XMLMime;
import org.geowebcache.util.wms.BBOX;

public class TruncateTask extends GWCTask {
    private static Log log = LogFactory.getLog(org.geowebcache.rest.TruncateTask.class);
    
    private final SeedRequest req;
    
    private final TileLayer tl;
    
    private final static double[] nullBbox = {0.0,0.0,0.0,0.0};
    
    public TruncateTask(SeedRequest req, TileLayer tl) {
        this.req = req;
        this.tl = tl;
        
        super.type = GWCTask.TYPE_TRUNCATE;
        super.layerName = tl.getName();
        super.tilesTotal = 0;
        super.timeRemaining = 0;
        super.timeSpent = 0;
        super.tilesDone = 0;
    }
    
    void doAction() throws GeoWebCacheException {
        
        tl.isInitialized();
        
        Cache cache = tl.getCache();

        int[][] bounds = null;
        
        if(req.getBounds() == null) {
            //TODO need nicer interface, just send null
            bounds = tl.getCoveredGridLevels(req.getSRS(), tl.getGrid(req.getSRS()).getBounds());
        } else if(! Arrays.equals(req.getBounds().coords, nullBbox)) {
            bounds = tl.getCoveredGridLevels(req.getSRS(), req.getBounds());
        }
        
        // Check if MimeType supports metatiling, in which case 
        // we may have to throw a wider net
        MimeType[] mimeTypes = null;
        if (req.getMimeFormat() != null && req.getMimeFormat().length() > 0) {
            MimeType mimeType = MimeType.createFromFormat(req.getMimeFormat());
            if(mimeType == XMLMime.kml) {
                mimeTypes = new MimeType[2];
                mimeTypes[0] = mimeType;
                mimeTypes[1] = XMLMime.kmz;
                log.info("Truncate request was for KML. This will also truncate all KMZ archives.");
            } else {
                mimeTypes = new MimeType[1];
                mimeTypes[0] = mimeType;
            }
            

            if (bounds != null) {
                int[] metaFactors = tl.getMetaTilingFactors();

                int gridBounds[][] = tl.getGrid(req.getSRS())
                        .getGridCalculator().getGridBounds();

                if (metaFactors[0] > 1 || metaFactors[1] > 1
                        && mimeType.supportsTiling()) {
                    bounds = GridCalculator.expandBoundsToMetaTiles(gridBounds,
                            bounds, metaFactors);
                }
            }
        }
        
        int count = cache.truncate(tl, req.getSRS(), 
                req.getZoomStart(), req.getZoomStop(), 
                bounds, mimeTypes);
        log.info("Completed truncating " + count + " tiles");
    }

}
