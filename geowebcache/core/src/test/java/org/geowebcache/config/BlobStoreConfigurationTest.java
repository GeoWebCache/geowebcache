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
 * Copyright 2018
 *
 */
package org.geowebcache.config;


import java.util.Collection;
import java.util.Optional;
import java.util.Set;

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
