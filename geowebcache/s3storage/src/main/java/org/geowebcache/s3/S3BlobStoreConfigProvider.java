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
 * @author Gabriel Roldan, Boundless Spatial Inc, Copyright 2015
 */
package org.geowebcache.s3;

import org.geowebcache.GeoWebCacheEnvironment;
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.config.BlobStoreConfig;
import org.geowebcache.config.XMLConfigurationProvider;

import com.google.common.base.Strings;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.SingleValueConverter;
import com.thoughtworks.xstream.converters.basic.BooleanConverter;
import com.thoughtworks.xstream.converters.basic.IntConverter;
import com.thoughtworks.xstream.converters.basic.StringConverter;

public class S3BlobStoreConfigProvider implements XMLConfigurationProvider {

    private static GeoWebCacheEnvironment gwcEnvironment = null;

    private static SingleValueConverter NullableIntConverter = new IntConverter() {

        @Override
        public Object fromString(String str) {
            str = resolveFromEnv(str);
            if (Strings.isNullOrEmpty(str)) {
                return null;
            }
            return super.fromString(str);
        }
    };

    private static SingleValueConverter NullableBooleanConverter = new BooleanConverter() {

        @Override
        public Object fromString(String str) {
            str = resolveFromEnv(str);
            if (Strings.isNullOrEmpty(str)) {
                return null;
            }
            return super.fromString(str);
        }
    };

    private static SingleValueConverter NullableStringConverter = new StringConverter() {
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
        if (gwcEnvironment != null && str != null
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
        xs.alias("S3BlobStore", S3BlobStoreConfig.class);
        xs.registerLocalConverter(S3BlobStoreConfig.class, "maxConnections", NullableIntConverter);
        xs.registerLocalConverter(S3BlobStoreConfig.class, "proxyPort", NullableIntConverter);
        xs.registerLocalConverter(S3BlobStoreConfig.class, "useHTTPS", NullableBooleanConverter);
        xs.registerLocalConverter(S3BlobStoreConfig.class, "useGzip", NullableBooleanConverter);
        xs.registerLocalConverter(S3BlobStoreConfig.class, "bucket", NullableStringConverter);
        xs.registerLocalConverter(S3BlobStoreConfig.class, "awsAccessKey", NullableStringConverter);
        xs.registerLocalConverter(S3BlobStoreConfig.class, "awsSecretKey", NullableStringConverter);
        xs.registerLocalConverter(S3BlobStoreConfig.class, "prefix", NullableStringConverter);
        xs.registerLocalConverter(S3BlobStoreConfig.class, "proxyHost", NullableStringConverter);
        xs.registerLocalConverter(BlobStoreConfig.class, "enabled", NullableBooleanConverter);
        return xs;
    }

}
