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
 */
package org.geowebcache.jetty;

import java.io.IOException;
import java.util.Objects;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.rules.ExternalResource;

public class HttpClientRule extends ExternalResource {

    private CredentialsProvider creds;
    private CloseableHttpClient client;
    private String name;

    private static CredentialsProvider providerForCreds(String username, String password) {
        CredentialsProvider creds = new BasicCredentialsProvider();
        creds.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
        return creds;
    }

    public HttpClientRule(String username, String password, String clientName) {
        this(providerForCreds(username, password), clientName);
    }

    public HttpClientRule(CredentialsProvider creds, String clientName) {
        this.creds = creds;
        HttpClientBuilder builder = HttpClientBuilder.create();
        if (creds != null) {
            builder.setDefaultCredentialsProvider(creds);
        }
        client = builder.build();
    }

    @Override
    protected void before() throws Throwable {
        HttpClientBuilder builder = HttpClientBuilder.create();
        if (creds != null) {
            builder.setDefaultCredentialsProvider(creds);
        }
        client = builder.build();
    }

    @Override
    protected void after() {
        if (client != null) {
            try {
                client.close();
            } catch (IOException e) {
                throw new AssertionError(
                        "Unexpected exception while closing HTTP Client " + name, e);
            }
        }
    }

    public CloseableHttpClient getClient() {
        Objects.requireNonNull(client);
        return client;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return String.format("HttpClientRule[%s]", name);
    }

    public static HttpClientRule anonymous(String clientName) {
        return new HttpClientRule(null, clientName);
    }

    public static HttpClientRule anonymous() {
        return anonymous("anonymous");
    }
}
