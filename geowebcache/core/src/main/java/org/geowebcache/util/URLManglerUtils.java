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
package org.geowebcache.util;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/** Shared URL assembly for GeoWebCache URL manglers. */
public final class URLManglerUtils {

    private URLManglerUtils() {}

    /**
     * Builds a URL after allowing one mangler to mutate its base, path, and query parameters.
     *
     * <p>Query parameters embedded in {@code path} remain in place; parameters from {@code kvp} are appended after them
     * and before any fragment. The input map is copied so callers retain ownership of their map.
     */
    public static String buildURL(
            String baseURL,
            String contextPath,
            String path,
            Map<String, String> kvp,
            URLMangler urlMangler,
            URLMangler.URLType type) {
        StringBuilder base = new StringBuilder(StringUtils.strip(baseURL, "/"));
        String context = StringUtils.strip(contextPath, "/");
        String remainingPath = StringUtils.stripStart(path, "/");
        boolean trailingSlash = path != null && (path.isEmpty() || path.endsWith("/"));
        StringBuilder pathBuffer = new StringBuilder();
        if (StringUtils.isNotBlank(context)) pathBuffer.append(context);
        if (StringUtils.isNotBlank(remainingPath)) {
            if (!pathBuffer.isEmpty()) pathBuffer.append('/');
            pathBuffer.append(remainingPath);
        }
        Map<String, String> parameters = kvp == null ? new LinkedHashMap<>() : new LinkedHashMap<>(kvp);

        urlMangler.mangleURL(base, pathBuffer, parameters, type);

        String result = base.toString();
        if (!pathBuffer.isEmpty()) {
            result = appendPath(result, pathBuffer.toString());
        }
        if (trailingSlash && !pathBuffer.isEmpty() && !result.endsWith("/")) result += "/";
        return appendQueryParameters(result, parameters);
    }

    private static String appendPath(String base, String path) {
        if (base.endsWith("/") || path.isEmpty()) return base + path;
        return base + "/" + path;
    }

    private static String appendQueryParameters(String url, Map<String, String> parameters) {
        if (parameters.isEmpty()) return url;
        String fragment = "";
        int fragmentIndex = url.indexOf('#');
        if (fragmentIndex >= 0) {
            fragment = url.substring(fragmentIndex);
            url = url.substring(0, fragmentIndex);
        }
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (!query.isEmpty()) query.append('&');
            query.append(ServletUtils.URLEncode(entry.getKey())).append('=');
            if (entry.getValue() != null) query.append(ServletUtils.URLEncode(entry.getValue()));
        }
        if (url.endsWith("?") || url.endsWith("&")) return url + query + fragment;
        return url + (url.indexOf('?') >= 0 ? '&' : '?') + query + fragment;
    }
}
