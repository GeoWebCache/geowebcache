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
 * @author Fernando Mino, GeoSolutions S.A.S., Copyright 2026
 */
package org.geowebcache.service.wmts;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds the WMTS URLs needed while generating capabilities and related backlinks.
 *
 * <p>The service and REST endpoints are carried separately so their query parameters can remain mutable until the final
 * URL serialization step. This avoids injecting propagated parameters into the path segment of template URLs and lets
 * the callers append them only once the full path has already been resolved.
 *
 * @param serviceBaseUrl base URL for the KVP service endpoint
 * @param serviceQueryParameters propagated query parameters to append to the service endpoint
 * @param restBaseUrl base URL for the REST endpoint
 * @param restQueryParameters propagated query parameters to append to the REST endpoint
 */
public record WMTSUrls(
        String serviceBaseUrl,
        Map<String, String> serviceQueryParameters,
        String restBaseUrl,
        Map<String, String> restQueryParameters) {

    public WMTSUrls {
        serviceQueryParameters = serviceQueryParameters == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(serviceQueryParameters));
        restQueryParameters = restQueryParameters == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(restQueryParameters));
    }
}
