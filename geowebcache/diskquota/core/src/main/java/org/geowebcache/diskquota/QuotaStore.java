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
 * <p>Copyright 2019
 */
package org.geowebcache.diskquota;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import org.geowebcache.diskquota.storage.PageStats;
import org.geowebcache.diskquota.storage.PageStatsPayload;
import org.geowebcache.diskquota.storage.Quota;
import org.geowebcache.diskquota.storage.TilePage;
import org.geowebcache.diskquota.storage.TilePageCalculator;
import org.geowebcache.diskquota.storage.TileSet;
import org.geowebcache.diskquota.storage.TileSetVisitor;

public interface QuotaStore {

    public abstract void createLayer(final String layerName) throws InterruptedException;

    /**
     * Returns the globally used quota
     *
     * @return A Quota object (may be null)
     */
    public abstract Quota getGloballyUsedQuota() throws InterruptedException;

    /**
     * Returns the quota used by the specified tileSetId
     *
     * @return A Quota object (never null)
     */
    public abstract Quota getUsedQuotaByTileSetId(final String tileSetId) throws InterruptedException;

    public abstract void deleteLayer(final String layerName);

    public abstract void renameLayer(String oldLayerName, String newLayerName) throws InterruptedException;

    /**
     * @return the used quota for the given layer, may need to create a new one before returning if no quota usage
     *     information for that layer already exists
     */
    public abstract Quota getUsedQuotaByLayerName(final String layerName) throws InterruptedException;

    public abstract long[][] getTilesForPage(TilePage page) throws InterruptedException;

    public abstract Set<TileSet> getTileSets();

    public abstract TileSet getTileSetById(final String tileSetId) throws InterruptedException;

    public abstract void accept(TileSetVisitor visitor);

    public abstract TilePageCalculator getTilePageCalculator();

    /** Adds the {@link TilePage#getNumPresentTilesInPage() number of tiles} present in each of the argument pages */
    public abstract void addToQuotaAndTileCounts(
            final TileSet tileSet, final Quota quotaDiff, final Collection<PageStatsPayload> tileCountDiffs)
            throws InterruptedException;

    /**
     * Asynchronously updates (or set if not exists) the {@link PageStats#getFrequencyOfUsePerMinute()} and
     * {@link PageStats#getLastAccessTimeMinutes()} values for the stored versions of the page statistics using
     * {@link PageStats#addHitsAndAccessTime(long, int, int)}; these values are influenced by the {@code PageStats}'
     * {@link PageStats#getFillFactor() fillFactor}.
     */
    public abstract Future<List<PageStats>> addHitsAndSetAccesTime(final Collection<PageStatsPayload> statsUpdates);

    /** */
    public abstract TilePage getLeastFrequentlyUsedPage(final Set<String> layerNames) throws InterruptedException;

    /** */
    public abstract TilePage getLeastRecentlyUsedPage(final Set<String> layerNames) throws InterruptedException;

    public abstract PageStats setTruncated(final TilePage tilePage) throws InterruptedException;

    public abstract void deleteGridSubset(String layerName, String gridSetId);

    public abstract void deleteParameters(String layerName, String parametersId);

    /** Closes the quota store, releasing any resources the store might be depending onto */
    public abstract void close() throws Exception;
}
