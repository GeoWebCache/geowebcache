/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 */
package org.geowebcache.service.gmaps;

import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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

/** Class to convert from Google Maps coordinates into the internal representation of a tile. */
public class GMapsConverter extends Service {
    public static final String SERVICE_GMAPS = "gmaps";

    private StorageBroker sb;

    private TileLayerDispatcher tld;

    private GridSetBroker gsb;

    /** Protected no-argument constructor to allow run-time instrumentation */
    protected GMapsConverter() {
        super(SERVICE_GMAPS);
    }

    public GMapsConverter(StorageBroker sb, TileLayerDispatcher tld, GridSetBroker gsb) {
        super(SERVICE_GMAPS);

        this.sb = sb;
        this.tld = tld;
        this.gsb = gsb;
    }

    @Override
    public ConveyorTile getConveyor(HttpServletRequest request, HttpServletResponse response)
            throws ServiceException, GeoWebCacheException {
        String layerId = super.getLayersParameter(request);

        String encoding = request.getCharacterEncoding();

        Map<String, String[]> params = request.getParameterMap();
        String strFormat = ServletUtils.stringFromMap(params, encoding, "format");
        String strZoom = ServletUtils.stringFromMap(params, encoding, "zoom");
        String strX = ServletUtils.stringFromMap(params, encoding, "x");
        String strY = ServletUtils.stringFromMap(params, encoding, "y");
        String strCached = ServletUtils.stringFromMap(params, encoding, "cached");
        String strMetaTiled = ServletUtils.stringFromMap(params, encoding, "metatiled");

        long[] gridLoc =
                GMapsConverter.convert(
                        Integer.parseInt(strZoom), Integer.parseInt(strX), Integer.parseInt(strY));

        String layers = ServletUtils.stringFromMap(params, encoding, "layers");
        if (layers == null || layers.length() == 0) {
            layers = ServletUtils.stringFromMap(params, encoding, "layer");
        }

        TileLayer tileLayer = tld.getTileLayer(layers);
        Map<String, String> filteringParameters =
                tileLayer.getModifiableParameters(params, encoding);

        MimeType mimeType = null;
        try {
            if (strFormat == null) {
                strFormat = "image/png";
            }
            mimeType = MimeType.createFromFormat(strFormat);
        } catch (MimeException me) {
            throw new ServiceException("Unable to determine requested format, " + strFormat);
        }

        ConveyorTile ret =
                new ConveyorTile(
                        sb,
                        layerId,
                        gsb.getWorldEpsg3857().getName(),
                        gridLoc,
                        mimeType,
                        filteringParameters,
                        request,
                        response);

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
                        "Layer "
                                + tile.getLayerId()
                                + " is not configured to allow bypassing the cache.");
            }

            tile.setTileLayer(tl);
            tl.getNoncachedTile(tile);

            Service.writeTileResponse(tile, false);
        }
    }

    /**
     * Convert Google's tiling coordinates into an {x,y,x}
     *
     * <p>see http://code.google.com/apis/maps/documentation/overlays.html#Custom_Map_Types
     */
    public static long[] convert(long zoomLevel, long x, long y) throws ServiceException {
        // Extent is the total number of tiles in y direction
        long extent = (long) Math.pow(2, zoomLevel);

        if (x < 0 || x > extent - 1) {
            throw new ServiceException("The X coordinate is not sane: " + x);
        }

        if (y < 0 || y > extent - 1) {
            throw new ServiceException("The Y coordinate is not sane: " + y);
        }

        // xPos and yPos correspond to the top left hand corner
        long[] gridLoc = {x, extent - y - 1, zoomLevel};

        return gridLoc;
    }
}
