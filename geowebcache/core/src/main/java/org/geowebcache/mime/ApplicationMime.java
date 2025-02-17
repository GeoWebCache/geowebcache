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
 * @author Arne Kepp, The Open Planning Project, Copyright 2009
 */
package org.geowebcache.mime;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ApplicationMime extends MimeType {

    public static final String MAPBOX_TILES_LEGACY_MIME = "application/x-protobuf;type=mapbox-vector";

    protected boolean vector;

    public static final ApplicationMime bil16 =
            new ApplicationMime("application/bil16", "bil16", "bil16", "application/bil16", false);

    public static final ApplicationMime bil32 =
            new ApplicationMime("application/bil32", "bil32", "bil32", "application/bil32", false);

    public static final ApplicationMime json =
            new ApplicationMime("application/json", "json", "json", "application/json", false);

    public static final ApplicationMime topojson =
            new ApplicationMime("application/json", "topojson", "topojson", "application/json;type=topojson", true);

    public static final ApplicationMime geojson =
            new ApplicationMime("application/json", "geojson", "geojson", "application/json;type=geojson", true);

    public static final ApplicationMime utfgrid =
            new ApplicationMime("application/json", "utfgrid", "utfgrid", "application/json;type=utfgrid", true);

    public static final ApplicationMime mapboxVector = new ApplicationMime(
            "application/vnd.mapbox-vector-tile",
            "pbf",
            "mapbox-vectortile",
            "application/vnd.mapbox-vector-tile",
            true);

    static Set<ApplicationMime> ALL = ImmutableSet.of(bil16, bil32, json, topojson, geojson, utfgrid, mapboxVector);

    private static final List<String> BINARY_FORMATS =
            Arrays.asList(bil16.mimeType, bil32.mimeType, mapboxVector.mimeType, utfgrid.mimeType);

    private static Map<String, ApplicationMime> BY_FORMAT = Maps.uniqueIndex(ALL, mimeType -> mimeType.getFormat());

    private static Map<String, ApplicationMime> BY_EXTENSION =
            Maps.uniqueIndex(ALL, mimeType -> mimeType.getFileExtension());

    private ApplicationMime(String mimeType, String fileExtension, String internalName, String format, boolean vector) {
        super(mimeType, fileExtension, internalName, format, false);
        this.vector = vector;
    }

    public ApplicationMime(String mimeType, String fileExtension, String internalName, String format)
            throws MimeException {
        super(mimeType, fileExtension, internalName, format, false);
    }

    protected static ApplicationMime checkForFormat(String formatStr) throws MimeException {
        ApplicationMime mimeType = BY_FORMAT.get(formatStr);
        if (mimeType == null && formatStr.equals(MAPBOX_TILES_LEGACY_MIME)) {
            return mapboxVector;
        }
        return mimeType;
    }

    protected static ApplicationMime checkForExtension(String fileExtension) throws MimeException {
        ApplicationMime mimeType = BY_EXTENSION.get(fileExtension);
        return mimeType;
    }

    @Override
    public boolean isVector() {
        return vector;
    }

    @Override
    protected boolean isBinary() {
        return BINARY_FORMATS.contains(this.getMimeType());
    }

    @Override
    public boolean isInlinePreferred() {
        return getMimeType().contains("json");
    }
}
