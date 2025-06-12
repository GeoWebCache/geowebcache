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
 * @author Imran Rajjad / Geosolutions 2019
 */
package org.geowebcache.seed;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import java.io.Serial;
import java.io.Serializable;
import java.util.Iterator;
import java.util.logging.Logger;
import org.geotools.util.logging.Logging;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;
import org.geowebcache.util.ServletUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** @author ImranR */
@XStreamAlias("truncateAll")
public class TruncateAllRequest implements MassTruncateRequest, Serializable {

    /** serialVersionUID */
    @Serial
    private static final long serialVersionUID = -4730372010898498464L;

    private static final Logger log = Logging.getLogger(TruncateAllRequest.class.getName());

    @XStreamOmitField
    private StringBuilder trucatedLayers = new StringBuilder();

    @Override
    public boolean doTruncate(StorageBroker sb, TileBreeder breeder) throws StorageException, GeoWebCacheException {
        Iterator<TileLayer> iterator = breeder.getLayers().iterator();
        TileLayer toTruncate;
        Iterator<String> gridSetIterator;
        String gridSetId = "";
        boolean truncated = false;
        while (iterator.hasNext()) {
            toTruncate = iterator.next();
            // get all grid sets
            gridSetIterator = toTruncate.getGridSubsets().iterator();
            while (gridSetIterator.hasNext()) {
                gridSetId = gridSetIterator.next();
                truncated = sb.deleteByGridSetId(toTruncate.getName(), gridSetId);
                log.info("Layer: " + toTruncate.getName() + ",Truncated Gridset :" + gridSetId);
            }
            if (truncated) {
                if (getTrucatedLayers().length() > 0) trucatedLayers.append(",");
                getTrucatedLayers().append(toTruncate.getName());
            }
        }

        return true;
    }

    @Override
    public ResponseEntity<String> getResponse(String contentType) {
        // for gui send page
        // for others stay legacy
        if (contentType.equalsIgnoreCase("application/x-www-form-urlencoded")) {
            return new ResponseEntity<>(getResponsePage().toString(), HttpStatus.OK);
        } else return MassTruncateRequest.super.getResponse(contentType);
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

    private StringBuilder getResponsePage() {
        StringBuilder doc = new StringBuilder();
        String content = "<p>Truncated All Layers</p>\n"
                + "<p>Truncated Layers:"
                + getTrucatedLayersList().toString()
                + "</p>";

        doc.append("<html>\n"
                + ServletUtils.gwcHtmlHeader("../", "GWC Seed Form")
                + "<body>\n"
                + ServletUtils.gwcHtmlLogoLink("../"));
        doc.append("<p>" + content + "</p> ");
        doc.append("<p><a href=\"../demo\">Go back</a></p>");
        return doc;
    }
}
