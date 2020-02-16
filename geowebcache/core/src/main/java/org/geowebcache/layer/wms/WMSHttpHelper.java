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
package org.geowebcache.layer.wms;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Map;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.TileResponseReceiver;
import org.geowebcache.mime.ErrorMime;
import org.geowebcache.mime.MimeType;
import org.geowebcache.service.ServiceException;
import org.geowebcache.util.GWCVars;
import org.geowebcache.util.HttpClientBuilder;
import org.geowebcache.util.ServletUtils;
import org.springframework.util.Assert;

/** This class is a wrapper for HTTP interaction with WMS backend */
public class WMSHttpHelper extends WMSSourceHelper {
    private static Log log = LogFactory.getLog(org.geowebcache.layer.wms.WMSHttpHelper.class);

    private final URL proxyUrl;

    private final String httpUsername;

    private final String httpPassword;

    private volatile HttpClient client;

    private boolean doAuthentication;

    public WMSHttpHelper() {
        this(null, null, null);
    }

    public WMSHttpHelper(String httpUsername, String httpPassword, URL proxyUrl) {
        super();
        this.httpUsername = httpUsername;
        this.httpPassword = httpPassword;
        this.proxyUrl = proxyUrl;
    }

    HttpClient getHttpClient() {
        if (client == null) {
            synchronized (this) {
                if (client != null) {
                    return client;
                }

                HttpClientBuilder builder =
                        new HttpClientBuilder(
                                null,
                                getBackendTimeout(),
                                httpUsername,
                                httpPassword,
                                proxyUrl,
                                getConcurrency());
                doAuthentication = builder.isDoAuthentication();
                client = builder.buildClient();
            }
        }

        return client;
    }

    /** Loops over the different backends, tries the request */
    @Override
    protected void makeRequest(
            TileResponseReceiver tileRespRecv,
            WMSLayer layer,
            Map<String, String> wmsParams,
            MimeType expectedMimeType,
            Resource target)
            throws GeoWebCacheException {
        Assert.notNull(target, "Target resource can't be null");
        Assert.isTrue(target.getSize() == 0, "Target resource is not empty");

        URL wmsBackendUrl = null;

        final Integer backendTimeout = layer.getBackendTimeout();
        int backendTries = 0; // keep track of how many backends we have tried
        GeoWebCacheException fetchException = null;
        while (target.getSize() == 0 && backendTries < layer.getWMSurl().length) {
            String requestUrl = layer.nextWmsURL();

            try {
                wmsBackendUrl = new URL(requestUrl);
            } catch (MalformedURLException maue) {
                throw new GeoWebCacheException(
                        "Malformed URL: " + requestUrl + " " + maue.getMessage());
            }
            try {
                connectAndCheckHeaders(
                        tileRespRecv,
                        wmsBackendUrl,
                        wmsParams,
                        expectedMimeType,
                        backendTimeout,
                        target,
                        layer.getHttpRequestMode());
            } catch (GeoWebCacheException e) {
                fetchException = e;
            }

            backendTries++;
        }

        if (target.getSize() == 0) {
            String msg = "All backends (" + backendTries + ") failed.";
            if (fetchException != null) {
                msg += " Reason: " + fetchException.getMessage() + ". ";
            }
            msg +=
                    " Last request: '"
                            + wmsBackendUrl.toString()
                            + "'. "
                            + (tileRespRecv.getErrorMessage() == null
                                    ? ""
                                    : tileRespRecv.getErrorMessage());

            tileRespRecv.setError();
            tileRespRecv.setErrorMessage(msg);
            throw new GeoWebCacheException(msg);
        }
    }

