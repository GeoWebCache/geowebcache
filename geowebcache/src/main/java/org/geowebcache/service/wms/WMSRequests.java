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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.Buffer;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.Grid;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.mime.MimeType;
import org.geowebcache.tile.Tile;
import org.geowebcache.util.Configuration;

public class WMSRequests {
    private static Log log = LogFactory.getLog(org.geowebcache.service.wms.WMSRequests.class);

    // The following two are only needed for the temporary getcapabilities code
    protected static List<Configuration> getCapConfigs;

    static String getCapsStr;
    
    /**
     * Creates getCapabilities response by looping over tile layers
     * 
     * @param tLD
     * @param response
     * @throws GeoWebCacheException
     */
    static void handleGetCapabilities(TileLayerDispatcher tLD,
            HttpServletResponse response) throws GeoWebCacheException {
        Map<String, TileLayer> layerMap = tLD.getLayers();
        Iterator<TileLayer> iter = layerMap.values().iterator();

        StringBuffer buf = new StringBuffer();
        buf.append(getCapabilitiesHeader());

        while (iter.hasNext()) {
            TileLayer tl = iter.next();
            tl.isInitialized();
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

    private static String getCapabilitiesHeader() throws GeoWebCacheException {
        String wms = fetchOriginalWMSCapabilitiesDocument();

        if (wms == null) {
            throw new GeoWebCacheException(
                    "Unable to retrieve original WMS Capabilities document");
        }

        int split = wms.indexOf("<VendorSpecificCapabilities/>");
        if (split != -1) {
            // we have an empty VendorSpecificCapabilities to fill in...
            String header = wms.substring(0, split);
            return header + "\n<VendorSpecificCapabilities>";
        }
        split = wms.indexOf("</VendorSpecificCapabilities>");
        
        // Was never read anyway
        //if (split != -1) {
            // we have an existing VendorSpecificCapabilities to add to
        //    String header = wms.substring(0, split);
        //}
        
        // look for <UserDefinedSymbolization .. VendorSpecificCapabilities goes
        // before this element
        split = wms.indexOf("<UserDefinedSymbolization");
        if (split == -1) {
            // look for <Layer> ... VendorSpecificParameters goes before this
            // element
            split = wms.indexOf("<Layer");
        }
        String header = wms.substring(0, split);

        return header + "\n<VendorSpecificCapabilities>";
    }

    private static String getCapabilitiesFooter() throws GeoWebCacheException {
        // return "\n</VendorSpecificCapabilities>" +
        // "\n</WMT_MS_Capabilities>";
        String wms = fetchOriginalWMSCapabilitiesDocument();
        int split = wms.indexOf("<VendorSpecificCapabilities/>");
        if (split != -1) {
            // we have an empty VendorSpecificCapabilities to fill in...
            String footer = wms.substring(split + 29);
            return "\n</VendorSpecificCapabilities>" + footer;
        }
        split = wms.indexOf("<VendorSpecificCapabilities/>");
        if (split != -1) {
            // we have an existing VendorSpecificCapabilities to add to
            String footer = wms.substring(split + 28);
            return "\n</VendorSpecificCapabilities>" + footer;
        }
        // look for <UserDefinedSymbolization .. VendorSpecificCapabilities goes
        // before this element
        split = wms.indexOf("<UserDefinedSymbolization");
        if (split == -1) {
            // look for <Layer> ... VendorSpecificParameters goes before this
            // element
            split = wms.indexOf("<Layer");
        }
        String footer = wms.substring(split);
        return "\n</VendorSpecificCapabilities>" + footer;
    }

    /**
     * Fetch the original WMS capabilities document (we will add our vendor
     * specific parameters here).
     * <p>
     * Currently this is returned as a String; in the future we can make use of
     * the GeoTools WMSCapabilities data structure (and strip out any layers
     * that are not mentioned explicitly).
     * 
     * @return The original WMS capabilities document prior to processing
     * @throws GeoWebCacheException 
     */
     static synchronized String fetchOriginalWMSCapabilitiesDocument()
            throws GeoWebCacheException {
        if (getCapsStr != null) {
            return getCapsStr;
        }
        StringBuffer buf = new StringBuffer();

        if (getCapConfigs == null) {
            throw new GeoWebCacheException("No configuration object available"
                    + " to use for WMS Capabilities");
        }

        Iterator<Configuration> configIter = getCapConfigs.iterator();

        CONFIG: while (configIter.hasNext()) {
            List<TileLayer> configLayers = null;
            Configuration config = configIter.next();
            try {
                configLayers = config.getTileLayers(false);
            } catch (GeoWebCacheException gwce) {
                log.error(gwce.getMessage());
                log.error("Failed to add layers from " + config.getIdentifier());
            }

            Iterator<TileLayer> iter = null;

            if (configLayers != null) {
                iter = configLayers.iterator();
            }

            while (iter != null && iter.hasNext()) {
                TileLayer layer = iter.next();
                WMSLayer wmsLayer;

                if (!(layer instanceof WMSLayer)) {
                    continue;
                } else {
                    wmsLayer = (WMSLayer) layer;
                }

                String url = wmsLayer.getWMSurl()[0];
                InputStream input = null;
                BufferedReader process = null;
                try {
                    URL capabilitiesURL = new URL(
                            url + "?REQUEST=GetCapabilities&SERVICE=WMS&VESION=1.1.0");
                    URLConnection connection = capabilitiesURL.openConnection();
                    input = connection.getInputStream();
                    InputStreamReader reader = new InputStreamReader(input);
                    process = new BufferedReader(reader);

                    buf = new StringBuffer();
                    String line;
                    while ((line = process.readLine()) != null) {
                        buf.append(line);
                        buf.append("\n");
                    }
                    if (buf.length() != 0) {
                        break CONFIG; // we managed to read a capabilities into buf
                    }
                    /*
                     * // TODO only use the parts of the capabilities file that
                     * // are mentioned in our configuration!
                     * 
                     * WebMapServer wms = new WebMapServer(capabilitiesURL);
                     * WMSCapabilities capabilities = wms.getCapabilities();
                     */
                } catch (Throwable notConnected) {
                    // continue WMSURL
                } finally {
                    if (process != null) {
                        try {
                            process.close();
                        } catch (IOException e) {
                            // Do nothing
                        }
                    }
                    if (input != null) {
                        try {
                            input.close();
                        } catch (IOException ioe) {
                            // Do nothing.
                        }
                    }
                }
            }
        }
        getCapsStr = buf.toString();
        return getCapsStr;
    }

    public static void setConfig(List<Configuration> configs) {
        getCapConfigs = configs;
    }

    /**
     * 
     * @param tl
     * @return
     */
    private static String getTileSets(TileLayer tl) throws GeoWebCacheException {
        String ret = "";

        List<MimeType> mimeList = tl.getMimeTypes();
        String strStyles = tl.getStyles();
        if (strStyles == null) {
            strStyles = "";
        }

        Iterator<Grid> iter = tl.getGrids().values().iterator();
        while (iter.hasNext()) {
            Grid grid = iter.next();

            // These should be adjusted bounds!
            String[] strBounds = doublesToStrings(grid.getBounds().coords);
            String strResolutions = getResolutionString(grid.getResolutions());
            String strName = tl.getName();

            for (MimeType mime : mimeList) {
                String strFormat = mime.getFormat();
                ret += getTileSet(strName, grid.getSRS().toString(), strBounds,
                        strStyles, strResolutions, strFormat);
            }
        }

        return ret;
    }

    private static String getTileSet(String strName, String strSRS,
            String[] strBounds, String strStyles, String strResolutions,
            String strFormat) {
        return "\n<TileSet>" 
            + "<SRS>" + strSRS + "</SRS>"
            + "<BoundingBox srs=\"" + strSRS + "\"" + " minx=\""
            + strBounds[0] + "\"" + " miny=\"" + strBounds[1] + "\""
            + " maxx=\"" + strBounds[2] + "\"" + " maxy=\"" + strBounds[3]
            + "\" />" 
            + "<Resolutions>" + strResolutions + "</Resolutions>"
            + "<Width>256</Width>" 
            + "<Height>256</Height>" 
            + "<Format>" + strFormat + "</Format>" 
            + "<Layers>" + strName + "</Layers>"
            + "<Styles>" + strStyles + "</Styles>" 
            + "</TileSet>";
    }

    private static String[] doublesToStrings(double[] doubles) {
        String[] ret = new String[doubles.length];
        for (int i = 0; i < doubles.length; i++) {
            ret[i] = Double.toString(doubles[i]);
        }
        return ret;
    }

    private static String getResolutionString(double[] resolutions) {
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
    private static void writeData(HttpServletResponse response, byte[] data)
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

    public static void handleProxy(TileLayerDispatcher tld, Tile tile) throws GeoWebCacheException {

        WMSLayer layer = null;
        TileLayer tl = tld.getTileLayer(tile.getLayerId());

        if(tl == null) {
            throw new GeoWebCacheException(tile.getLayerId() + " is unknown.");
        }
        
        if (tl instanceof WMSLayer) {
            layer = (WMSLayer) tl;
        } else {
            throw new GeoWebCacheException(tile.getLayerId()
                    + " is not served by a WMS backend.");
        }

        String queryStr = tile.servletReq.getQueryString();
        String serverStr = layer.getWMSurl()[0];

        try {
            URL url;
            if (serverStr.endsWith("?")) {
                url = new URL(serverStr + queryStr);
            } else {
                url = new URL(serverStr + "?" + queryStr);
            }

            HttpURLConnection wmsBackendCon = (HttpURLConnection) url
                    .openConnection();
            HttpServletResponse response = tile.servletResp;

            if (wmsBackendCon.getContentEncoding() != null) {
                response.setCharacterEncoding(wmsBackendCon
                        .getContentEncoding());
            }

            response.setContentType(wmsBackendCon.getContentType());

            int read = 0;
            byte[] data = new byte[1024];
            while (read > -1) {
                read = wmsBackendCon.getInputStream().read(data);
                if (read > -1) {
                    response.getOutputStream().write(data, 0, read);
                }
            }
        } catch (IOException ioe) {
            tile.servletResp.setStatus(500);
            log.error(ioe.getMessage());
        }
    }
}
