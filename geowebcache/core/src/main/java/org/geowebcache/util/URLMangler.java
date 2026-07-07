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
 * @author Robert Marianski, OpenGeo, 20012
 */
package org.geowebcache.util;

import java.util.Collections;
import java.util.Map;

/**
 * subset copied from org.geoserver.ows.URLMangler
 *
 * <p>This hook allows others to plug in custom url generation.
 */
public interface URLMangler {

    /**
     * Allows for a custom url generation strategy
     *
     * @param baseURL the base url - contains the url up to the domain and port
     * @param contextPath the servlet context path, like /geoserver/gwc
     * @param path the remaining path after the context path
     * @return the full generated url from the pieces
     */
    public String buildURL(String baseURL, String contextPath, String path);

    /**
     * Allows for a custom url generation strategy while also carrying query parameters separately from the path.
     *
     * <p>The default implementation preserves the legacy behavior and ignores the query parameter map. New callers
     * should prefer overriding this method so propagated parameters can remain separated until the final URL is
     * emitted.
     *
     * @param baseURL the base url, contains the url up to the domain and port
     * @param contextPath the servlet context path, like /geoserver/gwc
     * @param path the remaining path after the context path
     * @param queryParameters propagated query parameters to preserve separately from the path
     * @return the generated url (without serialized query parameters) together with the resulting query parameter map,
     *     so implementations return their result instead of mutating the {@code queryParameters} argument
     */
    default UrlAndParams buildURL(
            String baseURL, String contextPath, String path, Map<String, String> queryParameters) {
        return new UrlAndParams(buildURL(baseURL, contextPath, path), queryParameters);
    }

    /**
     * Result of {@link #buildURL(String, String, String, Map)}: the generated URL (without a serialized query string)
     * and the query parameters that should eventually be appended to it.
     */
    record UrlAndParams(String url, Map<String, String> queryParameters) {
        public UrlAndParams {
            queryParameters = queryParameters == null ? Collections.emptyMap() : queryParameters;
        }
    }
}
