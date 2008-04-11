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

public class XMLMime extends MimeType {
    private static Log log = LogFactory.getLog(org.geowebcache.mime.XMLMime.class);

    public XMLMime(String mimeType, String fileExtension, String internalName) {
        super();
        if(mimeType.substring(0,12).equalsIgnoreCase("application/")) {
            super.mimeType = mimeType;
            super.fileExtension = fileExtension;
            super.internalName = internalName;
        } else {
            log.error("MIME type " + mimeType + " does not start with application/");
        }
        if(fileExtension == null || fileExtension.length() == 0) {
            log.warn("Setting file extension based on mimeType for " + mimeType);
            super.fileExtension = mimeType.substring(12, mimeType.length());
        }
        // Don't try to guess an internal name
    }

    protected static XMLMime checkForMimeType(String mimeType) {
        if (mimeType.equalsIgnoreCase("application/vnd.google-earth.kml+xml")) {
            return new XMLMime("application/vnd.google-earth.kml+xml", "kml", "kml");
        } else if (mimeType.equalsIgnoreCase("application/vnd.google-earth.kmz")) {
            return new XMLMime("application/vnd.google-earth.kmz", "kmz", "kmz");
        } else if (mimeType.equalsIgnoreCase("application/vnd.ogc.gml")) {
            return new XMLMime("application/vnd.ogc.gml", "gml", "gml");
        }
        
        return null;
    }
    
    public static XMLMime createFromMimeType(String mimeType) {
        XMLMime xmlMime = checkForMimeType(mimeType);
        if(xmlMime == null) {
            log.error("Unsupported MIME type: " + mimeType + ", returning null.");
        }
        
        return xmlMime;
    }
    
    protected static XMLMime checkForExtension(String fileExtension) {
        if (fileExtension.equalsIgnoreCase("kml")) {
            return new XMLMime("application/vnd.google-earth.kml+xml", "kml", "kml");
        } else if (fileExtension.equalsIgnoreCase("kmz")) {
            return new XMLMime("application/vnd.google-earth.kmz", "kmz", "kmz");
        } else if (fileExtension.equalsIgnoreCase("gml")) {
            return new XMLMime("application/vnd.ogc.gml", "gml", "gml");
        }
        
        return null;
    }
    
    public static XMLMime createFromExtension(String fileExtension) {
        XMLMime xmlMime = checkForExtension(fileExtension);
        if(xmlMime == null) {
            log.error("Unsupported MIME type: " + fileExtension + ", returning null");
        }
        
        return xmlMime;
    }
}
