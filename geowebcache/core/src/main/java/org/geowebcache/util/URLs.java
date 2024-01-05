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
 * @author Gabriel Roldan, Camptocamp, Copyright 2023
 */
package org.geowebcache.util;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/** Utilities for manipulating and converting to and from {@link URL}s. */
public class URLs {

    private URLs() {
        // private constructor signaling this is a pure-utility class
    }

    /**
     * Creates a {@code URL} object from the {@code String} representation.
     *
     * <p>Prefer this method to {@code new URL(String)}, which is <a
     * href="https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URL.html#constructor-deprecation">deprecated
     * in Java 21</a>.
     *
     * <p>Note a {@link URISyntaxException} will be rethrown as {@link MalformedURLException} to
     * preserve the behavior of code that used to call {@code new URL(String)} and expected either a
     * {@code MalformedURLException} or its super type {@code java.io.IOException}.
     *
     * @param url a URL string to build a {@link URL} from
     * @return the URL built from the argument
     * @throws MalformedURLException if {code}url{code} is not a valid {@link URI} nor a valid
     *     {@link URL} as per {@link URI#toURL()}
     */
    public static URL of(String url) throws MalformedURLException {
        try {
            return new URI(url).toURL();
        } catch (URISyntaxException orig) {
            MalformedURLException e = new MalformedURLException(orig.getMessage());
            e.initCause(orig);
            throw e;
        }
    }
}
