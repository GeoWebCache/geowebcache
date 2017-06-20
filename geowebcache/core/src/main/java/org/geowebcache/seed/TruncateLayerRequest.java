package org.geowebcache.seed;

import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;

import com.thoughtworks.xstream.annotations.XStreamAlias;

/**
 * A request to completely truncate a layer's cache.
 * 
 * @author Kevin Smith, OpenGeo
 */
@XStreamAlias("truncateLayer")
public class TruncateLayerRequest implements MassTruncateRequest {

    String layerName;

    public boolean doTruncate(StorageBroker sb, TileBreeder breeder) throws StorageException {
        return sb.delete(layerName);
    }

}
