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
 * @author Gabriel Roldan, Boundless Spatial Inc, Copyright 2015
 * @author Dana Lambert, Catalyst IT Ltd NZ, Copyright 2020
 */
package org.geowebcache.swift;

import com.google.common.base.Strings;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.SingleValueConverter;
import com.thoughtworks.xstream.converters.basic.BooleanConverter;
import com.thoughtworks.xstream.converters.basic.IntConverter;
import com.thoughtworks.xstream.converters.basic.StringConverter;
import org.geowebcache.GeoWebCacheEnvironment;
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.config.BlobStoreInfo;
import org.geowebcache.config.Info;
import org.geowebcache.config.XMLConfigurationProvider;

/**
 * Reads Swift Blobstore configuration from geowebcache.xml and creates a SwiftBlobStoreInfo object representing this information.
 */
public class SwiftBlobStoreConfigProvider implements XMLConfigurationProvider {

    private static GeoWebCacheEnvironment gwcEnvironment = null;

    private static SingleValueConverter EnvironmentNullableIntConverter =
            new IntConverter() {

                @Override
                public Object fromString(String str) {
                    str = resolveFromEnv(str);
                    if (Strings.isNullOrEmpty(str)) {
                        return null;
                    }
                    return super.fromString(str);
                }
            };

    private static SingleValueConverter EnvironmentNullableBooleanConverter =
            new BooleanConverter() {

                @Override
                public Object fromString(String str) {
                    str = resolveFromEnv(str);
                    if (Strings.isNullOrEmpty(str)) {
                        return null;
                    }
                    return super.fromString(str);
                }
            };

    private static SingleValueConverter EnvironmentStringConverter =
            new StringConverter() {
                @Override
                public Object fromString(String str) {
                    str = resolveFromEnv(str);
                    if (Strings.isNullOrEmpty(str)) {
                        return null;
                    }
                    return str;
                }
            };

    private static String resolveFromEnv(String str) {
        if (gwcEnvironment == null) {
            gwcEnvironment = GeoWebCacheExtensions.bean(GeoWebCacheEnvironment.class);
        }
        if (gwcEnvironment != null
                && str != null
                && GeoWebCacheEnvironment.ALLOW_ENV_PARAMETRIZATION) {
            Object result = gwcEnvironment.resolveValue(str);
            if (result == null) {
                return null;
            }
            return result.toString();
        }
        return str;
    }

    @Override
    public XStream getConfiguredXStream(XStream xs) {
        xs.alias("SwiftBlobStore", SwiftBlobStoreInfo.class);
        xs.registerLocalConverter(
                SwiftBlobStoreInfo.class, "maxConnections", EnvironmentNullableIntConverter);
        xs.registerLocalConverter(
                SwiftBlobStoreInfo.class, "proxyPort", EnvironmentNullableIntConverter);
        xs.registerLocalConverter(
                SwiftBlobStoreInfo.class, "useHTTPS", EnvironmentNullableBooleanConverter);
        xs.registerLocalConverter(
                SwiftBlobStoreInfo.class, "useGzip", EnvironmentNullableBooleanConverter);
        xs.registerLocalConverter(SwiftBlobStoreInfo.class, "bucket", EnvironmentStringConverter);
        xs.registerLocalConverter(
                SwiftBlobStoreInfo.class, "jcloudsProvider", EnvironmentStringConverter);
        xs.registerLocalConverter(
                SwiftBlobStoreInfo.class, "jcloudsRegion", EnvironmentStringConverter);
        xs.registerLocalConverter(
                SwiftBlobStoreInfo.class, "jcloudsKeystoneVersion", EnvironmentStringConverter);
        xs.registerLocalConverter(
                SwiftBlobStoreInfo.class, "jcloudsKeystoneScope", EnvironmentStringConverter);
        xs.registerLocalConverter(
                SwiftBlobStoreInfo.class, "jcloudsIdentity", EnvironmentStringConverter);
        xs.registerLocalConverter(
                SwiftBlobStoreInfo.class, "jcloudsCredential", EnvironmentStringConverter);
        xs.registerLocalConverter(SwiftBlobStoreInfo.class, "prefix", EnvironmentStringConverter);
        xs.registerLocalConverter(
                SwiftBlobStoreInfo.class, "proxyHost", EnvironmentStringConverter);
        xs.registerLocalConverter(
                BlobStoreInfo.class, "enabled", EnvironmentNullableBooleanConverter);
        xs.aliasField("id", SwiftBlobStoreInfo.class, "name");
        return xs;
    }

    @Override
    public boolean canSave(Info i) {
        return i instanceof SwiftBlobStoreInfo;
    }
}
