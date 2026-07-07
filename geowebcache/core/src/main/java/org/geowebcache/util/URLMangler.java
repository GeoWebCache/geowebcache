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

import java.util.Map;

/** Hook allowing custom URL generation and mangling. */
public interface URLMangler {

    enum URLType {
        EXTERNAL,
        RESOURCE,
        SERVICE
    }

    /**
     * Callback that can change the base URL, path, or query parameter map before URL serialization.
     *
     * @param baseURL mutable base URL buffer containing host, port, and application base
     * @param path mutable path buffer after the application name
     * @param kvp mutable GET request parameters, which may be enriched or modified
     * @param type URL type for consideration during mangling
     */
    void mangleURL(StringBuilder baseURL, StringBuilder path, Map<String, String> kvp, URLType type);
}
