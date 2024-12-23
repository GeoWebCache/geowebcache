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

import com.google.common.base.Optional;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.UncheckedGeoWebCacheException;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;

/**
 * Truncate the tiles within a bounding box for a layer across all parameters and formats
 *
 * @author smithkm
 */
@XStreamAlias("truncateExtent")
public class TruncateBboxRequest implements MassTruncateRequest {
    String layerName;

    private BoundingBox bounds;

    private String gridSetId;

    public TruncateBboxRequest(String layerName, BoundingBox bounds, String gridSetId) {
        super();
        this.layerName = layerName;
        this.bounds = bounds;
        this.gridSetId = gridSetId;
    }

    @Override
    public boolean doTruncate(StorageBroker sb, TileBreeder breeder) throws StorageException, GeoWebCacheException {
        final Set<Map<String, String>> allParams = sb.getCachedParameters(layerName);
        final TileLayer tileLayer = breeder.findTileLayer(layerName);
        final Collection<MimeType> allFormats = tileLayer.getMimeTypes();
        final GridSubset subSet = tileLayer.getGridSubset(gridSetId);
        final int minZ = Optional.fromNullable(subSet.getMinCachedZoom()).or(subSet.getZoomStart());
        final int maxZ = Optional.fromNullable(subSet.getMaxCachedZoom()).or(subSet.getZoomStop());
        // Create seed request for each combination of params and format
        Function<Map<String, String>, Stream<SeedRequest>> seedRequestMapper = params -> allFormats.stream()
                .map(format -> new SeedRequest(
                        layerName,
                        bounds,
                        gridSetId,
                        1,
                        minZ,
                        maxZ,
                        format.getMimeType(),
                        GWCTask.TYPE.TRUNCATE,
                        params));
        try {
            int taskCount = Stream.concat(
                            allParams.stream(),
                            Stream.of((Map<String, String>) null)) // Add null for the default parameters
                    .flatMap(seedRequestMapper)
                    .map(request -> {
                        try {
                            breeder.seed(layerName, request);
                            return 1;
                        } catch (GeoWebCacheException e) {
                            throw new UncheckedGeoWebCacheException(e);
                        }
                    })
                    .reduce((x, y) -> x + y)
                    .orElse(0);
            return taskCount > 0;
        } catch (UncheckedGeoWebCacheException e) {
            throw e.getCause();
        }
    }
}
