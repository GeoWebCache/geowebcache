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
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpConnectionParams;

class GeoRSSReaderFactory {

    public GeoRSSReader createReader(final URL url, final String username, final String password) 
    throws IOException {
        
        HttpClient httpClient = new HttpClient();
        GetMethod getMethod = new GetMethod(url.toString());
        
        if(username != null) {
            AuthScope authscope = new AuthScope(url.getHost(), url.getPort());
            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(username, password);
            
            httpClient.getState().setCredentials(authscope, credentials);
            getMethod.setDoAuthentication(true);
        }

        HttpConnectionParams params = httpClient.getHttpConnectionManager().getParams();
        params.setConnectionTimeout(120 * 1000);
        params.setSoTimeout(120 * 1000);
        
        httpClient.executeMethod(getMethod);

        String contentEncoding = getMethod.getResponseCharSet();
        if (contentEncoding == null) {
            contentEncoding = "UTF-8";
        }
        
        InputStream in = getMethod.getResponseBodyAsStream();
        Reader reader = new BufferedReader(new InputStreamReader(in, contentEncoding));
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
