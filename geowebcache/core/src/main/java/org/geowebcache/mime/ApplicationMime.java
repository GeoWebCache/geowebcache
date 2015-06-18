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
 * @author Arne Kepp, The Open Planning Project, Copyright 2009
 *  
 */
package org.geowebcache.mime;

import java.util.Map;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

public class ApplicationMime extends MimeType {

    public static final ApplicationMime bil16 = new ApplicationMime(
            "application/bil16", "bil16", "bil16",
            "application/bil16", false);

    public static final ApplicationMime bil32 = new ApplicationMime(
            "application/bil32", "bil32", "bil32",
            "application/bil32", false);
    
    public static final ApplicationMime json = new ApplicationMime(
            "application/json", "json", "json",
            "application/json", false);
    
    public static final ApplicationMime topojson = new ApplicationMime("application/json",
            "topojson", "topojson", "application/json;type=topojson", false);

    public static final ApplicationMime geojson = new ApplicationMime("application/json",
            "geojson", "geojson", "application/json;type=geojson", false);

    public static final ApplicationMime mapboxVector = new ApplicationMime("application/x-protobuf",
            "pbf", "mapbox-vectortile", "application/x-protobuf;type=mapbox-vector", false);

    private static Set<ApplicationMime> ALL = ImmutableSet.of(bil16, bil32, json, topojson,
            geojson, mapboxVector);

    private static Map<String, ApplicationMime> BY_FORMAT = Maps.uniqueIndex(ALL,
            new Function<ApplicationMime, String>() {

                @Override
                public String apply(ApplicationMime mimeType) {
                    return mimeType.getFormat();
                }
            });
    
    private static Map<String, ApplicationMime> BY_EXTENSION = Maps.uniqueIndex(ALL,
            new Function<ApplicationMime, String>() {

                @Override
                public String apply(ApplicationMime mimeType) {
                    return mimeType.getFileExtension();
                }
            });

    private ApplicationMime(String mimeType, String fileExtension, 
                String internalName, String format, boolean noop) {
        super(mimeType, fileExtension, internalName, format, false);
    }
        
    public ApplicationMime(String mimeType, String fileExtension, 
            String internalName, String format) throws MimeException {        
        super(mimeType, fileExtension, internalName, format, false);
    }

    protected static ApplicationMime checkForFormat(String formatStr) throws MimeException {
        ApplicationMime mimeType = BY_FORMAT.get(formatStr);
        return mimeType;
    }
    
    protected static ApplicationMime checkForExtension(String fileExtension) throws MimeException {
        ApplicationMime mimeType = BY_EXTENSION.get(fileExtension);
        return mimeType;
    }
}
