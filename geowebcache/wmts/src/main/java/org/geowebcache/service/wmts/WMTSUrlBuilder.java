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
 * @author Fernando Mino, GeoSolutions, Copyright 2026
 */
package org.geowebcache.service.wmts;

import org.geowebcache.util.URLMangler;
import org.geowebcache.util.URLManglerUtils;

/**
 * Holds the base components used to generate WMTS service and REST URLs.
 *
 * <p>The service and REST bases are deliberately kept separate. A request-level {@code base_url} override may apply to
 * service and KVP links while REST resource templates must continue to use the regular servlet base URL. This builder
 * does not store serialized query parameters; each complete URL is assembled through {@link URLManglerUtils} only after
 * its full path template has been supplied.
 *
 * @param serviceBaseUrl base URL used for WMTS service and KVP links, including any request-level override
 * @param restBaseUrl base URL used for WMTS REST resource links
 * @param contextPath servlet context path shared by the service and REST endpoints
 * @param urlMangler callback used to apply proxy, security, or other URL transformations
 */
public record WMTSUrlBuilder(String serviceBaseUrl, String restBaseUrl, String contextPath, URLMangler urlMangler) {

    /**
     * Builds a URL for a WMTS service or KVP endpoint.
     *
     * <p>The supplied path may contain an existing query string or unresolved WMTS template placeholders. Parameters
     * added by the mangler are serialized after that path query string.
     *
     * @param path endpoint path, including any endpoint-specific query string
     * @return the fully assembled and mangled service URL
     */
    public String serviceUrl(String path) {
        return URLManglerUtils.buildURL(
                serviceBaseUrl, contextPath, path, null, urlMangler, URLMangler.URLType.SERVICE);
    }

    /**
     * Builds a URL for a WMTS REST resource or REST capabilities endpoint.
     *
     * @param path REST resource path, including any existing query string or unresolved WMTS placeholders
     * @return the fully assembled and mangled REST URL
     */
    public String restUrl(String path) {
        return URLManglerUtils.buildURL(restBaseUrl, contextPath, path, null, urlMangler, URLMangler.URLType.SERVICE);
    }

    /**
     * Applies URL mangling to an extension-provided absolute operation URL.
     *
     * <p>The custom URL is treated as the complete base URL, so the standard WMTS context path is not added. This
     * preserves extension-specific paths while still allowing registered manglers to add or modify query parameters.
     *
     * @param url extension-provided operation URL
     * @return the fully mangled custom operation URL
     */
    public String customUrl(String url) {
        return URLManglerUtils.buildURL(url, null, null, null, urlMangler, URLMangler.URLType.SERVICE);
    }
}
