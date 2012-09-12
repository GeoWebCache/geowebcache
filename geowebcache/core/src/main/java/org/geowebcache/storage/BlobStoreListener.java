package org.geowebcache.storage;

public interface BlobStoreListener {

    void tileStored(String layerName, String gridSetId, String blobFormat, String parametersId,
            long x, long y, int z, long blobSize);

    void tileDeleted(String layerName, String gridSetId, String blobFormat, String parametersId,
            long x, long y, int z, long blobSize);

    void tileUpdated(String layerName, String gridSetId, String blobFormat, String parametersId,
            long x, long y, int z, long blobSize, long oldSize);

    void layerDeleted(String layerName);

    void layerRenamed(String oldLayerName, String newLayerName);

    void gridSubsetDeleted(String layerName, String gridSetId);

}
