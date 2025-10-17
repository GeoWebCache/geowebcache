/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Nuno Oliveira, GeoSolutions S.A.S., Copyright 2016
 */
package org.geowebcache.service.wmts;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.GeoWebCacheExtensionPriority;
import org.geowebcache.config.meta.ServiceInformation;
import org.geowebcache.conveyor.Conveyor;
import org.geowebcache.io.XMLBuilder;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.service.OWSException;
import org.geowebcache.storage.StorageBroker;

/** Implementations of this interface will be able to extend WMTS operations behavior. */
public interface WMTSExtension extends GeoWebCacheExtensionPriority {

    /** This method should return a list of extra schemas that will be added to GetCapabilities operation result. */
    String[] getSchemaLocations();

    /**
     * This method will be invoked during the production of a capabilities document allowing extensions to register
     * extra namespaces.
     */
    void registerNamespaces(XMLBuilder xmlBuilder) throws IOException;

    /**
     * This method will be invoked during the production of a capabilities document allowing extensions to add extra
     * metadata in the OperationsMetadata section.
     */
    void encodedOperationsMetadata(XMLBuilder xmlBuilder) throws IOException;

    /**
     * Allow extensions to contribute extras operations to the GetCapabilities result. For more advanced use cases
     * method {@link #encodedOperationsMetadata(XMLBuilder)} should be used.
     */
    default List<OperationMetadata> getExtraOperationsMetadata() throws IOException {
        return Collections.emptyList();
    }

    /** Allows extensions to provide extra information about the service. */
    ServiceInformation getServiceInformation();

    /**
     * A conveyor represents a request against the WMTS service and will contain the information need by the WMTS
     * service to execute it. This method will be invoked by the WMTS service when identifying a request allowing
     * extensions to support custom requests or produce a custom conveyor for a certain request. This method should
     * return NULL if the extension is not interested in the current request.
     */
    default Conveyor getConveyor(HttpServletRequest request, HttpServletResponse response, StorageBroker storageBroker)
            throws GeoWebCacheException, OWSException {
        return null;
    }

    /**
     * Allows extensions to handle a WMTS operation. This method should return TRUE if the request was handled and FALSE
     * otherwise.
     */
    default boolean handleRequest(Conveyor conveyor) throws OWSException {
        return false;
    }

    /**
     * This method will be invoked during the production of a capabilities document allowing extensions to add extra
     * metadata to a layer.
     */
    default void encodeLayer(XMLBuilder xmlBuilder, TileLayer tileLayer) throws IOException {
        // nothing to do
    }

    /** By default an extension will have the lowest priority. */
    @Override
    default int getPriority() {
        return GeoWebCacheExtensionPriority.LOWEST;
    }

    /** Container for an operation metadata. */
    class OperationMetadata {

        private final String name;
        private final String baseUrl;

        public OperationMetadata(String name) {
            this(name, null);
        }

        public OperationMetadata(String name, String baseUrl) {
            this.name = name;
            this.baseUrl = baseUrl;
        }

        public String getName() {
            return name;
        }

        public String getBaseUrl() {
            return baseUrl;
        }
    }
}
