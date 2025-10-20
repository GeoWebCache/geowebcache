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
 * @author Lennart Juette, PTV AG (http://www.ptvag.com) 2010
 */
package org.geowebcache.util;

import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.geotools.util.logging.Logging;

/** Builder class for HttpClients */
public class HttpClientBuilder {

    static final Logger log = Logging.getLogger(HttpClientBuilder.class.toString());

    private UsernamePasswordCredentials httpcredentials = null;

    private AuthScope authscope = null;

    private Integer backendTimeoutMillis = null;

    private boolean doAuthentication = false;

    private RequestConfig connectionConfig;

    private org.apache.hc.client5.http.impl.classic.HttpClientBuilder clientBuilder;

    public HttpClientBuilder() {
        super();
    }

    /**
     * Instantiates a new http client builder
     *
     * @param url The server url, or null if no authentication is required or if the client is going to be used against
     *     a single server only
     */
    public HttpClientBuilder(
            URL url, Integer backendTimeout, String httpUsername, String httpPassword, URL proxyUrl, int concurrency) {
        if (url != null) {
            this.setHttpCredentials(httpUsername, httpPassword, new AuthScope(url.getHost(), url.getPort()));
        } else {
            this.setHttpCredentials(httpUsername, httpPassword, new AuthScope(null, -1));
        }
        this.setBackendTimeout(backendTimeout);
        setConnectionConfig(RequestConfig.custom()
                .setCookieSpec(StandardCookieSpec.RELAXED)
                .setExpectContinueEnabled(true)
                .setResponseTimeout(backendTimeoutMillis, TimeUnit.MILLISECONDS)
                .setRedirectsEnabled(true)
                .build());

        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(backendTimeoutMillis, TimeUnit.MILLISECONDS)
                .build();

        @SuppressWarnings("PMD.CloseResource")
        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(concurrency)
                .setDefaultConnectionConfig(connectionConfig)
                .build();

        clientBuilder = HttpClients.custom();
        clientBuilder.useSystemProperties();
        clientBuilder.setDefaultRequestConfig(this.connectionConfig);
        clientBuilder.setConnectionManager(connectionManager);
    }

    /*
     * private String extractVMArg(String arg) { String[] proxyArg = arg.split("="); if
     * (proxyArg.length == 2 && proxyArg[1].length() > 0) return proxyArg[1]; return null; }
     */

    public void setHttpCredentials(String username, String password, URL authscopeUrl) {
        AuthScope authscope = new AuthScope(authscopeUrl.getHost(), authscopeUrl.getPort());
        setHttpCredentials(username, password, authscope);
    }

    public void setHttpCredentials(String username, String password, AuthScope authscope) {
        if (username != null && authscope != null) {
            this.authscope = authscope;
            this.httpcredentials = new UsernamePasswordCredentials(username, password.toCharArray());
            this.doAuthentication = true;
        } else {
            this.authscope = null;
            this.httpcredentials = null;
            this.doAuthentication = false;
        }
    }

    /** @param backendTimeout timeout in seconds */
    public void setBackendTimeout(final int backendTimeout) {
        this.backendTimeoutMillis = backendTimeout * 1000;
    }

    /**
     * uses the configuration of this builder to generate a HttpClient
     *
     * @return the generated HttpClient
     */
    public CloseableHttpClient buildClient() {

        if (authscope != null && httpcredentials != null) {
            BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(authscope, httpcredentials);
            clientBuilder.setDefaultCredentialsProvider(credsProvider);
        }
        CloseableHttpClient httpClient = clientBuilder.build();

        return httpClient;
    }

    /** returns true if this builder was configured to pass HTTP credentials to the generated HttpClient. */
    public boolean isDoAuthentication() {
        return doAuthentication;
    }
    /** @return the httpcredentials */
    public UsernamePasswordCredentials getHttpcredentials() {
        return httpcredentials;
    }

    /** @param httpcredentials the httpcredentials to set */
    public void setHttpcredentials(UsernamePasswordCredentials httpcredentials) {
        this.httpcredentials = httpcredentials;
    }

    /** @return the connectionConfig */
    public RequestConfig getConnectionConfig() {
        return connectionConfig;
    }

    /** @param connectionConfig the connectionConfig to set */
    public void setConnectionConfig(RequestConfig connectionConfig) {
        this.connectionConfig = connectionConfig;
    }
}
