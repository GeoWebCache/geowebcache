/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Nuno Oliveira, GeoSolutions S.A.S., Copyright 2016
 */
package org.geowebcache.service.wmts;

import org.geowebcache.GeoWebCacheExtensionPriority;
import org.geowebcache.config.meta.ServiceInformation;
import org.geowebcache.io.XMLBuilder;

import java.io.IOException;

/**
 * Implementations of this interface will be able to extend WMTS operations behavior.
 */
public interface WMTSExtension extends GeoWebCacheExtensionPriority {

    /**
     * This method should return a list of extra schemas that will be added
     * to GetCapabilities operation result.
     */
    String[] getSchemaLocations();

    /**
     * This method will be invoked during the production of a capabilities
     * document allowing extensions to register extra namespaces.
     */
    void registerNamespaces(XMLBuilder xmlBuilder) throws IOException;

    /**
     * This method will be invoked during the production of a capabilities
     * document allowing extensions to add extra metadata in the
     * OperationsMetadata section.
     */
    void encodedOperationsMetadata(XMLBuilder xmlBuilder) throws IOException;

    /**
     * Allows extensions to provide extra information about the service.
     */
    ServiceInformation getServiceInformation();

    /**
     * By default an extension will have the lowest priority.
     */
    @Override
    default int getPriority() {
        return GeoWebCacheExtensionPriority.LOWEST;
    }
}