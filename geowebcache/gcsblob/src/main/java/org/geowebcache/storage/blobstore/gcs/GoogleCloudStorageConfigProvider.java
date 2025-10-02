/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Gabriel Roldan, Camptocamp, Copyright 2025
 */
package org.geowebcache.storage.blobstore.gcs;

import com.thoughtworks.xstream.XStream;
import org.geowebcache.config.Info;
import org.geowebcache.config.XMLConfigurationProvider;

/**
 * Configures XStream for XML persistence of {@link GoogleCloudStorageBlobStoreInfo} objects.
 *
 * <p>This class sets up the necessary aliases for serializing and deserializing the blob store configuration to and
 * from {@code geowebcache.xml}.
 *
 * @since 1.28
 */
public class GoogleCloudStorageConfigProvider implements XMLConfigurationProvider {

    @Override
    public XStream getConfiguredXStream(XStream xs) {
        Class<GoogleCloudStorageBlobStoreInfo> clazz = GoogleCloudStorageBlobStoreInfo.class;
        xs.alias("GoogleCloudStorageBlobStore", clazz);
        xs.aliasField("id", clazz, "name");
        xs.allowTypes(new Class[] {GoogleCloudStorageBlobStoreInfo.class});
        return xs;
    }

    @Override
    public boolean canSave(Info i) {
        return i instanceof GoogleCloudStorageBlobStoreInfo;
    }
}
