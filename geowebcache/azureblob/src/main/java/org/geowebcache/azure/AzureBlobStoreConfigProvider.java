/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Andrea Aime, GeoSolutions, Copyright 2019
 */
package org.geowebcache.azure;

import com.thoughtworks.xstream.XStream;
import org.geowebcache.config.Info;
import org.geowebcache.config.XMLConfigurationProvider;

public class AzureBlobStoreConfigProvider implements XMLConfigurationProvider {

    @Override
    public XStream getConfiguredXStream(XStream xs) {
        Class<AzureBlobStoreInfo> clazz = AzureBlobStoreInfo.class;
        xs.alias("AzureBlobStore", clazz);
        xs.aliasField("id", clazz, "name");
        return xs;
    }

    @Override
    public boolean canSave(Info i) {
        return i instanceof AzureBlobStoreInfo;
    }
}
