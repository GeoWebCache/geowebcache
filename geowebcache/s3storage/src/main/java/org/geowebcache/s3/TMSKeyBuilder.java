/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Gabriel Roldan, Boundless Spatial Inc, Copyright 2015
 */
package org.geowebcache.s3;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.filter.parameters.ParametersUtils;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;

import com.google.common.base.Throwables;

final class TMSKeyBuilder {

    /**
     * Key format, comprised of
     * {@code <prefix>/<layer name>/<gridset id>/<format id>/<parameters hash>/<z>/<x>/<y>.<extension>}
     */
    private static final String TILE_FORMAT = "%s/%s/%s/%s/%s/%d/%d/%d.%s";

    /**
     * Coordinates prefix: {@code <prefix>/<layer name>/<gridset id>/<format id>/<parameters hash>/}
     */
    private static final String COORDINATES_PREFIX_FORMAT = "%s/%s/%s/%s/%s/";

    /**
     * Layer prefix format, comprised of {@code <prefix>/<layer name>/}
     */
    private static final String LAYER_PREFIX_FORMAT = "%s/%s/";

    /**
     * layer + gridset prefix format, comprised of {@code <prefix>/<layer name>/<gridset id>/}
     */
    private static final String GRIDSET_PREFIX_FORMAT = "%s/%s/%s/";

    public static final String LAYER_METADATA_OBJECT_NAME = "metadata.properties";
    public static final String PARAMETERS_METADATA_OBJECT_PREFIX = "parameters-";
    public static final String PARAMETERS_METADATA_OBJECT_NAME = 
            PARAMETERS_METADATA_OBJECT_PREFIX+"%s.properties";

    private static final String LAYER_METADATA_FORMAT = "%s/%s/" + 
            LAYER_METADATA_OBJECT_NAME;
    private static final String PARAMETERS_METADATA_FORMAT = "%s/%s/" + 
            PARAMETERS_METADATA_OBJECT_NAME;
    private static final String PARAMETERS_METADATA_PREFIX_FORMAT = 
            "%s/%s/" + PARAMETERS_METADATA_OBJECT_PREFIX;

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
            throw Throwables.propagate(e);
        }
        return layer.getId();
    }
    public Set<String> layerGridsets(String layerName) {
        TileLayer layer;
        try {
            layer = layers.getTileLayer(layerName);
        } catch (GeoWebCacheException e) {
            throw Throwables.propagate(e);
        }
        return layer.getGridSubsets();
    }
    public Set<String> layerFormats(String layerName) {
        TileLayer layer;
        try {
            layer = layers.getTileLayer(layerName);
        } catch (GeoWebCacheException e) {
            throw Throwables.propagate(e);
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
            shortFormat = mimeType.getFileExtension();// png, png8, png24, etc
            extension = mimeType.getInternalName();// png, jpeg, etc
        } catch (MimeException e) {
            throw Throwables.propagate(e);
        }

        String key = String.format(TILE_FORMAT, prefix, layer, gridset, shortFormat, parametersId,
                z, x, y, extension);
        return key;
    }

    public String forLayer(final String layerName) {
        String layerId = layerId(layerName);
        return String.format(LAYER_PREFIX_FORMAT, prefix, layerId);
    }

    public String forGridset(final String layerName, final String gridsetId) {
        String layerId = layerId(layerName);
        return String.format(GRIDSET_PREFIX_FORMAT, prefix, layerId, gridsetId);
    }
    
    public Set<String> forParameters(final String layerName, final String parametersId) {
        String layerId = layerId(layerName);
        return layerGridsets(layerName).stream()
            .flatMap(gridsetId -> layerFormats(layerName).stream()
                .map(format -> 
                    String.format(COORDINATES_PREFIX_FORMAT, prefix, 
                            layerId, gridsetId, format, parametersId)))
            .collect(Collectors.toSet());
    }

    public String layerMetadata(final String layerName) {
        String layerId = layerId(layerName);
        return String.format(LAYER_METADATA_FORMAT, prefix, layerId);
    }
    public String parametersMetadata(final String layerName, final String parametersId) {
        String layerId = layerId(layerName);
        return String.format(PARAMETERS_METADATA_FORMAT, prefix, layerId, parametersId);
    }
    public String parametersMetadataPrefix(final String layerName) {
        String layerId = layerId(layerName);
        return String.format(PARAMETERS_METADATA_PREFIX_FORMAT, prefix, layerId);
    }

    /**
     * @return the key prefix up to the coordinates (i.e.
     *         {@code "<prefix>/<layer>/<gridset>/<format>/<parametersId>"})
     */
    public String coordinatesPrefix(TileRange obj) {
        checkNotNull(obj.getLayerName());
        checkNotNull(obj.getGridSetId());
        checkNotNull(obj.getMimeType());

        String layer = layerId(obj.getLayerName());
        String gridset = obj.getGridSetId();
        MimeType mimeType = obj.getMimeType();

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
        shortFormat = mimeType.getFileExtension();// png, png8, png24, etc

        String key = String.format(COORDINATES_PREFIX_FORMAT, prefix, layer, gridset, shortFormat,
                parametersId);
        return key;
    }

    public String pendingDeletes() {
        return String.format("%s/%s", prefix, "_pending_deletes.properties");
    }
}
