package org.geowebcache.diskquota;

import org.geowebcache.diskquota.storage.TileSet;

public class QuotaUpdate {

    private final TileSet tileSet;

    private long size;

    private long[] tileIndex;

    /**
     * 
     * @param layerName
     * @param gridsetId
     * @param blobFormat
     * @param parametersId
     * @param size
     *            bytes to add or subtract from a quota: positive value increase quota, negative
     *            value decreases it
     * @param tileIndex
     */
    public QuotaUpdate(String layerName, String gridsetId, String blobFormat, String parametersId,
            long size, long[] tileIndex) {
        this(new TileSet(layerName, gridsetId, blobFormat, parametersId), size, tileIndex);
    }

    public QuotaUpdate(TileSet tileset, long quotaUpdateSize, long[] tileIndex) {
        this.tileSet = tileset;
        this.size = quotaUpdateSize;
        this.tileIndex = tileIndex;
    }

    public TileSet getTileSet() {
        return tileSet;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long[] getTileIndex() {
        return tileIndex;
    }

    @Override
    public String toString() {
        return new StringBuilder("[").append(tileSet.toString()).append(", ").append(size)
                .append(" bytes]").toString();
    }
}