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
 * @author Arne Kepp, Copyright 2010
 */
package org.geowebcache.layer.wms;

import java.net.URL;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;

import junit.framework.TestCase;

public class HttpClientTest extends TestCase {

    final static boolean RUN_PERFORMANCE_TEST = false;
   
    final static int LOOP_COUNT = 100000;
    
    protected void setUp() throws Exception {
        super.setUp();
    }
    
    /**
     * Some numbers just for creating HttpClient instances 
     * (less than what is done below)
     * 
     * Core i7 , Java 1.6 values:
     * 1 000 000 in 559 ms
     *  1 00 000 in 267 ms
     *    10 000 in 186 ms
     *     1 000 in 134 ms
     *     
     * @throws Exception
     */
    public void testHttpClientConstruction() throws Exception {
        if(RUN_PERFORMANCE_TEST) {
            long start = System.currentTimeMillis();
            for(int i=0; i< LOOP_COUNT; i++) {
                HttpClient hc = new HttpClient();
                
                URL url = new URL("http://localhost:8080/test");
                GetMethod getMethod = new GetMethod(url.toString());
                
                AuthScope authscope = new AuthScope(url.getHost(), url.getPort());
                UsernamePasswordCredentials credentials = new UsernamePasswordCredentials("username", "password");

                hc.getState().setCredentials(authscope, credentials);
                getMethod.setDoAuthentication(true);
                hc.getParams().setAuthenticationPreemptive(true);
                
                if(hc.getPort() == 0) {
                    // Dummy
                }
                //System.out.print(i);
            }
            long stop = System.currentTimeMillis();
            
            long diff =  ( stop - start );
            
            System.out.println( "Time to create " + LOOP_COUNT + " in " + diff + " milliseconds");
        }
       
    }
}