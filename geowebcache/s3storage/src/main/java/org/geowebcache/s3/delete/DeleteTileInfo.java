package org.geowebcache.s3.delete;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.geowebcache.storage.TileObject;

public class DeleteTileInfo {
    public static final Pattern keyRegex = Pattern.compile("(.*)/(.*)/(.*)/(.*)/(.*)/(\\d+)/(\\d+)/(\\d+)\\..*");

    public static final int PREFIX_GROUP_POS = 1;
    public static final int LAYER_ID_GROUP_POS = 2;
    public static final int GRID_SET_ID_GROUP_POS = 3;
    public static final int TYPE_GROUP_POS = 4;
    public static final int PARAMETERS_ID_GROUP_POS = 5;
    public static final int X_GROUP_POS = 7;
    public static final int Y_GROUP_POS = 8;
    public static final int Z_GROUP_POS = 6;

    final String prefix;
    final String layerId;
    final String gridSetId;
    final String format;
    final String parametersSha;
    final long x;
    final long y;
    final long z;
    final Long version;
    TileObject tile;
    long size;
    Map<String, String> parameters;

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
            TileObject tile) {
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

    public long[] XYZ() {
        return new long[] {x, y, z};
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
                null);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private String prefix;
        private String layerId;
        private String gridSetId;
        private String format;
        private String parametersSha;
        private Long x;
        private Long y;
        private Long z;
        private Long version;

        public Builder withPrefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder withLayerId(String layerId) {
            this.layerId = layerId;
            return this;
        }

        public Builder withGridSetId(String gridSetId) {
            this.gridSetId = gridSetId;
            return this;
        }

        public Builder withFormat(String format) {
            this.format = format;
            return this;
        }

        public Builder withParametersId(String parametersSha) {
            this.parametersSha = parametersSha;
            return this;
        }

        public Builder withX(long x) {
            this.x = x;
            return this;
        }

        public Builder withY(long y) {
            this.y = y;
            return this;
        }

        public Builder withZ(long z) {
            this.z = z;
            return this;
        }

        DeleteTileInfo build() {
            checkNotNull(prefix, "Prefix cannot be null");
            checkNotNull(layerId, "LayerId cannot be null");
            checkNotNull(gridSetId, "GridSetId cannot be null");
            checkNotNull(format, "Format cannot be null");
            checkNotNull(parametersSha, "ParametersSha cannot be null");
            checkNotNull(x, "X cannot be null");
            checkNotNull(y, "Y cannot be null");
            checkNotNull(z, "Z cannot be null");

            return new DeleteTileInfo(prefix, layerId, gridSetId, format, parametersSha, x, y, z, version, null);
        }
    }

    public static boolean isPathValid(String path, String prefix) {
        List<String> results = new ArrayList<>(List.of(path.split("/")));
        if (path.contains("//")) {
            return false;
        }
        if (path.endsWith(".")) {
            return false;
        }

        if (Objects.equals(results.get(0), prefix)) {
            results.remove(0);
        }

        if (results.isEmpty()) {
            return false;
        }

        // Check all the token are valid
        return IntStream.range(0, results.size())
                        .mapToObj(index -> {
                            if (index == X_GROUP_POS - 2 || index == Z_GROUP_POS - 2) {
                                return isALong(results.get(index));
                            }

                            if (index == Y_GROUP_POS - 2) {
                                String[] lastPathPart = results.get(index).split("\\.");
                                if (lastPathPart.length == 1 && isALong(lastPathPart[0])) {
                                    return true;
                                }
                                return lastPathPart.length == 2
                                        && isALong(lastPathPart[0])
                                        && !lastPathPart[1].isBlank();
                            }

                            return !results.get(index).isEmpty();
                        })
                        .filter(x -> x)
                        .count()
                == results.size();
    }

    private static Boolean isALong(String test) {
        try {
            Long.parseLong(test);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static String toLayerId(String prefix, String layerId) {
        checkNotNull(layerId, "LayerId cannot be null");
        return Stream.of(prefix, layerId).filter(Objects::nonNull).collect(Collectors.joining("/")) + "/";
    }

    public static String toGridSet(String prefix, String layerId, String gridSetId) {
        checkNotNull(layerId, "LayerId cannot be null");
        checkNotNull(gridSetId, "GridSetId cannot be null");
        return Stream.of(prefix, layerId, gridSetId).filter(Objects::nonNull).collect(Collectors.joining("/"));
    }

    public static String toFormat(String prefix, String layerId, String gridSetId, String format) {
        checkNotNull(layerId, "LayerId cannot be null");
        checkNotNull(gridSetId, "GridSetId cannot be null");
        checkNotNull(format, "Format cannot be null");
        return Stream.of(prefix, layerId, gridSetId, format)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("/"));
    }

    public static String toParametersId(
            String prefix, String layerId, String gridSetId, String format, String parametersId) {
        checkNotNull(layerId, "LayerId cannot be null");
        checkNotNull(gridSetId, "GridSetId cannot be null");
        checkNotNull(format, "Format cannot be null");
        checkNotNull(parametersId, "ParametersId cannot be null");
        return Stream.of(prefix, layerId, gridSetId, format, parametersId)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("/"));
    }

    public static String toZoomPrefix(
            String prefix, String layerId, String gridSetId, String format, String parametersId, long zoomLevel) {
        checkNotNull(layerId, "LayerId cannot be null");
        checkNotNull(gridSetId, "GridSetId cannot be null");
        checkNotNull(format, "Format cannot be null");
        checkNotNull(parametersId, "ParametersId cannot be null");
        return Stream.of(prefix, layerId, gridSetId, format, parametersId, String.valueOf(zoomLevel))
                .filter(Objects::nonNull)
                .collect(Collectors.joining("/"));
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
