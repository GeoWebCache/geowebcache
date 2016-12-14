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
 * @author Gabriel Roldan (OpenGeo) 2010
 *  
 */
package org.geowebcache.georss;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.util.HttpClientBuilder;

class GeoRSSReaderFactory {

    private static final Log log = LogFactory.getLog(GeoRSSReaderFactory.class);

    public GeoRSSReader createReader(final URL url, final String username, final String password)
            throws IOException {

        if (log.isDebugEnabled()) {
            log.debug("Creating GeoRSS reader for URL " + url.toExternalForm() + " with user "
                    + username);
        }

        HttpClientBuilder builder = new HttpClientBuilder();
        builder.setHttpCredentials(username, password, url);
        builder.setBackendTimeout(120);

        HttpClient httpClient = builder.buildClient();

        GetMethod getMethod = new GetMethod(url.toString());
        getMethod.setRequestHeader("Connection", "close");
        if (builder.isDoAuthentication()) {
            getMethod.setDoAuthentication(true);
            httpClient.getParams().setAuthenticationPreemptive(true);
        }

        if (log.isDebugEnabled()) {
            log.debug("Executing HTTP GET requesr for feed URL " + url.toExternalForm());
        }
        httpClient.executeMethod(getMethod);

        if (log.isDebugEnabled()) {
            log.debug("Building GeoRSS reader out of URL response");
        }
        String contentEncoding = getMethod.getResponseCharSet();
        if (contentEncoding == null) {
            contentEncoding = "UTF-8";
        }

        InputStream in = getMethod.getResponseBodyAsStream();
        Reader reader = new BufferedReader(new InputStreamReader(in, contentEncoding));
        if (log.isDebugEnabled()) {
            log.debug("GeoRSS reader created, returning.");
        }
        return createReader(reader);
    }

    public GeoRSSReader createReader(final Reader feed) throws IOException {
        GeoRSSReader reader;
        try {
            reader = new StaxGeoRSSReader(feed);
        } catch (XMLStreamException e) {
            throw new IllegalStateException(e);
        } catch (FactoryConfigurationError e) {
            throw new IllegalStateException(e);
        }
        return reader;
    }
}
