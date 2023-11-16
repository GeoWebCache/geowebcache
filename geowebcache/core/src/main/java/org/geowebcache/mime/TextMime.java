/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Arne Kepp, The Open Planning Project, Copyright 2009
 */
package org.geowebcache.mime;

public class TextMime extends MimeType {

    public static final TextMime txt = new TextMime("text/plain", "txt", "txt", "text/plain", true);

    public static final TextMime txtHtml =
            new TextMime("text/html", "txt.html", "html", "text/html", true);

    public static final TextMime txtMapml =
            new TextMime("text/mapml", "mapml", "mapml", "text/mapml", true);

    public static final TextMime txtXml = new TextMime("text/xml", "xml", "xml", "text/xml", true);

    public static final TextMime txtCss = new TextMime("text/css", "css", "css", "text/css", true);

    public static final TextMime txtJs =
            new TextMime("text/javascript", "js", "javascript", "text/javascript", true);

    private TextMime(
            String mimeType,
            String fileExtension,
            String internalName,
            String format,
            boolean noop) {
        super(mimeType, fileExtension, internalName, format, false);
    }

    protected static TextMime checkForFormat(String formatStr) throws MimeException {
        if (formatStr.toLowerCase().startsWith("text")) {
            if (formatStr.equalsIgnoreCase("text/plain")) {
                return txt;
            } else if (formatStr.startsWith("text/html")) {
                return txtHtml;
            } else if (formatStr.startsWith("text/mapml")) {
                return txtMapml;
            } else if (formatStr.startsWith("text/xml")) {
                return txtXml;
            } else if (formatStr.startsWith("text/css")) {
                return txtCss;
            } else if (formatStr.startsWith("text/javascript")) {
                return txtJs;
            }
        }

        return null;
    }

    protected static TextMime checkForExtension(String fileExtension) throws MimeException {
        if (fileExtension.equalsIgnoreCase("txt")) {
            return txt;
        } else if (fileExtension.equalsIgnoreCase("txt.html")) {
            return txtHtml;
        } else if (fileExtension.equalsIgnoreCase("html")) {
            return txtHtml;
        } else if (fileExtension.equalsIgnoreCase("mapml")) {
            return txtMapml;
        } else if (fileExtension.equalsIgnoreCase("xml")) {
            return txtXml;
        } else if (fileExtension.equalsIgnoreCase("css")) {
            return txtCss;
        } else if (fileExtension.equalsIgnoreCase("js")) {
            return txtJs;
        }

        return null;
    }
}
