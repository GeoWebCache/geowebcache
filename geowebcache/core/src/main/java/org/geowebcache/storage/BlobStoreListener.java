package org.geowebcache.storage;

public interface BlobStoreListener {

    void tileStored(String layerName, String gridSetId, String blobFormat, String parameters,
            long x, long y, int z, long blobSize);

    void tileDeleted(String layerName, String gridSetId, String blobFormat, String parameters,
            long x, long y, int z, long blobSize);

    void layerDeleted(String layerName);

}
