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

    public static final ImageMime png = 
        new ImageMime("image/png", "png", "png", "image/png", true);

    public static final ImageMime jpeg = 
        new ImageMime("image/jpeg", "jpeg", "jpeg", "image/jpeg", true);
    
    public static final ImageMime gif = 
        new ImageMime("image/gif", "gif", "gif", "image/gif", true);    
    
    public static final ImageMime tiff = 
            new ImageMime("image/tiff", "tiff", "tiff", "image/tiff", true);
    
    public static final ImageMime png8 = 
        new ImageMime("image/png", "png8", "png", "image/png8", true);
    
    public static final ImageMime png24 = 
        new ImageMime("image/png", "png24", "png", "image/png24", true);
    
    
    public ImageMime(String mimeType, String fileExtension, 
            String internalName, String format, boolean noop) {
        super(mimeType, fileExtension, internalName, format, true);   
    }
    
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
            return png;
        } else if ( tmpStr.equalsIgnoreCase("jpeg")) {
            return jpeg;
        } else if ( tmpStr.equalsIgnoreCase("gif")) {
            return gif;
        } else if ( tmpStr.equalsIgnoreCase("tiff")) {
            return tiff;
        } else if ( tmpStr.equalsIgnoreCase("png8")) {
            return png8;
        } else if ( tmpStr.equalsIgnoreCase("png24")) {
            return png24;
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
            return png;
        } else if (fileExtension.equalsIgnoreCase("jpeg")) {
            return jpeg;
        } else if (fileExtension.equalsIgnoreCase("gif")) {
            return gif;
        } else if (fileExtension.equalsIgnoreCase("tiff")) {
            return tiff;
        } else if (fileExtension.equalsIgnoreCase("png8")) {
            return png8;
        } else if (fileExtension.equalsIgnoreCase("png24")) {
            return png24;
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
