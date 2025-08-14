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
 * @author Nuno Oliveira, GeoSolutions S.A.S., Copyright 2016
 */
package org.geowebcache.sqlite;

import com.google.errorprone.annotations.FormatMethod;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.geotools.util.logging.Logging;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;

/** Some utilities objects and functions used internally. */
final class Utils {

    private static Logger LOGGER = Logging.getLogger(Utils.class.getName());

    // if we get the windows path separator we need to escape it for regex use
    static final String REGEX_FILE_SEPARATOR = File.separator.equals("\\") ? "\\\\" : File.separator;

    private Utils() {}

    static final class Tuple<T, U> {

        final T first;
        final U second;

        private Tuple(T first, U second) {
            this.first = first;
            this.second = second;
        }

        static <R, S> Tuple<R, S> tuple(R first, S second) {
            return new Tuple<>(first, second);
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (object == null || getClass() != object.getClass()) return false;
            Tuple<?, ?> tuple = (Tuple<?, ?>) object;
            return Objects.equals(first, tuple.first) && Objects.equals(second, tuple.second);
        }

        @Override
        public int hashCode() {
            return Objects.hash(first, second);
        }

        @Override
        public String toString() {
            return "Tuple{" + "first=" + first + ", second=" + second + '}';
        }
    }

    @SafeVarargs
    static <K, V> Map<K, V> tuplesToMap(Tuple<K, V>... tuples) {
        Map<K, V> map = new HashMap<>();
        for (Tuple<K, V> tuple : tuples) {
            map.put(tuple.first, tuple.second);
        }
        return map;
    }

    @FormatMethod
    static void check(boolean condition, String message, Object... arguments) {
        if (!condition) {
            throw exception(message, arguments);
        }
    }

    @FormatMethod
    static RuntimeException exception(String message, Object... arguments) {
        String finalMessage = message.formatted(arguments);
        LOGGER.severe(finalMessage);
        return new RuntimeException(finalMessage);
    }

    @FormatMethod
    static RuntimeException exception(Exception exception, String message, Object... arguments) {
        String finalMessage = message.formatted(arguments);
        LOGGER.severe(finalMessage);
        return new RuntimeException(finalMessage, exception);
    }

    static byte[] resourceToByteArray(Resource resource) {
        try {
            return IOUtils.toByteArray(resource.getInputStream());
        } catch (Exception exception) {
            throw exception(exception, "Error converting resource to byte array.");
        }
    }

    static Resource byteArrayToResource(byte[] data) {
        return new ByteArrayResource(data);
    }

    static String buildPath(String... pathParts) {
        return StringUtils.join(pathParts, File.separator);
    }

    static void createFileParents(File file) {
        File parentFile = file.getParentFile();
        if (parentFile != null) {
            parentFile.mkdirs();
        }
    }
}
