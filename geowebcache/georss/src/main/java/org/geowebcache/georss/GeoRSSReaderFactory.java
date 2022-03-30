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
 * @author Gabriel Roldan (OpenGeo) 2010
 */
package org.geowebcache.georss;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLStreamException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.geotools.util.logging.Logging;
import org.geowebcache.util.HttpClientBuilder;

class GeoRSSReaderFactory {

    private static final Logger log = Logging.getLogger(GeoRSSReaderFactory.class.getName());

    public GeoRSSReader createReader(final URL url, final String username, final String password)
            throws IOException {

        if (log.isLoggable(Level.FINE)) {
            log.fine(
                    "Creating GeoRSS reader for URL "
                            + url.toExternalForm()
                            + " with user "
                            + username);
        }

        HttpClientBuilder builder = new HttpClientBuilder();
        builder.setHttpCredentials(username, password, url);
        builder.setBackendTimeout(120);

        HttpClient httpClient = builder.buildClient();

        HttpGet getMethod = new HttpGet(url.toString());

        if (log.isLoggable(Level.FINE)) {
            log.fine("Executing HTTP GET requesr for feed URL " + url.toExternalForm());
        }
        HttpResponse response = httpClient.execute(getMethod);

        if (log.isLoggable(Level.FINE)) {
            log.fine("Building GeoRSS reader out of URL response");
        }
        String contentEncoding = response.getEntity().getContentEncoding().getValue();
        if (contentEncoding == null) {
            contentEncoding = "UTF-8";
        }

        @SuppressWarnings("PMD.CloseResource") // The stream will be kept open to get new events
        Reader reader =
                new BufferedReader(
                        new InputStreamReader(response.getEntity().getContent(), contentEncoding));
        if (log.isLoggable(Level.FINE)) {
            log.fine("GeoRSS reader created, returning.");
        }
        return createReader(reader);
    }

    public GeoRSSReader createReader(final Reader feed) throws IOException {
        GeoRSSReader reader;
        try {
            reader = new StaxGeoRSSReader(feed);
        } catch (XMLStreamException | FactoryConfigurationError e) {
            throw new IllegalStateException(e);
        }
        return reader;
    }
}
