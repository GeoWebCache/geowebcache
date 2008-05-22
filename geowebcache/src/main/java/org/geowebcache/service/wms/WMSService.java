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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.SRS;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.layer.TileRequest;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.service.Service;
import org.geowebcache.service.ServiceException;
import org.geowebcache.service.ServiceRequest;
import org.geowebcache.util.ServletUtils;
import org.geowebcache.util.wms.BBOX;

public class WMSService extends Service {
    public static final String SERVICE_WMS = "wms";

    private static Log log = LogFactory
            .getLog(org.geowebcache.service.wms.WMSService.class);

    public WMSService() {
        super(SERVICE_WMS);
    }

    public ServiceRequest getServiceRequest(HttpServletRequest request)
            throws ServiceException {
        String[] keys = { "layers", "request" };
        Map<String, String> values = ServletUtils.selectedStringsFromMap(
                request.getParameterMap(), keys);

        // Look for getCapabilities
        String req = values.get(keys[1]);
        if (req != null && req.equalsIgnoreCase("getcapabilities")) {
            String[] data = { "getcapabilities" };
            ServiceRequest servReq = new ServiceRequest(null, data);
            servReq.setFlag(true, ServiceRequest.SERVICE_REQUEST_DIRECT);
            return servReq;
        }

        // Look for layer
        String layers = values.get(keys[0]);
        if (layers == null) {
            throw new ServiceException(
                    "Unable to parse layers parameter from request.");
        }

        return new ServiceRequest(layers);
    }

    public TileRequest getTileRequest(TileLayer tileLayer,
            ServiceRequest servReq, HttpServletRequest request)
            throws GeoWebCacheException {

        WMSParameters wmsParams = new WMSParameters(request);

        MimeType mime = null;
        String strFormat = wmsParams.getFormat();
        try {
            mime = MimeType.createFromFormat(strFormat);
        } catch (MimeException me) {
            throw new ServiceException(
                    "Unable to determined requested format, " + strFormat);
        }

        if (wmsParams.getSrs() == null) {
            throw new ServiceException("No SRS specified");
        }

        SRS srs = new SRS(wmsParams.getSrs());
        int srsIdx = tileLayer.getSRSIndex(srs);
        if (srsIdx < 0) {
            throw new ServiceException("Unable to match requested SRS "
                    + wmsParams.getSrs() + " to those supported by layer");
        }

        BBOX bbox = wmsParams.getBBOX();
        if (bbox == null || !bbox.isSane()) {
            throw new ServiceException(
                    "The bounding box parameter is missing or not sane");
        }

        String strOrigin = wmsParams.getOrigin();
        if (strOrigin != null) {
            String[] split = strOrigin.split(",");
            if (split.length != 2) {
                throw new ServiceException("Unable to parse tilesOrigin,"
                        + "should not be set anyway: " + strOrigin);
            }
            double x = Double.valueOf(split[0]);
            double y = Double.valueOf(split[1]);

            if (Math.abs(x + 180.0) < 0.5 && x + Math.abs(y + 90.0) < 0.5) {
                // ok, fine for EPSG:4326
            } else if (Math.abs(x + 20037508.34) < 1.0
                    && x + Math.abs(y + 20037508.34) < 1.0) {
                // ok, fine for EPSG:9000913
            } else {
                throw new ServiceException("The tilesOrigin parameter "
                        + strOrigin
                        + " is not accepted by GeoWebCache, please omit"
                        + " or use lower left corner of world bounds.");
            }
        }

        return new TileRequest(tileLayer.getGridLocForBounds(srsIdx, bbox),
                mime, srs);
    }

    public void handleRequest(TileLayerDispatcher tLD,
            HttpServletRequest request, ServiceRequest servReq,
            HttpServletResponse response) throws GeoWebCacheException {

        String[] data = servReq.getData();
        if (data != null && data[0].equalsIgnoreCase("getcapabilities")) {
            handleGetCapabilities(tLD, response);
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

        String xml = getCapabilitiesHeader();

        while (iter.hasNext()) {
            TileLayer tl = iter.next();
            if (!tl.isInitialized()) {
                tl.initialize();
            }
            xml += getTileSets(tl);
        }

        xml += getCapabilitiesFooter();

        try {
            writeData(response, xml.getBytes());
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
    private String getTileSets(TileLayer tl) {
        String ret = "";
        SRS[] srsList = tl.getProjections();
        MimeType[] mimeList = tl.getMimeTypes();
        String strStyles = tl.getStyles();
        if(strStyles == null) {
            strStyles = "";
        }
            

        for (int srsIdx = 0; srsIdx < srsList.length; srsIdx++) {
            String strSRS = srsList[srsIdx].toString();
            // These should be adjusted bounds!
            String[] strBounds = doublesToStrings(tl.getBounds(srsIdx).coords);
            String strResolutions = getResolutionString(
                    tl.getResolutions(srsIdx));
            String strName = tl.getName();

            for (int mimeIdx = 0; mimeIdx < mimeList.length; mimeIdx++) {
                String strFormat = mimeList[mimeIdx].getFormat();
                ret += getTileSet(strName, strSRS, strBounds, 
                        strStyles, strResolutions, strFormat);
            }
        }

        return ret;
    }

    private String getTileSet(String strName, String strSRS,
            String[] strBounds, String strStyles, 
            String strResolutions, String strFormat) {
        return "\n<TileSet>" 
            + "<SRS>" + strSRS + "</SRS>"
            + "<BoundingBox srs=\"" + strSRS + "\"" + " minx=\""
            + strBounds[0] + "\"" + " miny=\"" + strBounds[1] + "\""
            + " maxx=\"" + strBounds[2] + "\"" + " maxy=\"" + strBounds[3] + "\" />" 
            + "<Resolutions>" + strResolutions + "</Resolutions>"
            + "<Width>256</Width>" 
            + "<Height>256</Height>" 
            + "<Format>" + strFormat + "</Format>" 
            + "<Layers>" + strName + "</Layers>"
            + "<Styles>" + strStyles + "</Styles>" 
            + "</TileSet>";
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
