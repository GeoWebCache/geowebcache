package org.geowebcache.rest.seed;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.layer.AbstractTileLayer;
import org.geowebcache.layer.TileLayer;

import java.io.IOException;

/**
 * Empty tile layer used for mocking
 */
public class TestTileLayer extends AbstractTileLayer {
    
    @Override
    protected boolean initializeInternal(GridSetBroker gridSetBroker) {
        return true;
    }

    @Override
    public ConveyorTile getTile(ConveyorTile tile) throws GeoWebCacheException, IOException, OutsideCoverageException {
        return null;
    }

    @Override
    public ConveyorTile getNoncachedTile(ConveyorTile tile) throws GeoWebCacheException {
        return null;
    }

    @Override
    public void seedTile(ConveyorTile tile, boolean tryCache) throws GeoWebCacheException, IOException {

    }

    @Override
    public ConveyorTile doNonMetatilingRequest(ConveyorTile tile) throws GeoWebCacheException {
        return null;
    }

    @Override
    public String getStyles() {
        return null;
    }
}
