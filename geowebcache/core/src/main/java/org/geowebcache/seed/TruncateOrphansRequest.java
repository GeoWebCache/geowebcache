/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Kevin Smith, Boundless, 2017
 */
package org.geowebcache.seed;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;

/**
 * Truncate atiles from a cache where we know they are no longer reachable, or we can't know that due to missing
 * metadata.
 *
 * @author smithkm
 */
@XStreamAlias("truncateOrphans")
public class TruncateOrphansRequest implements MassTruncateRequest {
    String layerName;

    @Override
    public boolean doTruncate(StorageBroker sb, TileBreeder breeder) throws GeoWebCacheException, StorageException {
        final TileLayer layer = breeder.findTileLayer(layerName);
        return sb.purgeOrphans(layer);
    }
}
