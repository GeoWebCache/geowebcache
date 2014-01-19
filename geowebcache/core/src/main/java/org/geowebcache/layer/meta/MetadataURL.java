package org.geowebcache.layer.meta;

import java.net.URL;

public class MetadataURL {

    private String type;
    private String format;
    private URL url;

    public MetadataURL(String type, String format, URL url) {
        this.url = url;
        this.type = type;
        this.format = format;
    }

    public String getType() {
        return type;
    }

    public String getFormat() {
        return format;
    }

    public URL getUrl() {
        return url;
    }
}

