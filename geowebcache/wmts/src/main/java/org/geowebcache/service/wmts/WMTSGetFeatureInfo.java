/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * <p>Copyright 2019
 */
package org.geowebcache.service.wmts;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.util.Map;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
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

    private static Logger log = Logging.getLogger("org.geowebcache.service.wmts.WMTSService");

    private ConveyorTile convTile;

    int i;

    int j;

    protected WMTSGetFeatureInfo(ConveyorTile convTile) throws OWSException {

        String[] keys = {"i", "j"};

        Map<String, String> values = ServletUtils.selectedStringsFromMap(
                convTile.getRequestParameters(), convTile.servletReq.getCharacterEncoding(), keys);

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
        if (gridSet.getTileHeight() <= j || j < 0) {
            throw new OWSException(
                    400, "PointIJOutOfRange", "J", "J was " + j + ", must be between 0 and " + gridSet.getTileHeight());
        }

        if (gridSet.getTileWidth() <= i || i < 0) {
            throw new OWSException(
                    400, "PointIJOutOfRange", "I", "I was " + i + ", must be between 0 and " + gridSet.getTileWidth());
        }

        Resource data = null;
        try {
            BoundingBox bbox = convTile.getGridSubset().boundsFromIndex(convTile.getTileIndex());
            data = layer.getFeatureInfo(
                    convTile,
                    bbox,
                    convTile.getGridSubset().getTileHeight(),
                    convTile.getGridSubset().getTileWidth(),
                    i,
                    j);
        } catch (GeoWebCacheException e) {
            throw new OWSException(500, "NoApplicableCode", "", e.getMessage());
        }

        convTile.servletResp.setStatus(HttpServletResponse.SC_OK);
        convTile.servletResp.setContentType(convTile.getMimeType().getMimeType());
        int size = (int) data.getSize();
        convTile.servletResp.setContentLength(size);

        stats.log(size, CacheResult.OTHER);

        try {
            @SuppressWarnings("PMD.CloseResource") // managed by servlet container
            OutputStream os = convTile.servletResp.getOutputStream();
            data.transferTo(Channels.newChannel(os));
            os.flush();
        } catch (IOException ioe) {
            log.fine("Caught IOException" + ioe.getMessage());
        }
    }
}
