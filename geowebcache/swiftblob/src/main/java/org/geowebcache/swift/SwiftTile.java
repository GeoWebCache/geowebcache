package org.geowebcache.swift;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.io.Resource;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.BlobStoreListenerList;
import org.geowebcache.storage.TileObject;
import org.jclouds.io.Payload;
import org.jclouds.io.payloads.BaseMutableContentMetadata;
import org.jclouds.io.payloads.ByteArrayPayload;

public class SwiftTile {
    static final Log log = LogFactory.getLog(SwiftBlobStore.class);

    private final String layerName;
    private final String gridSetId;
    private final String blobFormat;
    private final String parametersId;

    private final long x;
    private final long y;
    private final int z;

    private final long outputLength;

    private boolean existed = false;
    private long oldSize = 0L;

    private final byte[] data;

    public SwiftTile(final TileObject tile) throws IOException {
        this.layerName = tile.getLayerName();
        this.gridSetId = tile.getGridSetId();
        this.blobFormat = tile.getBlobFormat();
        this.parametersId = tile.getParametersId();

        Resource blob = tile.getBlob();
        checkNotNull(blob, "Object Blob must not be null.");
        checkNotNull(this.blobFormat, "Object Blob Format must not be null.");

        long[] xyz = tile.getXYZ();
        this.x = xyz[0];
        this.y = xyz[1];
        this.z = (int) xyz[2];

        try (InputStream stream = blob.getInputStream()) {
            data = ByteStreams.toByteArray(stream);
        }

        this.outputLength = data.length;
    }

    private BaseMutableContentMetadata getMetadata() {
        BaseMutableContentMetadata metadata = new BaseMutableContentMetadata();
        metadata.setContentLength(outputLength);
        try {
            metadata.setContentType(MimeType.createFromFormat(blobFormat).getMimeType());
        } catch (MimeException e) {
            // Do nothing if we cannot determine the mimeType;
            log.warn("Could not determine mimetype for " + toString());
        }
        return metadata;
    }

    public Payload getPayload() {
        Payload payload = new ByteArrayPayload(data);
        payload.setContentMetadata(getMetadata());
        return payload;
    }

    public void setExisted(long oldSize) {
        this.existed = true;
        this.oldSize = oldSize;
    }

    public void notifyListeners(BlobStoreListenerList listeners) {
        boolean hasListeners = !listeners.isEmpty();

        if (hasListeners && existed) {
            listeners.sendTileUpdated(
                    layerName, gridSetId, blobFormat, parametersId, x, y, z, outputLength, oldSize);
        } else if (hasListeners) {
            listeners.sendTileStored(
                    layerName, gridSetId, blobFormat, parametersId, x, y, z, outputLength);
        }
    }

    public String toString() {
        String format = "%s, %s, %s, %s, xyz=%d,%d,%d";
        return String.format(format, layerName, gridSetId, blobFormat, parametersId, x, y, z);
    }
}
