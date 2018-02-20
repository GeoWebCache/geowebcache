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
 * @author Gabriel Roldan, OpenGeo, Copyright 2010
 */
package org.geowebcache.rest.config;

import java.io.InputStream;

import org.geowebcache.config.GeoWebCacheConfiguration;
import org.geowebcache.config.TileLayerConfiguration;
import org.geowebcache.io.GeoWebCacheXStream;
import org.geowebcache.rest.filter.WMSRasterFilterUpdate;
import org.geowebcache.rest.filter.XmlFilterUpdate;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

/**
 * Utility class to parse the rest xml messages into their corresponding java objects.
 * 
 */
public class XMLConfiguration {

    private static XStream getConfiguredXStream(XStream xs) {
        // Restrict classes that can be serialized/deserialized
        // Allowing arbitrary classes to be deserialized is a security issue.
        {

            
            // Allow any implementation of these extension points
            xs.allowTypeHierarchy(org.geowebcache.layer.TileLayer.class);
            xs.allowTypeHierarchy(org.geowebcache.filter.parameters.ParameterFilter.class);
            xs.allowTypeHierarchy(org.geowebcache.filter.request.RequestFilter.class);
            xs.allowTypeHierarchy(org.geowebcache.config.BlobStoreInfo.class);
            xs.allowTypeHierarchy(TileLayerConfiguration.class);
            
            // Allow anything that's part of GWC
            // TODO: replace this with a more narrow whitelist
            xs.allowTypesByWildcard(new String[]{"org.geowebcache.**"});
        }
        
        xs.setMode(XStream.NO_REFERENCES);

        xs.alias("gwcConfiguration", GeoWebCacheConfiguration.class);
        xs.useAttributeFor(GeoWebCacheConfiguration.class, "xmlns_xsi");
        xs.aliasField("xmlns:xsi", GeoWebCacheConfiguration.class, "xmlns_xsi");
        xs.useAttributeFor(GeoWebCacheConfiguration.class, "xmlns");

        xs.alias("wmsRasterFilterUpdate", WMSRasterFilterUpdate.class);

        return xs;
    }

    public static XmlFilterUpdate parseXMLFilterUpdate(final InputStream in) {
        XStream xs = getConfiguredXStream(new GeoWebCacheXStream(new DomDriver()));

        XmlFilterUpdate fu = (XmlFilterUpdate) xs.fromXML(in);

        return fu;
    }
}
