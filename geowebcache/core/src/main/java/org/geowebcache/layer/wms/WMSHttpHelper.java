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
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.TileResponseReceiver;
import org.geowebcache.mime.ErrorMime;
import org.geowebcache.service.ServiceException;
import org.geowebcache.util.GWCVars;
import org.geowebcache.util.HttpClientBuilder;
import org.geowebcache.util.ServletUtils;

/**
 * This class is a wrapper for HTTP interaction with WMS backend
 * 
 * All methods in this class MUST be written as if they were static
 * 
 */
public class WMSHttpHelper extends WMSSourceHelper {
    private static Log log = LogFactory.getLog(org.geowebcache.layer.wms.WMSHttpHelper.class);

    private final URL proxyUrl;

    private final String httpUsername;

    private final String httpPassword;

    public WMSHttpHelper() {
        this(null, null, null);
    }

    public WMSHttpHelper(String httpUsername, String httpPassword, URL proxyUrl) {
        super();
        this.httpUsername = httpUsername;
        this.httpPassword = httpPassword;
        this.proxyUrl = proxyUrl;
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
    @Override
    protected byte[] makeRequest(TileResponseReceiver tileRespRecv, WMSLayer layer,
            String wmsParams, String expectedMimeType) throws GeoWebCacheException {
        byte[] data = null;
        URL wmsBackendUrl = null;

        int backendTries = 0; // keep track of how many backends we have tried
        GeoWebCacheException fetchException = null;
        while (data == null && backendTries < layer.getWMSurl().length) {
            String requestUrl = layer.nextWmsURL() + wmsParams;

            try {
                wmsBackendUrl = new URL(requestUrl);
            } catch (MalformedURLException maue) {
                throw new GeoWebCacheException("Malformed URL: " + requestUrl + " "
                        + maue.getMessage());
            }
            try {
                data = connectAndCheckHeaders(tileRespRecv, wmsBackendUrl, wmsParams,
                        expectedMimeType, layer.getBackendTimeout());
            } catch (GeoWebCacheException e) {
                fetchException = e;
            }

            backendTries++;
        }

        if (data == null) {
            String msg = "All backends (" + backendTries + ") failed.";
            if (fetchException != null) {
                msg += " Reason: " + fetchException.getMessage() + ". ";
            }
            msg += " Last request: '"
                    + wmsBackendUrl.toString()
                    + "'. "
                    + (tileRespRecv.getErrorMessage() == null ? "" : tileRespRecv.getErrorMessage());

            tileRespRecv.setError();
            tileRespRecv.setErrorMessage(msg);
            throw new GeoWebCacheException(msg);
        }
        return data;
    }

    /**
     * Executes the actual HTTP request, checks the response headers (status and MIME) and
     * 
     * @param tileRespRecv
     * @param wmsBackendUrl
     * @param wmsparams
     * @return
     * @throws GeoWebCacheException
     */
    private byte[] connectAndCheckHeaders(TileResponseReceiver tileRespRecv, URL wmsBackendUrl,
            String wmsParams, String requestMime, Integer backendTimeout)
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
            } catch (IOException ce) {
                if (log.isDebugEnabled()) {
                    String message = "Error forwarding request " + wmsBackendUrl.toString();
                    log.debug(message, ce);
                }
                throw new GeoWebCacheException(ce);
            }
            // Check that the response code is okay
            tileRespRecv.setStatus(responseCode);
            if (responseCode != 200 && responseCode != 204) {
                tileRespRecv.setError();
                throw new ServiceException("Unexpected response code from backend: " + responseCode
                        + " for " + wmsBackendUrl.toString());
            }

            // Check that we're not getting an error MIME back.
            String responseMime = getMethod.getResponseHeader("Content-Type").getValue();
            if (responseCode != 204 && responseMime != null
                    && !mimeStringCheck(requestMime, responseMime)) {
                String message = null;
                if (responseMime.equalsIgnoreCase(ErrorMime.vnd_ogc_se_inimage.getFormat())) {
                    byte[] error = new byte[2048];
                    try {
                        int readLength = 0;
                        int readAccu = 0;
                        InputStream inStream = getMethod.getResponseBodyAsStream();
                        while (readLength > -1 && readAccu < error.length) {
                            int left = error.length - readAccu;
                            readLength = inStream.read(error, readAccu, left);
                            readAccu += readLength;
                        }
                    } catch (IOException ioe) {
                        // Do nothing
                    }
                    message = new String(error);
                } else if (responseMime != null
                        && responseMime.startsWith("application/vnd.ogc.se_xml")) {
                    try {
                        message = IOUtils.toString(getMethod.getResponseBodyAsStream());
                    } catch (IOException e) {
                        //
                    }
                }
                String msg = "MimeType mismatch, expected " + requestMime + " but got "
                        + responseMime + " from " + wmsBackendUrl.toString()
                        + (message == null ? "" : (":\n" + message));
                tileRespRecv.setError();
                tileRespRecv.setErrorMessage(msg);
                log.warn(msg);
            }

            // Everything looks okay, try to save expiration
            if (tileRespRecv.getExpiresHeader() == GWCVars.CACHE_USE_WMS_BACKEND_VALUE) {
                String expireValue = getMethod.getResponseHeader("Expires").getValue();
                long expire = ServletUtils.parseExpiresHeader(expireValue);
                if (expire != -1) {
                    tileRespRecv.setExpiresHeader(expire / 1000);
                }
            }

            // Read the actual data
            if (responseCode != 204) {
                try {
                    if (responseLength < 1) {
                        ret = ServletUtils.readStream(getMethod.getResponseBodyAsStream(), 16384,
                                2048);
                    } else {
                        ret = new byte[responseLength];
                        int readLength = 0;
                        int readAccu = 0;
                        InputStream inStream = getMethod.getResponseBodyAsStream();
                        while (readLength > -1 && readAccu < responseLength) {
                            int left = responseLength - readAccu;
                            readLength = inStream.read(ret, readAccu, left);
                            readAccu += readLength;
                        }
                        if (readAccu != responseLength) {
                            tileRespRecv.setError();
                            throw new GeoWebCacheException("Responseheader advertised "
                                    + responseLength + " bytes, but only received " + readLength
                                    + " from " + wmsBackendUrl.toString());
                        }
                    }
                } catch (IOException ioe) {
                    tileRespRecv.setError();
                    log.error("Caught IO exception, " + wmsBackendUrl.toString() + " "
                            + ioe.getMessage());
                }
            } else {
                ret = new byte[0];
            }

        } finally {
            if (getMethod != null){
                getMethod.releaseConnection();
            }
        }

        return ret;
    }

    /**
     * sets up a HTTP GET request to a URL and configures authentication.
     * 
     * @param url
     *            endpoint to talk to
     * @param backendTimeout
     *            timeout to use in seconds
     * @return executed GetMethod (that has to be closed after reading the response!)
     * @throws HttpException
     * @throws IOException
     */
    public GetMethod executeRequest(URL url, Integer backendTimeout) throws HttpException,
            IOException {
        HttpClientBuilder builder = new HttpClientBuilder(url, backendTimeout, httpUsername,
                httpPassword, proxyUrl);
        HttpClient httpClient = builder.buildClient();

        GetMethod getMethod = new GetMethod(url.toString());
        getMethod.setDoAuthentication(builder.isDoAuthentication());

        httpClient.executeMethod(getMethod);
        return getMethod;
    }
}
