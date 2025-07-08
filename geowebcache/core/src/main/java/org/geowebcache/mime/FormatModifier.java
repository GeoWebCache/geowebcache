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
 * @author Arne Kepp, OpenGeo (The Open Planning Project), Copyright 2009
 */
package org.geowebcache.mime;

import java.io.Serializable;
import java.util.logging.Logger;
import javax.imageio.ImageWriteParam;
import org.geotools.util.logging.Logging;

public class FormatModifier implements Serializable {

    // TODO May want to add initialization with double locking instead of testing each time

    private static Logger log = Logging.getLogger(FormatModifier.class.getName());

    private String responseFormat;

    private String requestFormat;

    private transient MimeType reqFormat;

    private transient MimeType resFormat;

    private Boolean transparent;

    private String bgColor;

    private String palette;

    private String compressionQuality;

    private transient Float compressQual;

    private transient ImageWriteParam imgWriteParam;

    public FormatModifier() {}

    public synchronized MimeType getRequestFormat() {
        if (requestFormat == null) {
            return getResponseFormat();
        }

        if (reqFormat == null) {
            try {
                reqFormat = MimeType.createFromFormat(requestFormat);
            } catch (MimeException e) {
                log.fine(e.getMessage());
            }
        }

        return reqFormat;
    }

    public synchronized MimeType getResponseFormat() {
        if (resFormat == null) {
            try {
                resFormat = MimeType.createFromFormat(responseFormat);
            } catch (MimeException e) {
                log.fine(e.getMessage());
            }
        }

        return resFormat;
    }

    public Boolean getTransparent() {
        return transparent;
    }

    public String getBgColor() {
        return bgColor;
    }

    public String getPalette() {
        return palette;
    }

    private Float getCompressionQuality() {
        if (compressionQuality != null && compressQual == null) {
            compressQual = Float.parseFloat(compressionQuality);
        }

        return compressQual;
    }

    public synchronized ImageWriteParam adjustImageWriteParam(ImageWriteParam param) {
        if (imgWriteParam == null) {
            if (getCompressionQuality() != null) {
                if (ImageMime.jpeg.equals(getResponseFormat())) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(getCompressionQuality());
                } else {
                    log.fine("FormatModifier only supports JPEG image parameters at this time.");
                }
            }
            imgWriteParam = param;
        }

        return imgWriteParam;
    }
}
