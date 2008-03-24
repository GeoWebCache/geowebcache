package org.geowebcache.service.wms;

import javax.servlet.http.HttpServletResponse;

import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileRequest;

public class WMSConverter {
    public static TileRequest convert(WMSParameters wmsParams, 
            TileLayer layer, HttpServletResponse response) {
        return new TileRequest(
                layer.gridLocForBounds(wmsParams.getBBOX()),
                wmsParams.getImageMime());
    }
}
