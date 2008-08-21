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
 * @author Chris Whitney
 *  
 */
package org.geowebcache.service;

import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Request {

    private static Log log = LogFactory
            .getLog(org.geowebcache.service.Request.class);

    private URL server = null;

    private Parameters params = null;

    public Request(String server, Parameters params) {
        this.setServer(server);
        // this.setParametersFromConfiguration();
        if (this.params != null) {
            this.params.merge(params);
        } else {
            this.params = params;
        }
    }

    /**
     * @return the address URL
     */
    public URL getServer() {
        return server;
    }

    /**
     * @param address
     *            the address URL to set
     */
    public void setServer(URL server) {
        this.server = server;
    }

    public void setServer(String address) {
        try {
            server = new URL(address);
        } catch (MalformedURLException mue) {
            log.error("Invalid server URL String: ", mue);
            // Do nothing, leave this.server set to previous value
        }
    }

    public Parameters getParameters() {
        return params;
    }

    public void setParameters(Parameters params) {
        this.params = params;
    }

    public URL getURL() {
        URL address = null;
        try {
            String serverAdr = server.toExternalForm();
            if(serverAdr.charAt(serverAdr.length() - 1) != '?') { 
                address = new URL( serverAdr +'?'+ params.getURLString(false));
            } else {
                address = new URL( serverAdr + params.getURLString(false));
            }
            
            log.debug("url: " + address);
        } catch (MalformedURLException mue) {
            log.error("Invalid URL from server and parameters: ", mue);
        }
        return address;
    }

    @Override
    public String toString() {
        return getURL().toString();
    }

}
