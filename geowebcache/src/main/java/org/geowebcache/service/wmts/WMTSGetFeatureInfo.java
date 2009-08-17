package org.geowebcache.service.wmts;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.geowebcache.layer.TileLayerDispatcher;

public class WMTSGetFeatureInfo {

    private TileLayerDispatcher tld;
    
    private String urlStr;
    
    protected WMTSGetFeatureInfo(TileLayerDispatcher tld, HttpServletRequest servReq) {
        this.tld = tld;
        urlStr = servReq.getRequestURL().toString() + "?SERVICE=WMS&amp;";
    }
    
    protected void writeResponse(HttpServletResponse response) {
    
    }
}
