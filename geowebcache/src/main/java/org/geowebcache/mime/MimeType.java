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

public class MimeType {
    protected String mimeType;

    protected String fileExtension;

    protected String internalName;
    
    protected boolean supportsTiling;

    private static Log log = LogFactory
            .getLog(org.geowebcache.mime.MimeType.class);

    public MimeType(boolean supportsTiling) {
        this.supportsTiling = supportsTiling;
    }

    public MimeType(String mimeType, String fileExtension, String internalName, boolean supportsTiling) {
        this.mimeType = mimeType;
        this.fileExtension = fileExtension;
        this.internalName = internalName;
        this.supportsTiling = supportsTiling;
    }

    // The string representing the MIME type
    public String getMimeType() {
        return mimeType;
    }

    // Used for saving to files etc
    public String getFileExtension() {
        return fileExtension;
    }

    // Used for internal purposes, like picking image renderer
    public String getInternalName() {
        return internalName;
    }
    
    public boolean supportsTiling() {
        return supportsTiling;
    }
    

    /**
     * Get the MIME type object for a given MIME type string
     * 
     * @param mimeStr
     * @return
     */
    public static MimeType createFromMimeType(String mimeStr) throws MimeException {
        MimeType mimeType = null;
        if(mimeStr == null) {
            throw new MimeException("MimeType was not set");
        }
        
        if (mimeStr.substring(0, 6).equalsIgnoreCase("image/")) {
            mimeType = ImageMime.checkForMimeType(mimeStr);
        } else if (mimeStr.substring(0, 12).equalsIgnoreCase("application/")) {
            // TODO May have to deal with error MIMEs too
            mimeType = XMLMime.checkForMimeType(mimeStr);
        }

        if (mimeType == null) {
            log.error("Unsupported MIME type: " + mimeStr + ", returning null");
        }
        return mimeType;
    }

    /**
     * Get the MIME type object for a given file extension
     * 
     * @param mimeStr
     * @return
     */
    public static MimeType createFromExtension(String fileExtension) {
        MimeType mimeType = null;

        mimeType = ImageMime.checkForExtension(fileExtension);
        if (mimeType != null) {
            return mimeType;
        }

        mimeType = XMLMime.checkForExtension(fileExtension);
        if (mimeType != null) {
            return mimeType;
        }

        log.error("Unsupported MIME type: " + fileExtension
                + ", returning null");
        return null;
    }

    public boolean equals(Object obj) {
        if (obj.getClass() == this.getClass()) {
            MimeType mimeObj = (MimeType) obj;
            if (this.mimeType.equalsIgnoreCase(mimeObj.mimeType)) {
                return true;
            }
        }
        return false;
    }
}
