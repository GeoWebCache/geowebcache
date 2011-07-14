package org.geowebcache.seed;

import org.geowebcache.grid.BoundingBox;

public class SeedEstimate {
    public String layerName;
    public String gridSetId;
    public BoundingBox bounds;
    public int zoomStart;
    public int zoomStop;
    public int threadCount;
    public int maxThroughput;

    public long timeSpent;
    public long tilesDone;

    public long tilesTotal;
    public long timeRemaining;
}
