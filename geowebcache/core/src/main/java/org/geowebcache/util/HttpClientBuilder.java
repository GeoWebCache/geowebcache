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
 * @author Lennart Juette, PTV AG (http://www.ptvag.com) 2010
 */
package org.geowebcache.util;

import java.net.URL;
import java.util.logging.Logger;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.geotools.util.logging.Logging;

/** Builder class for HttpClients */
public class HttpClientBuilder {

    static final Logger log = Logging.getLogger(HttpClientBuilder.class.toString());

    private UsernamePasswordCredentials httpcredentials = null;

    private AuthScope authscope = null;

    private Integer backendTimeoutMillis = null;
    private static final HttpClientConnectionManager connectionManager =
            new PoolingHttpClientConnectionManager();

    private boolean doAuthentication = false;

    private RequestConfig connectionConfig;

    private org.apache.http.impl.client.HttpClientBuilder clientBuilder;

    public HttpClientBuilder() {
        super();
    }

    /**
     * Instantiates a new http client builder
     *
     * @param url The server url, or null if no authentication is required or if the client is going
     *     to be used against a single server only
     */
    public HttpClientBuilder(
            URL url,
            Integer backendTimeout,
            String httpUsername,
            String httpPassword,
            URL proxyUrl,
            int concurrency) {
        if (url != null) {
            this.setHttpCredentials(
                    httpUsername, httpPassword, new AuthScope(url.getHost(), url.getPort()));
        } else {
            this.setHttpCredentials(httpUsername, httpPassword, AuthScope.ANY);
        }

        setConnectionConfig(
                RequestConfig.custom()
                        .setCookieSpec(CookieSpecs.DEFAULT)
                        .setExpectContinueEnabled(true)
                        .setSocketTimeout(backendTimeoutMillis)
                        .setConnectTimeout(backendTimeoutMillis)
                        .setRedirectsEnabled(true)
                        .build());

        clientBuilder = org.apache.http.impl.client.HttpClientBuilder.create();
        clientBuilder.useSystemProperties();
        clientBuilder.setConnectionManager(connectionManager);
        clientBuilder.setMaxConnTotal(concurrency);
        this.setBackendTimeout(backendTimeout);
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
            this.httpcredentials = new UsernamePasswordCredentials(username, password);
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
    public HttpClient buildClient() {

        if (authscope != null && httpcredentials != null) {
            BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(authscope, httpcredentials);
            clientBuilder.setDefaultCredentialsProvider(credsProvider);
        }
        HttpClient httpClient = clientBuilder.build();

        return httpClient;
    }

    /**
     * returns true if this builder was configured to pass HTTP credentials to the generated
     * HttpClient.
     */
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
