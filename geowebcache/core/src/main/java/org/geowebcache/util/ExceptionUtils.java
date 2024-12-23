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
 * <p>Copyright 2018
 */
package org.geowebcache.util;

import com.google.common.collect.Streams;
import java.util.Arrays;
import java.util.stream.Stream;

public class ExceptionUtils {
    private ExceptionUtils() {}

    /** Returns true if the provided throwable is of the specified class, or if any of those is suppresses is. */
    public static <T extends Throwable> boolean isOrSuppresses(T e, Class<? extends T> klazz) {
        return Streams.concat(Stream.of(e), Arrays.stream(e.getSuppressed())).anyMatch(klazz::isInstance);
    }
}
