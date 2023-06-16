/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * <p>Copyright 2019
 */
package org.geowebcache.seed;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import java.util.Iterator;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;

/**
 * A request to completely truncate a layer's cache.
 *
 * @author Kevin Smith, OpenGeo
 */
@XStreamAlias("truncateLayer")
public class TruncateLayerRequest implements MassTruncateRequest {

    private static final Logger log = Logging.getLogger(TruncateLayerRequest.class.getName());

    String layerName;

    @Override
    public boolean doTruncate(StorageBroker sb, TileBreeder breeder) throws StorageException {
        try {
            TileLayer tLayer = breeder.findTileLayer(layerName);
            Iterator<String> gridSetIterator = tLayer.getGridSubsets().iterator();
            String gridSetId;
            while (gridSetIterator.hasNext()) {
                gridSetId = gridSetIterator.next();
                sb.deleteByGridSetId(layerName, gridSetId);
                log.info("Layer: " + layerName + ",Truncated Gridset :" + gridSetId);
            }
        } catch (GeoWebCacheException e) {
            // did we hit a layer that has nothing on storage, or a layer that is not there?
            throw new IllegalArgumentException("Could not find layer " + layerName);
        }
        return true;
    }
}
