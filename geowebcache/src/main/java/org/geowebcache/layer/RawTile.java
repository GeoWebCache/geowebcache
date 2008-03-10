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
 * @author Arne Kepp, The Open Planning Project, Copyright 2007
 *  
 */
package org.geowebcache.layer;

import java.io.Serializable;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Facilitates use with JCS and other backends that only deal with objects.
 * 
 * @author Arne Kepp, The Open Planning Project
 */
public class RawTile implements Serializable {
	/**
	 * @serial
	 */
	private static final long serialVersionUID = -5171595780192211809L;
	// Store the image  in memory
	private byte[] data = null;
	
	public RawTile(byte[] data) {
		this.data = data;
	}

	public byte[] getData() {
		return data;
	}
	
	public void write(OutputStream out) throws IOException {
		out.write(data);
	}
}
