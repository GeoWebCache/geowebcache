package org.geowebcache.seed;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;

import com.thoughtworks.xstream.annotations.XStreamAlias;

import java.util.stream.Stream;

/**
 * A request to completely truncate a layer's cache.
 * 
 * @author Kevin Smith, OpenGeo
 */
@XStreamAlias("truncateLayer")
public class TruncateLayerRequest implements MassTruncateRequest {

    String layerName;

    public boolean doTruncate(StorageBroker sb, TileBreeder breeder) throws StorageException{
        boolean truncated = sb.delete(layerName);
        if (!truncated) {
            // did we hit a layer that has nothing on storage, or a layer that is not there?
            try {
                breeder.findTileLayer(layerName);
            } catch(GeoWebCacheException e) {
                throw new IllegalArgumentException("Could not find layer " + layerName);
            }
        }
        return true;
    }

}
