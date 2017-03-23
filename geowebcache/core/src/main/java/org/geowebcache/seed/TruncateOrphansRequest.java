package org.geowebcache.seed;

import java.util.Map;

import org.geowebcache.config.Configuration;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;
import com.thoughtworks.xstream.converters.extended.NamedMapConverter;

@XStreamAlias("truncateOrphans")
public class TruncateOrphansRequest implements MassTruncateRequest {
    String layerName;
    
    @Override
    public boolean doTruncate(StorageBroker sb, Configuration config, TileBreeder breeder) throws StorageException {
        final TileLayer layer = config.getTileLayer(layerName);
        return sb.purgeOrphans(layer);
    }

}
