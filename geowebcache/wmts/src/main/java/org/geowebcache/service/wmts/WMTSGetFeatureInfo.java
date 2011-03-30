package org.geowebcache.service.wmts;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.Conveyor.CacheResult;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.service.OWSException;
import org.geowebcache.stats.RuntimeStats;
import org.geowebcache.util.ServletUtils;

public class WMTSGetFeatureInfo {

    private static Log log = LogFactory.getLog(org.geowebcache.service.wmts.WMTSService.class);

    private ConveyorTile convTile;

    int i;

    int j;

    protected WMTSGetFeatureInfo(ConveyorTile convTile) throws OWSException {

        String[] keys = { "i", "j" };

        Map<String, String> values;
        values = ServletUtils.selectedStringsFromMap(convTile.servletReq.getParameterMap(),
                convTile.servletReq.getCharacterEncoding(), keys);

        try {
            i = Integer.parseInt(values.get("i"));
        } catch (NumberFormatException nfe) {
            throw new OWSException(400, "MissingParameterValue", "I", "I was not specified");
        }

        try {
            j = Integer.parseInt(values.get("j"));
        } catch (NumberFormatException nfe) {
            throw new OWSException(400, "MissingParameterValue", "J", "J was not specified");
        }

        this.convTile = convTile;
    }

    protected void writeResponse(RuntimeStats stats) throws OWSException {
        TileLayer layer = convTile.getLayer();

        GridSet gridSet = convTile.getGridSubset().getGridSet();
        if (gridSet.getTileHeight() < j || j < 0) {
            throw new OWSException(400, "PointIJOutOfRange", "J", "J was " + j
                    + ", must be between 0 and " + gridSet.getTileHeight());
        }

        if (gridSet.getTileWidth() < i || i < 0) {
            throw new OWSException(400, "PointIJOutOfRange", "I", "I was " + i
                    + ", must be between 0 and " + gridSet.getTileWidth());
        }

        Resource data = null;
        try {
            BoundingBox bbox = convTile.getGridSubset().boundsFromIndex(convTile.getTileIndex());
            data = layer.getFeatureInfo(convTile, bbox, convTile.getGridSubset().getTileHeight(),
                    convTile.getGridSubset().getTileWidth(), i, j);
        } catch (GeoWebCacheException e) {
            throw new OWSException(500, "NoApplicableCode", "", e.getMessage());
        }

        convTile.servletResp.setStatus(HttpServletResponse.SC_OK);
        convTile.servletResp.setContentType(convTile.getMimeType().getMimeType());
        int size = (int) data.getSize();
        convTile.servletResp.setContentLength(size);

        stats.log(size, CacheResult.OTHER);

        try {
            OutputStream os = convTile.servletResp.getOutputStream();
            data.transferTo(Channels.newChannel(os));
            os.flush();
        } catch (IOException ioe) {
            log.debug("Caught IOException" + ioe.getMessage());
        }

    }
}
