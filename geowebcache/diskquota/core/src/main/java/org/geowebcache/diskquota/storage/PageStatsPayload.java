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
package org.geowebcache.diskquota.storage;

import org.springframework.util.Assert;

/**
 * Summarizes the changes occurred to a TilePage between two update actions on the disk quota store (the disk quota
 * subsystem does not send changes tile by tile, but accumulates them for a short while to avoid flooding the storage
 * with requests)
 */
public class PageStatsPayload {

    private int numTiles;

    private long lastAccessTime;

    private int numHits;

    private final TilePage page;

    private TileSet tileSet;

    public PageStatsPayload(final TilePage page) {
        Assert.notNull(page, "Page can't be null");
        this.page = page;
    }

    public PageStatsPayload(final TilePage page, final TileSet tileSet) {
        Assert.notNull(page, "Page can't be null");
        this.page = page;
        this.tileSet = tileSet;
    }

    public TilePage getPage() {
        return page;
    }

    public int getNumTiles() {
        return numTiles;
    }

    public void setNumTiles(int numTiles) {
        this.numTiles = numTiles;
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }

    public void setLastAccessTime(long lastAccessTime) {
        this.lastAccessTime = lastAccessTime;
    }

    public int getNumHits() {
        return numHits;
    }

    public void setNumHits(int numHits) {
        this.numHits = numHits;
    }

    public TileSet getTileSet() {
        return tileSet;
    }

    public void setTileSet(TileSet tileSet) {
        this.tileSet = tileSet;
    }

    @Override
    public String toString() {
        return "PageStatsPayload [numTiles="
                + numTiles
                + ", lastAccessTime="
                + lastAccessTime
                + ", numHits="
                + numHits
                + ", page="
                + page
                + ", tileSet="
                + tileSet
                + "]";
    }
}
