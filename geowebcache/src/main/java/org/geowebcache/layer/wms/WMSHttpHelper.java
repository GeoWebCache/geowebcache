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
package org.geowebcache.layer.wms;

import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubSet;
import org.geowebcache.layer.TileResponseReceiver;
import org.geowebcache.mime.ErrorMime;
import org.geowebcache.service.ServiceException;
import org.geowebcache.util.GWCVars;
import org.geowebcache.util.ServletUtils;

/**
 * This class is a wrapper for HTTP interaction with WMS backend
 */
public class WMSHttpHelper {
    private static Log log = LogFactory.getLog(org.geowebcache.layer.wms.WMSHttpHelper.class);
    
    /**
     * Used for metatiling requests
     * 
     * @param metaTile
     * @return
     * @throws GeoWebCacheException
     */
    protected static byte[] makeRequest(WMSMetaTile metaTile)
            throws GeoWebCacheException {
        
        String wmsParams = metaTile.getWMSParams();
        WMSLayer layer = metaTile.getLayer();
        
        return makeRequest(metaTile, layer, wmsParams, metaTile.getRequestFormat().getFormat());
    }

    /**
     * Used for a non-metatiling requests
     * 
     * @param tile
     * @return
     * @throws GeoWebCacheException
     */
    protected static byte[] makeRequest(ConveyorTile tile) throws GeoWebCacheException {
        return makeRequest(tile, false);
    }

    protected static byte[] makeRequest(ConveyorTile tile, boolean requestTiled) throws GeoWebCacheException {
        WMSLayer layer = (WMSLayer) tile.getLayer();
        
        GridSubSet gridSubSet = tile.getGridSubSet();
        
        String wmsParams = layer.getWMSRequestTemplate(tile.getMimeType());

        StringBuilder strBuilder = new StringBuilder(wmsParams);
        
        strBuilder.append("&FORMAT=").append(tile.getMimeType().getFormat());
        strBuilder.append("&SRS=").append(gridSubSet.getSRS().toString());
        strBuilder.append("&HEIGHT=").append(gridSubSet.getTileHeight());
        strBuilder.append("&WIDTH=").append(gridSubSet.getTileWidth());
        strBuilder.append("&TILED=").append(requestTiled);
        
        BoundingBox bbox = gridSubSet.boundsFromIndex(tile.getTileIndex());
        
        strBuilder.append("&BBOX=").append(bbox);
        
        if(requestTiled) {
            strBuilder.append("&TILED=").append("TRUE");
        }
        
        strBuilder.append(tile.getFullParameters());
        
        return makeRequest(tile, layer, strBuilder.toString(), tile.getMimeType().getMimeType());
    }

    /**
     * Loops over the different backends, tries the request
     * 
     * @param tileRespRecv
     * @param profile
     * @param wmsparams
     * @return
     * @throws GeoWebCacheException
     */
    private static byte[] makeRequest(TileResponseReceiver tileRespRecv,
            WMSLayer layer, String wmsParams, String expectedMimeType)
            throws GeoWebCacheException {
        byte[] data = null;
        URL wmsBackendUrl = null;

        int backendTries = 0; // keep track of how many backends we have tried
        while (data == null && backendTries < layer.getWMSurl().length) {
            String requestUrl = layer.nextWmsURL() + wmsParams;
            
            try {
                wmsBackendUrl = new URL(requestUrl);
            } catch (MalformedURLException maue) {
                throw new GeoWebCacheException("Malformed URL: "
                        + requestUrl + " " + maue.getMessage());
            }
            
            data = connectAndCheckHeaders(tileRespRecv, wmsBackendUrl, wmsParams, expectedMimeType, layer.backendTimeout);

            backendTries++;
        }

        if (data == null) {
            String msg = "All backends (" + backendTries + ") failed, "
                    + "last one: " + wmsBackendUrl.toString() + "\n\n"
                    + tileRespRecv.getErrorMessage();

            tileRespRecv.setError();
            tileRespRecv.setErrorMessage(msg);
            throw new GeoWebCacheException(msg);
        }
        return data;
    }

