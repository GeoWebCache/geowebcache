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

import java.io.IOException;
import java.net.URLConnection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Connection {
	private static Log log = LogFactory.getLog(org.geowebcache.service.Connection.class);

	private Request request = null;
	private URLConnection outgoing_request = null;

	public Connection(Request request) {
		setRequest(request);
	}

	public void connect() throws IOException {
		try {
			System.out.println("Requesting: " + request.getURL());
			outgoing_request = request.getURL().openConnection();
		} catch(IOException ioe) {
			log.error("Failed to connect to " + request.toString() + " : ", ioe);
		}
	}

	/**
	 * @return the request
	 */
	public Request getRequest() {
		return request;
	}

	/**
	 * @param request the request to set
	 */
	public void setRequest(Request request) {
		this.request = request;
	}

	/**
	 * @return the Response for this connection
	 */
	public Response getResponse() {
		return new Response(outgoing_request);
	}

}
