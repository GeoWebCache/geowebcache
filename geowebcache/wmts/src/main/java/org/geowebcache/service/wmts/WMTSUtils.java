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
import java.util.stream.Collectors;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.mime.MimeType;

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
}
