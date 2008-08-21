/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 * 
 */
package org.geowebcache.service.wms;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.Grid;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.service.Service;
import org.geowebcache.service.ServiceException;
import org.geowebcache.tile.Tile;
import org.geowebcache.util.ServletUtils;
import org.geowebcache.util.wms.BBOX;

public class WMSService extends Service {
    public static final String SERVICE_WMS = "wms";

    private static Log log = LogFactory
            .getLog(org.geowebcache.service.wms.WMSService.class);

    public WMSService() {
        super(SERVICE_WMS);
    }

    public Tile getTile(HttpServletRequest request, HttpServletResponse response)
            throws GeoWebCacheException {
        String[] keys = { "layers", "request" };
        String[] values = ServletUtils.selectedStringsFromMap(request
                .getParameterMap(), keys);

        // Look for getCapabilities
        String req = values[1];
        if (req != null && req.equalsIgnoreCase("getcapabilities")) {
            Tile tile = new Tile(values[0], request, response);
            tile.setHint("getcapabilities");
            tile.setRequestHandler(Tile.RequestHandler.SERVICE);
            return tile;
        }

        // Look for layer
        String layers = values[0];
        if (layers == null) {
            throw new ServiceException(
                    "Unable to parse layers parameter from request.");
        }

        TileLayer tileLayer = Service.tlDispatcher.getTileLayer(layers);

        WMSParameters wmsParams = new WMSParameters(request);
        MimeType mimeType = null;
        String strFormat = wmsParams.getFormat();

        try {
            mimeType = MimeType.createFromFormat(strFormat);
        } catch (MimeException me) {
            throw new ServiceException("Unable to determine requested format, "
                    + strFormat);
        }

        if (wmsParams.getSrs() == null) {
            throw new ServiceException("No SRS specified");
        }

        SRS srs = wmsParams.getSrs();
        //int srsIdx = tileLayer.getSRSIndex(srs);

        if (tileLayer.supportsProjection(srs)) {
            throw new ServiceException("Unable to match requested SRS "
                    + wmsParams.getSrs() + " to those supported by layer");
        }

        BBOX bbox = wmsParams.getBBOX();
        if (bbox == null || !bbox.isSane()) {
            throw new ServiceException(
                    "The bounding box parameter is missing or not sane");
        }

        int[] tileIndex = tileLayer.getGridLocForBounds(srs, bbox);
        
        // String strOrigin = wmsParams.getOrigin();
        // if (strOrigin != null) {
        // String[] split = strOrigin.split(",");
        // if (split.length != 2) {
        // throw new ServiceException("Unable to parse tilesOrigin,"
        // + "should not be set anyway: " + strOrigin);
        // }
        // double x = Double.valueOf(split[0]);
        // double y = Double.valueOf(split[1]);
        //
        // if (Math.abs(x + 180.0) < 0.5 && x + Math.abs(y + 90.0) < 0.5) {
        // // ok, fine for EPSG:4326
        // } else if (Math.abs(x + 20037508.34) < 1.0
        // && x + Math.abs(y + 20037508.34) < 1.0) {
        // // ok, fine for EPSG:9000913
        // } else {
        // throw new ServiceException("The tilesOrigin parameter "
        // + strOrigin
        // + " is not accepted by GeoWebCache, please omit"
        // + " or use lower left corner of world bounds.");
        // }
        // }

        return new Tile(layers, srs, tileIndex, mimeType, request, response);
    }

    public void handleRequest(TileLayerDispatcher tLD, Tile tile)
            throws GeoWebCacheException {

        if (tile.hint != null && tile.hint.equalsIgnoreCase("getcapabilities")) {
            handleGetCapabilities(tLD, tile.servletResp);
        } else {
            throw new GeoWebCacheException(
                    "The WMS Service would love to help,"
                            + " but has no idea what you're trying to do?"
                            + "Please include request URL in your ");
        }
    }

