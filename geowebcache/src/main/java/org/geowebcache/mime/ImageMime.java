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
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 *  
 */
package org.geowebcache.mime;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ImageMime extends MimeType {
    private static Log log = LogFactory.getLog(org.geowebcache.mime.ImageMime.class);

    public ImageMime(String mimeType, String fileExtension, String internalName) {
        super();
        if(mimeType.substring(0,6).equalsIgnoreCase("image/")) {
            super.mimeType = mimeType;
            super.fileExtension = fileExtension;
            super.internalName = internalName;
        } else {
            log.error("MIME type " + mimeType + " does not start with image/");
        }
        if(fileExtension == null || fileExtension.length() == 0) {
            log.warn("Setting file extension based on mimeType for " + mimeType);
            super.fileExtension = mimeType.substring(6, mimeType.length());
        }
        // Don't try to guess an internal name
    }

    public static ImageMime createFromMimeType(String mimeType) {
        if (mimeType.equalsIgnoreCase("image/png")) {
            return new ImageMime("image/png", "png", "png");
        } else if (mimeType.equalsIgnoreCase("image/jpeg")) {
            return new ImageMime("image/jpeg", "jpeg", "jpeg");
        } else if (mimeType.equalsIgnoreCase("image/gif")) {
            return new ImageMime("image/gif", "gif", "gif");
        } else if (mimeType.equalsIgnoreCase("image/tiff")) {
            return new ImageMime("image/tiff", "tiff", "tiff");
        } else if (mimeType.equalsIgnoreCase("image/png8")) {
            return new ImageMime("image/png8", "png8", "png");
        } else {
            log.error("Unsupported MIME type: " + mimeType + ", falling back to PNG.");
            return new ImageMime("image/png", "png", "png");
        }
    }
    
    public static ImageMime createFromExtension(String fileExtension) {
        if (fileExtension.equalsIgnoreCase("png")) {
            return new ImageMime("image/png", "png", "png");
        } else if (fileExtension.equalsIgnoreCase("jpeg")) {
            return new ImageMime("image/jpeg", "jpeg", "jpeg");
        } else if (fileExtension.equalsIgnoreCase("gif")) {
            return new ImageMime("image/gif", "gif", "gif");
        } else if (fileExtension.equalsIgnoreCase("tiff")) {
            return new ImageMime("image/tiff", "tiff", "tiff");
        } else if (fileExtension.equalsIgnoreCase("png8")) {
            return new ImageMime("image/png8", "png8", "png");
        } else {
            log.error("Unsupported MIME type: " + fileExtension + ", falling back to PNG.");
            return new ImageMime("image/png", "png", "png");
        }
    }
    
}
