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
 * @author Arne Kepp, Copyright 2010
 */
package org.geowebcache.layer.wms;

import java.net.URL;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.geowebcache.util.HttpClientBuilder;
import org.junit.Before;
import org.junit.Test;

public class HttpClientTest {

    static final Log LOG = LogFactory.getLog(HttpClientTest.class);

    static final boolean RUN_PERFORMANCE_TEST = false;

    static final int LOOP_COUNT = 100000;

    @Before
    public void setUp() throws Exception {}

    /**
     * Some numbers just for creating HttpClient instances (less than what is done below)
     *
     * <p>Core i7 , Java 1.6 values: 1 000 000 in 559 ms 1 00 000 in 267 ms 10 000 in 186 ms 1 000
     * in 134 ms
     */
    @Test
    public void testHttpClientConstruction() throws Exception {
        HttpClientBuilder builder = new HttpClientBuilder();
        if (RUN_PERFORMANCE_TEST) {
            long start = System.currentTimeMillis();
            for (int i = 0; i < LOOP_COUNT; i++) {

                URL url = new URL("http://localhost:8080/test");
                AuthScope authscope = new AuthScope(url.getHost(), url.getPort());
                UsernamePasswordCredentials credentials =
                        new UsernamePasswordCredentials("username", "password");

                builder.setHttpcredentials(credentials);
                HttpClient hc = builder.buildClient();

                HttpGet getMethod = new HttpGet(url.toString());
            }
            long stop = System.currentTimeMillis();

            long diff = (stop - start);

            LOG.info("Time to create " + LOOP_COUNT + " in " + diff + " milliseconds");
        }
    }
}
