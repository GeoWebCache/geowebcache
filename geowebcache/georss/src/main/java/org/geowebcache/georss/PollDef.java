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
package org.geowebcache.georss;

import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.updatesource.GeoRSSFeedDefinition;
import org.springframework.util.Assert;

/** A tuple like holder for a scheduled georss poll */
class PollDef {
    private final TileLayer layer;

    private final GeoRSSFeedDefinition pollDef;

    public PollDef(final TileLayer layer, final GeoRSSFeedDefinition pollDef) {
        Assert.notNull(layer, "layer is null");
        Assert.notNull(pollDef, "GeoRSSFeedDefinition is null");
        this.layer = layer;
        this.pollDef = pollDef;
    }

    public String getLayerName() {
        return layer.getName();
    }

    public TileLayer getLayer() {
        return layer;
    }

    public GeoRSSFeedDefinition getPollDef() {
        return pollDef;
    }
}