    /**
     * Creates getCapabilities response by looping over tilelayers
     * 
     * @param tLD
     * @param response
     * @throws GeoWebCacheException
     */
    private void handleGetCapabilities(TileLayerDispatcher tLD,
            HttpServletResponse response) throws GeoWebCacheException {
        Map<String, TileLayer> layerMap = tLD.getLayers();
        Iterator<TileLayer> iter = layerMap.values().iterator();

        StringBuffer buf = new StringBuffer();
        buf.append(getCapabilitiesHeader());

        while (iter.hasNext()) {
            TileLayer tl = iter.next();
            if (!tl.isInitialized()) {
                // ooops ? (Always returns true :) )
            }
            buf.append(getTileSets(tl));
        }

        buf.append(getCapabilitiesFooter());

        try {
            writeData(response, buf.toString().getBytes());
        } catch (IOException ioe) {
            throw new GeoWebCacheException("Error doing getCapabilities: "
                    + ioe.getMessage());
        }

    }

    private static String getCapabilitiesHeader() {
        return "<WMT_MS_Capabilities version=\"1.1.1\" updateSequence=\"0\">\n"
                + "<VendorSpecificCapabilities>";
    }

    private static String getCapabilitiesFooter() {
        return "\n</VendorSpecificCapabilities>" + "\n</WMT_MS_Capabilities>";
    }

    /**
     * 
     * @param tl
     * @return
     */
    private String getTileSets(TileLayer tl) throws GeoWebCacheException {
        String ret = "";
        
        List<MimeType> mimeList = tl.getMimeTypes();
        String strStyles = tl.getStyles();
        if (strStyles == null) {
            strStyles = "";
        }

        Iterator<Grid> iter = tl.getGrids().values().iterator();
        while(iter.hasNext()) {
            Grid grid = iter.next();
            
            // These should be adjusted bounds!
            String[] strBounds = doublesToStrings(grid.getBounds().coords);
            String strResolutions = getResolutionString(grid.getResolutions());
            String strName = tl.getName();

            for (MimeType mime : mimeList) {
                String strFormat = mime.getFormat();
                ret += getTileSet(strName, grid.getSRS().toString(), strBounds, strStyles,
                        strResolutions, strFormat);
            }
        }

        return ret;
    }

    private String getTileSet(String strName, String strSRS,
            String[] strBounds, String strStyles, String strResolutions,
            String strFormat) {
        return "\n<TileSet>" + "<SRS>" + strSRS + "</SRS>"
                + "<BoundingBox srs=\"" + strSRS + "\"" + " minx=\""
                + strBounds[0] + "\"" + " miny=\"" + strBounds[1] + "\""
                + " maxx=\"" + strBounds[2] + "\"" + " maxy=\"" + strBounds[3]
                + "\" />" + "<Resolutions>" + strResolutions + "</Resolutions>"
                + "<Width>256</Width>" + "<Height>256</Height>" + "<Format>"
                + strFormat + "</Format>" + "<Layers>" + strName + "</Layers>"
                + "<Styles>" + strStyles + "</Styles>" + "</TileSet>";
    }

    private String[] doublesToStrings(double[] doubles) {
        String[] ret = new String[doubles.length];
        for (int i = 0; i < doubles.length; i++) {
            ret[i] = Double.toString(doubles[i]);
        }
        return ret;
    }

    private String getResolutionString(double[] resolutions) {
        String ret = "";
        for (int i = 0; i < resolutions.length; i++) {
            ret += Double.toString(resolutions[i]) + " ";
        }
        return ret;
    }

    /**
     * Should only be used for getCapabilities
     * 
     * @param response
     * @param data
     * @throws IOException
     */
    private void writeData(HttpServletResponse response, byte[] data)
            throws IOException {

        // Did we get anything?
        if (data == null || data.length == 0) {
            log.trace("sendData() had nothing to return");
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        log.trace("sendData() Sending data.");
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/vnd.ogc.wms_xml");
        response.setContentLength(data.length);
        try {
            OutputStream os = response.getOutputStream();
            os.write(data);
            os.flush();
        } catch (IOException ioe) {
            log.debug("Caught IOException" + ioe.getMessage());
        }
    }
}
