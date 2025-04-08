package org.geowebcache.s3.delete;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static java.util.function.Predicate.not;

import com.google.common.base.Strings;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.geowebcache.storage.TileObject;

public class DeleteTileInfo {
    public static final Pattern keyRegex = Pattern.compile(
            "^(?:(?<prefix>.+)[\\\\/])?(?<layer>.+)[\\\\/](?<gridSetId>.+)[\\\\/](?<format>.+)[\\\\/](?<parametersId>.+)[\\\\/](?<z>\\d+)[\\\\/](?<x>\\d+)[\\\\/](?<y>\\d+)\\.(?<extension>.+)$");

    public static final String PREFIX_GROUP_POS = "prefix";
    public static final String LAYER_ID_GROUP_POS = "layer";
    public static final String GRID_SET_ID_GROUP_POS = "gridSetId";
    public static final String TYPE_GROUP_POS = "format";
    public static final String PARAMETERS_ID_GROUP_POS = "parametersId";
    public static final String X_GROUP_POS = "x";
    public static final String Y_GROUP_POS = "y";
    public static final String Z_GROUP_POS = "z";
    public static final String EXTENSION_GROUP_POS = "extension";

    final String prefix;
    final String layerId;
    final String gridSetId;
    final String format;
    final String parametersSha;
    final long x;
    final long y;
    final long z;
    final Long version;
    final String extension;
    TileObject tile;
    long size;

    public DeleteTileInfo(
            String prefix,
            String layerId,
            String gridSetId,
            String format,
            String parametersSha,
            long x,
            long y,
            long z,
            Long version,
            TileObject tile,
            String extension) {

        this.prefix = prefix;
        this.layerId = layerId;
        this.gridSetId = gridSetId;
        this.format = format;
        this.x = x;
        this.y = y;
        this.z = z;
        this.parametersSha = parametersSha;
        this.version = version;
        this.tile = tile;
        this.extension = extension;
    }

    public TileObject getTile() {
        return tile;
    }

    public void setTile(TileObject tile) {
        this.tile = tile;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getSize() {
        return size;
    }

    // Key format, comprised of
    // {@code <prefix>/<layer name>/<gridset id>/<format id>/<parametershash>/<z>/<x>/<y>.<extension>}
    public String objectPath() {
        return toFullPath(prefix, layerId, gridSetId, format, parametersSha, z, x, y, format);
    }

    public static DeleteTileInfo fromObjectPath(String objectKey) {
        Matcher matcher = keyRegex.matcher(objectKey);
        checkArgument(matcher.matches());

        return new DeleteTileInfo(
                matcher.group(PREFIX_GROUP_POS),
                matcher.group(LAYER_ID_GROUP_POS),
                matcher.group(GRID_SET_ID_GROUP_POS),
                matcher.group(TYPE_GROUP_POS),
                matcher.group(PARAMETERS_ID_GROUP_POS),
                Long.parseLong(matcher.group(X_GROUP_POS)),
                Long.parseLong(matcher.group(Y_GROUP_POS)),
                Long.parseLong(matcher.group(Z_GROUP_POS)),
                null,
                null,
                matcher.group(EXTENSION_GROUP_POS));
    }

    public static boolean isPathValid(String path) {
        Matcher matcher = keyRegex.matcher(path);
        return matcher.matches();
    }

    public static String toLayerId(String prefix, String layerId) {
        checkNotNull(layerId, "LayerId cannot be null");
        return Stream.of(prefix, layerId).filter(not(Strings::isNullOrEmpty)).collect(Collectors.joining("/")) + "/";
    }

    public static String toGridSet(String prefix, String layerId, String gridSetId) {
        checkNotNull(layerId, "LayerId cannot be null");
        checkNotNull(gridSetId, "GridSetId cannot be null");
        return Stream.of(prefix, layerId, gridSetId)
                        .filter(not(Strings::isNullOrEmpty))
                        .collect(Collectors.joining("/"))
                + "/";
    }

    public static String toParametersId(
            String prefix, String layerId, String gridSetId, String format, String parametersId) {
        checkNotNull(layerId, "LayerId cannot be null");
        checkNotNull(gridSetId, "GridSetId cannot be null");
        checkNotNull(format, "Format cannot be null");
        checkNotNull(parametersId, "ParametersId cannot be null");
        return Stream.of(prefix, layerId, gridSetId, format, parametersId)
                        .filter(not(Strings::isNullOrEmpty))
                        .collect(Collectors.joining("/"))
                + "/";
    }

    public static String toZoomPrefix(
            String prefix, String layerId, String gridSetId, String format, String parametersId, long zoomLevel) {
        checkNotNull(layerId, "LayerId cannot be null");
        checkNotNull(gridSetId, "GridSetId cannot be null");
        checkNotNull(format, "Format cannot be null");
        checkNotNull(parametersId, "ParametersId cannot be null");
        return Stream.of(prefix, layerId, gridSetId, format, parametersId, String.valueOf(zoomLevel))
                        .filter(not(Strings::isNullOrEmpty))
                        .collect(Collectors.joining("/"))
                + "/";
    }

    public String toFullPath() {
        return toFullPath(prefix, layerId, gridSetId, format, parametersSha, z, x, y, format);
    }

    public static String toFullPath(
            String prefix,
            String layerId,
            String gridSetId,
            String format,
            String parametersId,
            long zoomLevel,
            long x,
            long y,
            String extension) {
        checkNotNull(layerId, "LayerId cannot be null");
        checkNotNull(gridSetId, "GridSetId cannot be null");
        checkNotNull(format, "Format cannot be null");
        checkNotNull(parametersId, "ParametersId cannot be null");
        checkNotNull(extension, "Extension cannot be null");
        return Stream.of(
                        prefix,
                        layerId,
                        gridSetId,
                        format,
                        parametersId,
                        String.valueOf(zoomLevel),
                        String.valueOf(x),
                        format("%d.%s", y, extension))
                .filter(Objects::nonNull)
                .collect(Collectors.joining("/"));
    }
}
