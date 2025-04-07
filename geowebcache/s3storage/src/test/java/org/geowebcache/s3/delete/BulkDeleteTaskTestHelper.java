package org.geowebcache.s3.delete;

import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.DeleteObjectsResult;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.geowebcache.storage.TileObject;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.geowebcache.s3.delete.DeleteTileRangeWithTileRange.ONE_BY_ONE_META_TILING_FACTOR;
import static org.geowebcache.s3.streams.StreamTestHelper.SINGLE_ZOOM_SINGLE_BOUND_MATCHING;

public class BulkDeleteTaskTestHelper {
    public static final Random RANDOM = new Random(System.currentTimeMillis());

    public static final String PREFIX = "prefix";
    public static final String LAYER_ID = "layer-id";
    public static final String BUCKET = "bucket";
    public static final String LAYER_NAME = "layer-name";

    public static final int BATCH = 100;

    public static final String GRID_SET_ID = "EPSG:4326";
    public static final String GRID_SET_ID_2 = "EPSG:900913";

    public static final String FORMAT_IN_KEY = "png";
    public static final String FORMAT_IN_KEY_2 = "jpg";
    public static final String EXTENSION = "png";

    public static final String PARAMETERS_ID = "75595e9159afae9c4669aee57366de8c196a57e1";

    public static final long TIMESTAMP = System.currentTimeMillis();

    public static final Set<String> EMPTY_SET_OF_GRID_SET_IDS = Set.of();
    public static final Set<String> SINGLE_SET_OF_GRID_SET_IDS = Set.of(GRID_SET_ID);
    public static final Set<String> ALL_SET_OF_GRID_SET_IDS = Set.of(GRID_SET_ID, GRID_SET_ID_2);

    public static final Set<String> EMPTY_SET_OF_FORMATS = Set.of();
    public static final Set<String> SINGLE_SET_OF_FORMATS = Set.of(FORMAT_IN_KEY);
    public static final Set<String> ALL_SET_OF_FORMATS = Set.of(FORMAT_IN_KEY, FORMAT_IN_KEY_2);

    public static final Long ZOOM_LEVEL_4 = 4L;
    public static final Long ZOOM_LEVEL_6 = 6L;
    public static final Long ZOOM_LEVEL_9 = 9L;

    public static final Set<Long> ZOOM_LEVEL_SET_0 = Set.of(0L);
    public static final Set<Long> ZOOM_LEVEL_SET_1 = Set.of(1L);
    // public static final Set<Long> ZOOM_LEVEL_4 = Set.of(4L);

    //public static final long[][] SMALL_RANGE_BOUNDS_ZOOM_4_ZOOM_4 = {{0,0,3,3,4}};
    //public static final long[][] LARGE_RANGE_BOUNDS_ZOOM_4_ZOOM_8 = {{0,0,8,8,4},{0,0,16,16,4},{0,0,32,32,7},{0,0,64,64,6},{0,0,64,64,6}};
    public static final long[] SMALL_BOUNDED_BOX = {0,0,3,3};
    //public static final long[] LARGE_BOUNDED_BOX = {0,0,128,128};

    public static final Set<Long> ZOOM_LEVEL_0_THROUGH_9 = Set.of(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);

    public static final long[] XYZ = {1, 2, 3};
    public static final Map<String, String> PARAMETERS = Map.of("key1", "value1", "key2", "value2");


    public static final TileObject TILE_OBJECT =
            TileObject.createCompleteTileObject(LAYER_NAME, XYZ, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS, null);
    public static final long BLOB_SIZE = 12_344_567L;

    static {
        TILE_OBJECT.setParametersId(PARAMETERS_ID);
        TILE_OBJECT.setBlobSize((int) BLOB_SIZE);
    }

    static long zoomScaleModifier(long zoomLevel) {
        return Math.min(Math.round(Math.pow(2.0, zoomLevel)), 32);
    }

    static List<S3ObjectSummary> generateLayerSummaries(
            Set<String> gridSetIds, Set<String> formats, Set<Long> setOfZoomLevels) {
        List<S3ObjectSummary> summaries = new ArrayList<>();

        gridSetIds.forEach(gridSetId -> formats.forEach(format -> setOfZoomLevels.forEach(z -> {
            List<S3ObjectSummary> layerSummaries =
                    generateZoomLevelSummaries(z, zoomScaleModifier(z), zoomScaleModifier(z), gridSetId, format);
            summaries.addAll(layerSummaries);
        })));

        return summaries;
    }

