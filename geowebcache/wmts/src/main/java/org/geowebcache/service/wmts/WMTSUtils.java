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
 * @author Sandro Salari, GeoSolutions S.A.S., Copyright 2017
 */
package org.geowebcache.service.wmts;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.mime.MimeType;
import org.geowebcache.util.ServletUtils;

final class WMTSUtils {

    private WMTSUtils() {}

    static List<String> getLayerFormats(TileLayer layer) throws IOException {
        return layer.getMimeTypes().stream().map(MimeType::getFormat).collect(Collectors.toList());
    }

    static List<String> getLayerFormatsExtensions(TileLayer layer) throws IOException {
        return layer.getMimeTypes().stream().map(MimeType::getFileExtension).collect(Collectors.toList());
    }

    public static List<String> getInfoFormats(TileLayer layer) {
        return layer.getInfoMimeTypes().stream().map(MimeType::getFormat).collect(Collectors.toList());
    }

    public static List<ParameterFilter> getLayerDimensions(List<ParameterFilter> filters) {
        List<ParameterFilter> dimensions = new ArrayList<>(0);
        if (filters != null) {
            dimensions = filters.stream()
                    .filter(filter -> !"STYLES".equalsIgnoreCase(filter.getKey()) && filter.getLegalValues() != null)
                    .collect(Collectors.toList());
        }
        return dimensions;
    }

    public static String getKvpServiceMetadataURL(String baseUrl) {
        String base = baseUrl;
        String anchor = "";

        // Split anchor
        if (base.indexOf('#') != -1) {
            anchor = base.substring(base.indexOf('#'));
            base = base.substring(0, base.indexOf('#'));
        }

        // Remove stray ? and &'s at the end of the URL
        int l = base.length();
        while (l > 0 && (base.charAt(l - 1) == '?' || base.charAt(l - 1) == '&')) {
            base = base.substring(0, l - 1);
            l--;
        }

        // Append the correct delimiter
        if (base.indexOf('?') == -1) {
            base += "?";
        } else {
            base += "&";
        }

        return base + "SERVICE=wmts&REQUEST=getcapabilities&VERSION=1.0.0" + anchor;
    }

    /**
     * Appends propagated query parameters to a URL while preserving the existing query string order and any anchor.
     *
     * <p>This helper stays local to WMTS because capability templates can still contain unresolved path placeholders
     * such as {@code {TileRow}} or {@code {TileMatrixSet}} at the point where the final backlink URL is serialized.
     * General-purpose URI builders such as Apache HttpComponents {@code URIBuilder} reject those template URLs as
     * invalid URIs, so WMTS needs a lightweight string-based helper that keeps the path intact and appends the
     * propagated query map at the end of the URL.
     *
     * <p>The method preserves the original path, keeps any fragment suffix at the end, and serializes the provided map
     * in iteration order. It does not normalize or deduplicate template path components, but it does percent-encode
     * each appended key and value so that propagated parameters (such as security tokens) cannot corrupt the query
     * string or inject additional parameters.
     */
    public static String appendQueryParameters(String url, Map<String, String> queryParameters) {
        if (queryParameters == null || queryParameters.isEmpty()) {
            return url;
        }

        String base = url;
        String anchor = "";
        int anchorIndex = base.indexOf('#');
        if (anchorIndex != -1) {
            anchor = base.substring(anchorIndex);
            base = base.substring(0, anchorIndex);
        }

        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : queryParameters.entrySet()) {
            if (!query.isEmpty()) {
                query.append("&");
            }
            query.append(ServletUtils.URLEncode(entry.getKey())).append("=");
            if (entry.getValue() != null) {
                query.append(ServletUtils.URLEncode(entry.getValue()));
            }
        }

        if (base.endsWith("?") || base.endsWith("&")) {
            return base + query + anchor;
        }

        if (base.contains("?")) {
            return base + "&" + query + anchor;
        }

        return base + "?" + query + anchor;
    }
}
