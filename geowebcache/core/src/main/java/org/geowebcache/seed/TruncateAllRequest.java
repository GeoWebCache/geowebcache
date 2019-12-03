/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Imran Rajjad / Geosolutions 2019
 */
package org.geowebcache.seed;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import java.io.Serializable;
import java.util.Iterator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;

/** @author ImranR */
@XStreamAlias("truncateAll")
public class TruncateAllRequest implements MassTruncateRequest, Serializable {

    /** serialVersionUID */
    private static final long serialVersionUID = -4730372010898498464L;

    private static final Log log = LogFactory.getLog(TruncateAllRequest.class);

    @XStreamOmitField private StringBuilder trucatedLayers = new StringBuilder();

    @Override
    public boolean doTruncate(StorageBroker sb, TileBreeder breeder)
            throws StorageException, GeoWebCacheException {
        Iterator<TileLayer> iterator = breeder.getLayers().iterator();
        TileLayer toTruncate;
        boolean truncated = false;
        while (iterator.hasNext()) {
            toTruncate = iterator.next();
            truncated = sb.delete(toTruncate.getName());
            if (!truncated) {
                // did we hit a layer that has nothing on storage, or a layer that is not there?
                try {
                    breeder.findTileLayer(toTruncate.getName());
                } catch (GeoWebCacheException e) {
                    throw new IllegalArgumentException(
                            "Could not find layer " + toTruncate.getName());
                }
            } else {
                log.info("Truncate All Job: Finished deleting layer " + toTruncate.getName());
                if (getTrucatedLayers().length() > 0) getTrucatedLayers().append(",");
                getTrucatedLayers().append(toTruncate.getName());
            }
        }

        return true;
    }

    public StringBuilder getTrucatedLayers() {
        // null safe
        if (trucatedLayers == null) trucatedLayers = new StringBuilder();
        return trucatedLayers;
    }

    public String getTrucatedLayersList() {
        if (getTrucatedLayers().length() == 0) return "No Layers were truncated";
        else return getTrucatedLayers().toString();
    }
}