    static List<S3ObjectSummary> generateZoomLevelSummaries(
            long zoomLevel, long xScale, long yScale, String gridSetId, String format) {
        List<S3ObjectSummary> summaries = new ArrayList<>();

        LongStream.range(0, xScale).forEach(x -> LongStream.range(0, yScale).forEach(y -> {
            long size = RANDOM.nextLong() % 9_900_000L + 100_000L;
            S3ObjectSummary summary = generateFromConstants(gridSetId, format, x, y, zoomLevel, size);
            summaries.add(summary);
        }));
        return summaries;
    }

    static S3ObjectSummary generateFromConstants(String gridSetId, String format, long x, long y, long z, long size) {
        return generate(BUCKET, PREFIX, LAYER_ID, gridSetId, format, PARAMETERS_ID, x, y, z, size);
    }

    static S3ObjectSummary generate(
            String bucket,
            String prefix,
            String layerId,
            String gridSetId,
            String format,
            String parametersId,
            long x,
            long y,
            long z,
            long size) {
        S3ObjectSummary summary = new S3ObjectSummary();
        String key = DeleteTileInfo.toFullPath(prefix, layerId, gridSetId, format, parametersId, z, x, y, format);

        summary.setBucketName(bucket);
        summary.setKey(key);
        summary.setSize(size);
        summary.setLastModified(new Date(TIMESTAMP));
        summary.setStorageClass("Standard");

        return summary;
    }

    // public static final List<S3ObjectSummary> S_3_OBJECT_EMPTY_SUMMARY_LIST = new ArrayList<>();
    public static final List<S3ObjectSummary> S_3_OBJECT_SUMMARY_SINGLE_TILE_LIST =
            generateLayerSummaries(SINGLE_SET_OF_GRID_SET_IDS, SINGLE_SET_OF_FORMATS, ZOOM_LEVEL_SET_0);

    public static List<S3ObjectSummary> S_3_OBJECT_SUMMARY_SINGLE_BATCH_LIST() {
        return generateLayerSummaries(SINGLE_SET_OF_GRID_SET_IDS, SINGLE_SET_OF_FORMATS, ZOOM_LEVEL_SET_1);
    }

    public static final List<S3ObjectSummary> S_3_OBJECT_SUMMARY_LARGE_LIST =
            generateLayerSummaries(SINGLE_SET_OF_GRID_SET_IDS, SINGLE_SET_OF_FORMATS, ZOOM_LEVEL_0_THROUGH_9);

    public static DeleteObjectsResult generateDeleteObjectsResult(DeleteObjectsRequest request) {
        List<DeleteObjectsResult.DeletedObject> deletedObjects = request.getKeys().stream()
                .map(key -> {
                    DeleteObjectsResult.DeletedObject deletedObject = new DeleteObjectsResult.DeletedObject();
                    deletedObject.setKey(key.getKey());
                    deletedObject.setVersionId(key.getVersion());
                    deletedObject.setDeleteMarker(false);
                    return deletedObject;
                })
                .collect(Collectors.toList());
        DeleteObjectsResult result = new DeleteObjectsResult(deletedObjects);
        result.setRequesterCharged(false);
        return result;
    }

    public static DeleteObjectsResult emptyDeleteObjectsResult() {
        return new DeleteObjectsResult(Collections.emptyList());
    }

    public static final CompositeDeleteTileParameterId ALL_GRIDS_ALL_FORMATS_COMPOSITE_TILE_PARAMETERS =
            new CompositeDeleteTileParameterId(
                    PREFIX, BUCKET, LAYER_ID, ALL_SET_OF_GRID_SET_IDS, ALL_SET_OF_FORMATS, PARAMETERS_ID, LAYER_NAME);


    public static final CompositeDeleteTilesInRange SINGLE_ZOOM_SINGLE_BOUND_COMPOSITE_DELETE_TILES_IN_RANGE =
            new CompositeDeleteTilesInRange(PREFIX, BUCKET, LAYER_ID, FORMAT_IN_KEY, SINGLE_ZOOM_SINGLE_BOUND_MATCHING);

    public static final DeleteTileZoomInBoundedBox SINGLE_ZOOM_SINGLE_BOUND_DELETE_TILES_IN_RANGE =
            new DeleteTileZoomInBoundedBox(PREFIX, BUCKET, LAYER_ID, GRID_SET_ID, FORMAT_IN_KEY, PARAMETERS_ID, ZOOM_LEVEL_4,
                    SMALL_BOUNDED_BOX, SINGLE_ZOOM_SINGLE_BOUND_MATCHING, ONE_BY_ONE_META_TILING_FACTOR);

    public static final Long SINGLE_ZOOM_SINGLE_BOUND_TILES = 16L;
}
