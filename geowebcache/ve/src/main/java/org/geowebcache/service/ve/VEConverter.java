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
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 */
package org.geowebcache.service.ve;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.service.Service;
import org.geowebcache.service.ServiceException;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.util.ServletUtils;

/** Class to convert from Virtual Earth quad keys to the internal representation of a tile. */
public class VEConverter extends Service {
    public static final String SERVICE_VE = "ve";

    private static Logger log = Logging.getLogger(org.geowebcache.service.ve.VEConverter.class.getName());

    private StorageBroker sb;

    private TileLayerDispatcher tld;

    private GridSetBroker gsb;

    /** Protected no-argument constructor to allow run-time instrumentation */
    protected VEConverter() {
        super(SERVICE_VE);
    }

    public VEConverter(StorageBroker sb, TileLayerDispatcher tld, GridSetBroker gsb) {
        super(SERVICE_VE);
        this.sb = sb;
        this.tld = tld;
        this.gsb = gsb;
    }

    @Override
    public ConveyorTile getConveyor(HttpServletRequest request, HttpServletResponse response) throws ServiceException {
        Map<String, String[]> params = request.getParameterMap();

        String layerId = super.getLayersParameter(request);

        String encoding = request.getCharacterEncoding();

        String strQuadKey = ServletUtils.stringFromMap(params, encoding, "quadkey");
        String strFormat = ServletUtils.stringFromMap(params, encoding, "format");
        String strCached = ServletUtils.stringFromMap(params, encoding, "cached");
        String strMetaTiled = ServletUtils.stringFromMap(params, encoding, "metatiled");

        long[] gridLoc = VEConverter.convert(strQuadKey);

        MimeType mimeType = null;
        if (strFormat != null) {
            try {
                mimeType = MimeType.createFromFormat(strFormat);
            } catch (MimeException me) {
                throw new ServiceException("Unable to determined requested format, " + strFormat);
            }
        }

        ConveyorTile ret = new ConveyorTile(
                sb, layerId, gsb.getWorldEpsg3857().getName(), gridLoc, mimeType, null, request, response);

        if (strCached != null && !Boolean.parseBoolean(strCached)) {
            ret.setRequestHandler(ConveyorTile.RequestHandler.SERVICE);
            if (strMetaTiled != null && !Boolean.parseBoolean(strMetaTiled)) {
                ret.setHint("not_cached,not_metatiled");
            } else {
                ret.setHint("not_cached");
            }
        }

        return ret;
    }

    /** NB The following code is shared across Google Maps, Mobile Google Maps and Virtual Earth */
    public void handleRequest(ConveyorTile tile) throws GeoWebCacheException {
        if (tile.getHint() != null) {
            // boolean requestTiled = true;
            if (!tile.getHint().equals("not_cached,not_metatiled")
                    && !tile.getHint().equals("not_cached")) {
                throw new GeoWebCacheException("Hint " + tile.getHint() + " is not known.");
            }

            TileLayer tl = tld.getTileLayer(tile.getLayerId());

            if (tl == null) {
                throw new GeoWebCacheException("Unknown layer " + tile.getLayerId());
            }

            if (!tl.isCacheBypassAllowed().booleanValue()) {
                throw new GeoWebCacheException(
                        "Layer " + tile.getLayerId() + " is not configured to allow bypassing the cache.");
            }

            tile.setTileLayer(tl);
            tl.getNoncachedTile(tile);

            Service.writeTileResponse(tile, false);
        }
    }

    /**
     * Convert a quadkey into the internal representation {x,y,z} of a grid location
     *
     * @return internal representation
     */
    @SuppressWarnings("PMD.EmptyControlStatement")
    public static long[] convert(String strQuadKey) {
        char[] quadArray = strQuadKey.toCharArray();

        long zoomLevel = quadArray.length;

        long extent = (long) Math.pow(2, zoomLevel);
        long yPos = 0;
        long xPos = 0;

        // Now we traverse the quadArray from left to right, interpretation
        // 0 1
        // 2 3
        // see http://msdn2.microsoft.com/en-us/library/bb259689.aspx
        //
        // What we'll end up with is the top left hand corner of the bbox
        //
        for (char curChar : quadArray) {
            // For each round half as much is at stake
            extent = extent / 2;

            if (curChar == '0') {
                // X stays
                yPos += extent;
            } else if (curChar == '1') {
                xPos += extent;
                yPos += extent;
            } else if (curChar == '2') {
                // X stays
                // Y stays
            } else if (curChar == '3') {
                xPos += extent;
                // Y stays
            } else {
                log.log(Level.SEVERE, "Don't know how to interpret quadKey: " + strQuadKey);
                return null;
            }
        }

        long[] gridLoc = {xPos, yPos, zoomLevel};

        return gridLoc;
    }
}
