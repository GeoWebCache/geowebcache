package org.geowebcache.layer.wms;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;

public interface WMSSourceHelper {
    public byte[] makeFeatureInfoRequest(ConveyorTile tile, int x, int y) throws GeoWebCacheException;
    public byte[] makeRequest(ConveyorTile tile) throws GeoWebCacheException;
    public byte[] makeRequest(WMSMetaTile metaTile) throws GeoWebCacheException;
}
