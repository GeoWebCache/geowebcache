package org.geowebcache.service.wmts;

import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.conveyor.Conveyor.CacheResult;
import org.geowebcache.grid.GridSet;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.service.OWSException;
import org.geowebcache.stats.RuntimeStats;
import org.geowebcache.util.ServletUtils;

public class WMTSGetFeatureInfo {
    
    private static Log log = LogFactory.getLog(org.geowebcache.service.wmts.WMTSService.class);
    
    private ConveyorTile convTile;
    
    private String[] values;
    
    int i;
    
    int j;
    
    protected WMTSGetFeatureInfo(ConveyorTile convTile) throws OWSException {
        
        String[] keys = { "i", "j" };
        
        values = ServletUtils.selectedStringsFromMap(
                convTile.servletReq.getParameterMap(), 
                convTile.servletReq.getCharacterEncoding(), 
                keys );
        
        try {
            i = Integer.parseInt(values[0]);
        } catch(NumberFormatException nfe) {
            throw new OWSException(400, "MissingParameterValue", "I", "I was not specified"); 
        }
        
        try {
            j = Integer.parseInt(values[1]);
        } catch(NumberFormatException nfe) {
            throw new OWSException(400, "MissingParameterValue", "J", "J was not specified"); 
        }
        
        this.convTile = convTile;
    }
    
    protected void writeResponse(RuntimeStats stats) throws OWSException {
        TileLayer layer = convTile.getLayer();
        
        WMSLayer wmsLayer = null;

        if (layer instanceof WMSLayer) {
            wmsLayer = (WMSLayer) layer;
        }
        
        GridSet gridSet = convTile.getGridSubset().getGridSet();
        if(gridSet.getTileHeight() < j || j < 0) {
            throw new OWSException(400, "PointIJOutOfRange", "J", "J was " + j + ", must be between 0 and " + gridSet.getTileHeight()); 
        }
        
        if(gridSet.getTileWidth() < i || i < 0) {
            throw new OWSException(400, "PointIJOutOfRange", "I", "I was " + i + ", must be between 0 and " + gridSet.getTileWidth()); 
        }

        byte[] data = null;
        try {
            data = wmsLayer.getFeatureInfo(convTile, i, j);
        } catch (GeoWebCacheException e) {
            throw new OWSException(500, "NoApplicableCode", "", e.getMessage()); 
        }

        convTile.servletResp.setStatus(HttpServletResponse.SC_OK);
        convTile.servletResp.setContentType(convTile.getMimeType().getMimeType());
        convTile.servletResp.setContentLength(data.length);

        stats.log(data.length, CacheResult.OTHER);
        
        try {
            OutputStream os = convTile.servletResp.getOutputStream();
            os.write(data);
            os.flush();
        } catch (IOException ioe) {
            log.debug("Caught IOException" + ioe.getMessage());
        }

    }
}
