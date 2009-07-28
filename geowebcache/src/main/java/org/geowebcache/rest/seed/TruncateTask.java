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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.GridCalculator;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.mime.MimeType;
import org.geowebcache.mime.XMLMime;
import org.geowebcache.rest.GWCTask;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileRangeObject;

public class TruncateTask extends GWCTask {
    private static Log log = LogFactory.getLog(org.geowebcache.rest.seed.TruncateTask.class);
    
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

        tl.isInitialized();

        int[][] bounds = null;

        if (req.getBounds() == null) {
            // TODO need nicer interface, just send null
            bounds = tl.getCoveredGridLevels(req.getSRS(), tl.getGrid(req.getSRS()).getBounds());
        } else if (!Arrays.equals(req.getBounds().coords, nullBbox)) {
            bounds = tl.getCoveredGridLevels(req.getSRS(), req.getBounds());
        }

        MimeType mimeType = MimeType.createFromFormat(req.getMimeFormat());

        if (bounds != null) {
            int[] metaFactors = tl.getMetaTilingFactors();

            int gridBounds[][] = tl.getGrid(req.getSRS()).getGridCalculator().getGridBounds();

            if (metaFactors[0] > 1 || metaFactors[1] > 1 && mimeType.supportsTiling()) {
                bounds = GridCalculator.expandBoundsToMetaTiles(gridBounds, bounds, metaFactors);
            }
        }
        
        TileRangeObject trObj = new TileRangeObject(layerName, req.getSRS(), req.getZoomStart(), req.getZoomStop(), bounds, mimeType, req.getParameters());
        
        try {
            storageBroker.delete(trObj);
        } catch (StorageException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
