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
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicNameValuePair;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.TileResponseReceiver;
import org.geowebcache.mime.ErrorMime;
import org.geowebcache.mime.MimeType;
import org.geowebcache.service.ServiceException;
import org.geowebcache.util.GWCVars;
import org.geowebcache.util.HttpClientBuilder;
import org.geowebcache.util.ServletUtils;
import org.geowebcache.util.URLs;
import org.springframework.util.Assert;

/** This class is a wrapper for HTTP interaction with WMS backend */
public class WMSHttpHelper extends WMSSourceHelper {
    private static final Logger log = Logging.getLogger(WMSHttpHelper.class.getName());

    private final URL proxyUrl;

    private final String httpUsername;

    private final String httpPassword;

    protected volatile HttpClient client;

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
                wmsBackendUrl = URLs.of(requestUrl);
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

        HttpResponse method = null;
        final int responseCode;
        int responseLength = 0;

        try {
            method = executeRequest(wmsBackendUrl, wmsParams, backendTimeout, httpRequestMode);
            responseCode = method.getStatusLine().getStatusCode();
            if (responseCode == 200) {
                if (method.getFirstHeader("length") != null) {
                    responseLength = Integer.parseInt(method.getFirstHeader("length").getValue());
                } else if (method.getFirstHeader("Content-Length") != null) {
                    responseLength =
                            Integer.parseInt(method.getFirstHeader("Content-Length").getValue());
                } else if (method.getEntity() != null) {
                    responseLength = Math.toIntExact(method.getEntity().getContentLength());
                } else {
                    throw new ServiceException(
                            "Unable to determine response length from: "
                                    + wmsBackendUrl.toString());
                }
            }
            // Do not set error at this stage
        } catch (IOException ce) {
            if (log.isLoggable(Level.FINE)) {
                String message = "Error forwarding request " + wmsBackendUrl.toString();
                log.log(Level.FINE, message, ce);
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
        String responseMime = method.getFirstHeader("Content-Type").getValue();
        if (responseCode != 204
                && responseMime != null
                && !requestMimeType.isCompatible(responseMime)) {
            String message = null;
            if (responseMime.equalsIgnoreCase(ErrorMime.vnd_ogc_se_inimage.getFormat())) {
                // TODO: revisit: I don't understand why it's trying to create a String message
                // out of an ogc_se_inimage response?

                try (InputStream stream = method.getEntity().getContent()) {
                    byte[] error = IOUtils.toByteArray(stream);
                    message = new String(error);
                } catch (IOException ioe) {
                    // Do nothing
                }
            } else if (responseMime != null
                    && responseMime.toLowerCase().startsWith("application/vnd.ogc.se_xml")) {
                try (InputStream stream = method.getEntity().getContent()) {
                    message = IOUtils.toString(stream, StandardCharsets.UTF_8);
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
            log.warning(msg);
        }

        // Everything looks okay, try to save expiration
        if (tileRespRecv.getExpiresHeader() == GWCVars.CACHE_USE_WMS_BACKEND_VALUE) {
            String expireValue = method.getFirstHeader("Expires").getValue();
            long expire = ServletUtils.parseExpiresHeader(expireValue);
            if (expire != -1) {
                tileRespRecv.setExpiresHeader(expire / 1000);
            }
        }

        // Read the actual data
        if (responseCode != 204) {
            try (InputStream inStream = method.getEntity().getContent()) {
                if (inStream == null) {
                    log.severe("No response for " + method);
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
                log.severe(
                        "Caught IO exception, "
                                + wmsBackendUrl.toString()
                                + " "
                                + ioe.getMessage());
            }
        }
    }

    /**
     * sets up an HTTP request to a URL and configures authentication.
     *
     * @param url endpoint to talk to
     * @param queryParams parameters for the query string
     * @param backendTimeout timeout to use in seconds
     * @param httpRequestMode the method used to perform requests (can be null, in such case {@link
     *     org.geowebcache.layer.wms.WMSLayer.HttpRequestMode#Get} will be used
     * @return executed method (that has to be closed after reading the response!)
     */
    public HttpResponse executeRequest(
            final URL url,
            final Map<String, String> queryParams,
            final Integer backendTimeout,
            WMSLayer.HttpRequestMode httpRequestMode)
            throws IOException {

        // prepare the request
        NameValuePair[] params = null;

        if (queryParams != null) {
            params = new NameValuePair[queryParams.size()];
            int i = 0;
            for (Map.Entry<String, String> e : queryParams.entrySet()) {
                params[i] = new BasicNameValuePair(e.getKey(), e.getValue());
                i++;
            }
        }

        HttpRequestBase method;
        String urlString = url.toString();
        if (httpRequestMode == WMSLayer.HttpRequestMode.FormPost) {
            HttpPost pm = new HttpPost(urlString);
            if (queryParams != null && !queryParams.isEmpty()) {
                HttpEntity requestEntity = new StringEntity(processRequestParameters(queryParams));
                pm.setEntity(requestEntity);
            }
            method = pm;
        } else {
            if (queryParams != null && !queryParams.isEmpty()) {
                String qs = processRequestParameters(queryParams);
                if (urlString.contains("?")) {
                    urlString = urlString + "&" + qs;
                } else if (urlString.endsWith("?")) {
                    urlString += qs;
                } else {
                    urlString = urlString + "?" + qs;
                }
            }
            method = new HttpGet(urlString);
        }

        // fire!
        if (log.isLoggable(Level.FINER)) {
            log.finer(method.toString());
        }
        return getHttpClient().execute(method);
    }

    private String processRequestParameters(Map<String, String> parameters)
            throws UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder();
        for (String parameterName : parameters.keySet()) {
            sb.append(parameterName)
                    .append('=')
                    .append(
                            URLEncoder.encode(
                                    parameters.get(parameterName),
                                    StandardCharsets.UTF_8.toString()))
                    .append('&');
        }
        return sb.substring(0, sb.length() - 1);
    }
}
