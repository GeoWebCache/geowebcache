package org.geowebcache.config;


import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 *
 */
public abstract class BlobStoreConfigurationTest extends ConfigurationTest<BlobStoreInfo, BlobStoreConfiguration>{

    @Override
    protected void addInfo(BlobStoreConfiguration config, BlobStoreInfo info) throws Exception {
        config.addBlobStore(info);
    }

    @Override
    protected Optional<BlobStoreInfo> getInfo(BlobStoreConfiguration config, String name) throws Exception {
        return config.getBlobStore(name);
    }

    @Override
    protected Collection<? extends BlobStoreInfo> getInfos(BlobStoreConfiguration config) throws Exception {
        return config.getBlobStores();
    }

    @Override
    protected Set<String> getInfoNames(BlobStoreConfiguration config) throws Exception {
        return config.getBlobStoreNames();
    }

    @Override
    protected void removeInfo(BlobStoreConfiguration config, String name) throws Exception {
        config.removeBlobStore(name);
    }

    @Override
    protected void renameInfo(BlobStoreConfiguration config, String oldName, String newName) throws Exception {
        config.renameBlobStore(oldName, newName);
    }

    @Override
    protected void modifyInfo(BlobStoreConfiguration config, BlobStoreInfo info) throws Exception {
        config.modifyBlobStore(info);
    }
}
