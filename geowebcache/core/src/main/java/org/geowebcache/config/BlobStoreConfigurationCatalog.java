package org.geowebcache.config;

import java.util.List;

public interface BlobStoreConfigurationCatalog extends BaseConfiguration{

    List<BlobStoreConfig> getBlobStores();

}