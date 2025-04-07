package org.geowebcache.s3.callback;

import org.geowebcache.storage.BlobStoreListener;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class BlobStoreCaptureListener implements BlobStoreListener {
    long tileStoredCount = 0;
    long tileDeletedCount = 0;
    long tileUpdatedCount = 0;
    long layerDeletedCount = 0;
    long layerRenamedCount = 0;
    long gridSetIdDeletedCount = 0;
    long parametersDeletedCount = 0;

    @Override
    public void tileStored(
            String layerName,
            String gridSetId,
            String blobFormat,
            String parametersId,
            long x,
            long y,
            int z,
            long blobSize) {
        checkNotNull(layerName, "LayerName cannot be null");
        checkNotNull(gridSetId, "GridSetId cannot be null");
        checkNotNull(blobFormat, "BlobFormat cannot be null");
        checkNotNull(parametersId, "ParametersId cannot be null");
        checkArgument(blobSize > 0, "BlobSize must be greater than 0");

        tileStoredCount++;
    }

    @Override
    public void tileDeleted(
            String layerName,
            String gridSetId,
            String blobFormat,
            String parametersId,
            long x,
            long y,
            int z,
            long blobSize) {
        checkNotNull(layerName, "LayerName cannot be null");
        checkNotNull(gridSetId, "GridSetId cannot be null");
        checkNotNull(blobFormat, "BlobFormat cannot be null");
        checkNotNull(parametersId, "ParametersId cannot be null");
        checkArgument(blobSize > 0, "BlobSize must be greater than 0");

        tileDeletedCount++;
    }

    @Override
    public void tileUpdated(
            String layerName,
            String gridSetId,
            String blobFormat,
            String parametersId,
            long x,
            long y,
            int z,
            long blobSize,
            long oldSize) {
        checkNotNull(layerName, "LayerName cannot be null");
        checkNotNull(gridSetId, "GridSetId cannot be null");
        checkNotNull(blobFormat, "BlobFormat cannot be null");
        checkNotNull(parametersId, "ParametersId cannot be null");
        checkArgument(blobSize > 0, "BlobSize must be greater than 0");
        checkArgument(oldSize > 0, "OldSize must be greater than 0");
        tileUpdatedCount++;
    }

    @Override
    public void layerDeleted(String layerName) {
        checkNotNull(layerName, "LayerName cannot be null");

        layerDeletedCount++;
    }

    @Override
    public void layerRenamed(String oldLayerName, String newLayerName) {
        checkNotNull(oldLayerName, "oldLayerName cannot be null");
        checkNotNull(newLayerName, "newLayerName cannot be null");
        layerRenamedCount++;
    }

    @Override
    public void gridSubsetDeleted(String layerName, String gridSetId) {
        checkNotNull(layerName, "LayerName cannot be null");
        checkNotNull(gridSetId, "GridSetId cannot be null");
        gridSetIdDeletedCount++;
    }

    @Override
    public void parametersDeleted(String layerName, String parametersId) {
        checkNotNull(layerName, "layerName cannot be null");
        checkNotNull(parametersId, "parametersId cannot be null");
        parametersDeletedCount++;
    }
}
