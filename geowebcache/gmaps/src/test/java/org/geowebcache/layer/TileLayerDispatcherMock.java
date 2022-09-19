package org.geowebcache.layer;

import org.geowebcache.GeoWebCacheException;

public class TileLayerDispatcherMock extends TileLayerDispatcher {

    private final TileLayer layer;

    public TileLayerDispatcherMock(TileLayer layer) {
        super(null, null);
        this.layer = layer;
    }

    @Override
    public TileLayer getTileLayer(String layerIdent) throws GeoWebCacheException {
        return layer;
    }
}
