package org.geowebcache.seed;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.UncheckedGeoWebCacheException;
import org.geowebcache.config.Configuration;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;

import com.google.common.base.Optional;
import com.sun.media.imageio.stream.StreamSegment;
import com.thoughtworks.xstream.annotations.XStreamAlias;

/**
 * Truncate the tiles within a bounding box for a layer across all parameters and formats
 * @author smithkm
 *
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
    public boolean doTruncate(StorageBroker sb, Configuration config, TileBreeder breeder) throws StorageException, GeoWebCacheException {
        final Set<Map<String,String>> allParams = sb.getCachedParameters(layerName);
        final TileLayer tileLayer = config.getTileLayer(layerName);
        final Collection<MimeType> allFormats = tileLayer.getMimeTypes();
        final GridSubset subSet = tileLayer.getGridSubset(gridSetId);
        final int minZ = Optional.fromNullable(subSet.getMinCachedZoom()).or(subSet.getZoomStart());
        final int maxZ = Optional.fromNullable(subSet.getMaxCachedZoom()).or(subSet.getZoomStop());
        try {
            int taskCount = Stream.concat(allParams.stream(), 
                                Stream.of((Map<String,String>)null)) // Add null for the default parameters
                .flatMap(params->allFormats.stream()
                    .map(format->
                            new SeedRequest(layerName, bounds, gridSetId, 1, minZ, maxZ, format.getMimeType(), GWCTask.TYPE.TRUNCATE, params)))
                .map(request->{
                    try {
                        breeder.seed(layerName, request);
                        return 1;
                    } catch (GeoWebCacheException e) {
                        throw new UncheckedGeoWebCacheException(e);
                    }
                })
                .reduce((x,y)->x+y)
                .orElse(0);
            return taskCount>0;
        } catch (UncheckedGeoWebCacheException e) {
            throw e.getCause();
        }
    }

}
