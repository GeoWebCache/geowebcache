package org.geowebcache.mime;

public class TextMime extends MimeType {

    public static final TextMime txt = new TextMime(
       "text/plain", "txt", "txt",
       "text/plain", true);
    
    public static final TextMime txtHtml = new TextMime(
            "text/html", "txt.html", "html",
            "text/html", true);
    
    public static final TextMime txtXml = new TextMime(
            "text/xml", "xml", "xml",
            "text/xml", true);
    
    public static final TextMime txtCss = new TextMime(
            "text/css", "css", "css",
            "text/css", true);
    
    public static final TextMime txtJs = new TextMime(
            "text/javascript", "js", "javascript",
            "text/javascript", true);
        
    private TextMime(String mimeType, String fileExtension, 
            String internalName, String format, boolean noop) {
        super(mimeType, fileExtension, internalName, format, false);
    }
    
    protected static TextMime checkForFormat(String formatStr) throws MimeException {
        if(formatStr.toLowerCase().startsWith("text")) {
            if(formatStr.equalsIgnoreCase("text/plain")) {
                return txt;
            } else if(formatStr.startsWith("text/html")) {
                return txtHtml;
            } else if(formatStr.startsWith("text/xml")) {
                return txtXml;
            } else if(formatStr.startsWith("text/css")) {
                return txtCss;
            } else if(formatStr.startsWith("text/javscript")) {
                return txtJs;
            } 

        }
        
        return null;
    }
    
    protected static TextMime checkForExtension(String fileExtension) throws MimeException {
        if(fileExtension.equalsIgnoreCase("txt")) {
            return txt;
        } else if(fileExtension.equalsIgnoreCase("txt.html")) {
            return txtHtml;
        } else if(fileExtension.equalsIgnoreCase("html")) {
            return txtHtml;
        } else if(fileExtension.equalsIgnoreCase("xml")) {
            return txtXml;
        } else if(fileExtension.equalsIgnoreCase("css")) {
            return txtCss;
        } else if(fileExtension.equalsIgnoreCase("js")) {
            return txtJs;
        } 
        
        return null;
    }
}
