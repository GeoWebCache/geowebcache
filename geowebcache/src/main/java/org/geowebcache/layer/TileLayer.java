package org.geowebcache.layer;

import java.io.IOException;
import javax.servlet.http.HttpServletResponse;

import org.geowebcache.util.wms.BBOX;

public interface TileLayer {

    public String supportsProjection(String srs);
    public String supportsMime(String mimeType);
    public String supportsBbox(String srs, BBOX bounds);
    
    public byte[] getData(TileRequest tileRequest, String requestURI, 
            HttpServletResponse response) throws IOException;
    
    public String getProjection();
    public BBOX getBounds();
    public int[] getMetaTilingFactors();
    public int[][] getCoveredGridLevels(BBOX bounds);
    public String getName();
    public void destroy();
    public int[] gridLocForBounds(BBOX bounds);
    
}
