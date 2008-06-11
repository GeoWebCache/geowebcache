package org.geowebcache.mime;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class TextMime extends MimeType {
    private static Log log = LogFactory.getLog(org.geowebcache.mime.TextMime.class);

    public static final TextMime txt = new TextMime(
       "text/plain", "txt", "txt",
       "text/plain", true);
    
    private TextMime(String mimeType, String fileExtension, 
            String internalName, String format, boolean noop) {
        super(mimeType, fileExtension, internalName, format, false);
    }
    
    protected static TextMime checkForFormat(String formatStr) throws MimeException {
        if(formatStr.equalsIgnoreCase("text/plain")) {
            return txt;
        }
        return null;
    }
    
    protected static TextMime checkForExtension(String fileExtension) throws MimeException {
        if(fileExtension.equalsIgnoreCase("txt")) {
            return txt;
        }
        
        return null;
    }
}
