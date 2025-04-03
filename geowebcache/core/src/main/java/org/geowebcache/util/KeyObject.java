package org.geowebcache.util;

import com.google.common.base.Preconditions;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;

public class KeyObject {
    public static final Pattern keyRegex = Pattern.compile("(.*)/(.*)/(.*)/(.*)/(.*)/(\\d+)/(\\d+)/(\\d+)\\..*");

    public static final int PREFIX_GROUP_POS = 1;
    public static final int LAYER_ID_GROUP_POS = 2;
    public static final int GRID_SET_ID_GROUP_POS = 3;
    public static final int TYPE_GROUP_POS = 4;
    public static final int PARAMETERS_SHAR_GOROUP_POS = 5;
    public static final int X_GROUP_POS = 6;
    public static final int Y_GROUP_POS = 7;
    public static final int Z_GROUP_POS = 8;

    final String prefix;
    final String layerId;
    final String gridSetId;
    final String format;
    final String parametersSha;
    final long x;
    final long y;
    final long z;
    final Long version;

    private KeyObject(String prefix, String layerId, String gridSetId, String format, String parametersSha, long x, long y, long z, Long version) {
        this.prefix = prefix;
        this.layerId = layerId;
        this.gridSetId = gridSetId;
        this.format = format;
        this.x = x;
        this.y = y;
        this.z = z;
        this.parametersSha = parametersSha;
        this.version = version;
    }

    public long[] XYZ() {
        return new long[]{ x, y, z };
    }

    // Key format, comprised of
    // {@code <prefix>/<layer name>/<gridset id>/<format id>/<parametershash>/<z>/<x>/<y>.<extension>}
    public String objectPath() {
        return String.format("%s/%s/%s/%s/%s/%d/%d/%d.%s", prefix, layerId, gridSetId, format, parametersSha,z,x,y,format);
    }

    public static KeyObject fromObjectPath(String objectKey) {
        Matcher matcher = keyRegex.matcher(objectKey);
        Preconditions.checkArgument(matcher.matches());

        return new KeyObject(
                matcher.group(PREFIX_GROUP_POS),
                matcher.group(LAYER_ID_GROUP_POS),
                matcher.group(GRID_SET_ID_GROUP_POS),
                matcher.group(TYPE_GROUP_POS),
                matcher.group(PARAMETERS_SHAR_GOROUP_POS),
                Long.parseLong(matcher.group(X_GROUP_POS)),
                Long.parseLong(matcher.group(Y_GROUP_POS)),
                Long.parseLong(matcher.group(Z_GROUP_POS)),
                null
        );
    }
    public static KeyObject fromVersionedObjectPath(String objectKey, Long version) {
        Matcher matcher = keyRegex.matcher(objectKey);
        Preconditions.checkArgument(matcher.matches());

        return new KeyObject(
                matcher.group(PREFIX_GROUP_POS),
                matcher.group(LAYER_ID_GROUP_POS),
                matcher.group(GRID_SET_ID_GROUP_POS),
                matcher.group(TYPE_GROUP_POS),
                matcher.group(PARAMETERS_SHAR_GOROUP_POS),
                Long.parseLong(matcher.group(X_GROUP_POS)),
                Long.parseLong(matcher.group(Y_GROUP_POS)),
                Long.parseLong(matcher.group(Z_GROUP_POS)),
                version
        );
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

        public Builder withVersion(long version) {
            this.version = version;
            return this;
        }

        KeyObject build() {
            checkNotNull(prefix, "Prefix cannot be null");
            checkNotNull(layerId, "LayerId cannot be null");
            checkNotNull(gridSetId, "GridSetId cannot be null");
            checkNotNull(format, "Format cannot be null");
            checkNotNull(parametersSha, "ParametersSha cannot be null");
            checkNotNull(x, "X cannot be null");
            checkNotNull(y, "Y cannot be null");
            checkNotNull(z, "Z cannot be null");

            return new KeyObject(
                    prefix,
                    layerId,
                    gridSetId,
                    format,
                    parametersSha,
                    x,
                    y,
                    z,
                    version
            );
        }

        public Builder withoutVersion() {
            this.version = null;
            return this;
        }
    }
}

