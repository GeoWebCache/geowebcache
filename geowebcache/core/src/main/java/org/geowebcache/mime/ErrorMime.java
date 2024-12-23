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

import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;

public class ErrorMime extends MimeType {
    private static Logger log = Logging.getLogger(ErrorMime.class.getName());

    public static final ErrorMime vnd_ogc_se_inimage = new ErrorMime("application/vnd.ogc.se_inimage");

    private ErrorMime(String mimeType) {
        super(mimeType, null, null, mimeType, false);
    }

    public ErrorMime(String mimeType, String fileExtension, String internalName, String format) throws MimeException {
        super(mimeType, fileExtension, internalName, format, false);

        // Check for trouble
        if (mimeType.length() < 12 || !mimeType.substring(0, 12).equalsIgnoreCase("application/")) {
            throw new MimeException("MIME type " + mimeType + " does not start with application/");
        }
    }

    public static ErrorMime createFromMimeType(String mimeType) throws MimeException {
        if (mimeType.equalsIgnoreCase("application/vnd.ogc.se_inimage")) {
            return vnd_ogc_se_inimage;
        } else {
            log.log(
                    Level.SEVERE,
                    "Unsupported MIME type: " + mimeType + ", falling back to application/vnd.ogc.se_inimage.");
            return vnd_ogc_se_inimage;
        }
    }
}
