package org.geowebcache.s3.streams;

import org.geowebcache.s3.delete.DeleteTileInfo;
import org.geowebcache.s3.delete.DeleteTileRangeWithTileRange;
import org.geowebcache.storage.TileObject;

import java.util.function.Supplier;

public class TileIteratorSupplier implements Supplier<DeleteTileInfo> {
    private final TileIterator tileIterator;
    private final DeleteTileRangeWithTileRange deleteTileZoomInBoundedBox;

    public TileIteratorSupplier(TileIterator tileIterator, DeleteTileRangeWithTileRange deleteTileZoomInBoundedBox) {
        this.tileIterator = tileIterator;
        this.deleteTileZoomInBoundedBox = deleteTileZoomInBoundedBox;
    }

    @Override
    public DeleteTileInfo get() {
        synchronized (this) {
            if (tileIterator.hasNext()) {
                var stuff = tileIterator.next();
                var tileRange = tileIterator.getTileRange();
                return new DeleteTileInfo(
                        deleteTileZoomInBoundedBox.getPrefix(),
                        deleteTileZoomInBoundedBox.getLayerId(),
                        deleteTileZoomInBoundedBox.getGridSetId(),
                        deleteTileZoomInBoundedBox.getFormat(),
                        deleteTileZoomInBoundedBox.getParametersId(),
                        stuff[0],
                        stuff[1],
                        stuff[2],
                        null,
                        TileObject.createCompleteTileObject(
                                tileRange.getLayerName(),
                                stuff,
                                tileRange.getGridSetId(),
                                deleteTileZoomInBoundedBox.getFormat(),
                                tileRange.getParameters(),
                                null
                        )
                );
            } else {
                return null;
            }
        }
    }
}
