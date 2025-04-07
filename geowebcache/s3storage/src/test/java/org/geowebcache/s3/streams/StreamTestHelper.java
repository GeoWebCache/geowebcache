package org.geowebcache.s3.streams;

import static org.geowebcache.s3.delete.BulkDeleteTaskTestHelper.*;

import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.TileRange;

public class StreamTestHelper {

    public static MimeType PNG_MIME_TYPE;

    static {
        try {
            PNG_MIME_TYPE = MimeType.createFromExtension("png");
        } catch (MimeException e) {
            System.err.println(e.getMessage());
        }
    }

    public static final long[][] SINGLE_ZOOM_4_SINGLE_BOUND_MATCHING = {{0, 0, 3, 3, 4}};
    public static final long[][] SINGLE_ZOOM_4_MULTIPLE_BOUNDS_MATCHING = {
        {0, 0, 3, 3, 4}, {5, 5, 8, 8, 4}, {9, 9, 12, 12, 4}
    };
    public static final long[][] MULTIPLE_ZOOM_4_5_6_MULTIPLE_BOUNDS_MATCHING = {
        {0, 0, 3, 3, 4}, {5, 5, 8, 8, 5}, {9, 9, 12, 12, 6}
    };

    public static final TileRange SINGLE_ZOOM_SINGLE_BOUND_MATCHING = new TileRange(
            LAYER_NAME,
            GRID_SET_ID,
            ZOOM_LEVEL_4.intValue(),
            ZOOM_LEVEL_4.intValue(),
            SINGLE_ZOOM_4_SINGLE_BOUND_MATCHING,
            PNG_MIME_TYPE,
            PARAMETERS);

    public static final TileRange SINGLE_ZOOM_SINGLE_BOUND_NOT_MATCHING = new TileRange(
            LAYER_NAME,
            GRID_SET_ID,
            ZOOM_LEVEL_9.intValue(),
            ZOOM_LEVEL_9.intValue(),
            SINGLE_ZOOM_4_SINGLE_BOUND_MATCHING,
            PNG_MIME_TYPE,
            PARAMETERS);

    public static final TileRange SINGLE_ZOOM_MULTIPLE_BOUNDS_MATCHING = new TileRange(
            LAYER_NAME,
            GRID_SET_ID,
            ZOOM_LEVEL_4.intValue(),
            ZOOM_LEVEL_4.intValue(),
            SINGLE_ZOOM_4_MULTIPLE_BOUNDS_MATCHING,
            PNG_MIME_TYPE,
            PARAMETERS);

    public static final TileRange MULTIPLE_ZOOM_SINGLE_BOUND_PER_ZOOM_MATCHING = new TileRange(
            LAYER_NAME,
            GRID_SET_ID,
            ZOOM_LEVEL_4.intValue(),
            ZOOM_LEVEL_6.intValue(),
            MULTIPLE_ZOOM_4_5_6_MULTIPLE_BOUNDS_MATCHING,
            PNG_MIME_TYPE,
            PARAMETERS);
}
