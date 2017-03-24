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
 * @author Gabriel Roldan (OpenGeo) 2010
 *  
 */
package org.geowebcache.diskquota.storage;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.diskquota.CacheCleaner;
import org.geowebcache.diskquota.storage.PagePyramid.PageLevelInfo;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileRange;
import org.springframework.util.Assert;

/**
 * Supports the organization of tiles into groups (tile pages) for disk quota accounting purposes
 */
public class TilePageCalculator {
    
    private static final Log log = LogFactory.getLog(TilePageCalculator.class);
    
    private TileLayerDispatcher tld;
    
    private StorageBroker sb;

    public TilePageCalculator(final TileLayerDispatcher tld, final StorageBroker sb) {
        this.tld = tld;
        this.sb = sb;
    }

    public TilePage pageForTile(final TileSet tileSet, final long[] tileIndex) {
        int[] pageIndexForTile = new int[3];
        pageIndexForTile(tileSet, tileIndex, null);
        String tileSetId = tileSet.getId();
        int pageX = pageIndexForTile[0];
        int pageY = pageIndexForTile[1];
        byte zoomLevel = (byte) pageIndexForTile[2];
        return new TilePage(tileSetId, pageX, pageY, zoomLevel);
    }

    public int[] pageIndexForTile(final TileSet tileSet, final long[] tileIndex,
            int[] pageIndexTarget) {

        Assert.notNull(tileSet);
        Assert.notNull(tileIndex);
        Assert.isTrue(pageIndexTarget != null && pageIndexTarget.length > 2);

        PagePyramid pagePyramid = getPagePyramid(tileSet);
        pagePyramid.pageIndexForTile(tileIndex[0], tileIndex[1], (int) tileIndex[2],
                pageIndexTarget);
        return pageIndexTarget;
    }

    private PagePyramid getPagePyramid(TileSet tileSet) {
        PagePyramid pagePyramid = newPagePyramid(tileSet);
        return pagePyramid;
    }

    public BigInteger getTilesPerPage(TileSet tileSet, int zoomLevel) {
        PagePyramid pagePyramid = getPagePyramid(tileSet);
        PageLevelInfo pageInfo = pagePyramid.getPageInfo(zoomLevel);
        BigInteger tilesPerPage = pageInfo.tilesPerPage;
        return tilesPerPage;
    }

    /**
     * Returns a grid subset coverage range suitable for {@link TileRange}
     * 
     * @param page
     * @return {@code [minTileX, minTileY, maxTileX, maxTileY, zoomlevel]}
     */
    public long[][] toGridCoverage(TileSet tileSet, TilePage page) {
        PagePyramid pagePyramid = getPagePyramid(tileSet);
        int pageX = page.getPageX();
        int pageY = page.getPageY();
        int level = page.getZoomLevel();
        long[][] gridCoverage = pagePyramid.toGridCoverage(pageX, pageY, level);
        return gridCoverage;
    }

    public Set<String> getLayerNames() {
        return tld.getLayerNames();
    }

    public Set<TileSet> getTileSetsFor(final String layerName) {
        return getTileSetsFor(layerName, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public Set<TileSet> getTileSetsFor(final String layerName,
            final Collection<GridSubset> gridSubSets, final List<MimeType> mimeTypes) {
        
        return getTileSetsFor(layerName, Optional.of(gridSubSets), Optional.of(mimeTypes), Optional.empty());
    }
    
    public Set<TileSet> getTileSetsFor(
            final String layerName, 
            final Optional<? extends Collection<GridSubset>> gridSubsets, 
            final Optional<? extends Collection<MimeType>> mimeTypes,
            final Optional<? extends Collection<String>> parameterIds) {
        
        // If we don't have gridsets or mime types, we need to look up the layer object
        final TileLayer tileLayer;
        if(!(gridSubsets.isPresent() && mimeTypes.isPresent())) {
            try {
                tileLayer = tld.getTileLayer(layerName);
            } catch (GeoWebCacheException e) {
                throw new IllegalArgumentException(e);
            }
        } else {
            tileLayer = null;
        }
        
        // Loop over the gridset names, getting them from the layer if they were not provided
        return gridSubsets
            .map(Collection::stream)
            .map(stream->stream.map(GridSubset::getName))
            .orElse(tileLayer.getGridSubsets().stream())
            // Loop over the mime type names, getting them from the layer if they were not provided
            .flatMap(gridsetId->mimeTypes
                    .map(Collection::stream)
                    .orElse(tileLayer.getMimeTypes().stream())
                    .map(MimeType::getFormat)
                    // Loop over the parameter ids, getting them from the storage broker if they were not provided
                    .flatMap(blobFormat->parameterIds
                            .map(Collection::stream)
                            .orElseGet(()->{
                                try {
                                    return sb.getCachedParameterIds(layerName).stream();
                                } catch (StorageException e) {
                                    log.error("Error while retreiving cached parameter IDs for layer "+layerName, e);
                                    return Stream.of();
                                }
                            })
                        .map(parametersId->
                            new TileSet(layerName, gridsetId, blobFormat, parametersId))))
            .collect(Collectors.toSet());
    }
    
    private PagePyramid newPagePyramid(final TileSet tileSet) {
        final String layerName = tileSet.getLayerName();
        final TileLayer tileLayer;
        try {
            tileLayer = tld.getTileLayer(layerName);
        } catch (GeoWebCacheException e) {
            throw new IllegalArgumentException(e);
        }

        final String gridsetId = tileSet.getGridsetId();
        final GridSubset gridSubset = tileLayer.getGridSubset(gridsetId);
        return newPagePyramid(gridSubset);
    }

    PagePyramid newPagePyramid(final GridSubset gridSubset) {
        int zoomStart = gridSubset.getZoomStart();
        int zoomStop = gridSubset.getZoomStop();
        final long[][] coverages = gridSubset.getCoverages();

        PagePyramid pagePyramid = new PagePyramid(coverages, zoomStart, zoomStop);
        return pagePyramid;
    }
}
