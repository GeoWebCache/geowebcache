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
    private static Log log = LogFactory
            .getLog(org.geowebcache.mime.ImageMime.class);

    public ImageMime(String mimeType, String fileExtension, String internalName, String format) 
    throws MimeException {
        super(mimeType, fileExtension, internalName, format, true);
        
        // Check for trouble
        if(mimeType.length() < 6 || ! mimeType.substring(0,6).equalsIgnoreCase("image/")) {
            throw new MimeException("MIME type " + mimeType + " does not start with application/");
        }
    }

    protected static ImageMime checkForFormat(String formatStr)
    throws MimeException {
        String tmpStr = formatStr.substring(6,formatStr.length());
        
        if ( tmpStr.equalsIgnoreCase("png")) {
            return new ImageMime("image/png", "png", "png", "image/png");
        } else if ( tmpStr.equalsIgnoreCase("jpeg")) {
            return new ImageMime("image/jpeg", "jpeg", "jpeg", "image/jpeg");
        } else if ( tmpStr.equalsIgnoreCase("gif")) {
            return new ImageMime("image/gif", "gif", "gif", "image/gif");
        } else if ( tmpStr.equalsIgnoreCase("tiff")) {
            return new ImageMime("image/tiff", "tiff", "tiff", "image/tiff");
        } else if ( tmpStr.equalsIgnoreCase("png8")) {
            return new ImageMime("image/png8", "png8", "png", "image/png8");
        } else if ( tmpStr.equalsIgnoreCase("png24")) {
            return new ImageMime("image/png24", "png24", "png", "image/png24");
        }
        return null;
    }

//    public static ImageMime createFromMimeType(String mimeType) {
//        ImageMime imageMime = checkForMimeType(mimeType);
//        if (imageMime == null) {
//            log.error("Unsupported MIME type: " + mimeType
//                    + ", falling back to PNG.");
//            imageMime = new ImageMime("image/png", "png", "png");
//        }
//
//        return imageMime;
//    }

    protected static ImageMime checkForExtension(String fileExtension) 
    throws MimeException {
        if (fileExtension.equalsIgnoreCase("png")) {
            return new ImageMime("image/png", "png", "png", "image/png");
        } else if (fileExtension.equalsIgnoreCase("jpeg")) {
            return new ImageMime("image/jpeg", "jpeg", "jpeg", "image/jpeg");
        } else if (fileExtension.equalsIgnoreCase("gif")) {
            return new ImageMime("image/gif", "gif", "gif", "image/gif");
        } else if (fileExtension.equalsIgnoreCase("tiff")) {
            return new ImageMime("image/tiff", "tiff", "tiff", "image/tiff");
        } else if (fileExtension.equalsIgnoreCase("png8")) {
            return new ImageMime("image/png8", "png8", "png", "image/png8");
        } else if (fileExtension.equalsIgnoreCase("png24")) {
            return new ImageMime("image/png24", "png24", "png", "image/png24");
        }
        return null;
    }

//    public static ImageMime createFromExtension(String fileExtension) {
//        ImageMime imageMime = checkForExtension(fileExtension);
//        if (imageMime == null) {
//            log.error("Unsupported MIME type: " + fileExtension
//                    + ", falling back to PNG.");
//            imageMime = new ImageMime("image/png", "png", "png");
//        }
//
//        return imageMime;
//    }
}
