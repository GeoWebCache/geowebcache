package org.geowebcache.s3;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.regex.Pattern;

import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.TileObject;

import com.google.common.base.Throwables;

class TMSKeyBuilder {

    /**
     * Key format, comprised of
     * {@code <prefix>/<layer name>/<gridset id>/<format id>/<parameters hash>/<z>/<x>/<y>.<extension>}
     */
    private static final String TILE_FORMAT = "%s/%s/%s/%s/%s/%d/%d/%d.%s";

    /**
     * Layer prefix format, comprised of {@code <prefix>/<layer name>/}
     */
    private static final String LAYER_PREFIX_FORMAT = "%s/%s/";

    /**
     * layer + gridset prefix format, comprised of {@code <prefix>/<layer name>/<gridset id>/}
     */
    private static final String GRIDSET_PREFIX_FORMAT = "%s/%s/%s/";

    public static final String LAYER_METADATA_OBJECT_NAME = "metadata.properties";

    private static final String LAYER_METADATA_FORMAT = "%s/%s/" + LAYER_METADATA_OBJECT_NAME;

    private String prefix;

    public TMSKeyBuilder(final String prefix) {
        this.prefix = prefix;
    }

    public String forTile(TileObject obj) {
        checkNotNull(obj.getLayerName());
        checkNotNull(obj.getGridSetId());
        checkNotNull(obj.getBlobFormat());
        checkNotNull(obj.getParameters());
        checkNotNull(obj.getXYZ());

        String layer = obj.getLayerName();
        String gridset = obj.getGridSetId();
        String shortFormat;
        String paramsHash = obj.getParametersId();
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

        String key = String.format(TILE_FORMAT, prefix, layer, gridset, shortFormat, paramsHash, z,
                x, y, extension);
        return key;
    }

    public String forLayer(final String layerName) {
        return String.format(LAYER_PREFIX_FORMAT, prefix, layerName);
    }

    public String forGridset(final String layerName, final String gridsetId) {
        return String.format(GRIDSET_PREFIX_FORMAT, prefix, layerName, gridsetId);
    }

    public String layerMetadata(final String layerName) {
        return String.format(LAYER_METADATA_FORMAT, prefix, layerName);
    }
}
