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
 * @author Lennart Juette, PTV AG (http://www.ptvag.com)
 *
 */
package org.geowebcache.util;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.params.HttpConnectionParams;

/**
 * Builder class for HttpClients
 * 
 */
public class HttpClientBuilder {

    private UsernamePasswordCredentials httpcredentials = null;

    private UsernamePasswordCredentials proxycredentials = null;

    private AuthScope authscope = null;

    private URL proxyUrl = null;

    private Integer backendTimeout = null;

    private boolean doAuthentication = false;

    public HttpClientBuilder() {
        super();
    }

    public HttpClientBuilder(URL url, Integer backendTimeout, String httpUsername,
            String httpPassword, URL proxyUrl) {
        this.setHttpCredentials(httpUsername, httpPassword,
                new AuthScope(url.getHost(), url.getPort()));

        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        List<String> lst = runtimeMXBean.getInputArguments();
        String proxyHost = null;
        String proxyPort = null;
        for (String arg : lst) {
            if (arg.startsWith("-Dhttp.proxyHost=")) {
                proxyHost = extractVMArg(arg);
            } else if (arg.startsWith("-Dhttp.proxyPort=")) {
                proxyPort = extractVMArg(arg);
            }
        }
        if (proxyHost != null)
            try {
                proxyUrl = new URL(proxyHost + ((proxyPort != null) ? (":" + proxyPort) : ("")));
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        this.setProxy(proxyUrl);
        this.setBackendTimeout(backendTimeout);
    }

    private String extractVMArg(String arg) {
        String[] proxyArg = arg.split("=");
        if (proxyArg.length == 2 && proxyArg[1].length() > 0)
            return proxyArg[1];
        return null;
    }

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
     * 
     * @param proxyUrl
     */
    public void setProxy(URL proxyUrl) {
        this.proxyUrl = proxyUrl;
        if (this.proxyUrl != null && this.proxyUrl.getUserInfo() != null) {
            String[] userinfo = this.proxyUrl.getUserInfo().split(":");
            this.proxycredentials = new UsernamePasswordCredentials(userinfo[0], userinfo[1]);
        }
    }

    public void setBackendTimeout(int backendTimeout) {
        this.backendTimeout = backendTimeout * 1000;
    }

    /**
     * uses the configuration of this builder to generate a HttpClient
     * 
     * @return the generated HttpClient
     */
    public HttpClient buildClient() {
        HttpClient httpClient = new HttpClient();
        HttpConnectionParams params = httpClient.getHttpConnectionManager().getParams();
        params.setConnectionTimeout(backendTimeout * 1000);
        params.setSoTimeout(backendTimeout * 1000);

        if (authscope != null && httpcredentials != null) {
            httpClient.getState().setCredentials(authscope, httpcredentials);
        }

        if (proxyUrl != null) {
            httpClient.getHostConfiguration().setProxy(proxyUrl.getHost(), proxyUrl.getPort());
            if (proxycredentials != null) {
                httpClient.getState().setProxyCredentials(
                        new AuthScope(proxyUrl.getHost(), proxyUrl.getPort()), proxycredentials);
            }
        }
        return httpClient;
    }

    /**
     * returns true if this builder was configured to pass HTTP credentials to the generated
     * HttpClient.
     * 
     * @return
     */
    public boolean isDoAuthentication() {
        return doAuthentication;
    }
}
