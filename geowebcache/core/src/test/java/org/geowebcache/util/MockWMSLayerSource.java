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
 * @author Gabriel Roldan (OpenGeo) 2010
 */
package org.geowebcache.util;

import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.layer.wms.WMSLayer;
import org.geowebcache.layer.wms.WMSSourceHelper;

/**
 * A bean that takes all WMS layers and sets them a <strong>mock</strong>
 * {@link WMSLayer#setSourceHelper(WMSSourceHelper) sourceHelper}, only for test purposes.
 *
 * @author groldan
 */
public class MockWMSLayerSource {

    private static final Logger log = Logging.getLogger(MockWMSLayerSource.class.getName());

    public MockWMSLayerSource(TileLayerDispatcher tld) {
        log.info(
                """
                '
                ---------------------------------------------------------------------------------
                Replacing all WMS layer backend helpers by a mock one, don't forget to remove this
                ---------------------------------------------------------------------------------""");

        for (TileLayer layer : tld.getLayerList()) {
            if (layer instanceof WMSLayer sLayer) {
                sLayer.setSourceHelper(fakeWMSSource);
            }
        }
    }

    private static WMSSourceHelper fakeWMSSource = new MockWMSSourceHelper();
}
