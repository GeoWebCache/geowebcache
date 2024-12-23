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
 * @author Kevin Smith, Boundless, 2017
 */
package org.geowebcache.filter.parameters;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.codec.digest.DigestUtils;

public class ParametersUtils {

    /**
     * This should be treated as an opaque Identifier and should not be parsed, it is used to to maintain compatibility
     * with old caches. For any other uses, {@link #getKvp(Map)} is preferred as it uses safe escaping of values.
     */
    public static String getLegacyParametersKvp(Map<String, String> parameters) {
        StringBuilder sb = new StringBuilder();
        SortedMap<String, String> sorted = new TreeMap<>(parameters);
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if (sb.length() == 0) {
                sb.append("?");
            } else {
                sb.append("&");
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        String paramtersKvp = sb.toString();
        return paramtersKvp;
    }

    private static String encUTF8(String s) {
        try {
            // This would be much nicer if URLEncoder.encode took a charset object instead of a
            // string
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Standard encoding UTF-8 is missing");
        }
    }

    private static String decUTF8(String s) {
        try {
            // This would be much nicer if URLDecoder.decode took a charset object instead of a
            // string
            return URLDecoder.decode(s, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Standard encoding UTF-8 is missing");
        }
    }

    /**
     * Create a comparator that compares the results of a function applied to both its arguments.
     *
     * @param derivation A function taking a T and returning a Comparable
     * @return A comparator that compares the result of derivation applied to both arguments.
     */
    static <T, U extends Comparable<U>> Comparator<T> derivedComparator(Function<T, U> derivation) {
        return (t1, t2) -> derivation.apply(t1).compareTo(derivation.apply(t2));
    }

    /** Turns the parameter list into a sorted KVP string */
    public static String getKvp(Map<String, String> parameters) {
        return parameters.entrySet().stream()
                .sorted(derivedComparator(Entry::getKey))
                .map(e -> String.join("=", encUTF8(e.getKey()), encUTF8(e.getValue())))
                .collect(Collectors.joining("&"));
    }

    /**
     * Turns the a sorted KVP string into a parameter map.
     *
     * <p>This should only be used for parsing strings created by {@link #getKvp(Map)} not for parsing raw query strings
     */
    public static Map<String, String> getMap(String kvp) {
        return Arrays.stream(kvp.split("&"))
                .filter(((Predicate<String>) String::isEmpty).negate())
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(p -> decUTF8(p[0]), p -> decUTF8(p[1])));
    }

    /** Returns the parameters identifier for the given parameters map */
    public static String getId(Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return null;
        }
        String parametersKvp = getLegacyParametersKvp(parameters);
        return ParametersUtils.buildKey(parametersKvp);
    }

    public static String buildKey(String parametersKvp) {
        return DigestUtils.sha1Hex(parametersKvp);
    }
}
