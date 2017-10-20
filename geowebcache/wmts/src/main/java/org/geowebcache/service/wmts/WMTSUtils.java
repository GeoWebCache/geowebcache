/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Sandro Salari, GeoSolutions S.A.S., Copyright 2017
 * 
 */

package org.geowebcache.service.wmts;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.mime.MimeType;

final class WMTSUtils {
    
    private static Log log = LogFactory.getLog(WMTSUtils.class);

    private WMTSUtils() {
    }

    protected static List<String> getLayerFormats(TileLayer layer) throws IOException {
        return layer.getMimeTypes().stream().map(MimeType::getFormat).collect(Collectors.toList());
    }

    public static List<String> getInfoFormats(TileLayer layer) {
        return layer.getInfoMimeTypes().stream().map(MimeType::getFormat)
                .collect(Collectors.toList());
    }

    public static List<ParameterFilter> getLayerDimensions(List<ParameterFilter> filters) {
        List<ParameterFilter> dimensions = new ArrayList<ParameterFilter>(0);
        if (filters != null) {
            dimensions = filters.stream()
                    .filter(filter -> !"STYLES".equalsIgnoreCase(filter.getKey())
                            && filter.getLegalValues() != null)
                    .collect(Collectors.toList());
        }
        return dimensions;
    }

}
