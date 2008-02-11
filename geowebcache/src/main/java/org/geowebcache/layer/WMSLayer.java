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

import java.util.Properties;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.geowebcache.cache.*;
import org.geowebcache.service.Parameters;
import org.geowebcache.util.Configuration;

public class WMSLayer {
	private static Log log = LogFactory.getLog(org.geowebcache.layer.WMSLayer.class);
	
	protected String url = "http://localhost:8080/geoserver/wms";
	protected String layers = "topp:states";
	
	public WMSLayer(Properties props) {		
		setParametersFromProperties(props);

		if(log.isTraceEnabled()) {
			log.trace("Created a new layer: " + this.toString());
		}
	}

	private void setParametersFromProperties(Properties props) {
		String propUrl = props.getProperty("url");
		if(propUrl != null)
			this.url = propUrl;

		String propLayers = props.getProperty("layers");
		if(propLayers != null)
			this.layers = propLayers;
	}
}
