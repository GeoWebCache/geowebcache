package org.geowebcache.layer;

import java.util.EventListener;

import org.geowebcache.conveyor.ConveyorTile;

public interface TileLayerListener extends EventListener {

    void tileRequested(TileLayer layer, ConveyorTile tile);

}
