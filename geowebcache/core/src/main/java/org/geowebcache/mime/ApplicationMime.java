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
    
    private ApplicationMime(String mimeType, String fileExtension, 
                String internalName, String format, boolean noop) {
        super(mimeType, fileExtension, internalName, format, false);
    }
        
    public ApplicationMime(String mimeType, String fileExtension, 
            String internalName, String format) throws MimeException {        
        super(mimeType, fileExtension, internalName, format, false);
    }

    protected static ApplicationMime checkForFormat(String formatStr) throws MimeException {
        if (formatStr.equalsIgnoreCase(bil16.format)) {
            return bil16;
        } else if (formatStr.equalsIgnoreCase(bil32.format)) {
            return bil32;
        } else if (formatStr.equalsIgnoreCase(json.format)) {
            return json;
        }
        
        return null;
    }
    
    protected static ApplicationMime checkForExtension(String fileExtension) throws MimeException {
        if (fileExtension.equals(bil16.fileExtension)) {
            return bil16;
        } else if (fileExtension.equals(bil32.fileExtension)) {
            return bil32;
        } else if (fileExtension.equals(json.fileExtension)) {
            return json;
        }
        
        return null;
    }
}
