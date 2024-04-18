/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Andrea Aime, GeoSolutions, Copyright 2019
 */
package org.geowebcache.util;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Strings;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.filter.parameters.ParametersUtils;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;

/** Helper allowing to build TMS paths, commonly used in blob storage */
public final class TMSKeyBuilder {

    private static final String DELIMITER = "/";

    public static final String LAYER_METADATA_OBJECT_NAME = "metadata.properties";
    public static final String PARAMETERS_METADATA_OBJECT_PREFIX = "parameters-";
    public static final String PARAMETERS_METADATA_OBJECT_SUFFIX = ".properties";
    public static final String PENDING_DELETES = "_pending_deletes.properties";

    private String prefix;

    private TileLayerDispatcher layers;

    public TMSKeyBuilder(final String prefix, TileLayerDispatcher layers) {
        this.prefix = prefix;
        this.layers = layers;
    }

    public String layerId(String layerName) {
        TileLayer layer;
        try {
            layer = layers.getTileLayer(layerName);
        } catch (GeoWebCacheException e) {
            throw new RuntimeException(e);
        }
        return layer.getId();
    }

    public Set<String> layerGridsets(String layerName) {
        TileLayer layer;
        try {
            layer = layers.getTileLayer(layerName);
        } catch (GeoWebCacheException e) {
            throw new RuntimeException(e);
        }
        return layer.getGridSubsets();
    }

    public Set<String> layerFormats(String layerName) {
        TileLayer layer;
        try {
            layer = layers.getTileLayer(layerName);
        } catch (GeoWebCacheException e) {
            throw new RuntimeException(e);
        }
        return layer.getMimeTypes().stream()
                .map(MimeType::getFileExtension)
                .collect(Collectors.toSet());
    }

    public String forTile(TileObject obj) {
        checkNotNull(obj.getLayerName());
        checkNotNull(obj.getGridSetId());
        checkNotNull(obj.getBlobFormat());
        checkNotNull(obj.getXYZ());

        String layer = layerId(obj.getLayerName());
        String gridset = obj.getGridSetId();
        String shortFormat;
        String parametersId = obj.getParametersId();
        if (parametersId == null) {
            Map<String, String> parameters = obj.getParameters();
            parametersId = ParametersUtils.getId(parameters);
            if (parametersId == null) {
                parametersId = "default";
            } else {
                obj.setParametersId(parametersId);
            }
        }
        Long x = Long.valueOf(obj.getXYZ()[0]);
        Long y = Long.valueOf(obj.getXYZ()[1]);
        Long z = Long.valueOf(obj.getXYZ()[2]);
        String extension;
        try {
            String format = obj.getBlobFormat();
            MimeType mimeType = MimeType.createFromFormat(format);
            shortFormat = mimeType.getFileExtension(); // png, png8, png24, etc
            extension = mimeType.getInternalName(); // png, jpeg, etc
        } catch (MimeException e) {
            throw new RuntimeException(e);
        }

        // Key format, comprised of
        // {@code <prefix>/<layer name>/<gridset id>/<format id>/<parameters
        // hash>/<z>/<x>/<y>.<extension>}
        String key =
                join(
                        false,
                        prefix,
                        layer,
                        gridset,
                        shortFormat,
                        parametersId,
                        z,
                        x,
                        y + "." + extension);
        return key;
    }

    public String forLocation(String prefix, long[] loc, MimeType mime) {
        Long x = loc[0];
        Long y = loc[1];
        Long z = loc[2];
        String extension = mime.getInternalName();

        return join(false, prefix, z, x, y + "." + extension);
    }

    public String forLayer(final String layerName) {
        String layerId = layerId(layerName);
        // Layer prefix format, comprised of {@code <prefix>/<layer name>/}
        return join(true, prefix, layerId);
    }

    public String forGridset(final String layerName, final String gridsetId) {
        String layerId = layerId(layerName);
        // Layer prefix format, comprised of {@code <prefix>/<layer name>/}
        return join(true, prefix, layerId, gridsetId);
    }

    public Set<String> forParameters(final String layerName, final String parametersId) {
        String layerId = layerId(layerName);
        // Coordinates prefix: {@code <prefix>/<layer name>/<gridset id>/<format id>/<parameters
        // hash>/}
        Set<String> set = new HashSet<>();
        for (String gridsetId : layerGridsets(layerName)) {
            for (String format : layerFormats(layerName)) {
                String join = join(true, prefix, layerId, gridsetId, format, parametersId);
                set.add(join);
            }
        }
        return set;
    }

    public String layerMetadata(final String layerName) {
        String layerId = layerId(layerName);
        return join(false, prefix, layerId, LAYER_METADATA_OBJECT_NAME);
    }

    public String storeMetadata() {
        return join(false, prefix, LAYER_METADATA_OBJECT_NAME);
    }

    public String parametersMetadata(final String layerName, final String parametersId) {
        String layerId = layerId(layerName);
        return join(
                false,
                prefix,
                layerId,
                PARAMETERS_METADATA_OBJECT_PREFIX
                        + parametersId
                        + PARAMETERS_METADATA_OBJECT_SUFFIX);
    }

    public String parametersMetadataPrefix(final String layerName) {
        String layerId = layerId(layerName);
        return join(false, prefix, layerId, PARAMETERS_METADATA_OBJECT_PREFIX);
    }

    /**
     * @return the key prefix up to the coordinates (i.e. {@code
     *     "<prefix>/<layer>/<gridset>/<format>/<parametersId>"})
     */
    public String coordinatesPrefix(TileRange obj, boolean endWithSlash) {
        checkNotNull(obj.getLayerName());
        checkNotNull(obj.getGridSetId());
        checkNotNull(obj.getMimeType());

        String layer = layerId(obj.getLayerName());
        String gridset = obj.getGridSetId();
        MimeType mimeType = obj.getMimeType();

        String parametersId = obj.getParametersId();
        if (parametersId == null) {
            Map<String, String> parameters = obj.getParameters();
            parametersId = ParametersUtils.getId(parameters);
            if (parametersId == null) {
                parametersId = "default";
            } else {
                obj.setParametersId(parametersId);
            }
        }
        String shortFormat = mimeType.getFileExtension(); // png, png8, png24, etc

        String key = join(endWithSlash, prefix, layer, gridset, shortFormat, parametersId);
        return key;
    }

    public String pendingDeletes() {
        if (!Strings.isNullOrEmpty(prefix)) return String.format("%s/%s", prefix, PENDING_DELETES);
        else return PENDING_DELETES;
    }

    private static String join(boolean closing, Object... elements) {
        StringJoiner joiner = new StringJoiner(DELIMITER);
        for (Object o : elements) {
            String s = o == null ? null : o.toString();
            if (!Strings.isNullOrEmpty(s)) {
                joiner.add(s);
            }
        }
        if (closing) {
            joiner.add("");
        }
        return joiner.toString();
    }
}