    /**
     * Executes the actual HTTP request, checks the response headers (status and
     * MIME) and
     * 
     * @param tileRespRecv
     * @param wmsBackendUrl
     * @param wmsparams
     * @return
     * @throws GeoWebCacheException
     */
    private static byte[] connectAndCheckHeaders(
            TileResponseReceiver tileRespRecv, URL wmsBackendUrl,
            String wmsParams, String requestMime, int backendTimeout) 
    throws GeoWebCacheException {
        
        byte[] ret = null;
        HttpURLConnection wmsBackendCon = null;
        int responseCode = -1;
        int responseLength = -1;

        try { // finally
            try {
                wmsBackendCon = (HttpURLConnection) wmsBackendUrl.openConnection();
                wmsBackendCon.setConnectTimeout(backendTimeout * 1000);
                wmsBackendCon.setReadTimeout(backendTimeout * 1000);
                
                responseCode = wmsBackendCon.getResponseCode();
                responseLength = wmsBackendCon.getContentLength();

                // Do not set error at this stage
            } catch (ConnectException ce) {
                log.error("Error forwarding request "
                        + wmsBackendUrl.toString() + " " + ce.getMessage());
                return null;
            } catch (IOException ioe) {
                log.error("Error forwarding request "
                        + wmsBackendUrl.toString() + " " + ioe.getMessage());
                return null;
            }

            // Check that the response code is okay
            tileRespRecv.setStatus(responseCode);
            if (responseCode != 200 && responseCode != 204) {
                tileRespRecv.setError();
                throw new ServiceException(
                        "Unexpected response code from backend: " + responseCode
                                + " for " + wmsBackendUrl.toString());
            }

            // Check that we're not getting an error MIME back.
            String responseMime = wmsBackendCon.getContentType();
            if (responseCode != 204
                    && responseMime != null
	            && ! mimeStringCheck(requestMime,responseMime)) {
                String message = null;
                if (responseMime.equalsIgnoreCase(ErrorMime.vnd_ogc_se_inimage
                        .getFormat())) {
                    byte[] error = new byte[2048];
                    try {
                        int readLength = 0;
                        int readAccu = 0;
                        while(readLength > -1 && readAccu < error.length) {
                            int left = error.length - readAccu;
                            readLength = wmsBackendCon.getInputStream().read(error,readAccu, left);
                            readAccu += readLength;
                        }
                    } catch (IOException ioe) {
                        // Do nothing
                    }
                    message = new String(error);
                }
                String msg = "MimeType mismatch, expected " + requestMime
                        + " but got " + responseMime + " from "
                        + wmsBackendUrl.toString() + "\n\n" + message;
                tileRespRecv.setError();
                tileRespRecv.setErrorMessage(msg);
                log.error(msg);
            }

            // Everything looks okay, try to save expiration
            if (tileRespRecv.getExpiresHeader() == GWCVars.CACHE_USE_WMS_BACKEND_VALUE) {
                String expireValue = wmsBackendCon.getHeaderField("Expires");
                long expire = ServletUtils.parseExpiresHeader(expireValue);
                if(expire != -1) {
                    tileRespRecv.setExpiresHeader(expire / 1000);
                }
            }

            // Read the actual data
            if (responseCode != 204) {
                try {
                    if (responseLength < 1) {
                        ret = ServletUtils.readStream(wmsBackendCon.getInputStream(), 16384, 2048);
                    } else {
                        ret = new byte[responseLength];
                        int readLength = 0;
                        int readAccu = 0;
                        while(readLength > -1 && readAccu < responseLength) {
                            int left = responseLength - readAccu;
                            readLength = wmsBackendCon.getInputStream().read(ret,readAccu,left);
                            readAccu += readLength;
                        }
                        if (readAccu != responseLength) {
                            tileRespRecv.setError();
                            throw new GeoWebCacheException(
                                    "Responseheader advertised "+ responseLength
                                    + " bytes, but only received " + readLength 
                                    + " from " + wmsBackendUrl.toString());
                        }
                    }
                } catch (IOException ioe) {
                    tileRespRecv.setError();
                    log.error("Caught IO exception, " 
                            + wmsBackendUrl.toString() + " " + ioe.getMessage());
                }
            } else {
                ret = new byte[0];
            }

        } finally {
            wmsBackendCon.disconnect();
        }

        return ret;
    }
    
    private static boolean mimeStringCheck(String requestMime, String responseMime) {
        if(responseMime.equalsIgnoreCase(requestMime)) {
            return true;
        } else {
            if(requestMime.startsWith("image/png") && responseMime.startsWith("image/png")) {
                return true;
            }
        }
        return false;
    }
}
