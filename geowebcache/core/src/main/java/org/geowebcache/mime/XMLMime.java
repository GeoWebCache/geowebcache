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
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 */
package org.geowebcache.mime;

public class XMLMime extends MimeType {

    public static final XMLMime ogcxml =
            new XMLMime("application/vnd.ogc.se_xml", "ogc-xml", "ogc-xml", "application/vnd.ogc.se_xml", false);

    public static final XMLMime kml = new XMLMime(
            "application/vnd.google-earth.kml+xml", "kml", "kml", "application/vnd.google-earth.kml+xml", false);

    public static final XMLMime kmz =
            new XMLMime("application/vnd.google-earth.kmz", "kmz", "kmz", "application/vnd.google-earth.kmz", false);

    public static final XMLMime gml =
            new XMLMime("application/vnd.ogc.gml", "gml", "gml", "application/vnd.ogc.gml", false);

    public static final XMLMime gml3 =
            new XMLMime("application/vnd.ogc.gml/3.1.1", "gml3", "gml3", "application/vnd.ogc.gml/3.1.1", false);

    private XMLMime(String mimeType, String fileExtension, String internalName, String format, boolean noop) {
        super(mimeType, fileExtension, internalName, format, false);
    }

    protected static XMLMime checkForFormat(String formatStr) throws MimeException {
        if (formatStr.equalsIgnoreCase("application/vnd.google-earth.kml+xml")) {
            return kml;
        } else if (formatStr.equalsIgnoreCase("application/vnd.google-earth.kmz")) {
            return kmz;
        } else if (formatStr.equalsIgnoreCase("application/vnd.ogc.se_xml")) {
            return ogcxml;
        } else if (formatStr.equalsIgnoreCase("application/vnd.ogc.gml")) {
            return gml;
        } else if (formatStr.equalsIgnoreCase("application/vnd.ogc.gml/3.1.1")) {
            return gml3;
        }

        return null;
    }

    protected static XMLMime checkForExtension(String fileExtension) throws MimeException {
        if (fileExtension.equalsIgnoreCase("kml")) {
            return kml;
        } else if (fileExtension.equalsIgnoreCase("kmz")) {
            return kmz;
        } else if (fileExtension.equalsIgnoreCase("gml")) {
            return gml;
        } else if (fileExtension.equalsIgnoreCase("gml3")) {
            return gml3;
        }

        return null;
    }

    @Override
    public boolean isInlinePreferred() {
        return true;
    }
}
