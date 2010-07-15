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
import java.io.InputStream;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.TileResponseReceiver;
import org.geowebcache.mime.ErrorMime;
import org.geowebcache.service.ServiceException;
import org.geowebcache.util.GWCVars;
import org.geowebcache.util.ServletUtils;

/**
 * This class is a wrapper for HTTP interaction with WMS backend
 * 
 * All methods in this class MUST be written as if they were static
 * 
 */
public class WMSHttpHelper extends WMSSourceHelper {
    private static Log log = LogFactory.getLog(org.geowebcache.layer.wms.WMSHttpHelper.class);
    
    private final AuthScope authscope;
    
    private final UsernamePasswordCredentials credentials;
    
    public WMSHttpHelper() {
        authscope = null;
        credentials = null;
    }
    
    public WMSHttpHelper(String username, String password) {
        // Let the authentication be valid for any host
        authscope = new AuthScope(null, -1);
        credentials = new UsernamePasswordCredentials(username, password);
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
    protected byte[] makeRequest(TileResponseReceiver tileRespRecv, 
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
            
            data = connectAndCheckHeaders(tileRespRecv, wmsBackendUrl, wmsParams, expectedMimeType,
                    layer.backendTimeout, layer.getHttpUsername(), layer.getHttpPassword());

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
    private byte[] connectAndCheckHeaders(
            TileResponseReceiver tileRespRecv, URL wmsBackendUrl,
            String wmsParams, String requestMime, int backendTimeout, String username, String password) 
    throws GeoWebCacheException {
        
        byte[] ret = null;
        GetMethod getMethod = null;
        int responseCode = -1;
        int responseLength = -1;

        try { // finally
            try {
                getMethod = executeRequest(wmsBackendUrl, backendTimeout);
                responseCode = getMethod.getStatusCode();
                responseLength = (int) getMethod.getResponseContentLength();

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
            String responseMime = getMethod.getResponseHeader("Content-Type").getValue();
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
                        InputStream inStream = getMethod.getResponseBodyAsStream();
                        while(readLength > -1 && readAccu < error.length) {
                            int left = error.length - readAccu;
                            readLength = inStream.read(error,readAccu, left);
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
                String expireValue = getMethod.getResponseHeader("Expires").getValue();
                long expire = ServletUtils.parseExpiresHeader(expireValue);
                if(expire != -1) {
                    tileRespRecv.setExpiresHeader(expire / 1000);
                }
            }

            // Read the actual data
            if (responseCode != 204) {
                try {
                    if (responseLength < 1) {
                        ret = ServletUtils.readStream(getMethod.getResponseBodyAsStream(), 16384, 2048);
                    } else {
                        ret = new byte[responseLength];
                        int readLength = 0;
                        int readAccu = 0;
                        InputStream inStream = getMethod.getResponseBodyAsStream();
                        while(readLength > -1 && readAccu < responseLength) {
                            int left = responseLength - readAccu;
                            readLength = inStream.read(ret,readAccu,left);
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
            if(getMethod!=null)
                getMethod.releaseConnection();
        }

        return ret;
    }
    
    /**
     * sets up a HTTP GET request to a URL and configures authentication and timeouts if possible
     * 
     * @param url endpoint to talk to
     * @param backendTimeout timeout to use in seconds
     * @return executed GetMethod (that has to be closed after reading the response!)
     * @throws HttpException
     * @throws IOException
     */
    public GetMethod executeRequest(URL url, int backendTimeout) 
    throws HttpException, IOException {
        HttpClient httpClient = new HttpClient();
        GetMethod getMethod = new GetMethod(url.toString());
        
        HttpConnectionParams params = httpClient.getHttpConnectionManager().getParams();
        params.setConnectionTimeout(backendTimeout * 1000);
        params.setSoTimeout(backendTimeout * 1000);

        if (authscope != null) {
            httpClient.getState().setCredentials(authscope, credentials);
            getMethod.setDoAuthentication(true);
            httpClient.getParams().setAuthenticationPreemptive(true);
        }
        
        httpClient.executeMethod(getMethod);

        return getMethod;
    }
}
