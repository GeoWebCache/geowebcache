package org.geowebcache.tile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class KMLTile extends Tile {
    String urlPrefix = null;
        
    public KMLTile(String layerId, HttpServletRequest servletReq, HttpServletResponse servletResp) {
        super(layerId, servletReq, servletResp);
    }
    
    public void setUrlPrefix(String urlPrefix) {
        this.urlPrefix = urlPrefix;
    }
    
    public String getUrlPrefix() {
        return urlPrefix;
    }
    
}
