package org.geowebcache.georss;

import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.updatesource.GeoRSSFeedDefinition;

/**
 * A tuple like holder for a scheduled georss poll
 */
class PollDef {
    private final TileLayer layer;

    private final GeoRSSFeedDefinition pollDef;

    public PollDef(final TileLayer layer, final GeoRSSFeedDefinition pollDef) {
        this.layer = layer;
        this.pollDef = pollDef;
    }

    public TileLayer getLayer() {
        return layer;
    }

    public GeoRSSFeedDefinition getPollDef() {
        return pollDef;
    }
}