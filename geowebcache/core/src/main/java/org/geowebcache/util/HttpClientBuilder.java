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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

/** Builder class for HttpClients */
public class HttpClientBuilder {

    static final Log log = LogFactory.getLog(HttpClientBuilder.class);

    private UsernamePasswordCredentials httpcredentials = null;

    private UsernamePasswordCredentials proxycredentials = null;

    private AuthScope authscope = null;

    private URL proxyUrl = null;

    private Integer backendTimeoutMillis = null;

    private boolean doAuthentication = false;

    private int concurrency;

    private RequestConfig connectionConfig;

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

        /*
         * RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean(); List<String> lst =
         * runtimeMXBean.getInputArguments(); String proxyHost = null; String proxyPort = null; for
         * (String arg : lst) { if (arg.startsWith("-Dhttp.proxyHost=")) { proxyHost =
         * extractVMArg(arg); } else if (arg.startsWith("-Dhttp.proxyPort=")) { proxyPort =
         * extractVMArg(arg); } } if (proxyHost != null) try { proxyUrl = new URL(proxyHost +
         * ((proxyPort != null) ? (":" + proxyPort) : (""))); } catch (MalformedURLException e) {
         * log.debug(e); } this.setProxy(proxyUrl);
         */
        this.setBackendTimeout(backendTimeout);
        this.concurrency = concurrency;
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

    /**
     * parses a proxyUrl parameter and configures (if possible) the builder to set a proxy when
     * generating a httpClient and (if possible) proxy authentication.
     */
    public void setProxy(URL proxyUrl) {
        this.proxyUrl = proxyUrl;
        if (this.proxyUrl != null && this.proxyUrl.getUserInfo() != null) {
            String[] userinfo = this.proxyUrl.getUserInfo().split(":");
            this.proxycredentials = new UsernamePasswordCredentials(userinfo[0], userinfo[1]);
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
        HttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        setConnectionConfig(
                RequestConfig.custom()
                        .setCookieSpec(CookieSpecs.DEFAULT)
                        .setExpectContinueEnabled(true)
                        .setSocketTimeout(backendTimeoutMillis)
                        .setConnectTimeout(backendTimeoutMillis)
                        .build());
        /*
         * if (concurrency > 0) { params.setMaxTotalConnections(concurrency);
         * params.setMaxConnectionsPerHost(HostConfiguration.ANY_HOST_CONFIGURATION, concurrency); }
         */

        org.apache.http.impl.client.HttpClientBuilder clientBuilder =
                org.apache.http.impl.client.HttpClientBuilder.create();
        clientBuilder.useSystemProperties();
        clientBuilder.setConnectionManager(connectionManager);
        if (authscope != null && httpcredentials != null) {
            BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(authscope, httpcredentials);
            clientBuilder.setDefaultCredentialsProvider(credsProvider);
        }
        HttpClient httpClient = clientBuilder.build();

        /*
         * if (proxyUrl != null) { httpClient.getHostConfiguration().setProxy(proxyUrl.getHost(),
         * proxyUrl.getPort()); if (proxycredentials != null) { httpClient .getState()
         * .setProxyCredentials( new AuthScope(proxyUrl.getHost(), proxyUrl.getPort()),
         * proxycredentials); } }
         */
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
