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
package org.geowebcache.rest.seed;

import java.util.Arrays;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubSet;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.mime.MimeType;
import org.geowebcache.rest.GWCTask;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileRangeObject;

public class TruncateTask extends GWCTask {
    
    private final SeedRequest req;
    
    private final TileLayer tl;
    
    private final StorageBroker storageBroker;
    
    private final static double[] nullBbox = {0.0,0.0,0.0,0.0};
    
    public TruncateTask(StorageBroker sb, SeedRequest req, TileLayer tl) {
        this.storageBroker = sb;
        this.req = req;
        this.tl = tl;
        
        super.type = GWCTask.TYPE_TRUNCATE;
        super.layerName = tl.getName();
    }
    
    public void doAction() throws GeoWebCacheException {
        GridSubSet gridSubSet = tl.getGridSubSet(req.getGridSetId());
        
        long[][] coverages = null;

        BoundingBox reqBounds = req.getBounds();
        if (req.getBounds() == null 
                || Arrays.equals(req.getBounds().coords, nullBbox)) {
            coverages = gridSubSet.getCoverages();
        } else {
            coverages = gridSubSet.getCoverageIntersections(reqBounds);
        }

        MimeType mimeType = MimeType.createFromFormat(req.getMimeFormat());

        int[] metaFactors = tl.getMetaTilingFactors();

        if (metaFactors[0] > 1 || metaFactors[1] > 1 && mimeType.supportsTiling()) {
            coverages = gridSubSet.expandToMetaFactors(coverages, metaFactors);
        }
        
        TileRangeObject trObj = new TileRangeObject(
                layerName, 
                req.getGridSetId(), 
                req.getZoomStart(), 
                req.getZoomStop(), 
                coverages, 
                mimeType, 
                req.getParameters());
        
        try {
            storageBroker.delete(trObj);
        } catch (StorageException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