    /** Executes the actual HTTP request, checks the response headers (status and MIME) and */
    private void connectAndCheckHeaders(
            TileResponseReceiver tileRespRecv,
            URL wmsBackendUrl,
            Map<String, String> wmsParams,
            MimeType requestMimeType,
            Integer backendTimeout,
            Resource target,
            WMSLayer.HttpRequestMode httpRequestMode)
            throws GeoWebCacheException {

        HttpMethodBase method = null;
        final int responseCode;
        final int responseLength;

        try { // finally
            try {
                method = executeRequest(wmsBackendUrl, wmsParams, backendTimeout, httpRequestMode);
                responseCode = method.getStatusCode();
                responseLength = (int) method.getResponseContentLength();

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
                throw new ServiceException(
                        "Unexpected response code from backend: "
                                + responseCode
                                + " for "
                                + wmsBackendUrl.toString());
            }

            // Check that we're not getting an error MIME back.
            String responseMime = method.getResponseHeader("Content-Type").getValue();
            if (responseCode != 204
                    && responseMime != null
                    && !requestMimeType.isCompatible(responseMime)) {
                String message = null;
                if (responseMime.equalsIgnoreCase(ErrorMime.vnd_ogc_se_inimage.getFormat())) {
                    // TODO: revisit: I don't understand why it's trying to create a String message
                    // out of an ogc_se_inimage response?

                    try (InputStream stream = method.getResponseBodyAsStream()) {
                        byte[] error = IOUtils.toByteArray(stream);
                        message = new String(error);
                    } catch (IOException ioe) {
                        // Do nothing
                    }
                } else if (responseMime != null
                        && responseMime.toLowerCase().startsWith("application/vnd.ogc.se_xml")) {
                    try (InputStream stream = method.getResponseBodyAsStream()) {
                        message = IOUtils.toString(stream, "UTF-8");
                    } catch (IOException e) {
                        //
                    }
                }
                String msg =
                        "MimeType mismatch, expected "
                                + requestMimeType
                                + " but got "
                                + responseMime
                                + " from "
                                + wmsBackendUrl.toString()
                                + (message == null ? "" : (":\n" + message));
                tileRespRecv.setError();
                tileRespRecv.setErrorMessage(msg);
                log.warn(msg);
            }

            // Everything looks okay, try to save expiration
            if (tileRespRecv.getExpiresHeader() == GWCVars.CACHE_USE_WMS_BACKEND_VALUE) {
                String expireValue = method.getResponseHeader("Expires").getValue();
                long expire = ServletUtils.parseExpiresHeader(expireValue);
                if (expire != -1) {
                    tileRespRecv.setExpiresHeader(expire / 1000);
                }
            }

            // Read the actual data
            if (responseCode != 204) {
                try (InputStream inStream = method.getResponseBodyAsStream()) {
                    if (inStream == null) {
                        String uri = method.getURI().getURI();
                        log.error("No response for " + method.getName() + " " + uri);
                    } else {
                        try (ReadableByteChannel channel = Channels.newChannel(inStream)) {
                            target.transferFrom(channel);
                        }
                    }
                    if (responseLength > 0) {
                        int readAccu = (int) target.getSize();
                        if (readAccu != responseLength) {
                            tileRespRecv.setError();
                            throw new GeoWebCacheException(
                                    "Responseheader advertised "
                                            + responseLength
                                            + " bytes, but only received "
                                            + readAccu
                                            + " from "
                                            + wmsBackendUrl.toString());
                        }
                    }
                } catch (IOException ioe) {
                    tileRespRecv.setError();
                    log.error(
                            "Caught IO exception, "
                                    + wmsBackendUrl.toString()
                                    + " "
                                    + ioe.getMessage());
                }
            }

        } finally {
            if (method != null) {
                method.releaseConnection();
            }
        }
    }

    /**
     * sets up a HTTP request to a URL and configures authentication.
     *
     * @param url endpoint to talk to
     * @param queryParams parameters for the query string
     * @param backendTimeout timeout to use in seconds
     * @param httpRequestMode the method used to perform requests (can be null, in such case {@link
     *     org.geowebcache.layer.wms.WMSLayer.HttpRequestMode#Get} will be used
     * @return executed method (that has to be closed after reading the response!)
     */
    public HttpMethodBase executeRequest(
            final URL url,
            final Map<String, String> queryParams,
            final Integer backendTimeout,
            WMSLayer.HttpRequestMode httpRequestMode)
            throws IOException {
        // grab the client
        HttpClient httpClient = getHttpClient();

        // prepare the request
        NameValuePair[] params = null;

        if (queryParams != null) {
            params = new NameValuePair[queryParams.size()];
            int i = 0;
            for (Map.Entry<String, String> e : queryParams.entrySet()) {
                params[i] = new NameValuePair(e.getKey(), e.getValue());
                i++;
            }
        }

        HttpMethodBase method;
        if (httpRequestMode == WMSLayer.HttpRequestMode.FormPost) {
            PostMethod pm = new PostMethod(url.toString());
            if (queryParams != null && queryParams.size() > 0) {
                pm.setRequestBody(params);
            }
            method = pm;
        } else {
            method = new GetMethod(url.toString());
            if (queryParams != null && queryParams.size() > 0) {
                method.setQueryString(params);
            }
        }
        method.setDoAuthentication(doAuthentication);

        // fire!
        if (log.isDebugEnabled()) {
            log.trace(method.getURI().getURI());
        }
        httpClient.executeMethod(method);
        return method;
    }
}
