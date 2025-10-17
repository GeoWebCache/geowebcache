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

/** Default implementation of a WMTSExtension that does nothing. */
public class WMTSExtensionImpl implements WMTSExtension {

    protected int priority = GeoWebCacheExtensionPriority.LOWEST;

    @Override
    public String[] getSchemaLocations() {
        return new String[0];
    }

    @Override
    public void registerNamespaces(XMLBuilder xmlBuilder) throws IOException {}

    @Override
    public void encodedOperationsMetadata(XMLBuilder xmlBuilder) throws IOException {}

    @Override
    public List<OperationMetadata> getExtraOperationsMetadata() throws IOException {
        return Collections.emptyList();
    }

    @Override
    public ServiceInformation getServiceInformation() {
        return null;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public Conveyor getConveyor(HttpServletRequest request, HttpServletResponse response, StorageBroker storageBroker)
            throws GeoWebCacheException, OWSException {
        return null;
    }

    @Override
    public boolean handleRequest(Conveyor conveyor) throws OWSException {
        return false;
    }

    @Override
    public void encodeLayer(XMLBuilder xmlBuilder, TileLayer tileLayer) throws IOException {}

    public void setPriority(int priority) {
        this.priority = priority;
    }
}
